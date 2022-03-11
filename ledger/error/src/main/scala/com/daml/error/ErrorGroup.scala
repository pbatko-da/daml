// Copyright (c) 2022 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.error

abstract class ErrorGroup()(implicit parent: ErrorGroupPath) {
  private val simpleClassName: String = getClass.getSimpleName.replace("$", "")
  val fullClassName: String = getClass.getName

  implicit val errorGroupPath: ErrorGroupPath =
    parent.extend(ErrorGroupPathSegment(docName = simpleClassName, className = fullClassName))
}
