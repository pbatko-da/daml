// Copyright (c) 2022 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.ledger.participant.state.index.v2

import com.daml.ledger.api.domain
import com.daml.lf.data.Ref
import com.daml.logging.LoggingContext

import scala.concurrent.{Future}

trait ParticipantPartyStore {
  import ParticipantPartyStore._

  def createPartyRecord(partyRecord: domain.ParticipantParty.PartyRecord)(implicit
      loggingContext: LoggingContext
  ): Future[Result[domain.ParticipantParty.PartyRecord]]

  def updatePartyRecord(partyRecordUpdate: PartyRecordUpdate)(implicit
      loggingContext: LoggingContext
  ): Future[Result[domain.ParticipantParty.PartyRecord]]

  def getPartyRecord(party: Ref.Party)(implicit
      loggingContext: LoggingContext
  ): Future[Result[domain.ParticipantParty.PartyRecord]]

}

object ParticipantPartyStore {
  type Result[T] = Either[Error, T]

  sealed trait Error extends RuntimeException
  final case class PartyNotFound(party: Ref.Party) extends Error
  final case class PartyExists(party: Ref.Party) extends Error
  final case class ConcurrentPartyUpdateDetected(party: Ref.Party) extends Error
}
