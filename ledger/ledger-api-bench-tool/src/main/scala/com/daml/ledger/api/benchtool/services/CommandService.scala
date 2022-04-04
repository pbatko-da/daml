// Copyright (c) 2022 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.ledger.api.benchtool.services

import com.daml.ledger.api.benchtool.AuthorizationHelper
import com.daml.ledger.api.v1.command_service._
import com.daml.ledger.api.v1.commands.{Commands}
import com.daml.ledger.api.v1.transaction.TreeEvent
//import com.daml.ledger.api.v1.value.{Identifier, Value}
import com.daml.ledger.api.validation.NoLoggingValueValidator
//import com.daml.ledger.test.model.Foo.Foo1
import com.daml.lf.data.Ref.ChoiceName
import com.daml.lf.engine.script.Converter
import com.daml.lf.engine.script.ledgerinteraction.ScriptLedgerClient
import com.daml.lf.value.Value.ContractId
import io.grpc.Channel
import org.slf4j.LoggerFactory
//import scalaz.OneAnd
//import scalaz.OneAnd._
import scalaz.std.either._
import scalaz.std.list._
//import scalaz.std.set._
//import scalaz.syntax.foldable._
//import scalaz.syntax.tag._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class CommandService(channel: Channel, authorizationToken: Option[String]) {
  private val logger = LoggerFactory.getLogger(getClass)
  private val service: CommandServiceGrpc.CommandServiceStub =
    AuthorizationHelper.maybeAuthedService(authorizationToken)(CommandServiceGrpc.stub(channel))

  def submitAndWait(
      commands: Commands
  )(implicit ec: ExecutionContext): Future[Seq[ScriptLedgerClient.CommandResult]] =
    service
      .submitAndWaitForTransactionTree(new SubmitAndWaitRequest(Some(commands)))
      .recoverWith { case NonFatal(ex) =>
        Future.failed {
          logger.error(s"Command submission error. Details: ${ex.getLocalizedMessage}", ex)
          ex
        }
      }
      .map { transactionTree: SubmitAndWaitForTransactionTreeResponse =>
        // copied from com.daml.lf.engine.script.ledgerinteraction.GrpcLedgerClient.submit

        println(transactionTree)

        def fromTreeEvent(ev: TreeEvent): Either[String, ScriptLedgerClient.CommandResult] =
          ev match {
            case TreeEvent(TreeEvent.Kind.Created(created)) =>
              for {
                cid <- ContractId.fromString(created.contractId)
              } yield ScriptLedgerClient.CreateResult(cid)
            case TreeEvent(TreeEvent.Kind.Exercised(exercised)) =>
              for {
                result <- NoLoggingValueValidator
                  .validateValue(exercised.getExerciseResult)
                  .left
                  .map(_.toString)
                templateId <- Converter.fromApiIdentifier(exercised.getTemplateId)
                choice <- ChoiceName.fromString(exercised.choice)
              } yield ScriptLedgerClient.ExerciseResult(templateId, choice, result)
            case TreeEvent(TreeEvent.Kind.Empty) =>
              sys.error("Invalid tree event Empty")
          }
//        import scalaz._
        val events: List[TreeEvent] = transactionTree.getTransaction.rootEventIds
          .map(evId => transactionTree.getTransaction.eventsById(evId))
          .toList

        import scalaz.syntax.traverse._
        val x: Either[String, Seq[ScriptLedgerClient.CommandResult]] =
          events.traverse(fromTreeEvent(_))
        println(x)

        x match {
          case Left(msg) => sys.error(msg)
          case Right(v) => v
        }
      }
}
