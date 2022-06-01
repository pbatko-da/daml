// Copyright (c) 2022 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
package com.daml.platform.store.dao.events

import java.sql.Connection

import com.daml.ledger.offset.Offset
import com.daml.platform.store.EventSequentialId
import com.daml.platform.store.dao.QueryRange

private[events] object EventsRange {
  // (0, 0] -- an empty range
  private val EmptyEventSeqIdRange =
    QueryRange(EventSequentialId.beforeBegin, EventSequentialId.beforeBegin)

  def isEmpty[A: Ordering](range: QueryRange[A]): Boolean = {
    val A = implicitly[Ordering[A]]
    A.gteq(range.startExclusive, range.endInclusive)
  }

  final class EventSeqIdReader(
      maxEventSequentialIdOfAnObservableEvent: Offset => Connection => Option[Long]
  ) {

    /** Converts Offset range to Event Sequential ID range.
      *
      * @param range offset range
      * @param connection SQL connection
      * @return a range of event sequential ids corresponding to the given offset range.
      */
    def readEventSeqIdRange(range: QueryRange[Offset])(
        connection: java.sql.Connection
    ): QueryRange[Long] =
      if (isEmpty(range))
        EmptyEventSeqIdRange
      else
        QueryRange(
          startExclusive = readEventSeqId(range.startExclusive)(connection),
          endInclusive = readEventSeqId(range.endInclusive)(connection),
        )

    /** @return a range of event sequential ids corresponding to an offset range
      *         starting at the ledger-begin offset and ending (inclusive) at the given end offset
      */
    def readEventSeqIdRange(endInclusive: Offset)(
        connection: java.sql.Connection
    ): QueryRange[Long] = {
      if (endInclusive == Offset.beforeBegin) EmptyEventSeqIdRange
      else EmptyEventSeqIdRange.copy(endInclusive = readEventSeqId(endInclusive)(connection))
    }

    private def readEventSeqId(offset: Offset)(connection: java.sql.Connection): Long =
      maxEventSequentialIdOfAnObservableEvent(offset)(connection)
        .getOrElse(EventSequentialId.beforeBegin)
  }

  /** 1. Calls `fetch` at least once and at most twice.
    * 2. Returns no more than `pageSize` results.
    * 3. Returns either:
    *  - all matching items from the given range,
    *  - or at least 10% of the requested `pageSize`.
    */
  private[events] def readPage[A](
      fetch: (
          QueryRange[Long],
          Option[Int],
          Option[Int],
      ) => Connection => Vector[A], // takes range, limit, fetchSize hint
      range: QueryRange[Long],
      pageSize: Int,
  ): Connection => Vector[A] = connection => {
    // At least 10% of the specified pageSize. (Exactly 10% for large enough pages.)
    val minPageSize =
      if (pageSize < 10) pageSize
      else if (pageSize < 100) 10
      else pageSize / 10
    val smallestEndInclusive: Long = range.endInclusive min (range.startExclusive + pageSize)
    val firstSubpage: Vector[A] =
      fetch(
        range.copy(endInclusive = smallestEndInclusive),
        None,
        Some(pageSize),
      )(connection)
    val found = firstSubpage.size
    if ((range.startExclusive + pageSize) >= range.endInclusive || found >= minPageSize) {
      // This is the case where we either:
      // - queried from the entire originally requested range (because the size of the originally requested range size was no larger than the specified pageSize)
      //   and fetched all matching items from the range,
      // - or we queried from a smaller range (because the the size of the originally requested range was larger than the specified pageSize)
      //   and fetched at least `minPageSize` items.
      firstSubpage
    } else {
      // This is the case where we queried from a smaller range than originally requested
      // and we fetched fewer than `minPageSize` items.
      //
      // We now query again, but this time use the originally requested range, and set a limit of items to fetch
      // such that we are satisfied if we fetch `minPageSize` items in total across this and the previous query.
      //
      // It might be the case case the originally requested range has fewer than `minPageSize` matching items but
      // this is fine as we are satisfied since we processed the entire originally requested range.
      assert(range.startExclusive + pageSize < range.endInclusive)
      val secondSubpage = fetch(
        range.copy(startExclusive = range.startExclusive + pageSize),
        Some(minPageSize - found),
        Some(minPageSize - found),
      )(connection)
      firstSubpage ++ secondSubpage
    }
  }
}
