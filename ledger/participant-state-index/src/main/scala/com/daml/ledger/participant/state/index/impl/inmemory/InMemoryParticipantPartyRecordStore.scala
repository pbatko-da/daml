package com.daml.ledger.participant.state.index.impl.inmemory

import com.daml.ledger.api.domain
import com.daml.ledger.api.domain.{ObjectMeta, ParticipantParty}
import com.daml.ledger.participant.state.index.v2.ParticipantPartyRecordStore.{PartyRecordExists, PartyRecordNotFound, PartyRecordNotFoundOnUpdateException, Result}
import com.daml.ledger.participant.state.index.v2.{LedgerPartyExists, ParticipantPartyRecordStore, PartyRecordUpdate}
import com.daml.lf.data.Ref
import com.daml.lf.data.Ref.Party
import com.daml.logging.{ContextualizedLogger, LoggingContext}

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

object InMemoryParticipantPartyRecordStore {
  case class PartyRecordInfo(
      party: Ref.Party,
      resourceVersion: Int,
      annotations: Map[String, String],
  )

  def toPartyRecord(info: PartyRecordInfo): ParticipantParty.PartyRecord =
    ParticipantParty.PartyRecord(
      party = info.party,
      metadata = ObjectMeta(
        resourceVersionO = Some(info.resourceVersion.toString),
        annotations = info.annotations,
      ),
    )

}
class InMemoryParticipantPartyRecordStore(executionContext: ExecutionContext)
    extends ParticipantPartyRecordStore {
  import InMemoryParticipantPartyRecordStore._

  implicit private val ec: ExecutionContext = executionContext
  private val logger = ContextualizedLogger.get(getClass)

  // Underlying mutable map to keep track of UserInfo state.
  // Structured so we can use a ConcurrentHashMap (to more closely mimic a real implementation, where performance is key).
  // We synchronize on a private object (the mutable map), not the service (which could cause deadlocks).
  // (No need to mark state as volatile -- rely on synchronized to establish the JMM's happens-before relation.)
  private val state: mutable.TreeMap[Ref.Party, PartyRecordInfo] = mutable.TreeMap()

  override def getPartyRecord(
      party: Party
  )(implicit loggingContext: LoggingContext): Future[Result[ParticipantParty.PartyRecord]] = {
    withState(withPartyRecord[ParticipantParty.PartyRecord](party)(toPartyRecord))
  }

  override def createPartyRecord(
      partyRecord: ParticipantParty.PartyRecord
  )(implicit loggingContext: LoggingContext): Future[Result[ParticipantParty.PartyRecord]] = {
    withState(withoutPartyRecord(partyRecord.party) {
      val info = doCreatePartyRecord(partyRecord)
      toPartyRecord(info)
    })
  }

  // TODO pbatko: Conformance test: race conditions: multiple update non-existing party-record (existing ledger party) calls
  override def updatePartyRecord(
      partyRecordUpdate: PartyRecordUpdate,
      ledgerPartyExists: LedgerPartyExists,
  )(implicit loggingContext: LoggingContext): Future[Result[ParticipantParty.PartyRecord]] = {
    if(!partyRecordUpdate.metadataUpdate.replaceAnnotations && partyRecordUpdate.metadataUpdate.annotationsUpdate == Some(Map.empty)){
      Future.successful(Left(ExplicitMergeUpdateWithDefaultValue))
    }
    val party = partyRecordUpdate.party
    val firstF = withState(
      state.get(party) match {
        case Some(info) =>
          val updatedInfo: PartyRecordInfo = doUpdatePartyRecord(
            partyRecordUpdate = partyRecordUpdate,
            party = party,
            info = info,
          )
          Right(toPartyRecord(updatedInfo))
        case None =>
          throw PartyRecordNotFoundOnUpdateException
      }
    ).map(tapSuccess { updatePartyRecord =>
      logger.info(s"Updated party record in participant local store: ${updatePartyRecord}")
    })
    firstF.recoverWith[Result[domain.ParticipantParty.PartyRecord]] {
      case PartyRecordNotFoundOnUpdateException =>
        for {
          partyExistsOnLedger <- ledgerPartyExists.exists(party)
          createdPartyRecord <-
            if (partyExistsOnLedger) {
              withState {
                val newPartyRecord = domain.ParticipantParty.PartyRecord(
                  party = party,
                  metadata = domain.ObjectMeta.empty,
                )
                for {
                  _ <- withoutPartyRecord(party = newPartyRecord.party) {
                    doCreatePartyRecord(newPartyRecord)
                  }
                  _ <- withPartyRecord(party) { info =>
                    val updatedInfo: PartyRecordInfo = doUpdatePartyRecord(
                      partyRecordUpdate = partyRecordUpdate,
                      party = party,
                      info = info,
                    )
                    toPartyRecord(updatedInfo)
                  }
                  updatePartyRecord <- {
                    withPartyRecord(party) { updatedInfo =>
                      toPartyRecord(updatedInfo)
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
  }
//      existsOnLedger: Boolean <- ledgerPartyExists.exists(party)
//      updatedPartyRecord <-
//        if (!existsOnLedger) {
      //          Future.successful(Left(ParticipantPartyRecordStore.PartyNotFound(party)))
      //        } else {
      //          withState(
      //            state.get(party) match {
      //              case Some(info) =>
      //                val updatedAnnotations = partyRecordUpdate.metadataUpdate.annotationsUpdate.map { newAnnotations =>
      //                  val existingAnnotations = info.annotations
      //                  if (partyRecordUpdate.metadataUpdate.replaceAnnotations) {
      //                    newAnnotations
      //                  } else {
      //                    existingAnnotations.concat(newAnnotations)
      //                  }
      //                }.getOrElse(info.annotations)
      //                val updatedInfo = PartyRecordInfo(
      //                  party = party,
      //                  resourceVersion = info.resourceVersion + 1,
      //                  annotations = updatedAnnotations
      //                )
      //                state.put(party, updatedInfo)
      //                Right(toPartyRecord(updatedInfo))
      //              case None =>
      //                val newParty = ParticipantParty.PartyRecord(
      //                  party = party,
      //                  metadata = ObjectMeta(
      //                    resourceVersionO = None,
      //                    annotations = Map.empty,
      //                  ),
      //                )
      //                val newInfo = doCreatePartyRecord(
      //                  partyRecord = newParty
      //                )
      //                Right(toPartyRecord(newInfo))
      //            }
      //          )
      //
      //
      //    } yield updatedPartyRecord
//  }
//    }

  private def doUpdatePartyRecord(partyRecordUpdate: PartyRecordUpdate,
                                  party: Party,
                                  info: PartyRecordInfo,
                                 ): PartyRecordInfo = {
    val updatedAnnotations = partyRecordUpdate.metadataUpdate.annotationsUpdate
      .map { newAnnotations =>
        val existingAnnotations = info.annotations
        if (partyRecordUpdate.metadataUpdate.replaceAnnotations) {
          newAnnotations
        } else {
          existingAnnotations.concat(newAnnotations)
        }
      }
      .getOrElse(info.annotations)
    val updatedInfo = PartyRecordInfo(
      party = party,
      resourceVersion = info.resourceVersion + 1,
      annotations = updatedAnnotations,
    )
    state.put(party, updatedInfo)
    updatedInfo
  }

  private def doCreatePartyRecord(
      partyRecord: ParticipantParty.PartyRecord
  ): PartyRecordInfo = {
    val info = PartyRecordInfo(
      party = partyRecord.party,
      resourceVersion = 0,
      annotations = partyRecord.metadata.annotations,
    )
    state.update(partyRecord.party, info)
    info
  }

  private def withState[T](t: => T): Future[T] =
    state.synchronized(
      Future(t)
    )

  private def withPartyRecord[T](
      party: Ref.Party
  )(f: InMemoryParticipantPartyRecordStore.PartyRecordInfo => T): Result[T] =
      state.get(party) match {
        case Some(partyRecord) => Right(f(partyRecord))
        case None => Left(PartyRecordNotFound(party))
      }

  private def withoutPartyRecord[T](party: Ref.Party)(t: => T): Result[T] =
      state.get(party) match {
        case Some(_) => Left(PartyRecordExists(party))
        case None => Right(t)
      }

  private def tapSuccess[T](f: T => Unit)(r: Result[T]): Result[T] = {
  r.foreach(f)
  r
  }
}
