// Copyright (c) 2022 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.platform.usermanagement

import java.sql.Connection

import com.daml.api.util.TimeProvider
import com.daml.ledger.api.domain
import com.daml.ledger.api.domain.User
import com.daml.ledger.participant.state.index.v2.{UserManagementStore, UserUpdate}
import com.daml.ledger.participant.state.index.v2.UserManagementStore.{
  ConcurrentUserUpdateDetected,
  Result,
  TooManyUserRights,
  UserExists,
  UserInfo,
  UserNotFound,
  UsersPage,
}
import com.daml.lf.data.Ref
import com.daml.lf.data.Ref.UserId
import com.daml.logging.{ContextualizedLogger, LoggingContext}
import com.daml.metrics.{DatabaseMetrics, Metrics}
import com.daml.platform.store.DbSupport
import com.daml.platform.store.backend.UserManagementStorageBackend
import com.daml.platform.usermanagement.PersistentUserManagementStore.{
  ConcurrentUserUpdateDetectedRuntimeException,
  TooManyUserRightsRuntimeException,
}

import scala.concurrent.{ExecutionContext, Future}

object UserManagementConfig {

  val DefaultMaxCacheSize = 100
  val DefaultCacheExpiryAfterWriteInSeconds = 5
  val DefaultMaxUsersPageSize = 1000

  val MaxRightsPerUser = 1000

  def default(enabled: Boolean): UserManagementConfig = UserManagementConfig(
    enabled = enabled,
    maxCacheSize = DefaultMaxCacheSize,
    cacheExpiryAfterWriteInSeconds = DefaultCacheExpiryAfterWriteInSeconds,
    maxUsersPageSize = DefaultMaxUsersPageSize,
  )
}
final case class UserManagementConfig(
    enabled: Boolean = false,
    maxCacheSize: Int = UserManagementConfig.DefaultMaxCacheSize,
    cacheExpiryAfterWriteInSeconds: Int =
      UserManagementConfig.DefaultCacheExpiryAfterWriteInSeconds,
    maxUsersPageSize: Int = UserManagementConfig.DefaultMaxUsersPageSize,
)

object PersistentUserManagementStore {

  /** Intended to be thrown within a DB transaction to abort it.
    * The resulting failed future will get mapped to a successful future containing scala.util.Left
    */
  final case class TooManyUserRightsRuntimeException(userId: Ref.UserId) extends RuntimeException

  final case class ConcurrentUserUpdateDetectedRuntimeException(userId: Ref.UserId)
      extends RuntimeException

  def cached(
      dbSupport: DbSupport,
      metrics: Metrics,
      timeProvider: TimeProvider,
      cacheExpiryAfterWriteInSeconds: Int,
      maxCacheSize: Int,
      maxRightsPerUser: Int,
  )(implicit
      executionContext: ExecutionContext,
      loggingContext: LoggingContext,
  ): UserManagementStore = {
    new CachedUserManagementStore(
      delegate = new PersistentUserManagementStore(
        dbSupport = dbSupport,
        metrics = metrics,
        maxRightsPerUser = maxRightsPerUser,
        timeProvider = timeProvider,
      ),
      expiryAfterWriteInSeconds = cacheExpiryAfterWriteInSeconds,
      maximumCacheSize = maxCacheSize,
      metrics = metrics,
    )
  }
}

class PersistentUserManagementStore(
    dbSupport: DbSupport,
    metrics: Metrics,
    timeProvider: TimeProvider,
    maxRightsPerUser: Int,
) extends UserManagementStore {

  private val backend = dbSupport.storageBackendFactory.createUserManagementStorageBackend
  private val dbDispatcher = dbSupport.dbDispatcher

  private val logger = ContextualizedLogger.get(getClass)

  override def getUserInfo(id: UserId)(implicit
      loggingContext: LoggingContext
  ): Future[Result[UserInfo]] = {
    inTransaction(_.getUserInfo) { implicit connection =>
      withUser(id) { dbUser =>
        val rights = backend.getUserRights(internalId = dbUser.internalId)(connection)
        val annotations = backend.getUserAnnotations(internalId = dbUser.internalId)(connection)
        val domainUser = toDomainUser(dbUser, annotations)
        UserInfo(domainUser, rights.map(_.domainRight))
      }
    }
  }

  override def createUser(
      user: domain.User,
      rights: Set[domain.UserRight],
  )(implicit loggingContext: LoggingContext): Future[Result[User]] = {
    inTransaction(_.createUser) { implicit connection: Connection =>
      withoutUser(user.id) {
        val now = epochMicroseconds()
        val dbUser = UserManagementStorageBackend.DbUserPayload(
          id = user.id,
          primaryPartyO = user.primaryParty,
          isDeactivated = user.isDeactivated,
          resourceVersion = 0,
          createdAt = now,
        )
        val internalId = backend.createUser(user = dbUser)(connection)
        user.metadata.annotations.foreach { case (key, value) =>
          backend.addUserAnnotation(
            internalId = internalId,
            key = key,
            value = value,
            updatedAt = now,
          )(connection)
        }
        rights.foreach(right =>
          backend.addUserRight(internalId = internalId, right = right, grantedAt = now)(
            connection
          )
        )
        if (backend.countUserRights(internalId)(connection) > maxRightsPerUser) {
          throw TooManyUserRightsRuntimeException(user.id)
        }
        toDomainUser(
          dbUser = dbUser,
          annotations = user.metadata.annotations,
        )
      }
    }.map(tapSuccess { _ =>
      logger.info(
        s"Created new user: ${user} with ${rights.size} rights: ${rightsDigestText(rights)}"
      )
    })(scala.concurrent.ExecutionContext.parasitic)
  }

  override def updateUser(
      userUpdate: UserUpdate
  )(implicit loggingContext: LoggingContext): Future[Result[User]] = {
    inTransaction(_.updateUser) { implicit connection =>
      for {
        _ <- withUser(id = userUpdate.id) { dbUser =>
          val now = epochMicroseconds()
          // TODO pbatko: Implement 'merge' and 'replace-all' strategies.
          //              'Merge' would be consistent with FieldMask docs
          //              'Replace-all' would be available by 'replace_annotations' request attribute

          // update annotations - replace-all
          userUpdate.metadataUpdate.annotationsUpdate.foreach { newValue =>
            backend.deleteUserAnnotations(internalId = dbUser.internalId)(connection)
            newValue.iterator.foreach { case (key, value) =>
              backend.addUserAnnotation(
                internalId = dbUser.internalId,
                key = key,
                value = value,
                updatedAt = now,
              )(connection)
            }
          }
          // update is_deactivated
          userUpdate.isDeactivatedUpdate.foreach { newValue =>
            backend.updateUserIsDeactivated(
              internalId = dbUser.internalId,
              isDeactivated = newValue,
            )(connection)
          }
          // update primary_party
          userUpdate.primaryPartyUpdate.foreach { newValue =>
            backend.updateUserPrimaryParty(
              internalId = dbUser.internalId,
              primaryPartyO = newValue,
            )(connection)
          }
          // update resource version
          // TODO implement resource version as a bigint attribute in participant_users table
          if (userUpdate.metadataUpdate.resourceVersionO.isDefined) {
            // TODO pbatko: Parse resource version toLong error handling
            val expectedResourceVersion = userUpdate.metadataUpdate.resourceVersionO.get.toLong
            if (
              !backend.compareAndIncreaseResourceVersion(
                internalId = dbUser.internalId,
                expectedResourceVersion = expectedResourceVersion,
              )(connection)
            ) {
              throw ConcurrentUserUpdateDetectedRuntimeException(userUpdate.id)
            }
          } else {
            backend.increaseResourceVersion(
              internalId = dbUser.internalId
            )(connection)
          }
          ()
        }
        // TODO pbatko: Determine if re-reading the user from DB is appropriate or desirable.
        //              It seems its not needed as any any updater, that can override our updates,
        //              is waiting on the updateResourceVersion call (or deleteAllAnnotationsCall)
        //              until we commit and so there should be no inconsistent updated state (a state
        //              that is inconsistent with any sequencial application of any updates). I.e. we
        //              know the updates we issued will take place in the db fully.
        //              On the other hand, it's convenient to read from the db because by doing that
        //              we don't have to re-implement update logic in memory.
        domainUser <- withUser(id = userUpdate.id) { dbUserAfterUpdates =>
          val annotations =
            backend.getUserAnnotations(internalId = dbUserAfterUpdates.internalId)(connection)
          toDomainUser(dbUser = dbUserAfterUpdates, annotations = annotations)
        }
      } yield domainUser
    }
  }

  override def deleteUser(
      id: UserId
  )(implicit loggingContext: LoggingContext): Future[Result[Unit]] = {
    inTransaction(_.deleteUser) { implicit connection =>
      if (!backend.deleteUser(id = id)(connection)) {
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
  )(implicit loggingContext: LoggingContext): Future[Result[Set[domain.UserRight]]] = {
    inTransaction(_.grantRights) { implicit connection =>
      withUser(id = id) { user =>
        val now = epochMicroseconds()
        val addedRights = rights.filter { right =>
          if (!backend.userRightExists(internalId = user.internalId, right = right)(connection)) {
            backend.addUserRight(
              internalId = user.internalId,
              right = right,
              grantedAt = now,
            )(connection)
            true
          } else {
            false
          }
        }
        if (backend.countUserRights(user.internalId)(connection) > maxRightsPerUser) {
          throw TooManyUserRightsRuntimeException(user.payload.id)
        } else {
          addedRights
        }
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
  )(implicit loggingContext: LoggingContext): Future[Result[Set[domain.UserRight]]] = {
    inTransaction(_.revokeRights) { implicit connection =>
      withUser(id = id) { user =>
        val revokedRights = rights.filter { right =>
          backend.deleteUserRight(internalId = user.internalId, right = right)(connection)
        }
        revokedRights
      }
    }.map(tapSuccess { revokedRights =>
      logger.info(
        s"Revoked ${revokedRights.size} user rights from user ${id}: ${rightsDigestText(revokedRights)}"
      )
    })(scala.concurrent.ExecutionContext.parasitic)

  }

  override def listUsers(
      fromExcl: Option[Ref.UserId],
      maxResults: Int,
  )(implicit
      loggingContext: LoggingContext
  ): Future[Result[UsersPage]] = {
    inTransaction(_.listUsers) { connection =>
      val dbUsers = fromExcl match {
        case None => backend.getUsersOrderedById(None, maxResults)(connection)
        case Some(fromExcl) => backend.getUsersOrderedById(Some(fromExcl), maxResults)(connection)
      }
      val users = dbUsers.map { dbUser =>
        val annotations = backend.getUserAnnotations(dbUser.internalId)(connection)
        toDomainUser(dbUser = dbUser, annotations = annotations)
      }
      Right(UsersPage(users = users))
    }
  }

  private def inTransaction[T](
      dbMetric: metrics.daml.userManagement.type => DatabaseMetrics
  )(thunk: Connection => Result[T])(implicit loggingContext: LoggingContext): Future[Result[T]] = {
    dbDispatcher
      .executeSql(dbMetric(metrics.daml.userManagement))(thunk)
      .recover[Result[T]] {
        case TooManyUserRightsRuntimeException(userId) => Left(TooManyUserRights(userId))
        case ConcurrentUserUpdateDetectedRuntimeException(userId) =>
          Left(ConcurrentUserUpdateDetected(userId))
      }(ExecutionContext.parasitic)
  }

  private def toDomainUser(
      dbUser: UserManagementStorageBackend.DbUserWithId,
      annotations: Map[String, String],
  ): domain.User = {
    toDomainUser(
      dbUser = dbUser.payload,
      annotations = annotations,
    )
  }

  private def toDomainUser(
      dbUser: UserManagementStorageBackend.DbUserPayload,
      annotations: Map[String, String],
  ): domain.User = {
    val payload = dbUser
    domain.User(
      id = payload.id,
      primaryParty = payload.primaryPartyO,
      isDeactivated = payload.isDeactivated,
      metadata = domain.ObjectMeta(
        resourceVersionO = Some(payload.resourceVersion.toString),
        annotations = annotations,
      ),
    )
  }

  private def withUser[T](
      id: Ref.UserId
  )(
      f: UserManagementStorageBackend.DbUserWithId => T
  )(implicit connection: Connection): Result[T] = {
    backend.getUser(id = id)(connection) match {
      case Some(user) => Right(f(user))
      case None => Left(UserNotFound(userId = id))
    }
  }

  private def withoutUser[T](
      id: Ref.UserId
  )(t: => T)(implicit connection: Connection): Result[T] = {
    backend.getUser(id = id)(connection) match {
      case Some(user) => Left(UserExists(userId = user.payload.id))
      case None => Right(t)
    }
  }

  private def tapSuccess[T](f: T => Unit)(r: Result[T]): Result[T] = {
    r.foreach(f)
    r
  }

  private def rightsDigestText(rights: Iterable[domain.UserRight]): String = {
    val closingBracket = if (rights.size > 5) ", ..." else ""
    rights.take(5).mkString("", ", ", closingBracket)
  }

  private def epochMicroseconds(): Long = {
    val now = timeProvider.getCurrentTime
    (now.getEpochSecond * 1000 * 1000) + (now.getNano / 1000)
  }
}
