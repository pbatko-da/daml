package com.daml.platform.partymanagement

import com.daml.ledger.participant.state.index.impl.inmemory.InMemoryParticipantPartyRecordStore
import com.daml.ledger.participant.state.index.v2.ParticipantPartyRecordStore
import com.daml.platform.store.PersistentStoreSpecBase
import com.daml.platform.store.backend.StorageBackendProviderPostgres
import com.daml.platform.store.platform.partymanagement.ParticipantPartyStoreTests
import org.scalatest.Assertion
import org.scalatest.freespec.AsyncFreeSpec

import scala.concurrent.Future

class InMemoryParticipantPartyRecordStoreSpec
  extends AsyncFreeSpec
  with ParticipantPartyStoreTests
  with PersistentStoreSpecBase
  with StorageBackendProviderPostgres {

  override def testIt(f: ParticipantPartyRecordStore => Future[Assertion]): Future[Assertion] = {
    f(
      new InMemoryParticipantPartyRecordStore(
        executionContext = executionContext,
      )
    )
  }

}
