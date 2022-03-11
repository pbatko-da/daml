// Copyright (c) 2022 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.error.definitions

import com.daml.lf.engine.{Error => LfError}

sealed abstract class ErrorCause extends Product with Serializable

// TODO pbatko: How does it fit with the rest of //ledger/error
object ErrorCause {
  final case class DamlLf(error: LfError) extends ErrorCause
  final case class LedgerTime(retries: Int) extends ErrorCause
}
