// Copyright (c) 2022 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.ledger.api.benchtool.submission

import com.codahale.metrics.MetricRegistry
import com.daml.ledger.api.benchtool.config.BenchToolConfig
import com.daml.ledger.api.benchtool.config.BenchToolConfig.SubmissionConfig.{
  ConsumingExercises,
  NonconsumingExercises,
}
import com.daml.ledger.api.benchtool.metrics.MetricsManager.NoOpMetricsManager
import com.daml.ledger.api.benchtool.services.LedgerApiServices
import com.daml.ledger.api.benchtool.util.ObserverWithResult
import com.daml.ledger.api.testing.utils.SuiteResourceManagementAroundAll
import com.daml.ledger.api.v1.ledger_offset.LedgerOffset
import com.daml.ledger.api.v1.transaction.{TransactionTree, TreeEvent}
import com.daml.ledger.api.v1.transaction_service.GetTransactionTreesResponse
import com.daml.platform.sandbox.SandboxBackend
import com.daml.platform.sandbox.fixture.SandboxFixture
//import com.daml.testing.postgresql.PostgresAroundAll
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.Future

case class CreateEvents(templateName: String, createArgumentsSerializedSize: Int)

case class ExerciseEvents(
    templateName: String,
    choiceName: String,
    choiceArgumentsSerializedSize: Int,
    consuming: Boolean,
)

case class CommandSubmissionStats(
    createEvents: Seq[CreateEvents],
    exerciseEvents: Seq[ExerciseEvents],
) {
  val consumingExercises: Seq[ExerciseEvents] = exerciseEvents.filter(_.consuming)
  val nonconsumingExercises: Seq[ExerciseEvents] = exerciseEvents.filterNot(_.consuming)
}

object FooObserverWithResult {
  def apply(): FooObserverWithResult = new FooObserverWithResult(LoggerFactory.getLogger(getClass))
}

class FooObserverWithResult(logger: Logger)
    extends ObserverWithResult[GetTransactionTreesResponse, CommandSubmissionStats](logger) {

  private val createEvents = collection.mutable.ArrayBuffer[CreateEvents]()
  private val exerciseEvents = collection.mutable.ArrayBuffer[ExerciseEvents]()

  override def streamName: String = "dummy-stream-name"

  override def onNext(value: GetTransactionTreesResponse): Unit = {
    val transactions: Seq[TransactionTree] = value.transactions
    transactions.foreach { transaction =>
      transaction.rootEventIds.map(transaction.eventsById).foreach { rootTreeEvent: TreeEvent =>
        val treeEvent = rootTreeEvent
        if (treeEvent.kind.isCreated) {
          val created = treeEvent.kind.created.get
          val argsSize = created.createArguments.fold(0)(_.serializedSize)
          val templateName =
            created.templateId.getOrElse(sys.error("Expected templateId")).entityName
          createEvents.addOne(
            CreateEvents(templateName = templateName, createArgumentsSerializedSize = argsSize)
          )
        }
        if (treeEvent.kind.isExercised) {
          val exercised = treeEvent.kind.exercised.get
          val argsSize = exercised.choiceArgument.fold(0)(_.serializedSize)
          val templateName =
            exercised.templateId.getOrElse(sys.error("Expected templateId")).entityName
          val choiceName = exercised.choice
          exerciseEvents.addOne(
            ExerciseEvents(
              templateName = templateName,
              choiceName = choiceName,
              choiceArgumentsSerializedSize = argsSize,
              consuming = exercised.consuming,
            )
          )
        }
      }
    }
  }

  override def completeWith(): Future[CommandSubmissionStats] = Future.successful(
    CommandSubmissionStats(
      createEvents = createEvents.toList,
      exerciseEvents = exerciseEvents.toList,
    )
  )
}

import org.scalatest.AppendedClues // Assertion, OptionValues}

class CommandSubmitterSpec
    extends AsyncFlatSpec
    with SandboxFixture
    with SuiteResourceManagementAroundAll
//    with SandboxBackend.Postgresql
    with Matchers
    with AppendedClues {

  println(SandboxBackend.Postgresql)

  it should "do it" in {

    val foo1Config = BenchToolConfig.SubmissionConfig.ContractDescription(
      template = "Foo1",
      weight = 1,
      payloadSizeBytes = 100,
      archiveChance = 1.0,
    )
    val foo2Config = BenchToolConfig.SubmissionConfig.ContractDescription(
      template = "Foo2",
      weight = 1,
      payloadSizeBytes = 1000,
      archiveChance = 0,
    )
    val consumingExercisesConfig = ConsumingExercises(
      probability = 0.0,
      payloadSizeBytes = 5000,
    )
    val nonconsumingExercisesConfig = NonconsumingExercises(
      probability = 2.0,
      payloadSizeBytes = 500,
    )
    val config = BenchToolConfig.SubmissionConfig(
      numberOfInstances = 1,
      numberOfObservers = 1,
      uniqueParties = false,
      instanceDistribution = List(
        foo1Config,
        foo2Config,
      ),
      nonconsumingExercises = Some(nonconsumingExercisesConfig),
      consumingExercises = Some(consumingExercisesConfig),
    )

    for {
      ledgerApiServicesF <- LedgerApiServices.forChannel(
        channel = channel,
        authorizationHelper = None,
      )
      apiServices = ledgerApiServicesF("someUser")
      tested = CommandSubmitter(
        names = new Names(),
        benchtoolUserServices = apiServices,
        adminServices = apiServices,
        metricRegistry = new MetricRegistry,
        metricsManager = NoOpMetricsManager(),
      )

      (signatory, observers) <- tested.prepare(config)

      _ <- tested.submit(
        config = config,
        signatory = signatory,
        observers = observers,
        maxInFlightCommands = 1,
        submissionBatchSize = 5,
      )

      transactionTreesObserver = FooObserverWithResult()
      _ <- apiServices.transactionService.transactionTrees(
        config = BenchToolConfig.StreamConfig.TransactionTreesStreamConfig(
          name = "dummy-name",
          filters = List(
            BenchToolConfig.StreamConfig.PartyFilter(
              party = signatory.toString,
              templates = List.empty,
            )
          ),
          beginOffset = None,
          endOffset = Some(LedgerOffset().withBoundary(LedgerOffset.LedgerBoundary.LEDGER_END)),
          objectives = None,
        ),
        observer = transactionTreesObserver,
      )
      observerResult: CommandSubmissionStats <- transactionTreesObserver.result
    } yield {
      observerResult.createEvents.size shouldBe config.numberOfInstances
      val avgSizePerTemplate: Map[String, Int] = observerResult.createEvents
        .groupBy(_.templateName)
        .view
        .mapValues(events => events.map(_.createArgumentsSerializedSize).sum / events.size)
        .toMap
      avgSizePerTemplate(
        "Foo1"
      ) should (be >= (foo1Config.payloadSizeBytes) and be <= (3 * foo1Config.payloadSizeBytes))
//      avgSizePerTemplate(
//        "Foo2"
//      ) should (be >= (foo2Config.payloadSizeBytes) and be <= (3 * foo2Config.payloadSizeBytes))
//

      // TODO pbatko: expected size should be take into account probability
      observerResult.consumingExercises.size shouldBe config.numberOfInstances withClue ("consumingExercises")
      // TODO pbatko: expected size should be take into account probability and multiplicity
      observerResult.nonconsumingExercises.size shouldBe 2 * config.numberOfInstances withClue ("nonconsumingExercises")
      val avgConsumingExerciseSize: Int = observerResult.consumingExercises
        .map(_.choiceArgumentsSerializedSize)
        .sum / observerResult.consumingExercises.size
      val avgNonconsumingExerciseSize: Int = observerResult.nonconsumingExercises
        .map(_.choiceArgumentsSerializedSize)
        .sum / observerResult.nonconsumingExercises.size
      avgConsumingExerciseSize should (be >= consumingExercisesConfig.payloadSizeBytes and be <= 3 * consumingExercisesConfig.payloadSizeBytes)
      avgNonconsumingExerciseSize should (be >= nonconsumingExercisesConfig.payloadSizeBytes and be <= 3 * nonconsumingExercisesConfig.payloadSizeBytes)
      succeed
    }
  }

}
