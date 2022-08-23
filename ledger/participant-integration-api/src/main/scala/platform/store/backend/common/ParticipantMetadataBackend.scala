// Copyright (c) 2022 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.platform.store.backend.common

import java.sql.Connection

import anorm.SqlParser.{long, str}
import anorm.{RowParser, SqlStringInterpolation, ~}
import com.daml.platform.store.backend.common.SimpleSqlAsVectorOf._

// TODO pbatko: Test it
object ParticipantMetadataBackend {

  private val AnnotationParser: RowParser[(String, String, Long)] =
    str("name") ~ str("val") ~ long("updated_at") map { case key ~ value ~ updateAt =>
      (key, value, updateAt)
    }

  def addAnnotation(
      annotationsTableName: String
  )(internalId: Int, key: String, value: String, updatedAt: Long)(
      connection: Connection
  ): Unit = {
    val _ =
      SQL"""
         INSERT INTO #$annotationsTableName (internal_id, name, val, updated_at)
         VALUES (
            $internalId,
            $key,
            $value,
            $updatedAt
         )
         """.executeUpdate()(connection)
  }

  def deleteAnnotations(
      annotationsTableName: String
  )(internalId: Int)(connection: Connection): Unit = {
    val _ = SQL"""
         DELETE FROM #$annotationsTableName
         WHERE
             internal_id = $internalId
       """.executeUpdate()(connection)
  }

  def getAnnotations(
      annotationsTableName: String
  )(internalId: Int)(connection: Connection): Map[String, String] = {
    SQL"""
         SELECT name, val, updated_at
         FROM #$annotationsTableName
         WHERE
          internal_id = $internalId
       """
      .asVectorOf(AnnotationParser)(connection)
      .iterator
      .map { case (key, value, _) => key -> value }
      .toMap
  }

  def compareAndIncraseResourceVersion(tableName: String)(
      internalId: Int,
      expectedResourceVersion: Long,
  )(connection: Connection): Boolean = {
    val rowsUpdated = SQL"""
         UPDATE #$tableName
         SET resource_version  = resource_version + 1
         WHERE
             internal_id = ${internalId}
             AND
             resource_version = ${expectedResourceVersion}
       """.executeUpdate()(connection)
    rowsUpdated == 1
  }

  def increaseResourceVersion(tableName: String)(internalId: Int)(
      connection: Connection
  ): Boolean = {
    val rowsUpdated = SQL"""
         UPDATE #$tableName
         SET resource_version  = resource_version + 1
         WHERE
             internal_id = ${internalId}
       """.executeUpdate()(connection)
    rowsUpdated == 1
  }

}
