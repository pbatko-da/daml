// Copyright (c) 2022 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.error

import com.daml.error.definitions.DamlContextualizedErrorLogger
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class DamlContextualizedErrorLoggerSpec extends AnyFlatSpec with Matchers {

  behavior of classOf[DamlContextualizedErrorLogger].getName

  it should "sort entries by keys and skip empty values" in {
    val contextMap = Map("c" -> "C", "a" -> "A", "b" -> "B", "empty value" -> "")

    val actual =
      DamlContextualizedErrorLogger.forTesting(getClass).formatContextAsString(contextMap)

    actual shouldBe "a=A, b=B, c=C"
  }
}
