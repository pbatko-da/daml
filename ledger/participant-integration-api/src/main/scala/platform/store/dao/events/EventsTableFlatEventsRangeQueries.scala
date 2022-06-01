// Copyright (c) 2022 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.platform.store.dao.events

import com.daml.platform.FilterRelation
import java.sql.Connection

import com.daml.platform.store.backend.EventStorageBackend
import com.daml.platform.store.backend.EventStorageBackend.FilterParams
import com.daml.platform.store.dao.QueryRange

private[events] final class EventsTableFlatEventsRangeQueries(
    storageBackend: EventStorageBackend
) {

  def readFlatTransactions(
      eventsRange: QueryRange[Long],
      filter: FilterRelation,
      pageSize: Int,
  ): Connection => Vector[EventStorageBackend.Entry[Raw.FlatEvent]] = {
    require(filter.nonEmpty, "The request must be issued by at least one party")

    // Route the request to the correct underlying query
    val filterParams = convertToFilterParams(filter)

    def read(eventsRange: QueryRange[Long], limit: Option[Int], fetchSizeHint: Option[Int])(
        connection: Connection
    ): Vector[EventStorageBackend.Entry[Raw.FlatEvent]] = {
      storageBackend.transactionEvents(
        startExclusive = eventsRange.startExclusive,
        endInclusive = eventsRange.endInclusive,
        limit = limit,
        fetchSizeHint = fetchSizeHint,
        filterParams = filterParams,
      )(connection)
    }

    EventsRange.readPage(
      fetch = read,
      range = eventsRange,
      pageSize = pageSize,
    )
  }

  private def convertToFilterParams(filter: FilterRelation): FilterParams = {
    if (filter.size == 1) {
      val (party, templateIds) = filter.iterator.next()
      if (templateIds.isEmpty) {
        // Single-party request, no specific template identifier
        FilterParams(
          wildCardParties = Set(party),
          partiesAndTemplates = Set.empty,
        )
      } else {
        // Single-party request, restricted to a set of template identifiers
        FilterParams(
          wildCardParties = Set.empty,
          partiesAndTemplates = Set(Set(party) -> templateIds),
        )
      }
    } else {
      // Multi-party requests
      // If no party requests specific template identifiers
      val parties = filter.keySet
      if (filter.forall(_._2.isEmpty))
        FilterParams(
          wildCardParties = parties,
          partiesAndTemplates = Set.empty,
        )
      else {
        // If all parties request the same template identifier
        val templateIds = filter.valuesIterator.flatten.toSet
        if (filter.valuesIterator.forall(_ == templateIds)) {
          FilterParams(
            wildCardParties = Set.empty,
            partiesAndTemplates = Set(parties -> templateIds),
          )
        } else {
          // The generic case: passing down in the same shape, collecting wildCardParties
          FilterParams(
            wildCardParties = filter.filter(_._2.isEmpty).keySet,
            partiesAndTemplates = filter.iterator.collect {
              case (party, templateIds) if templateIds.nonEmpty => Set(party) -> templateIds
            }.toSet,
          )
        }
      }
    }
  }
}
