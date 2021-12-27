// Copyright (c) 2021 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.platform.usermanagement

import java.time.Duration

import com.daml.caching.{CaffeineCache, ConcurrentCache}
import com.daml.ledger.api.domain
import com.daml.ledger.api.domain.{User, UserRight}
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
import com.github.benmanes.caffeine.cache.Caffeine

import scala.concurrent.{ExecutionContext, Future}

object CachedUserManagementStore {

  // TODO pbatko: copied from InMemory version
  case class UserInfo(user: User, rights: Set[UserRight]) {
    def toStateEntry: (Ref.UserId, UserInfo) = user.id -> this
  }

}

class CachedUserManagementStore(
    private val delegate: UserManagementStore,
    expiryAfterWriteInSeconds: Int = 10,
)(implicit val executionContext: ExecutionContext)
    extends UserManagementStore {

//  import CachedUserManagementStore._

  private val usersCache: ConcurrentCache[UserId, User] = CaffeineCache[Ref.UserId, User](
    builder = Caffeine
      .newBuilder()
      .expireAfterWrite(Duration.ofSeconds(expiryAfterWriteInSeconds.toLong))
      // TODO pbatko: Adjust maximum Size
      .maximumSize(10000),
    // TODO pbatko: Use metrics
    metrics = None,
  )

  import com.github.benmanes.caffeine.cache.Caffeine

//  val cache: LoadingCache[Nothing, Nothing] =
//    Caffeine.newBuilder.maximumSize(10000).expireAfterWrite(10, TimeUnit.MINUTES)
//      .build((key) => ???)

  // TODO pbatko: Using a single or separate caches for users and user rights:
  // Problem with using a single cache:
  // 1. When accessing just user rights we retrieve the user's record so we can't populate it easily in the cache.
  // 2. Conversely, when accessing just user we don't retrieve the user's rights so we can't populate them easily in the cache.
  private val userRightsCache: ConcurrentCache[UserId, Set[UserRight]] =
    CaffeineCache[Ref.UserId, Set[UserRight]](
      builder = Caffeine
        .newBuilder()
        .expireAfterWrite(Duration.ofSeconds(expiryAfterWriteInSeconds.toLong))
        .maximumSize(10000),
      // TODO pbatko: Use metrics
      metrics = None,
    )

  // TODO copied from Persistent version
  private def tapSuccess[T](f: T => Unit)(r: Result[T]): Result[T] = {
    r match {
      case Right(v) => f(v)
      case Left(error) =>
        error match {
          case UserNotFound(userId) =>
            // TODO pbatko: Do we need this? deleteUser() already handles it
            usersCache.invalidate(userId)
            userRightsCache.invalidate(userId)
          case _: UserExists =>
          case _: TooManyUserRights =>
        }
    }
    r
  }

  override def createUser(user: domain.User, rights: Set[domain.UserRight]): Future[Result[Unit]] =
    delegate
      .createUser(user, rights)
      .map(tapSuccess { _ =>
        usersCache.put(user.id, user)
        userRightsCache.put(user.id, rights)
      })

  override def getUser(id: UserId): Future[Result[domain.User]] = {
    usersCache
      .getIfPresent(id)
      .fold(
        delegate
          .getUser(id)
          .map(tapSuccess(user => usersCache.put(id, user)))
      )((user) => Future.successful(Right(user)))
  }

  override def deleteUser(id: UserId): Future[Result[Unit]] = {
    delegate
      .deleteUser(id)
      .map(tapSuccess { _ =>
        usersCache.invalidate(id)
        userRightsCache.invalidate(id)
      })
  }

  override def grantRights(
      id: UserId,
      rights: Set[domain.UserRight],
  ): Future[Result[Set[domain.UserRight]]] = {
    delegate
      .grantRights(id, rights)
      .map(
        tapSuccess(granted =>
          // If user rights are in the cache then make them up-to-date.
          userRightsCache
            .getIfPresent(id)
            .foreach(cachedRights => userRightsCache.put(id, cachedRights.union(granted)))
        )
      )
  }

  override def revokeRights(
      id: UserId,
      rights: Set[domain.UserRight],
  ): Future[Result[Set[domain.UserRight]]] = {
    delegate
      .revokeRights(id, rights)
      .map(
        tapSuccess(revoked =>
          // If user rights are in the cache then make them up-to-date.
          userRightsCache
            .getIfPresent(id)
            .foreach(cachedRights => userRightsCache.put(id, cachedRights.diff(revoked)))
        )
      )
  }

  override def listUserRights(id: UserId): Future[Result[Set[domain.UserRight]]] = {
    userRightsCache
      .getIfPresent(id)
      .fold(
        delegate.listUserRights(id).map(tapSuccess(rights => userRightsCache.put(id, rights)))
      )(rights => Future.successful(Right(rights)))

  }

  override def listUsers(): Future[Result[Users]] = {
    delegate.listUsers()
  }
}
