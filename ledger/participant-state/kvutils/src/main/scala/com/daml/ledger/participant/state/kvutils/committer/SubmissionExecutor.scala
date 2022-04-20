// Copyright (c) 2022 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.ledger.participant.state.kvutils.committer

import com.daml.ledger.participant.state.kvutils.DamlStateMap
import com.daml.ledger.participant.state.kvutils.KeyValueCommitting.PreExecutionResult
import com.daml.ledger.participant.state.kvutils.wire.DamlSubmission
import com.daml.lf.data.Ref
import com.daml.logging.LoggingContext

trait SubmissionExecutor {

  def runWithPreExecution(
      submission: DamlSubmission,
      participantId: Ref.ParticipantId,
      inputState: DamlStateMap,
  )(implicit loggingContext: LoggingContext): PreExecutionResult
}
