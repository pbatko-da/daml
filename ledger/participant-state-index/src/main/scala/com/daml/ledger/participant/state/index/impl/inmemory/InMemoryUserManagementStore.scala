// Copyright (c) 2022 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.ledger.participant.state.index.impl.inmemory

import com.daml.ledger.api.domain.{ObjectMeta, User, UserRight}
import com.daml.ledger.participant.state.index.v2.{
  AnnotationsUpdate,
  UserManagementStore,
  UserUpdate,
}
import com.daml.ledger.participant.state.index.v2.UserManagementStore._
import com.daml.lf.data.Ref
import com.daml.lf.data.Ref.UserId
import com.daml.logging.LoggingContext

import scala.collection.mutable
import scala.concurrent.Future

class InMemoryUserManagementStore(createAdmin: Boolean = true) extends UserManagementStore {
  import InMemoryUserManagementStore._

  // Underlying mutable map to keep track of UserInfo state.
  // Structured so we can use a ConcurrentHashMap (to more closely mimic a real implementation, where performance is key).
  // We synchronize on a private object (the mutable map), not the service (which could cause deadlocks).
  // (No need to mark state as volatile -- rely on synchronized to establish the JMM's happens-before relation.)
  private val state: mutable.TreeMap[Ref.UserId, UserInfo] = mutable.TreeMap()
  if (createAdmin) {
    state.put(AdminUser.user.id, AdminUser)
  }

  override def getUserInfo(id: UserId)(implicit
      loggingContext: LoggingContext
  ): Future[Result[UserManagementStore.UserInfo]] =
    withUser(id)(identity)

  override def createUser(user: User, rights: Set[UserRight])(implicit
      loggingContext: LoggingContext
  ): Future[Result[User]] =
    withoutUser(user.id) {
      val userWithResourceVersion =
        user.copy(metadata = user.metadata.copy(resourceVersionO = Some(0)))
      state.update(user.id, UserInfo(userWithResourceVersion, rights))
      userWithResourceVersion
    }

  override def updateUser(
      userUpdate: UserUpdate
  )(implicit loggingContext: LoggingContext): Future[Result[User]] = {
    withUser(userUpdate.id) { userInfo =>
      val updatedPrimaryParty = userUpdate.primaryPartyUpdateO.getOrElse(userInfo.user.primaryParty)
      val existingAnnotations = userInfo.user.metadata.annotations
      val updatedAnnotations =
        userUpdate.metadataUpdate.annotationsUpdateO.fold(existingAnnotations) {
          case AnnotationsUpdate.Merge(newAnnotations) => existingAnnotations.concat(newAnnotations)
          case AnnotationsUpdate.Replace(newAnnotations) => newAnnotations
        }
      val newResourceVersion = 1L + userInfo.user.metadata.resourceVersionO
        // TODO um-for-hub: Use error codes
        .getOrElse(
          sys.error(
            s"Could not find resource version on user: ${userInfo.user}. All created users must have a resource version"
          )
        )
      val updatedUserInfo = userInfo.copy(
        user = userInfo.user.copy(
          primaryParty = updatedPrimaryParty,
          metadata = ObjectMeta(
            resourceVersionO = Some(newResourceVersion),
            annotations = updatedAnnotations,
          ),
        )
      )
      state.update(userUpdate.id, updatedUserInfo)
      updatedUserInfo.user
    }
  }

  override def deleteUser(
      id: Ref.UserId
  )(implicit loggingContext: LoggingContext): Future[Result[Unit]] =
    withUser(id) { _ =>
      state.remove(id)
      ()
    }

  override def grantRights(
      id: Ref.UserId,
      granted: Set[UserRight],
  )(implicit loggingContext: LoggingContext): Future[Result[Set[UserRight]]] =
    withUser(id) { userInfo =>
      val newlyGranted = granted.diff(userInfo.rights) // faster than filter
      // we're not doing concurrent updates -- assert as backstop and a reminder to handle the collision case in the future
      assert(
        replaceInfo(userInfo, userInfo.copy(rights = userInfo.rights ++ newlyGranted))
      )
      newlyGranted
    }

  override def revokeRights(
      id: Ref.UserId,
      revoked: Set[UserRight],
  )(implicit loggingContext: LoggingContext): Future[Result[Set[UserRight]]] =
    withUser(id) { userInfo =>
      val effectivelyRevoked = revoked.intersect(userInfo.rights) // faster than filter
      // we're not doing concurrent updates -- assert as backstop and a reminder to handle the collision case in the future
      assert(
        replaceInfo(userInfo, userInfo.copy(rights = userInfo.rights -- effectivelyRevoked))
      )
      effectivelyRevoked
    }

  override def listUsers(
      fromExcl: Option[Ref.UserId],
      maxResults: Int,
  )(implicit
      loggingContext: LoggingContext
  ): Future[Result[UsersPage]] = {
    withState {
      val iter: Iterator[UserInfo] = fromExcl match {
        case None => state.valuesIterator
        case Some(after) => state.valuesIteratorFrom(start = after).dropWhile(_.user.id == after)
      }
      val users: Seq[User] = iter
        .take(maxResults)
        .map(_.user)
        .toSeq
      Right(UsersPage(users = users))
    }
  }

  private def withState[T](t: => T): Future[T] =
    state.synchronized(
      Future.successful(t)
    )

  private def withUser[T](id: Ref.UserId)(f: UserInfo => T): Future[Result[T]] =
    withState(
      state.get(id) match {
        case Some(user) => Right(f(user))
        case None => Left(UserNotFound(id))
      }
    )

  private def withoutUser[T](id: Ref.UserId)(t: => T): Future[Result[T]] =
    withState(
      state.get(id) match {
        case Some(_) => Left(UserExists(id))
        case None => Right(t)
      }
    )

  private def replaceInfo(oldInfo: UserInfo, newInfo: UserInfo) = state.synchronized {
    assert(
      oldInfo.user.id == newInfo.user.id,
      s"Replace info from if ${oldInfo.user.id} to ${newInfo.user.id} -> ${newInfo.rights}",
    )
    state.get(oldInfo.user.id) match {
      case Some(`oldInfo`) => state.update(newInfo.user.id, newInfo); true
      case _ => false
    }
  }

}

object InMemoryUserManagementStore {

  private val AdminUser = UserInfo(
    user = User(
      id = Ref.UserId.assertFromString(UserManagementStore.DefaultParticipantAdminUserId),
      primaryParty = None,
      isDeactivated = false,
      // TODO um-for-hub: Fill resource version
      metadata = ObjectMeta.empty,
    ),
    rights = Set(UserRight.ParticipantAdmin),
  )
}
