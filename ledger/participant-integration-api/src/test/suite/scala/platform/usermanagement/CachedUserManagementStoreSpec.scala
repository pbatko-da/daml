// Copyright (c) 2022 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.platform.usermanagement

import com.codahale.metrics.MetricRegistry
import com.daml.ledger.api.domain.{ObjectMeta, User, UserRight}
import com.daml.ledger.participant.state.index.impl.inmemory.InMemoryUserManagementStore
import com.daml.ledger.participant.state.index.v2.UserManagementStore
import com.daml.ledger.participant.state.index.v2.UserManagementStore.{
  UserInfo,
  UserNotFound,
  UsersPage,
}
import com.daml.ledger.resources.TestResourceContext
import com.daml.lf.data.Ref
import com.daml.logging.LoggingContext
import com.daml.metrics.Metrics
import com.daml.platform.store.platform.usermanagement.UserManagementStoreTests
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.Assertion
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Future

class CachedUserManagementStoreSpec
    extends AsyncFreeSpec
    with UserManagementStoreTests
    with TestResourceContext
    with Matchers
    with MockitoSugar
    with ArgumentMatchersSugar {

  private val user1 = User(
    id = Ref.UserId.assertFromString("user_id1"),
    primaryParty = Some(Ref.Party.assertFromString("primary_party1")),
    false,
    ObjectMeta.empty,
  )
  private val createdUser1 = User(
    id = Ref.UserId.assertFromString("user_id1"),
    primaryParty = Some(Ref.Party.assertFromString("primary_party1")),
    false,
    ObjectMeta(
      resourceVersionO = Some("0"),
      annotations = Map.empty,
    ),
  )

  private val right1 = UserRight.CanActAs(Ref.Party.assertFromString("party_id1"))
  private val right2 = UserRight.ParticipantAdmin
  private val right3 = UserRight.CanActAs(Ref.Party.assertFromString("party_id2"))
  private val rights = Set(right1, right2)
  private val userInfo = UserInfo(user1, rights)
  private val createdUserInfo = UserInfo(createdUser1, rights)

  "test user-not-found cache result gets invalidated after user creation" in {
    val delegate = spy(new InMemoryUserManagementStore())
    val tested = createTested(delegate)
    for {
      getYetNonExistent <- tested.getUserInfo(userInfo.user.id)
      _ <- tested.createUser(userInfo.user, userInfo.rights)
      get <- tested.getUserInfo(user1.id)
    } yield {
      getYetNonExistent shouldBe Left(UserNotFound(createdUserInfo.user.id))
      get shouldBe Right(createdUserInfo)
    }
  }

  "test cache population" in {
    val delegate = spy(new InMemoryUserManagementStore())
    val tested = createTested(delegate)

    for {
      _ <- tested.createUser(userInfo.user, userInfo.rights)
      get1 <- tested.getUserInfo(user1.id)
      get2 <- tested.getUserInfo(user1.id)
      getUser <- tested.getUser(user1.id)
      listRights <- tested.listUserRights(user1.id)
    } yield {
      verify(delegate, times(1)).createUser(userInfo.user, userInfo.rights)
      verify(delegate, times(1)).getUserInfo(userInfo.user.id)
      verifyNoMoreInteractions(delegate)
      get1 shouldBe Right(createdUserInfo)
      get2 shouldBe Right(createdUserInfo)
      getUser shouldBe Right(createdUserInfo.user)
      listRights shouldBe Right(createdUserInfo.rights)
    }
  }

  "test cache invalidation after every write method" in {
    val delegate = spy(new InMemoryUserManagementStore())
    val tested = createTested(delegate)

    val userInfo = UserInfo(user1, rights)

    for {
      _ <- tested.createUser(userInfo.user, userInfo.rights)
      get1 <- tested.getUserInfo(user1.id)
      _ <- tested.grantRights(user1.id, Set(right1))
      get2 <- tested.getUserInfo(user1.id)
      _ <- tested.revokeRights(user1.id, Set(right3))
      get3 <- tested.getUserInfo(user1.id)
      _ <- tested.deleteUser(user1.id)
      get4 <- tested.getUserInfo(user1.id)
    } yield {
      val order = inOrder(delegate)
      order.verify(delegate, times(1)).createUser(user1, userInfo.rights)
      order.verify(delegate, times(1)).getUserInfo(user1.id)
      order
        .verify(delegate, times(1))
        .grantRights(eqTo(user1.id), any[Set[UserRight]])(any[LoggingContext])
      order.verify(delegate, times(1)).getUserInfo(userInfo.user.id)
      order
        .verify(delegate, times(1))
        .revokeRights(eqTo(user1.id), any[Set[UserRight]])(any[LoggingContext])
      order.verify(delegate, times(1)).getUserInfo(userInfo.user.id)
      order.verify(delegate, times(1)).deleteUser(userInfo.user.id)
      order.verify(delegate, times(1)).getUserInfo(userInfo.user.id)
      order.verifyNoMoreInteractions()
      get1 shouldBe Right(createdUserInfo)
      get2 shouldBe Right(createdUserInfo)
      get3 shouldBe Right(createdUserInfo)
      get4 shouldBe Left(UserNotFound(createdUser1.id))
    }
  }

  "listing all users should not be cached" in {
    val delegate = spy(new InMemoryUserManagementStore(createAdmin = false))
    val tested = createTested(delegate)

    for {
      res0 <- tested.createUser(user1, rights)
      res1 <- tested.listUsers(fromExcl = None, maxResults = 100)
      res2 <- tested.listUsers(fromExcl = None, maxResults = 100)
    } yield {
      val order = inOrder(delegate)
      order.verify(delegate, times(1)).createUser(user1, rights)
      order.verify(delegate, times(2)).listUsers(fromExcl = None, maxResults = 100)
      order.verifyNoMoreInteractions()
      res0 shouldBe Right(createdUser1)
      res1 shouldBe Right(UsersPage(Seq(createdUser1)))
      res2 shouldBe Right(UsersPage(Seq(createdUser1)))
    }
  }

  "cache entries expire after a set time" in {
    val delegate = spy(new InMemoryUserManagementStore())
    val tested = createTested(delegate)

    for {
      create1 <- tested.createUser(user1, rights)
      get1 <- tested.getUserInfo(user1.id)
      get2 <- tested.getUserInfo(user1.id)
      get3 <- {
        Thread.sleep(2000); tested.getUserInfo(user1.id)
      }
    } yield {
      val order = inOrder(delegate)
      order
        .verify(delegate, times(1))
        .createUser(any[User], any[Set[UserRight]])(any[LoggingContext])
      order.verify(delegate, times(2)).getUserInfo(any[Ref.UserId])(any[LoggingContext])
      order.verifyNoMoreInteractions()
      create1 shouldBe Right(createdUser1)
      get1 shouldBe Right(createdUserInfo)
      get2 shouldBe Right(createdUserInfo)
      get3 shouldBe Right(createdUserInfo)
    }
  }

  private def createTested(delegate: InMemoryUserManagementStore): CachedUserManagementStore = {
    new CachedUserManagementStore(
      delegate,
      expiryAfterWriteInSeconds = 1,
      maximumCacheSize = 10,
      new Metrics(new MetricRegistry),
    )
  }

  override def testIt(f: UserManagementStore => Future[Assertion]): Future[Assertion] = {
    f(createTested(new InMemoryUserManagementStore(createAdmin = false)))
  }

}
