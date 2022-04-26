// Copyright (c) 2022 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.ledger.api.benchtool.submission

import com.codahale.metrics.MetricRegistry
import com.daml.ledger.api.benchtool.config.WorkflowConfig
import com.daml.ledger.api.benchtool.config.WorkflowConfig.SubmissionConfig.{ConsumingExercises, NonconsumingExercises}
import com.daml.ledger.api.benchtool.metrics.MetricsManager.NoOpMetricsManager
import com.daml.ledger.api.benchtool.services.LedgerApiServices
import com.daml.ledger.api.benchtool.submission.EventsObserver.{ObservedCreateEvent, ObservedEvents, ObservedExerciseEvent}
import com.daml.ledger.api.benchtool.util.ObserverWithResult
import com.daml.ledger.api.testing.utils.SuiteResourceManagementAroundAll
import com.daml.ledger.api.v1.event.CreatedEvent
import com.daml.ledger.api.v1.ledger_offset.LedgerOffset
import com.daml.ledger.api.v1.transaction.{TransactionTree, TreeEvent}
import com.daml.ledger.api.v1.transaction_service.GetTransactionTreesResponse
import com.daml.platform.sandbox.fixture.SandboxFixture
import org.scalactic.TripleEqualsSupport
import org.scalatest.{AppendedClues, OptionValues}
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.Future

class CommandSubmitterITSpec2
    extends AsyncFlatSpec
    with SandboxFixture
    with SuiteResourceManagementAroundAll
    with Matchers
    with AppendedClues
    with OptionValues {

  it should "divulge events" in {

    val config = WorkflowConfig.SubmissionConfig(
      numberOfInstances = 10,
      numberOfObservers = 1,
      numberOfDivulgees = 1,
      uniqueParties = false,
      instanceDistribution = List(
        WorkflowConfig.SubmissionConfig.ContractDescription(
          template = "Foo1",
          weight = 1,
          payloadSizeBytes = 0,
        ),
      ),
      nonConsumingExercises = None,
      consumingExercises = None,
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
      (signatory, observers, divulgees) <- tested.prepare(config)
      divulgee = divulgees.headOption.value
        config = config,
      _ <- tested.submit(
        signatory = signatory,
        observers = observers,
        divulgees = divulgees,
        maxInFlightCommands = 1,
        submissionBatchSize = 5,
      )
      eventsObserver = EventsObserver(expectedTemplateNames = Set("Foo1"))
      _ <- apiServices.transactionService.transactionTrees(
        config = WorkflowConfig.StreamConfig.TransactionTreesStreamConfig(
          name = "dummy-name",
          filters = List(
            WorkflowConfig.StreamConfig.PartyFilter(
              party = divulgee.toString,
              templates = List.empty,
            )
          ),
          beginOffset = None,
          endOffset = Some(LedgerOffset().withBoundary(LedgerOffset.LedgerBoundary.LEDGER_END)),
          objectives = None,
        ),
        observer = eventsObserver,
      )
      observerResult: ObservedEvents <- eventsObserver.result
    } yield {
      observerResult.createEvents.size shouldBe config.numberOfInstances withClue ("number of create events")


      succeed
    }
  }

}

object EventsObserver {
  def apply(expectedTemplateNames: Set[String]): EventsObserver = new EventsObserver(
    logger = LoggerFactory.getLogger(getClass),
    expectedTemplateNames = expectedTemplateNames,
  )

  case class ObservedExerciseEvent(
      templateName: String,
      choiceName: String,
      choiceArgumentsSerializedSize: Int,
      consuming: Boolean,
  )

  case class ObservedCreateEvent(templateName: String, createArgumentsSerializedSize: Int)

  case class ObservedEvents(
      expectedTemplateNames: Set[String],
      createEvents: Seq[ObservedCreateEvent],
      exerciseEvents: Seq[ObservedExerciseEvent],
  ) {
    private val _actualTemplateNames =
      (createEvents.map(_.templateName) ++ exerciseEvents.map(_.templateName)).toSet
    require(
      _actualTemplateNames.subsetOf(expectedTemplateNames),
      s"${_actualTemplateNames} must be a subset of $expectedTemplateNames",
    )

    val consumingExercises: Seq[ObservedExerciseEvent] = exerciseEvents.filter(_.consuming)
    val nonConsumingExercises: Seq[ObservedExerciseEvent] = exerciseEvents.filterNot(_.consuming)

    val avgSizeOfConsumingExercise: Int = {
      if (consumingExercises.isEmpty) 0
      else consumingExercises.map(_.choiceArgumentsSerializedSize).sum / consumingExercises.size
    }

    val avgSizeOfNonconsumingExercise: Int = {
      if (nonConsumingExercises.isEmpty) 0
      else
        nonConsumingExercises.map(_.choiceArgumentsSerializedSize).sum / nonConsumingExercises.size
    }

    val numberOfCreatesPerTemplateName: Map[String, Int] = {
      val groups = createEvents.groupBy(_.templateName)
      expectedTemplateNames.map(name => name -> groups.get(name).fold(0)(_.size)).toMap
    }

    val avgSizeOfCreateEventPerTemplateName: Map[String, Int] = {
      val groups = createEvents.groupBy(_.templateName)
      expectedTemplateNames.map { name =>
        val avgSize = groups
          .get(name)
          .fold(0)(events =>
            if (events.isEmpty) 0 else events.map(_.createArgumentsSerializedSize).sum / events.size
          )
        name -> avgSize
      }.toMap
    }

  }

}

/** Collects information about create and exercise events.
  */
class EventsObserver(expectedTemplateNames: Set[String], logger: Logger)
    extends ObserverWithResult[GetTransactionTreesResponse, ObservedEvents](logger) {

  private val createEvents = collection.mutable.ArrayBuffer[ObservedCreateEvent]()
  private val exerciseEvents = collection.mutable.ArrayBuffer[ObservedExerciseEvent]()

  override def streamName: String = "dummy-stream-name"

  override def onNext(value: GetTransactionTreesResponse): Unit = {
    value.transactions.foreach { transaction: TransactionTree =>
      val rootEvents = transaction.rootEventIds.map(transaction.eventsById)
      rootEvents.foreach { event: TreeEvent =>
        event.kind.created.foreach { created: CreatedEvent =>
          val argsSize = created.createArguments.fold(0)(_.serializedSize)
          val templateName =
            created.templateId.getOrElse(sys.error(s"Expected templateId in $created")).entityName
          createEvents.addOne(
            ObservedCreateEvent(
              templateName = templateName,
              createArgumentsSerializedSize = argsSize,
            )
          )
        }
        event.kind.exercised.foreach { exercised =>
          val argsSize = exercised.choiceArgument.fold(0)(_.serializedSize)
          val templateName = exercised.templateId
            .getOrElse(sys.error(s"Expected templateId in $exercised"))
            .entityName
          val choiceName = exercised.choice
          exerciseEvents.addOne(
            ObservedExerciseEvent(
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

  override def completeWith(): Future[ObservedEvents] = Future.successful(
    ObservedEvents(
      expectedTemplateNames = expectedTemplateNames,
      createEvents = createEvents.toList,
      exerciseEvents = exerciseEvents.toList,
    )
  )
}
