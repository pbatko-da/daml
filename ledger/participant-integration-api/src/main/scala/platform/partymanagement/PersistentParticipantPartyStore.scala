// Copyright (c) 2022 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.platform.partymanagement

import java.sql.Connection

import com.daml.api.util.TimeProvider
import com.daml.ledger.api.domain
import com.daml.ledger.api.domain.ParticipantParty
import com.daml.ledger.participant.state.index.v2.ParticipantPartyRecordStore.{
  ConcurrentPartyUpdate,
  PartyRecordNotFoundOnUpdateException,
  Result,
}
import com.daml.ledger.participant.state.index.v2.{
  LedgerPartyExists,
  ParticipantPartyRecordStore,
  PartyRecordUpdate,
}
import com.daml.lf.data.Ref
import com.daml.lf.data.Ref.Party
import com.daml.logging.{ContextualizedLogger, LoggingContext}
import com.daml.metrics.{DatabaseMetrics, Metrics}
import com.daml.platform.partymanagement.PersistentParticipantPartyStore.ConcurrentPartyRecordUpdateDetectedRuntimeException
import com.daml.platform.store.DbSupport
import com.daml.platform.store.backend.ParticipantPartyStorageBackend

import scala.concurrent.{ExecutionContext, Future}

object PersistentParticipantPartyStore {

  final case class ConcurrentPartyRecordUpdateDetectedRuntimeException(partyId: Ref.Party)
      extends RuntimeException

}

class PersistentParticipantPartyStore(
    dbSupport: DbSupport,
    metrics: Metrics,
    protected val timeProvider: TimeProvider,
    executionContext: ExecutionContext,
) extends ParticipantPartyRecordStore
    with EpochMicrosecondsMethodMixin {

  private implicit val ec: ExecutionContext = executionContext

  private val backend = dbSupport.storageBackendFactory.createParticipantPartyStorageBackend
  private val dbDispatcher = dbSupport.dbDispatcher

  private val logger = ContextualizedLogger.get(getClass)

  override def createPartyRecord(partyRecord: domain.ParticipantParty.PartyRecord)(implicit
      loggingContext: LoggingContext
  ): Future[Result[domain.ParticipantParty.PartyRecord]] = {
    inTransaction(_.createPartyRecord) { implicit connection: Connection =>
      for {
        _ <- withoutPartyRecord(id = partyRecord.party) {
        doCreatePartyRecord (partyRecord) (connection)
        }
        createPartyRecord <- withPartyRecord(id = partyRecord.party) { dbPartyRecord =>
          val annotations = backend.getPartyAnnotations(dbPartyRecord.internalId)(connection)
          toDomainPartyRecord(
            dbPartyRecord.payload,
            annotations,
          )
        }
      } yield createPartyRecord
    }.map(tapSuccess { _ =>
      logger.error(
        s"Created new party record in participant local store: ${partyRecord}"
      )
    })(scala.concurrent.ExecutionContext.parasitic)
  }

  // TODO pbatko: Do all in one transaction -
  override def updatePartyRecord(
      partyRecordUpdate: PartyRecordUpdate,
      ledgerPartyExists: LedgerPartyExists,
  )(implicit
      loggingContext: LoggingContext
  ): Future[Result[domain.ParticipantParty.PartyRecord]] = {
    val party = partyRecordUpdate.party
    val firstF = inTransaction(_.getPartyRecord) { implicit connection =>
      backend.getPartyRecord(party = party)(connection) match {
        // Update an existing party
        case Some(dbPartyRecord) =>
          doUpdatePartyRecord(
            dbPartyRecord = dbPartyRecord,
            partyRecordUpdate = partyRecordUpdate,
          )(connection)
          withPartyRecord(party) { updatedDbPartyRecord =>
            val annotations = backend.getPartyAnnotations(dbPartyRecord.internalId)(connection)
            toDomainPartyRecord(
              payload = updatedDbPartyRecord.payload,
              annotations = annotations,
            )
          }
        case None =>
          throw PartyRecordNotFoundOnUpdateException
      }
    }.map(tapSuccess { updatePartyRecord =>
      logger.info(s"Updated party record in participant local store: ${updatePartyRecord}")
    })

    firstF.recoverWith[Result[domain.ParticipantParty.PartyRecord]] {
      case PartyRecordNotFoundOnUpdateException => {
        for {
          partyExistsOnLedger <- ledgerPartyExists.exists(party)
          createdPartyRecord <-
            if (partyExistsOnLedger) {
              val newPartyRecord = domain.ParticipantParty.PartyRecord(
                party = party,
                metadata = domain.ObjectMeta.empty,
              )
              inTransaction(_.createPartyRecordOnUpdate) { implicit connection =>
                for {
                  _ <- withoutPartyRecord(id = newPartyRecord.party) {
                   doCreatePartyRecord(newPartyRecord)(connection)
                 }
                  _ <- withPartyRecord(party){ dbPartyRecord =>
                    doUpdatePartyRecord(
                      dbPartyRecord = dbPartyRecord,
                      partyRecordUpdate = partyRecordUpdate,
                    )(connection)
                  }
                  updatePartyRecord <- {
                    withPartyRecord(party) { updatedDbPartyRecord =>
                      val annotations = backend.getPartyAnnotations(updatedDbPartyRecord.internalId)(connection)
                      toDomainPartyRecord(
                        payload = updatedDbPartyRecord.payload,
                        annotations = annotations,
                      )
                    }

                  }
                } yield updatePartyRecord
              }.map(tapSuccess { newPartyRecord =>
                logger.error(
                  s"Party record to update didn't exist so created a new party record in participant local store: ${newPartyRecord}"
                )
              })(scala.concurrent.ExecutionContext.parasitic)
            } else {
              Future.successful(
                Left(ParticipantPartyRecordStore.PartyNotFound(party))
              )
            }
        } yield createdPartyRecord
      }

//          .map(tapSuccess { updatePartyRecord: ParticipantParty.PartyRecord =>
//          logger.error(
//            s"Updated party record in participant local store: ${updatePartyRecord}"
//          )
//        })(scala.concurrent.ExecutionContext.parasitic)
//          Right(backend.getPartyRecord(party = partyRecordUpdate.party)(connection))
//      updatePartyRecord <- partyRecordResultO match {
//        case Left(error) =>
//          Future.successful(Left(error))
//         Case 1: Party record does not exist. Action: create it.
//        case Right(None) =>
//      }
//    } yield updatePartyRecord
    }
  }

  override def getPartyRecord(
      party: Party
  )(implicit loggingContext: LoggingContext): Future[Result[ParticipantParty.PartyRecord]] = {
    inTransaction(_.getPartyRecord) { implicit connection =>
      withPartyRecord(party) { dbPartyRecord =>
        val annotations = backend.getPartyAnnotations(dbPartyRecord.internalId)(connection)
        toDomainPartyRecord(dbPartyRecord.payload, annotations)
      }
    }
  }

  private def doCreatePartyRecord(
      partyRecord: ParticipantParty.PartyRecord
  )(connection: Connection): Unit = {
    val now = epochMicroseconds()
    val dbParty = ParticipantPartyStorageBackend.DbPartyRecordPayload(
      party = partyRecord.party,
      resourceVersion = 0,
      createdAt = now,
    )
    val internalId = backend.createPartyRecord(
      partyRecord = dbParty
    )(connection)
    partyRecord.metadata.annotations.foreach { case (key, value) =>
      backend.addPartyAnnotation(
        internalId = internalId,
        key = key,
        value = value,
        updatedAt = now,
      )(connection)
    }
  }

  private def doUpdatePartyRecord(
      dbPartyRecord: ParticipantPartyStorageBackend.DbPartyRecord,
      partyRecordUpdate: PartyRecordUpdate,
  )(connection: Connection): Unit = {
    val now = epochMicroseconds()
    // Step 1: Update resource version
    //
    // NOTE: Each update operations (w/ or w/o resource-version) starts by writing to the 'resource_version' attribute
    //       of 'participant_party_records' to effectively obtain an exclusive lock for updating some party.
    //
    //       DB Details:
    //
    //       1) In Postgres under the default isolation level (Read Committed), if there are multiple on-going transactions
    //       writing to the same row, the fastest will write and continue while all the others will wait wait
    //       until the fastest commits or aborts.
    //
    //       2) In Oracle: TODO pbatko - if it's hard to achieve PG behavior consider locking in JVM (e.g. concurrent map)
    //       3) In H2: TODO pbatko
    //
    partyRecordUpdate.metadataUpdate.resourceVersionO match {
      case Some(expectedResourceVersion) =>
        if (
          !backend.compareAndIncreaseResourceVersion(
            internalId = dbPartyRecord.internalId,
            // TODO pbatko: Centralize resourceVersion parsing
            expectedResourceVersion = expectedResourceVersion.toLong,
          )(connection)
        ) {
          throw ConcurrentPartyRecordUpdateDetectedRuntimeException(
            partyRecordUpdate.party
          )
        }
      case None =>
        backend.increaseResourceVersion(
          internalId = dbPartyRecord.internalId
        )(connection)
    }
    // Step 2: Update annotations
    partyRecordUpdate.metadataUpdate.annotationsUpdate.foreach { newAnnotations =>
      val existingAnnotations = backend.getPartyAnnotations(dbPartyRecord.internalId)(connection)
      val updatedAnnotations = if (partyRecordUpdate.metadataUpdate.replaceAnnotations) {
        newAnnotations
      } else {
        existingAnnotations.concat(newAnnotations)
      }
      backend.deletePartyAnnotations(internalId = dbPartyRecord.internalId)(connection)
      updatedAnnotations.iterator.foreach { case (key, value) =>
        backend.addPartyAnnotation(
          internalId = dbPartyRecord.internalId,
          key = key,
          value = value,
          updatedAt = now,
        )(connection)
      }
    }
  }

  private def toDomainPartyRecord(
      payload: ParticipantPartyStorageBackend.DbPartyRecordPayload,
      annotations: Map[String, String],
  ): domain.ParticipantParty.PartyRecord = {
    domain.ParticipantParty.PartyRecord(
      party = payload.party,
      metadata = domain.ObjectMeta(
        resourceVersionO = Some(payload.resourceVersion.toString),
        annotations = annotations,
      ),
    )
  }

  private def withPartyRecord[T](
      id: Ref.Party
  )(
      f: ParticipantPartyStorageBackend.DbPartyRecord => T
  )(implicit connection: Connection): Result[T] = {
    backend.getPartyRecord(party = id)(connection) match {
      case Some(partyRecord) => Right(f(partyRecord))
      case None => Left(ParticipantPartyRecordStore.PartyRecordNotFound(party = id))
    }
  }

  private def withoutPartyRecord[T](
      id: Ref.Party
  )(t: => T)(implicit connection: Connection): Result[T] = {
    backend.getPartyRecord(party = id)(connection) match {
      case Some(partyRecord) =>
        Left(ParticipantPartyRecordStore.PartyRecordExists(party = partyRecord.payload.party))
      case None => Right(t)
    }
  }

  private def inTransaction[T](
      dbMetric: metrics.daml.participantPartyManagement.type => DatabaseMetrics
  )(thunk: Connection => Result[T])(implicit loggingContext: LoggingContext): Future[Result[T]] = {
    dbDispatcher
      .executeSql(dbMetric(metrics.daml.participantPartyManagement))(thunk)
      .recover[Result[T]] { case ConcurrentPartyRecordUpdateDetectedRuntimeException(userId) =>
        Left(ConcurrentPartyUpdate(userId))
      }(ExecutionContext.parasitic)
  }

  private def tapSuccess[T](f: T => Unit)(r: Result[T]): Result[T] = {
    r.foreach(f)
    r
  }
}
