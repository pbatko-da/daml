// Copyright (c) 2022 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.error

/** A component of [[ErrorGroupPath]]
  *
  * @param docName The name that will appear in the generated documentation for the grouping.
  * @param className Full class name of the corresponding [[ErrorGroup]].
  */
case class ErrorGroupPathSegment(
    docName: String,
    className: String,
) {
  require(
    docName.trim.nonEmpty,
    s"Grouping.docName must be non empty and must contain not only whitespace characters, but was: |${docName}|!",
  )
}

/** Used to hierarchically structure error codes in the official documentation.
  */
case class ErrorGroupPath(groupings: List[ErrorGroupPathSegment]) {
  def extend(grouping: ErrorGroupPathSegment): ErrorGroupPath =
    ErrorGroupPath(groupings :+ grouping)
}

object ErrorGroupPath {
  def root(): ErrorGroupPath = ErrorGroupPath(Nil)
}
