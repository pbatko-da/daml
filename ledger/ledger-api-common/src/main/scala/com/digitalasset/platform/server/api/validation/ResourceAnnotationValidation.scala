// Copyright (c) 2022 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.platform.server.api.validation

import java.nio.charset.StandardCharsets

import scala.util.matching.Regex

object ResourceAnnotationValidation {

  private val MaxAnnotationsSizeInBytes: Int = 256 * 1024

  sealed trait MetadataAnnotationsError
  final case class AnnotationsSizeExceededError(actualSizeInBytes: Long)
      extends MetadataAnnotationsError
  final case class InvalidAnnotationsKeyError(msg: String) extends MetadataAnnotationsError

  // TODO pbatko: Cleanup impl
  def validateAnnotations(
      annotations: Map[String, String]
  ): Either[MetadataAnnotationsError, Unit] = {
    val totalSize = annotations.iterator.foldLeft(0L) { case (size, (key, value)) =>
      val keySize = key.getBytes(StandardCharsets.UTF_8).length
      val valSize = value.getBytes(StandardCharsets.UTF_8).length
      size + keySize + valSize
    }
    if (totalSize > MaxAnnotationsSizeInBytes) {
      Left(AnnotationsSizeExceededError(actualSizeInBytes = totalSize))
    } else {
      val result = annotations.keys.iterator.foldLeft(Right(()): Either[String, Unit]) {
        (acc, key) =>
          for {
            _ <- acc
            _ <- isValidKey(key)
          } yield ()
      }
      result.left.map(InvalidAnnotationsKeyError)
    }
  }

  // Based on K8s annotations and labels
  val NamePattern = "([a-zA-Z0-9]+[a-zA-Z0-9-]*)?[a-zA-Z0-9]+"
  val KeySegmentRegex: Regex = "^([a-zA-Z0-9]+[a-zA-Z0-9.\\-_]*)?[a-zA-Z0-9]+$".r
  val DnsSubdomainRegex: Regex = ("^(" + NamePattern + "[.])*" + NamePattern + "$").r

  def isValidKey(key: String): Either[String, Unit] = {
    key.split('/') match {
      case Array(name) => isValidKeyNameSegment(name)
      case Array(prefix, name) =>
        for {
          _ <- isValidKeyPrefixSegment(prefix)
          _ <- isValidKeyNameSegment(name)
        } yield ()
      case _ => Left(s"Key '${shorten(key)}' contains more than one forward slash ('/') character")
    }
  }

  def isValidKeyPrefixSegment(prefixSegment: String): Either[String, Unit] = {
    if (prefixSegment.length > 253) {
      Left(
        s"Key prefix segment '${shorten(prefixSegment)}' exceeds maximum length of 253 characters"
      )
    } else {
      if (DnsSubdomainRegex.matches(prefixSegment)) {
        Right(())
      } else {
        Left(s"Key prefix segment '${shorten(prefixSegment)}' has invalid syntax")
      }
    }
  }

  def isValidKeyNameSegment(nameSegment: String): Either[String, Unit] = {
    if (nameSegment.length > 63) {
      Left(s"Key name segment '${shorten(nameSegment)}' exceeds maximum length of 63 characters")
    } else {
      if (KeySegmentRegex.matches(nameSegment)) {
        Right(())
      } else {
        Left(s"Kye name segment '${shorten(nameSegment)}' has invalid syntax")
      }
    }
  }

  private def shorten(s: String): String = {
    s.take(20) + (if (s.length > 20) "..." else "")
  }

}
