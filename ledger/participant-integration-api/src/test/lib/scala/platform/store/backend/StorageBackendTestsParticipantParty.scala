// Copyright (c) 2022 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.platform.store.backend

import java.sql.SQLException
import java.util.UUID

import com.daml.lf.data.Ref
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{Inside, OptionValues}

// TODO pbatko
private[backend] trait StorageBackendTestsParticipantParty
    extends Matchers
    with Inside
    with StorageBackendSpec
    with OptionValues {
  this: AnyFlatSpec =>

  behavior of "StorageBackend (participant party management)"

  // Representative values for each kind of user right
  private val zeroMicros: Long = 0

  private def tested = backend.participantPartyStorageBackend

  it should "compare and swap resource version" in {
    val partyRecord = newDbPartyRecord(createdAt = 123)
    val internalId = executeSql(tested.createPartyRecord(partyRecord))
    val dbParty = executeSql(tested.getPartyRecord(party = partyRecord.party)).value
    dbParty.payload.resourceVersion shouldBe 0
    executeSql(
      tested.compareAndIncreaseResourceVersion(
        internalId = internalId,
        expectedResourceVersion = 0,
      )
    ) shouldBe true
    executeSql(
      tested.compareAndIncreaseResourceVersion(
        internalId = internalId,
        expectedResourceVersion = 404,
      )
    ) shouldBe false
    executeSql(
      tested.compareAndIncreaseResourceVersion(
        internalId = internalId,
        expectedResourceVersion = 1,
      )
    ) shouldBe true
    executeSql(
      tested.getPartyRecord(party = partyRecord.party)
    ).value.payload.resourceVersion shouldBe 2
  }

  // TODO pbatko: conf test - test annotations size limit
  it should "get, add and delete party's annotations" in {
    val partyRecord = newDbPartyRecord(createdAt = 123)
    val internalId = executeSql(tested.createPartyRecord(partyRecord))
    executeSql(tested.getPartyAnnotations(internalId)) shouldBe Map.empty
    // Add key1
    executeSql(
      tested.addPartyAnnotation(internalId, key = "key1", value = "value1", updatedAt = 123)
    )
    executeSql(tested.getPartyAnnotations(internalId)) shouldBe Map("key1" -> "value1")
    // Add key2
    executeSql(
      tested.addPartyAnnotation(internalId, key = "key2", value = "value2", updatedAt = 123)
    )
    executeSql(tested.getPartyAnnotations(internalId)) shouldBe Map(
      "key1" -> "value1",
      "key2" -> "value2",
    )
    // Duplicated key2
    assertThrows[SQLException](
      executeSql(
        tested.addPartyAnnotation(internalId, key = "key2", value = "value2b", updatedAt = 123)
      )
    )
    // Delete
    executeSql(tested.deletePartyAnnotations(internalId))
    executeSql(tested.getPartyAnnotations(internalId)) shouldBe Map.empty
  }

  it should "handle created_at attribute correctly" in {
    val partyRecord = newDbPartyRecord(createdAt = 123)
    val _ = executeSql(tested.createPartyRecord(partyRecord))
    executeSql(tested.getPartyRecord(partyRecord.party)).map(_.payload.createdAt) shouldBe Some(123)
  }

  it should "create party record (createPartyRecord)" in {
    val partyRecord1 = newDbPartyRecord()
    val partyRecord2 = newDbPartyRecord()
    val internalId1 = executeSql(tested.createPartyRecord(partyRecord1))
    // Attempting to add a duplicate user
    assertThrows[SQLException](executeSql(tested.createPartyRecord(partyRecord1)))
    val internalId2 = executeSql(tested.createPartyRecord(partyRecord2))
    val _ =
      executeSql(tested.createPartyRecord(newDbPartyRecord()))
    internalId1 should not equal internalId2
  }

  it should "handle user ops (getPartyRecord)" in {
    val partyRecord1 = newDbPartyRecord()
    val partyRecord2 = newDbPartyRecord()
    val _ = executeSql(tested.createPartyRecord(partyRecord1))
    val getExisting = executeSql(tested.getPartyRecord(partyRecord1.party))
    val getNonexistent = executeSql(tested.getPartyRecord(partyRecord2.party))
    getExisting.value.payload shouldBe partyRecord1
    getNonexistent shouldBe None
  }

//  it should "get all users (getUsers) ordered by id" in {
//    val user1 = newDbPartyRecord(partyId = "user_id_1")
//    val user2 = newDbPartyRecord(partyId = "user_id_2")
//    val user3 = newDbPartyRecord(partyId = "user_id_3")
//    executeSql(tested.getUsersOrderedById(fromExcl = None, maxResults = 10)) shouldBe empty
//    val _ = executeSql(tested.createUser(user3))
//    val _ = executeSql(tested.createUser(user1))
//    executeSql(tested.getUsersOrderedById(fromExcl = None, maxResults = 10))
//      .map(_.payload) shouldBe Seq(
//      user1,
//      user3,
//    )
//    val _ = executeSql(tested.createUser(user2))
//    executeSql(tested.getUsersOrderedById(fromExcl = None, maxResults = 10))
//      .map(_.payload) shouldBe Seq(
//      user1,
//      user2,
//      user3,
//    )
//  }

//  it should "get all users (getUsers) ordered by id using binary collation" in {
//    val user1 = newDbPartyRecord(partyId = "a")
//    val user2 = newDbPartyRecord(partyId = "a!")
//    val user3 = newDbPartyRecord(partyId = "b")
//    val user4 = newDbPartyRecord(partyId = "a_")
//    val user5 = newDbPartyRecord(partyId = "!a")
//    val user6 = newDbPartyRecord(partyId = "_a")
//    val users = Seq(user1, user2, user3, user4, user5, user6)
//    users.foreach(user => executeSql(tested.createUser(user)))
//    executeSql(tested.getUsersOrderedById(fromExcl = None, maxResults = 10))
//      .map(_.payload.id) shouldBe Seq("!a", "_a", "a", "a!", "a_", "b")
//  }

  private def newDbPartyRecord(
      partyId: String = "",
      resourceVersion: Long = 0,
      createdAt: Long = zeroMicros,
  ): ParticipantPartyStorageBackend.DbPartyPayload = {
    val uuid = UUID.randomUUID.toString
    val party = if (partyId != "") partyId else s"party_id_$uuid"
    ParticipantPartyStorageBackend.DbPartyPayload(
      party = Ref.Party.assertFromString(party),
      resourceVersion = resourceVersion,
      createdAt = createdAt,
    )
  }

}
