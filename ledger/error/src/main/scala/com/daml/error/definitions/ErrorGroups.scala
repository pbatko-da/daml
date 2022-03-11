// Copyright (c) 2022 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.error.definitions

import com.daml.error.{ErrorGroupPath, ErrorGroup}

object ErrorGroups {
  val rootErrorGroupPath: ErrorGroupPath = ErrorGroupPath.root()

  object ParticipantErrorGroup extends ErrorGroup()(rootErrorGroupPath) {
    abstract class IndexErrorGroup extends ErrorGroup() {
      abstract class DatabaseErrorGroup extends ErrorGroup()
    }
    abstract class LedgerApiErrorGroup extends ErrorGroup() {
      abstract class CommandExecutionErrorGroup extends ErrorGroup()
      abstract class PackageServiceErrorGroup extends ErrorGroup()
      // TODO error codes: Move UM errors in the concrete subclassed object of the this group.
      abstract class UserManagementServiceErrorGroup extends ErrorGroup()
    }
  }
}
