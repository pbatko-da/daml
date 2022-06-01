// Copyright (c) 2022 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
package com.daml.platform.store.dao.events

import com.daml.platform.store.dao.QueryRange
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class EventsRangeSpec extends AnyFlatSpec with Matchers with ScalaCheckPropertyChecks {

  implicit override val generatorDrivenConfig: PropertyCheckConfiguration =
    PropertyCheckConfiguration(minSuccessful = 100)

  behavior of EventsRange.getClass.getSimpleName

  "isEmpty" should "work" in forAll(eventsRangeGen) { range =>
    val expected = range.startExclusive >= range.endInclusive
    EventsRange.isEmpty(range) shouldBe expected
  }

  private val eventsRangeGen: Gen[QueryRange[Int]] =
    for {
      a <- Arbitrary.arbitrary[Int]
      b <- Arbitrary.arbitrary[Int]
    } yield QueryRange(a, b)
}
