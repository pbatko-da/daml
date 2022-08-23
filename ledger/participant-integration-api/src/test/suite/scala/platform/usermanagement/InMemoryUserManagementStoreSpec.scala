// Copyright (c) 2022 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.platform.usermanagement

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

import com.codahale.metrics.{InstrumentedExecutorService, MetricRegistry}
import com.daml.api.util.TimeProvider
import com.daml.ledger.participant.state.index.impl.inmemory.InMemoryUserManagementStore
import com.daml.ledger.participant.state.index.v2.UserManagementStore
import com.daml.ledger.resources.ResourceContext
import com.daml.logging.{ContextualizedLogger, LoggingContext}
import com.daml.metrics.{DatabaseMetrics, MetricName, Metrics}
import com.daml.platform.configuration.ServerRole
import com.daml.platform.store.{DbSupport, DbType, FlywayMigrations}
import com.daml.platform.store.DbSupport.{ConnectionPoolConfig, DbConfig}
import com.daml.platform.store.backend.StorageBackendProviderPostgres
import com.daml.platform.store.platform.usermanagement.{
  UserManagementStoreSpecBase,
  UserManagementStoreSpecBaseBasic,
}
import com.daml.resources.Resource
import com.google.common.util.concurrent.ThreadFactoryBuilder
import org.scalatest.{Assertion, BeforeAndAfterEach}
import org.scalatest.freespec.AsyncFreeSpec

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}

class InMemoryUserManagementStoreSpec extends AsyncFreeSpec with UserManagementStoreSpecBase {

  override def testIt(f: UserManagementStore => Future[Assertion]): Future[Assertion] = {
    f(
      new InMemoryUserManagementStore(
        createAdmin = false
      )
    )
  }

}

class PersistentUserManagementStoreSpec
    extends AsyncFreeSpec
//    with UserManagementStoreSpecBase
    with UserManagementStoreSpecBaseBasic
    with StorageBackendProviderPostgres
    with BeforeAndAfterEach
//with StorageBackendSpec
    {

  protected val logger: ContextualizedLogger = ContextualizedLogger.get(getClass)

  var dbSupport: DbSupport = _

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    logger.warn("ALA123 PersistentUserManagementStoreSpec after super.beforeAll")

    val metrics = new Metrics(new MetricRegistry())

    val executorService = new InstrumentedExecutorService(
      Executors.newFixedThreadPool(
        2,
        new ThreadFactoryBuilder()
          .setNameFormat(s"ALA123-%d")
          .setUncaughtExceptionHandler((_, e) =>
            logger.error("Uncaught exception in the SQL executor.", e)
          )
          .build(),
      ),
      metrics.registry,
      "ALA123-threadpool",
    )
    val executionContext = ExecutionContext.fromExecutor(
      executorService,
      throwable => logger.error("ExecutionContext has failed with an exception", throwable),
    )
    val resourceContext = ResourceContext(executionContext)

    val x: Resource[ResourceContext, DbSupport] = DbSupport
      .owner(
        // NOTE: Based on com.daml.platform.store.dao.JdbcLedgerDaoBackend
        dbConfig = DbConfig(
          jdbcUrl,
          connectionPool = ConnectionPoolConfig(
            connectionPoolSize = DbType.Postgres.maxSupportedWriteConnections(16),
            connectionTimeout = 250.millis,
          ),
        ),
        serverRole = ServerRole.Testing(getClass),
        metrics = new Metrics(new MetricRegistry()),
      )(
        LoggingContext.empty
      )
      .acquire()(resourceContext)

    val dbSupportFuture: Future[DbSupport] = x.asFuture.flatMap { dbSupport =>
      logger.warn("ALA123 About to FlywayMigrations")
      new FlywayMigrations(jdbcUrl)(
        resourceContext,
        LoggingContext.empty,
      ).migrate()
        .map { _ =>
          logger.warn("ALA123 Done FlywayMigrations")
          dbSupport
        }(executionContext)
    }(executionContext)

    dbSupport = Await.result(dbSupportFuture, 20.seconds)
//          .use { dbSupport: DbSupport =>
//            for {
//              _ <- new FlywayMigrations(jdbcUrl).migrate()
//              testResult <- f(
//                new PersistentUserManagementStore(
//                  dbSupport = dbSupport,
//                  metrics = new Metrics(new MetricRegistry()),
//                  timeProvider = TimeProvider.UTC,
//                  maxRightsPerUser = 100,
//                )
//              )
//            } yield testResult
//          }

  }

  private val runningTests = new AtomicInteger(0)

  override protected def afterEach(): Unit = {
    assert(
      runningTests.decrementAndGet() == 0,
      "StorageBackendSpec tests must not run in parallel, as they all run against the same database.",
    )
    super.afterEach()
  }

  // Each test should start with an empty database to allow testing low-level behavior
  // However, creating a fresh database for each test would be too expensive.
  // Instead, we truncate all tables using the reset() call before each test.
  override protected def beforeEach(): Unit = {
    super.beforeEach()

    val dbMetrics = new DatabaseMetrics(
      new MetricRegistry(),
      MetricName("ALA123"),
      "ALA123 name xxx",
    )
    val resetFuture = dbSupport.dbDispatcher.executeSql(dbMetrics) { connection =>
      backend.reset.resetAll(connection)
      updateLedgerEndCache(connection)
      backend.stringInterningSupport.reset()
    }

    Await.ready(resetFuture, 10.seconds)

    assert(
      runningTests.incrementAndGet() == 1,
      "StorageBackendSpec tests must not run in parallel, as they all run against the same database.",
    )

//    // Reset the content of the index database
//    backend.reset.resetAll(defaultConnection)
//    updateLedgerEndCache(defaultConnection)
//
//    // Note: here we reset the MockStringInterning object to make sure each test starts with empty interning state.
//    // This is not strictly necessary, as tryInternalize() always succeeds in MockStringInterning - we don't have
//    // a problem where the interning would be affected by data left over by previous tests.
//    // To write tests that are sensitive to interning unknown data, we would have to use a custom storage backend
//    // implementation.
//    backend.stringInterningSupport.reset()
    ()
  }

  override def testIt(f: UserManagementStore => Future[Assertion]): Future[Assertion] = {
    f(
      new PersistentUserManagementStore(
        dbSupport = dbSupport,
        metrics = new Metrics(new MetricRegistry()),
        timeProvider = TimeProvider.UTC,
        maxRightsPerUser = 100,
      )
    )
  }

//  implicit protected val loggingContext: LoggingContext = LoggingContext.ForTesting
//
//  // TODO pbatko: Copied from com.daml.platform.store.backend.StorageBackendSpec.beforeAll
//  override protected def beforeAll(): Unit = {
//    super.beforeAll()
//
//    // Note: reusing the connection pool EC for initialization
////    implicit val ec: ExecutionContext = connectionPoolExecutionContext
//    implicit val ec: ExecutionContext = implicitly[ExecutionContext]
//    implicit val resourceContext: ResourceContext = ResourceContext(ec)
//    implicit val loggingContext: LoggingContext = LoggingContext.ForTesting
//
//    val dataSourceFuture = for {
//      _ <- new FlywayMigrations(jdbcUrl).migrate()
//      dataSource <- VerifiedDataSource(jdbcUrl)
//    } yield dataSource
//
//    dataSource = Await.result(dataSourceFuture, 60.seconds)
//
//    logger.info(
//      s"Finished setting up database $jdbcUrl for tests. You can now connect to this database to debug failed tests. Note that tables are truncated between each test."
//    )
//  }

}
