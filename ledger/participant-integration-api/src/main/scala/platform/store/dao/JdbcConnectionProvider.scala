// Copyright (c) 2021 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.platform.store.dao

import java.sql.Connection

import com.daml.ledger.api.health.ReportsHealth
import com.daml.metrics.DatabaseMetrics

/** A helper to run JDBC queries using a pool of managed connections */
private[platform] trait JdbcConnectionProvider extends ReportsHealth {

  /** Blocks are running in a single transaction as the commit happens when the connection
    * is returned to the pool.
    * The block must not recursively call [[runSQL]], as this could result in a deadlock
    * waiting for a free connection from the same pool.
    *
    * @param databaseMetrics Metrics to use for this SQL block.
    *
    * @param isolationLevel If set, the transaction will be run using a custom transaction isolation level.
    *                       See isolation levels defined on [[Connection]], e.g., [[Connection.TRANSACTION_SERIALIZABLE]]
    *                       Using a custom isolation level has a slight overhead, only use when required.
    */
  def runSQL[T](
      databaseMetrics: DatabaseMetrics,
      isolationLevel: Option[Int],
  )(block: Connection => T): T
}
