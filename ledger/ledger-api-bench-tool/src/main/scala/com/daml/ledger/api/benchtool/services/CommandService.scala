// Copyright (c) 2022 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.ledger.api.benchtool.services

import com.daml.ledger.api.auth.client.LedgerCallCredentials
import com.daml.ledger.api.benchtool.submission.CommandSubmitter
import com.daml.ledger.api.v1.command_service._
import com.daml.ledger.api.v1.commands.Commands
import com.google.protobuf.empty.Empty
import io.grpc.Channel
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class CommandService(channel: Channel, token: Option[String]) {
  private val logger = LoggerFactory.getLogger(getClass)
  private val service: CommandServiceGrpc.CommandServiceStub =
    CommandSubmitter.authedService(token)(CommandServiceGrpc.stub(channel))

  def submitAndWait(commands: Commands)(implicit ec: ExecutionContext): Future[Empty] = {
    token
      .fold(service)(LedgerCallCredentials.authenticatingStub(service, _))
      .submitAndWait(new SubmitAndWaitRequest(Some(commands)))
      .recoverWith { case NonFatal(ex) =>
        Future.failed {
          logger.error(s"Command submission error. Details: ${ex.getLocalizedMessage}", ex)
          ex
        }
      }
  }
}

//object CommandService {
//  def submitAndWaitRequest(
//      ledgerId: String,
//      applicationId: String,
//      commandId: String,
//      workflowId: String,
//      party: Party,
//      commands: List[Command],
//  ): SubmitAndWaitRequest =
//    new SubmitAndWaitRequest(
//      Some(
//        new Commands(
//          ledgerId = ledgerId,
//          applicationId = applicationId,
//          commandId = commandId,
//          party = party.unwrap,
//          commands = commands,
//          workflowId = workflowId,
//        )
//      )
//    )
//}
