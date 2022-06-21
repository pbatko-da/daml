// Copyright (c) 2022 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.ledger.api.benchtool

import com.daml.ledger.api.benchtool.config.WorkflowConfig.StreamConfig.{
  PartySetTemplatesFilter,
  PartyFilter,
  TransactionsStreamConfig,
}
import com.daml.ledger.api.benchtool.submission.AllocatedParties
import com.daml.ledger.client.binding.Primitive
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ConfigDesugaringSpec extends AnyFlatSpec with Matchers {

  it should "desugar party names" in {
    val randomSuffix = "-foo-123"

    def makePartyName(shortName: String): String = s"$shortName$randomSuffix"
    def makeParty(shortName: String): Primitive.Party = Primitive.Party(makePartyName(shortName))

    val desugaring = new ConfigDesugaring(
      submissionStepResult = Some(
        SubmissionStepResult(
          AllocatedParties(
            signatory = makeParty("s1"),
            observers = List(makeParty("o1")),
            divulgees = List(makeParty("d1")),
            extraSubmitters = List(makeParty("s1")),
            partySetParties = List("p1", "p2").map(makeParty),
          )
        )
      )
    )
    val templates: List[String] = List("t1", "t2")

    desugaring.desugar(
      TransactionsStreamConfig(
        name = "flat",
        filters = List(
          PartyFilter(
            party = "o1",
            templates = templates,
          ),
          PartyFilter(
            party = "d1",
            templates = templates,
          ),
        ),
        filterByPartySetO = Some(
          PartySetTemplatesFilter(
            partySetName = "party-set-123",
            templates = templates,
          )
        ),
      )
    ) shouldBe TransactionsStreamConfig(
      name = "flat",
      filters = List(
        PartyFilter(
          party = "o1-foo-123",
          templates = templates,
        ),
        PartyFilter(
          party = "d1-foo-123",
          templates = templates,
        ),
        PartyFilter(
          party = "p1-foo-123",
          templates = templates,
        ),
        PartyFilter(
          party = "p2-foo-123",
          templates = templates,
        ),
      ),
      filterByPartySetO = None,
    )

  }
}
