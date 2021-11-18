// Copyright (c) 2021 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.error.generator.app

import com.daml.error.ErrorGroupSegment
import com.daml.error.generator.{ErrorCodeDocumentationGenerator, ErrorDocItem, GroupDocItem}
import io.circe.Encoder
import io.circe.syntax._

import java.nio.file.{Files, Paths, StandardOpenOption}

/** Outputs information about self-service error codes needed for generating documentation to a json file.
  */
object Main {

  case class Output(errorCodes: Seq[ErrorDocItem], groups: Seq[GroupDocItem])

  implicit val groupingEncode: Encoder[ErrorGroupSegment] =
    Encoder.forProduct2(
      "docName",
      "className",
    )((grouping: ErrorGroupSegment) =>
      (
        grouping.docName,
        grouping.fullClassName,
      )
    )

  implicit val errorCodeEncode: Encoder[ErrorDocItem] =
    Encoder.forProduct8(
      "className",
      "category",
      "hierarchicalGrouping",
      "conveyance",
      "code",
      "deprecation",
      "explanation",
      "resolution",
    )(i =>
      (
        i.fullClassName,
        i.category,
        i.errorGroupPath.segments,
        i.conveyance,
        i.code,
        i.deprecation.fold("")(_.deprecation),
        i.explanation.fold("")(_.explanation),
        i.resolution.fold("")(_.resolution),
      )
    )

  implicit val groupEncode: Encoder[GroupDocItem] =
    Encoder.forProduct2(
      "className",
      "explanation",
    )(i =>
      (
        i.fullClassName,
        i.explanation.fold("")(_.explanation),
      )
    )

  implicit val outputEncode: Encoder[Output] =
    Encoder.forProduct2("errorCodes", "groups")(i => (i.errorCodes, i.groups))

  def main(args: Array[String]): Unit = {
    val (errorCodes, groups) = new ErrorCodeDocumentationGenerator().getDocItems
    val outputFile = Paths.get(args(0))
    val output = Output(errorCodes, groups)
    val outputText: String = output.asJson.spaces2
    val outputBytes = outputText.getBytes
    val _ = Files.write(outputFile, outputBytes, StandardOpenOption.CREATE_NEW)
  }
}
