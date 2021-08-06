// Copyright (c) 2021 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.platform.store.backend

import com.daml.ledger.api.domain.{LedgerId, ParticipantId}
import com.daml.lf.data.Ref
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Future
import scala.util.Success

private[backend] trait StorageBackendTestsInitialization[DB_BATCH] extends Matchers {
  this: AsyncFlatSpec with StorageBackendSpec[DB_BATCH] =>

  behavior of "StorageBackend (initialization)"

  import StorageBackendTestValues._

  it should "correctly handle repeated initialization" in {
    val ledgerId = LedgerId("ledger")
    val participantId = ParticipantId(Ref.ParticipantId.assertFromString("participant"))
    val otherLedgerId = LedgerId("otherLedger")
    val otherParticipantId = ParticipantId(Ref.ParticipantId.assertFromString("otherParticipant"))

    for {
      result1 <- executeSql(
        backend.initializeParameters(
          StorageBackend.IdentityParams(
            ledgerId = ledgerId,
            participantId = participantId,
          )
        )
      )
      result2 <- executeSql(
        backend.initializeParameters(
          StorageBackend.IdentityParams(
            ledgerId = otherLedgerId,
            participantId = participantId,
          )
        )
      )
      result3 <- executeSql(
        backend.initializeParameters(
          StorageBackend.IdentityParams(
            ledgerId = ledgerId,
            participantId = otherParticipantId,
          )
        )
      )
      result4 <- executeSql(
        backend.initializeParameters(
          StorageBackend.IdentityParams(
            ledgerId = ledgerId,
            participantId = participantId,
          )
        )
      )
    } yield {
      result1 shouldBe StorageBackend.InitializationResult.New
      result2 shouldBe StorageBackend.InitializationResult.Mismatch(ledgerId, Some(participantId))
      result3 shouldBe StorageBackend.InitializationResult.Mismatch(ledgerId, Some(participantId))
      result4 shouldBe StorageBackend.InitializationResult.AlreadyExists
    }
  }

  it should "not allow duplicate initialization" in {
    val n: Int = 64

    for {
      result <- Future.sequence(
        Vector.fill(n)(
          executeSerializableSql(backend.initializeParameters(someIdentityParams))
            .transform(x => Success(x.toEither))
        )
      )
    } yield {
      // Note: the StorageBackend.initializeParameters() call may fail if it conflicts with another concurrent call.
      // In such a rare case, the indexer would fail to start, and the RecoveringIndexer would be responsible
      // for restarting the indexer.
      // This test therefore only asserts that there is exactly one call that initializes the parameter table,
      // it does not handle failed calls in any way.
      result.collect { case Right(StorageBackend.InitializationResult.New) =>
        true
      } should have length 1
      result.collect { case Right(StorageBackend.InitializationResult.Mismatch(_, _)) =>
        true
      } should have length 0
    }
  }
}
