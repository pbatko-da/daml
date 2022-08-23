package com.daml.platform.partymanagement

import com.codahale.metrics.MetricRegistry
import com.daml.api.util.TimeProvider
import com.daml.ledger.participant.state.index.v2.ParticipantPartyRecordStore
import com.daml.metrics.Metrics
import com.daml.platform.store.PersistentStoreSpecBase
import com.daml.platform.store.backend.StorageBackendProviderPostgres
import com.daml.platform.store.platform.partymanagement.ParticipantPartyStoreTests
import org.scalatest.Assertion
import org.scalatest.freespec.AsyncFreeSpec

import scala.concurrent.Future

class PersistentParticipantPartyRecordStoreSpec
  extends AsyncFreeSpec
  with ParticipantPartyStoreTests
  with PersistentStoreSpecBase
  with StorageBackendProviderPostgres {

  override def testIt(f: ParticipantPartyRecordStore => Future[Assertion]): Future[Assertion] = {
    f(
      new PersistentParticipantPartyStore(
        dbSupport = dbSupport,
        metrics = new Metrics(new MetricRegistry()),
        timeProvider = TimeProvider.UTC,
        executionContext = executionContext,
      )
    )
  }

}
