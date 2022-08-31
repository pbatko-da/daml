// Copyright (c) 2022 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.platform.server.api.validation

import com.daml.platform.server.api.validation.ResourceAnnotationValidation.{
  AnnotationsSizeExceededError,
  InvalidAnnotationsKeyError,
  validateAnnotations,
}
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ResourceAnnotationValidationsSpec extends AnyFlatSpec with Matchers with EitherValues {

  it should "validate valid annotations key names" in {
    // invalid characters
    validateAnnotations(Map("" -> "")).left.value shouldBe a[InvalidAnnotationsKeyError]
    validateAnnotations(Map("&" -> "")).left.value shouldBe a[InvalidAnnotationsKeyError]
    validateAnnotations(Map("%" -> "")).left.value shouldBe a[InvalidAnnotationsKeyError]
    // valid characters
    validateAnnotations(Map("a" -> "")) shouldBe Right(())
    validateAnnotations(Map("Z" -> "")) shouldBe Right(())
    validateAnnotations(Map("7" -> "")) shouldBe Right(())
    validateAnnotations(Map("aA._-b" -> "")) shouldBe Right(())
    validateAnnotations(Map("aA._-b" -> "")) shouldBe Right(())
    validateAnnotations(
      Map("abcdefghijklmnopqrstuvwxyz.-_ABCDEFGHIJKLMNOPQRSTUVWXYZ" -> "")
    ) shouldBe Right(())
    validateAnnotations(Map("some-key.like_this" -> "")) shouldBe Right(())
    // too long
    validateAnnotations(Map("a" * 64 -> "")).left.value shouldBe a[InvalidAnnotationsKeyError]
    // just right size
    validateAnnotations(Map("a" * 63 -> "")) shouldBe Right(())
    // character in an invalid position
    validateAnnotations(Map(".aaa" -> "")).left.value shouldBe a[InvalidAnnotationsKeyError]
    validateAnnotations(Map("aaa_" -> "")).left.value shouldBe a[InvalidAnnotationsKeyError]
    validateAnnotations(Map("aaa-" -> "")).left.value shouldBe a[InvalidAnnotationsKeyError]
  }

  it should "validate valid annotations keys prefixes" in {
    validateAnnotations(Map("aaa/a" -> "")) shouldBe Right(())
    validateAnnotations(Map("AAA/a" -> "")) shouldBe Right(())
    validateAnnotations(Map("aaa-bbb/a" -> "")) shouldBe Right(())
    validateAnnotations(Map("aaa-bbb.ccc-ddd/a" -> "")) shouldBe Right(())
    validateAnnotations(Map("00.11.AA-bBb.ccc2/a" -> "")) shouldBe Right(())
    validateAnnotations(Map("aa--aa/a" -> "")) shouldBe Right(())

    validateAnnotations(Map(".user.management.daml/foo_" -> "")).left.value shouldBe a[
      InvalidAnnotationsKeyError
    ]
    validateAnnotations(Map("aaa./a" -> "")).left.value shouldBe a[InvalidAnnotationsKeyError]
    validateAnnotations(Map(".aaa/a" -> "")).left.value shouldBe a[InvalidAnnotationsKeyError]
    validateAnnotations(Map("-aaa/a" -> "")).left.value shouldBe a[InvalidAnnotationsKeyError]
    validateAnnotations(Map("aaa-/a" -> "")).left.value shouldBe a[InvalidAnnotationsKeyError]
    validateAnnotations(Map("aa..aa/a" -> "")).left.value shouldBe a[InvalidAnnotationsKeyError]

    validateAnnotations(Map(s"${"a" * 254}/a" -> "")).left.value shouldBe a[
      InvalidAnnotationsKeyError
    ]
    validateAnnotations(Map(s"${"a" * 253}/a" -> "")) shouldBe Right(())
  }

  it should "validate annotations' total size - single key, large value" in {
    val largeString = "a" * 256 * 1024
    val notSoLargeString = "a" * ((256 * 1024) - 1)

    validateAnnotations(annotations = Map("a" -> largeString)) shouldBe Left(
      AnnotationsSizeExceededError((256 * 1024) + 1)
    )
    validateAnnotations(annotations = Map("a" -> notSoLargeString)) shouldBe Right(())
  }

  it should "validate annotations' total size - many keys" in {
    val sixteenLetters = "abcdefghijklmnop"
    val value = "a" * 1022
    val mapWithManyKeys: Map[String, String] = (for {
      l1 <- sixteenLetters
      l2 <- sixteenLetters
    } yield {
      val key = s"$l1$l2"
      key -> value
    }).toMap
    val mapWithManyKeys2 = mapWithManyKeys.updated(key = "a", value = "")

    validateAnnotations(annotations = mapWithManyKeys) shouldBe Right(())
    validateAnnotations(annotations = mapWithManyKeys2) shouldBe Left(
      AnnotationsSizeExceededError((256 * 1024) + 1)
    )
  }

}
