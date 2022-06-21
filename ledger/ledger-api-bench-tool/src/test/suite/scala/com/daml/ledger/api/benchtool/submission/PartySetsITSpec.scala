// Copyright (c) 2022 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.ledger.api.benchtool.submission

import com.codahale.metrics.MetricRegistry
import com.daml.ledger.api.benchtool.{
  BenchtoolSandboxFixture,
  ConfigDesugaring,
  SubmissionStepResult,
}
import com.daml.ledger.api.benchtool.config.WorkflowConfig
import com.daml.ledger.api.benchtool.config.WorkflowConfig.FooSubmissionConfig.{
  ConsumingExercises,
  NonconsumingExercises,
  PartySet,
}
import com.daml.ledger.api.benchtool.config.WorkflowConfig.StreamConfig.PartySetTemplatesFilter
import com.daml.ledger.api.benchtool.metrics.MetricsManager.NoOpMetricsManager
import com.daml.ledger.api.benchtool.services.LedgerApiServices
import com.daml.ledger.api.testing.utils.SuiteResourceManagementAroundAll
import com.daml.ledger.api.v1.ledger_offset.LedgerOffset
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{AppendedClues, OptionValues}

import scala.concurrent.Future

class PartySetsITSpec
    extends AsyncFlatSpec
    with BenchtoolSandboxFixture
    with SuiteResourceManagementAroundAll
    with Matchers
    with AppendedClues
    with OptionValues {

  it should "divulge events" in {
    val submissionConfig = WorkflowConfig.FooSubmissionConfig(
      numberOfInstances = 10,
      numberOfObservers = 1,
      uniqueParties = false,
      instanceDistribution = List(
        WorkflowConfig.FooSubmissionConfig.ContractDescription(
          template = "Foo1",
          weight = 1,
          payloadSizeBytes = 0,
        )
      ),
      partySetO = Some(
        PartySet(
          name = "my-party-set-123",
          count = 100,
          visibility = 0.5,
          partyNamePrefix = "MyParty",
        )
      ),
      consumingExercises = Some(
        ConsumingExercises(
          probability = 0.3,
          payloadSizeBytes = 0,
        )
      ),
      nonConsumingExercises = Some(
        NonconsumingExercises(
          probability = 2.0,
          payloadSizeBytes = 0,
        )
      ),
    )
    for {
      ledgerApiServicesF <- LedgerApiServices.forChannel(
        channel = channel,
        authorizationHelper = None,
      )
      apiServices: LedgerApiServices = ledgerApiServicesF("someUser")
      names = new Names()
      submitter = CommandSubmitter(
        names = names,
        benchtoolUserServices = apiServices,
        adminServices = apiServices,
        metricRegistry = new MetricRegistry,
        metricsManager = NoOpMetricsManager(),
        waitForSubmission = true,
        commandGenerationParallelism = 1,
      )
      allocatedParties <- submitter.prepare(submissionConfig)
      configDesugaring = new ConfigDesugaring(Some(SubmissionStepResult(allocatedParties)))
      tested = new FooSubmission(
        submitter = submitter,
        maxInFlightCommands = 1,
        submissionBatchSize = 1,
        submissionConfig = submissionConfig,
        allocatedParties = allocatedParties,
        names = names,
        partySelectingRandomnessProviderOverride = Some(RandomnessProvider.forSeed(seed = 0)),
        consumingEventsRandomnessProviderOverride = Some(RandomnessProvider.forSeed(seed = 0)),
      )
      _ <- tested.performSubmission()
      _ = allocatedParties.partySetParties(87).toString shouldBe "MyParty-87"
      treeResults_myParty <- observeStreams(
        configDesugaring = configDesugaring,
        filterByParties = List("MyParty-87"),
        apiServices = apiServices,
        expectedTemplateNames = Set("Foo1"),
      )
      treeResults_partySet <- observeStreams(
        configDesugaring = configDesugaring,
        filterByPartySetNameO = Some("my-party-set-123"),
        apiServices = apiServices,
        expectedTemplateNames = Set("Foo1"),
      )

    } yield {
      { // Party from party set
        treeResults_myParty.numberOfCreatesPerTemplateName("Foo1") shouldBe 6
        treeResults_myParty.numberOfNonConsumingExercisesPerTemplateName("Foo1") shouldBe 12
        treeResults_myParty.numberOfConsumingExercisesPerTemplateName("Foo1") shouldBe 1
      }
      { // Party set
        treeResults_partySet.numberOfCreatesPerTemplateName("Foo1") shouldBe 10
        treeResults_partySet.numberOfNonConsumingExercisesPerTemplateName("Foo1") shouldBe 20
        treeResults_partySet.numberOfConsumingExercisesPerTemplateName("Foo1") shouldBe 1
      }

      succeed
    }
  }

  private def observeStreams(
      configDesugaring: ConfigDesugaring,
      filterByPartySetNameO: Option[String] = None,
      filterByParties: List[String] = List.empty,
      filterByTemplates: List[String] = List.empty,
      apiServices: LedgerApiServices,
      expectedTemplateNames: Set[String],
  ): Future[ObservedEvents] = {
    val treeTxObserver = TreeEventsObserver(expectedTemplateNames = expectedTemplateNames)
    val treeConfig = WorkflowConfig.StreamConfig.TransactionTreesStreamConfig(
      name = "dummy-name",
      filters = filterByParties.map(party =>
        WorkflowConfig.StreamConfig.PartyFilter(
          party = party,
          templates = filterByTemplates,
        )
      ),
      filterByPartySetO = filterByPartySetNameO.map(partySetName =>
        PartySetTemplatesFilter(
          partySetName = partySetName,
          templates = filterByTemplates,
        )
      ),
      beginOffset = None,
      endOffset = Some(LedgerOffset().withBoundary(LedgerOffset.LedgerBoundary.LEDGER_END)),
      objectives = None,
      maxItemCount = None,
      timeoutInSecondsO = None,
    )
    for {
      _ <- apiServices.transactionService.transactionTrees(
        config = configDesugaring.desugar(treeConfig),
        observer = treeTxObserver,
      )
      treeResults: ObservedEvents <- treeTxObserver.result
    } yield {
      treeResults
    }
  }

}
