// Copyright (c) 2022 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.platform.usermanagement

import java.sql.Connection

import com.daml.ledger.api.domain
import com.daml.ledger.participant.state.index.v2.UserManagementStore
import com.daml.ledger.participant.state.index.v2.UserManagementStore.{
  Result,
  UserExists,
  UserInfo,
  UserNotFound,
  Users,
}
import com.daml.lf.data.Ref
import com.daml.lf.data.Ref.UserId
import com.daml.logging.{ContextualizedLogger, LoggingContext}
import com.daml.metrics.Metrics
import com.daml.platform.store.appendonlydao.DbDispatcher
import com.daml.platform.store.backend.UserManagementStorageBackend
import com.daml.platform.store.backend.common.UserManagementStorageBackendTemplate

import scala.concurrent.Future

class PersistentUserManagementStore(
    dbDispatcher: DbDispatcher,
    metrics: Metrics,
) extends UserManagementStore {

  private val backend: UserManagementStorageBackend = UserManagementStorageBackendTemplate
  private val logger = ContextualizedLogger.get(getClass)

  implicit private val loggingContext: LoggingContext = LoggingContext.newLoggingContext(identity)

  override def getUserInfo(id: UserId): Future[Result[UserInfo]] = {
    inTransaction { implicit connection =>
      withUser(id) { dbUser =>
        val rights = backend.getUserRights(internalId = dbUser.internalId)(connection)
        UserInfo(dbUser.domainUser, rights)
      }
    }
  }

  override def createUser(
      user: domain.User,
      rights: Set[domain.UserRight],
  ): Future[Result[Unit]] = {
    inTransaction { implicit connection: Connection =>
      withoutUser(user.id) {
        val internalId = backend.createUser(user)(connection)
        rights.foreach(right =>
          backend.addUserRight(internalId = internalId, right = right)(
            connection
          )
        )
        ()
      }
    }.map(tapSuccess { _ =>
      // TODO participant user management: Unit test logged messages
      logger.info(
        s"Created new user: ${user} with ${rights.size} rights: ${rightsDigestText(rights)}"
      )
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

  override def grantRights(
      id: UserId,
      rights: Set[domain.UserRight],
  ): Future[Result[Set[domain.UserRight]]] = {
    inTransaction { implicit connection =>
      withUser(id = id) { user =>
        val addedRights = rights.filter { right =>
          if (!backend.userRightExists(internalId = user.internalId, right = right)(connection)) {
            backend.addUserRight(
              internalId = user.internalId,
              right = right,
            )(connection)
          } else {
            false
          }
        }
        addedRights
      }
    }.map(tapSuccess { grantedRights =>
      logger.info(
        s"Granted ${grantedRights.size} user rights to user ${id}: ${rightsDigestText(grantedRights)}"
      )
    })(scala.concurrent.ExecutionContext.parasitic)
  }

  override def revokeRights(
      id: UserId,
      rights: Set[domain.UserRight],
  ): Future[Result[Set[domain.UserRight]]] = {
    inTransaction { implicit connection =>
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
      logger.info(
        s"Revoked ${revokedRights.size} user rights from user ${id}: ${rightsDigestText(revokedRights)}"
      )
    })(scala.concurrent.ExecutionContext.parasitic)

  }

  override def listUsers(): Future[Result[Users]] = {
    inTransaction { connection =>
      Right(backend.getUsers()(connection))
    }
  }

  private def inTransaction[T](thunk: Connection => T): Future[T] = {
    // TODO participant user management: use finer-grained metrics
    dbDispatcher.executeSql(metrics.daml.userManagement.allUserManagement)(thunk)
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

  private def tapSuccess[T](f: T => Unit)(r: Result[T]): Result[T] = {
    r.map { v =>
      f(v)
      v
    }
  }

  private def rightsDigestText(rights: Iterable[domain.UserRight]): String = {
    val closingBracket = if (rights.size > 5) ", ..." else ""
    rights.take(5).mkString("", ", ", closingBracket)
  }

}
