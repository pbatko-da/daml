// Copyright (c) 2022 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.ledger.api.benchtool

import com.daml.ledger.api.benchtool.config.WorkflowConfig.StreamConfig
import com.daml.ledger.api.benchtool.config.WorkflowConfig.StreamConfig.{
  ActiveContractsStreamConfig,
  CompletionsStreamConfig,
  PartySetTemplatesFilter,
  PartyFilter,
  TransactionTreesStreamConfig,
  TransactionsStreamConfig,
}
import com.daml.ledger.client.binding.{Primitive, Template}
import com.daml.ledger.test.benchtool.Foo.{Foo1, Foo2, Foo3}
import scalaz.syntax.tag._

class ConfigDesugaring(submissionStepResult: Option[SubmissionStepResult]) {

  private val knownTemplatesMap: Map[String, Primitive.TemplateId[Template[_]]] = Map(
    "Foo1" -> Foo1.id,
    "Foo2" -> Foo2.id,
    "Foo3" -> Foo3.id,
  )

  def desugar[T <: StreamConfig](
      streamConfig: T
  ): T = {
    streamConfig match {
      case config: TransactionsStreamConfig =>
        config
          .copy(
            filters = desugarPartyTemplateFilters(config.filters) ++ desugarFilterByPartySet(
              config.filterByPartySetO
            ),
            filterByPartySetO = None,
          )
          .asInstanceOf[T]
      case config: TransactionTreesStreamConfig =>
        config
          .copy(
            filters = desugarPartyTemplateFilters(config.filters) ++ desugarFilterByPartySet(
              config.filterByPartySetO
            ),
            filterByPartySetO = None,
          )
          .asInstanceOf[T]
      case config: ActiveContractsStreamConfig =>
        config
          .copy(
            filters = desugarPartyTemplateFilters(config.filters) ++ desugarFilterByPartySet(
              config.filterByPartySetO
            ),
            filterByPartySetO = None,
          )
          .asInstanceOf[T]
      case config: CompletionsStreamConfig =>
        config.copy(parties = config.parties.map(party => desugarParty(party))).asInstanceOf[T]
    }
  }

  private def desugarFilterByPartySet(
      filter: Option[PartySetTemplatesFilter]
  ): List[PartyFilter] =
    filter.fold(List.empty[PartyFilter])(desugarFilterByPartySet)

  private def desugarFilterByPartySet(
      filter: PartySetTemplatesFilter
  ): List[PartyFilter] = {
    val templates = filter.templates
    submissionStepResult match {
      case None =>
        sys.error("Cannot desugar party-set-template-filter as submission step results are missing")
      case Some(submissionResult) =>
        submissionResult.allocatedParties.partySetParties.map { party =>
          PartyFilter(party = party.unwrap, templates = templates)
        }
    }
  }

  private def desugarPartyTemplateFilters(
      filters: List[StreamConfig.PartyFilter]
  ): List[StreamConfig.PartyFilter] = {
    filters.map { filter =>
      StreamConfig.PartyFilter(
        party = desugarParty(filter.party),
        templates = filter.templates.map(desugarTemplate),
      )
    }
  }

  private def desugarParty(
      partyShortName: String
  ): String =
    submissionStepResult match {
      case None => partyShortName
      case Some(summary) =>
        summary.allocatedParties.allAllocatedParties
          .map(_.unwrap)
          .find(_.contains(partyShortName))
          .getOrElse(throw new RuntimeException(s"Party not found: $partyShortName"))
    }

  def desugarTemplate(shortTemplateName: String): String =
    knownTemplatesMap
      .get(
        shortTemplateName
      )
      .map { templateId =>
        val id = templateId.unwrap
        s"${id.packageId}:${id.moduleName}:${id.entityName}"
      }
      .getOrElse(shortTemplateName)

}
