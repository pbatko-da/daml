// Copyright (c) 2022 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.error

import org.scalatest.BeforeAndAfter
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ErrorGroupSpec extends AnyFlatSpec with Matchers with BeforeAndAfter {

  object ErrorGroupBar extends ErrorGroup()(ErrorGroupPath.root())

  object ErrorGroupFoo1 extends ErrorGroup()(ErrorGroupPath.root()) {
    object ErrorGroupFoo2 extends ErrorGroup() {
      object ErrorGroupFoo3 extends ErrorGroup()
    }
  }

  it should "resolve correct error group names" in {
    ErrorGroupFoo1.ErrorGroupFoo2.ErrorGroupFoo3.errorGroupPath shouldBe ErrorGroupPath(
      List(
        ErrorGroupPathSegment("ErrorGroupFoo1", ErrorGroupFoo1.fullClassName),
        ErrorGroupPathSegment("ErrorGroupFoo2", ErrorGroupFoo1.ErrorGroupFoo2.fullClassName),
        ErrorGroupPathSegment(
          "ErrorGroupFoo3",
          ErrorGroupFoo1.ErrorGroupFoo2.ErrorGroupFoo3.fullClassName,
        ),
      )
    )
    ErrorGroupBar.errorGroupPath shouldBe ErrorGroupPath(
      List(ErrorGroupPathSegment("ErrorGroupBar", ErrorGroupBar.fullClassName))
    )
  }

}
