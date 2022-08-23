// Copyright (c) 2022 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.platform.partymanagement

import java.sql.Connection

import com.daml.api.util.TimeProvider
import com.daml.ledger.api.domain
import com.daml.ledger.api.domain.ParticipantParty
import com.daml.ledger.participant.state.index.v2.ParticipantPartyStore.{
  ConcurrentPartyUpdateDetected,
  Result,
}
import com.daml.ledger.participant.state.index.v2.{ParticipantPartyStore, PartyRecordUpdate}
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
    timeProvider: TimeProvider,
) extends ParticipantPartyStore {

  private val backend = dbSupport.storageBackendFactory.createParticipantPartyStorageBackend
  private val dbDispatcher = dbSupport.dbDispatcher

  private val logger = ContextualizedLogger.get(getClass)

  override def createPartyRecord(partyRecord: domain.ParticipantParty.PartyRecord)(implicit
      loggingContext: LoggingContext
  ): Future[Result[domain.ParticipantParty.PartyRecord]] = {
    inTransaction(_.createPartyRecord) { implicit connection: Connection =>
      withoutPartyRecord(id = partyRecord.party) {
        val now = epochMicroseconds()
        val dbParty = ParticipantPartyStorageBackend.DbPartyPayload(
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
        toDomainPartyRecord(dbParty, partyRecord.metadata.annotations)
      }
    }.map(tapSuccess { _ =>
      logger.error(
        s"Created new party record in participant local store: ${partyRecord}"
      )
    })(scala.concurrent.ExecutionContext.parasitic)
  }

  override def updatePartyRecord(partyRecordUpdate: PartyRecordUpdate)(implicit
      loggingContext: LoggingContext
  ): Future[Result[domain.ParticipantParty.PartyRecord]] = {
    inTransaction(_.updatePartyRecord) { implicit connection =>
      for {
        // TODO pbatko: If party exists on a ledger but there is not party record then create one quickly
        _ <- withPartyRecord(partyRecordUpdate.party) { dbPartyRecord =>
          val now = epochMicroseconds()

          // TODO pbatko: NOTE: Every invocation of this method will start with updating resourceVersion effectively getting an exclusive lock on it
          // update resource version
          // TODO implement resource version as a bigint attribute in participant_users table
          if (partyRecordUpdate.metadataUpdate.resourceVersionO.isDefined) {
            val expectedResourceVersion =
              partyRecordUpdate.metadataUpdate.resourceVersionO.get.toLong
            if (
              !backend.compareAndIncreaseResourceVersion(
                internalId = dbPartyRecord.internalId,
                expectedResourceVersion = expectedResourceVersion,
              )(connection)
            ) {
              throw ConcurrentPartyRecordUpdateDetectedRuntimeException(partyRecordUpdate.party)
            } else {
              backend.increaseResourceVersion(
                internalId = dbPartyRecord.internalId
              )(connection)
            }
          }

          // update annotations - replace-all
          // TODO implement merge semantics
//          val existingAnnotations = backend.getPartyAnnotations(dbPartyRecord.internalId)(connection)

          partyRecordUpdate.metadataUpdate.annotationsUpdate.foreach { newAnnotations =>
            val existingAnnotations: Map[String, String] =
              backend.getPartyAnnotations(dbPartyRecord.internalId)(connection)

            val updatedAnnotations =
              if (partyRecordUpdate.metadataUpdate.replaceAnnotations) {
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
          ()
        }
        updatedPartyRecord <- withPartyRecord(partyRecordUpdate.party) { updatedDbPartyRecord =>
          val annotations = backend.getPartyAnnotations(updatedDbPartyRecord.internalId)(connection)
          toDomainPartyRecord(
            updatedDbPartyRecord.payload,
            annotations,
          )
        }
      } yield updatedPartyRecord
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

  private def toDomainPartyRecord(
      dbParty: ParticipantPartyStorageBackend.DbPartyPayload,
      annotations: Map[String, String],
  ): domain.ParticipantParty.PartyRecord = {
    val payload = dbParty
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
      f: ParticipantPartyStorageBackend.DbPartyWithId => T
  )(implicit connection: Connection): Result[T] = {
    backend.getPartyRecord(party = id)(connection) match {
      case Some(party) => Right(f(party))
      case None => Left(ParticipantPartyStore.PartyNotFound(party = id))
    }
  }

  private def withoutPartyRecord[T](
      id: Ref.Party
  )(t: => T)(implicit connection: Connection): Result[T] = {
    backend.getPartyRecord(party = id)(connection) match {
      case Some(party) => Left(ParticipantPartyStore.PartyExists(party = party.payload.party))
      case None => Right(t)
    }
  }

  private def inTransaction[T](
      dbMetric: metrics.daml.participantPartyManagement.type => DatabaseMetrics
  )(thunk: Connection => Result[T])(implicit loggingContext: LoggingContext): Future[Result[T]] = {
    dbDispatcher
      .executeSql(dbMetric(metrics.daml.participantPartyManagement))(thunk)
      .recover[Result[T]] { case ConcurrentPartyRecordUpdateDetectedRuntimeException(userId) =>
        Left(ConcurrentPartyUpdateDetected(userId))
      }(ExecutionContext.parasitic)
  }

  private def epochMicroseconds(): Long = {
    val now = timeProvider.getCurrentTime
    (now.getEpochSecond * 1000 * 1000) + (now.getNano / 1000)
  }

  private def tapSuccess[T](f: T => Unit)(r: Result[T]): Result[T] = {
    r.foreach(f)
    r
  }

}
