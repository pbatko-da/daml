// Copyright (c) 2022 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.platform.store.backend.common

import java.sql.Connection

import com.daml.platform.store.backend.{ParticipantPartyStorageBackend}

import anorm.SqlParser.{int, long, str}
import anorm.{RowParser, SqlParser, SqlStringInterpolation, ~}
import com.daml.lf.data.Ref

import scala.util.Try

// TODO pbatko: test two transaction trying to update a resource - concurrent change control
class ParticipantPartyStorageBackendTemplate extends ParticipantPartyStorageBackend {

  private val ParticipantPartyRecordParser: RowParser[(Int, String, Long, Long)] =
    int("internal_id") ~
      str("party") ~
      long("resource_version") ~
      long("created_at") map { case internalId ~ party ~ resourceVersion ~ createdAt =>
        (internalId, party, resourceVersion, createdAt)
      }

  override def getPartyRecord(
      party: Ref.Party
  )(connection: Connection): Option[ParticipantPartyStorageBackend.DbPartyWithId] = {
    SQL"""
         SELECT
             internal_id,
             party,
             resource_version,
             created_at
         FROM participant_parties
         WHERE
             party = ${party: String}
       """
      .as(ParticipantPartyRecordParser.singleOpt)(connection)
      .map { case (internalId, party, resourceVersion, createdAt) =>
        ParticipantPartyStorageBackend.DbPartyWithId(
          internalId = internalId,
          payload = ParticipantPartyStorageBackend.DbPartyPayload(
            party = com.daml.platform.Party.assertFromString(party),
            resourceVersion = resourceVersion,
            createdAt = createdAt,
          ),
        )
      }
  }

  override def createPartyRecord(
      partyRecord: ParticipantPartyStorageBackend.DbPartyPayload
  )(connection: Connection): Int = {
    val party = partyRecord.party: String
    val resourceVersion = partyRecord.resourceVersion
    val createdAt = partyRecord.createdAt
    val internalId: Try[Int] = SQL"""
         INSERT INTO participant_parties (party, resource_version, created_at)
         VALUES ($party, $resourceVersion, $createdAt)
       """.executeInsert1("internal_id")(SqlParser.scalar[Int].single)(connection)
    internalId.get
  }

  override def getPartyAnnotations(internalId: Int)(connection: Connection): Map[String, String] = {
    ParticipantMetadataBackend.getAnnotations("participant_party_annotations")(internalId)(
      connection
    )
  }

  override def addPartyAnnotation(internalId: Int, key: String, value: String, updatedAt: Long)(
      connection: Connection
  ): Unit = {
    ParticipantMetadataBackend.addAnnotation("participant_party_annotations")(
      internalId,
      key,
      value,
      updatedAt,
    )(connection)
  }

  override def deletePartyAnnotations(internalId: Int)(connection: Connection): Unit = {
    ParticipantMetadataBackend.deleteAnnotations("participant_party_annotations")(internalId)(
      connection
    )
  }

  override def compareAndIncreaseResourceVersion(internalId: Int, expectedResourceVersion: Long)(
      connection: Connection
  ): Boolean = {
    ParticipantMetadataBackend.compareAndIncraseResourceVersion("participant_parties")(
      internalId,
      expectedResourceVersion,
    )(connection)
  }

  override def increaseResourceVersion(internalId: Int)(connection: Connection): Boolean = {
    ParticipantMetadataBackend.increaseResourceVersion("participant_parties")(internalId)(
      connection
    )
  }

}
