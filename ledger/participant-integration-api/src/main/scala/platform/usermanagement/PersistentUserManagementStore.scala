// Copyright (c) 2021 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.platform.usermanagement

import java.sql.Connection
import java.time.Instant

import com.daml.ledger.api.auth.{
  CachedUserManagementBasedAuthorizer,
  UserManagementBasedAuthorizer,
  UserManagementBasedAuthorizerImpl,
}
import com.daml.ledger.api.domain
import com.daml.ledger.participant.state.index.impl.inmemory.InMemoryUserManagementStore
import com.daml.ledger.participant.state.index.v2.UserManagementStore
import com.daml.ledger.participant.state.index.v2.UserManagementStore.{
  Result,
  TooManyUserRights,
  UserExists,
  UserNotFound,
  Users,
}
import com.daml.lf.data.Ref
import com.daml.lf.data.Ref.UserId
import com.daml.logging.{ContextualizedLogger, LoggingContext}
import com.daml.metrics.Metrics
import com.daml.platform.apiserver.services.ApiVersionService
import com.daml.platform.store.appendonlydao.DbDispatcher
import com.daml.platform.store.backend.UserManagementStorageBackend
import com.daml.platform.store.backend.common.UserManagementStorageBackendTemplate
import com.daml.platform.usermanagement.PersistentUserManagementStore.TooManyUserRightsRuntimeException

import scala.concurrent.{ExecutionContext, Future}

object PersistentUserManagementStore {

  /** Intended to be thrown within a DB transaction to abort it.
    * The resulting failed future will get mapped to a successful future containing scala.util.Left
    */
  final case class TooManyUserRightsRuntimeException(userId: Ref.UserId) extends RuntimeException

  def cached(dbDispatcher: DbDispatcher, metrics: Metrics)(implicit
      executionContext: ExecutionContext
  ): UserManagementStore = {
    new CachedUserManagementStore(
      delegate = new PersistentUserManagementStore(
        dbDispatcher = dbDispatcher,
        metrics = metrics,
        maxNumberOfUserRightsPerUserLimit = ApiVersionService.MaxNumberOfUserRightsPerUser,
      ),
      expiryAfterWriteInSeconds = CachedUserManagementStore.ExpiryAfterWriteInSeconds,
    )
  }

  def cachedAuthorizer(userManagementStore: UserManagementStore)(implicit
      executionContext: ExecutionContext,
      loggingContext: LoggingContext,
  ): UserManagementBasedAuthorizer = {
    new CachedUserManagementBasedAuthorizer(
      delegate = new UserManagementBasedAuthorizerImpl(
        userManagementStore = userManagementStore
      )(
        executionContext = executionContext,
        loggingContext = loggingContext,
      ),
      expiryAfterWriteInSeconds = CachedUserManagementStore.ExpiryAfterWriteInSeconds,
    )
  }
}

class PersistentUserManagementStore(
    dbDispatcher: DbDispatcher,
    metrics: Metrics,
    // TODO pbatko
    createAdminUser: Boolean = true,
    maxNumberOfUserRightsPerUserLimit: Int,
) extends UserManagementStore {

  private val backend: UserManagementStorageBackend = UserManagementStorageBackendTemplate
  private val logger = ContextualizedLogger.get(this.getClass)

  implicit private val loggingContext: LoggingContext = LoggingContext.newLoggingContext(identity)

  // TODO pbatko: Find the correct place and time to create admin user
  if (createAdminUser)
    createUser(
      InMemoryUserManagementStore.AdminUser.user,
      InMemoryUserManagementStore.AdminUser.rights,
    )

  override def createUser(
      user: domain.User,
      rights: Set[domain.UserRight],
  ): Future[Result[Unit]] = {
    inTransaction { implicit connection: Connection =>
      // For PostgreSQL on READ_COMMITTED isolation level the following undesirable results can occur:
      // 1)
      // T1 Checks if user1 exists.
      // T2 Adds user1 and commits.
      // T1 Attempts to add user1 and fails with a constrain violation exception.
      // Expected: Fail with UserNotFound exception or prevent the failure from occurring.
      withoutUser(user.id) {
        if (rights.size > maxNumberOfUserRightsPerUserLimit) {
          throw TooManyUserRightsRuntimeException(user.id)
        } else {
          val nowMicros = epochMicroSeconds()
          val internalId = backend.createUser(user, createdAt = nowMicros)(connection)
          rights.foreach(right =>
            backend.addUserRight(internalId = internalId, right = right, grantedAt = nowMicros)(
              connection
            )
          )
          if (backend.countUserRights(internalId)(connection) > maxNumberOfUserRightsPerUserLimit) {
            throw TooManyUserRightsRuntimeException(user.id)
          } else {
            ()
          }
        }
      }
    }.map(tapSuccess { _ =>
      // TODO: Unit test logged messages
      logger.info(s"Created new user: ${user}")
      foreachEachUserRight(rights) { suffix =>
        logger.info(s"New user: ${user} was created with right $suffix")
      }
    })(scala.concurrent.ExecutionContext.parasitic)
  }

  override def deleteUser(id: UserId): Future[Result[Unit]] = {
    inTransaction { implicit connection =>
      if (!backend.deleteUser(id = id)(connection = connection)) {
        Left(UserNotFound(userId = id))
      } else {
        Right(())
      }
    }.map(tapSuccess { _ =>
      logger.info(s"Deleted user with id: ${id}")
    })(scala.concurrent.ExecutionContext.parasitic)
  }

  override def getUser(id: UserId): Future[Result[domain.User]] = {
    inTransaction { implicit connection =>
      withUser(id) { dbUser =>
        dbUser.domainUser
      }
    }
  }

  override def grantRights(
      id: UserId,
      rights: Set[domain.UserRight],
  ): Future[Result[Set[domain.UserRight]]] = {
    inTransaction { implicit connection =>
      // For PostgreSQL on READ_COMMITTED isolation level the following undesirable results can occur:
      // 1)
      // T1 Adds some rights to user1 but haven't committed yet.
      // T2 Deletes user1 and commits.
      // T1 Attempts to add some more rights to user1 and fails on foreign key constraint violation.
      // Expected: Fail with UserNotFound exception or prevent the failure from occurring.
      //
      // 2)
      // T1 Checks if user-right1 exists for user1.
      // T2 Adds user-right1 to user user1 and commits.
      // T1 Attempts to add user-right1 to user1 and fails on a unique constraint violation.
      // Expected: Requesting to grant a right that a user already posses should always succeed.
      withUser(id = id) { user =>
        if (rights.size > maxNumberOfUserRightsPerUserLimit) {
          throw TooManyUserRightsRuntimeException(user.domainUser.id)
        } else {
          val addedRights = rights.filter { right =>
            if (!backend.userRightExists(user.internalId, right)(connection)) {
              backend.addUserRight(
                internalId = user.internalId,
                right = right,
                grantedAt = epochMicroSeconds(),
              )(connection)
            } else {
              false
            }
          }
          if (
            backend.countUserRights(user.internalId)(connection) > maxNumberOfUserRightsPerUserLimit
          ) {
            throw TooManyUserRightsRuntimeException(user.domainUser.id)
          } else {
            addedRights
          }
        }
      }
    }.map(tapSuccess { grantedRights =>
      foreachEachUserRight(grantedRights) { suffix =>
        logger.info(s"Granted user rights to user id: ${id}. Granted right $suffix")
      }
    })(scala.concurrent.ExecutionContext.parasitic)
  }

  override def revokeRights(
      id: UserId,
      rights: Set[domain.UserRight],
  ): Future[Result[Set[domain.UserRight]]] = {
    inTransaction { implicit connection =>
      // For PostgreSQL on READ_COMMITTED isolation level the following undesirable results can occur:
      // 1)
      // TODO pbatko: This one should be ok if we allow for DELETE statement to delete 1 or 0 rows.
      //              But is it ok to return success from revokeRights if target user no longer exists?
      // T1 Deletes some rights from user1 but haven't committed yet.
      // T2 Deletes user1 and commits.
      // T1 Attempts to delete some more rights from user1 and fails on foreign key constraint violation.
      // Expected: Fail with UserNotFound exception or prevent the failure from occurring.
      //
      // 2)
      // TODO pbatko: This one should be ok if we allow for DELETE statement to delete 1 or 0 rows.
      // T1 Checks if user-right1 exists for user1.
      // T2 Deletes user-right1 to user user1.
      // T1 Attempts to delete user-right1 to user1 and fails on a unique constraint violation.
      // Expected: Requesting to grant a right that a user already posses should always succeed.
      withUser(id = id) { user =>
        val revokedRights = rights.filter { right =>
          if (backend.userRightExists(internalId = user.internalId, right = right)(connection)) {
            backend.deleteUserRight(internalId = user.internalId, right = right)(connection)
          } else {
            false
          }
        }
        revokedRights
      }
    }.map(tapSuccess { revokedRights =>
      foreachEachUserRight(revokedRights) { suffix =>
        logger.info(s"Revoked user rights from user id: ${id}. Revoked right $suffix")
      }
    })(scala.concurrent.ExecutionContext.parasitic)

  }

  override def listUserRights(id: UserId): Future[Result[Set[domain.UserRight]]] = {
    inTransaction { implicit connection =>
      withUser(id = id) { user =>
        backend.getUserRights(internalId = user.internalId)(connection)
      }
    }
  }

  override def listUsers(): Future[Result[Users]] = {
    inTransaction { connection =>
      Right(backend.getUsers()(connection))
    }
  }

  private def inTransaction[T](thunk: Connection => Result[T]): Future[Result[T]] = {
    // TODO pbatko: use finer-grained metrics
    dbDispatcher
      .executeSql(metrics.daml.userManagement.allUserManagement)(thunk)
      .recover { case TooManyUserRightsRuntimeException(userId) =>
        Left.apply(TooManyUserRights(userId))
      }(ExecutionContext.parasitic)
  }

  private def withUser[T](
      id: Ref.UserId
  )(f: UserManagementStorageBackend.DbUser => T)(implicit connection: Connection): Result[T] = {
    backend.getUser(id = id)(connection) match {
      case Some(user) => Right(f(user))
      case None => Left(UserNotFound(userId = id))
    }
  }

  private def withoutUser[T](
      id: Ref.UserId
  )(t: => T)(implicit connection: Connection): Result[T] = {
    backend.getUser(id = id)(connection) match {
      case Some(user) => Left(UserExists(userId = user.domainUser.id))
      case None => Right(t)
    }
  }

  private def epochMicroSeconds(): Long = Instant.now.getEpochSecond * 1000 * 1000

  private def tapSuccess[T](f: T => Unit)(r: Result[T]): Result[T] = {
    r.map { v =>
      f(v)
      v
    }
  }

  private def foreachEachUserRight(rights: Iterable[domain.UserRight])(f: String => Unit): Unit = {
    val rightsCount = rights.size
    rights.zipWithIndex.foreach { case (right, i) =>
      f(s"${i + 1}/$rightsCount): ${right}")
    }
  }

}
