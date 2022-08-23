// Copyright (c) 2022 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.platform.store.platform.usermanagement

import com.daml.ledger.api.domain.{ObjectMeta, User, UserRight}
import com.daml.ledger.participant.state.index.v2.UserManagementStore
import com.daml.ledger.participant.state.index.v2.UserManagementStore.{
  UserExists,
  UserNotFound,
  UsersPage,
}
import com.daml.ledger.resources.TestResourceContext
import com.daml.lf.data.Ref
import com.daml.lf.data.Ref.{Party, UserId}
import com.daml.logging.LoggingContext
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{Assertion, EitherValues}

import scala.language.implicitConversions
import scala.concurrent.Future

/** Common tests for implementations of [[UserManagementStore]]
  */
trait UserManagementStoreTests extends TestResourceContext with Matchers with EitherValues {
  self: AsyncFreeSpec =>

  implicit val lc: LoggingContext = LoggingContext.ForTesting

  private implicit def toParty(s: String): Party =
    Party.assertFromString(s)

  private implicit def toUserId(s: String): UserId =
    UserId.assertFromString(s)

  def testIt(f: UserManagementStore => Future[Assertion]): Future[Assertion]

  def newUser(name: String): User = User(name, None, false, ObjectMeta.empty)
  def createdUser(name: String): User = User(name, None, false, ObjectMeta(
    resourceVersionO = Some("0"),
    annotations = Map.empty,
  ))

//  def clearResourceVersion(user: User): User = {
//    user.copy(metadata = user.metadata.copy(resourceVersionO = None))
//  }

  "user management" - {

    "allow creating a fresh user" in {
      testIt { tested =>
        for {
          res1 <- tested.createUser(newUser(s"user1"), Set.empty)
          res2 <- tested.createUser(newUser("user2"), Set.empty)
        } yield {
          res1 shouldBe Right(createdUser("user1"))
          res2 shouldBe Right(createdUser("user2"))
        }
      }
    }

    "disallow re-creating an existing user" in {
      testIt { tested =>
        val user = newUser("user1")
        for {
          res1 <- tested.createUser(user, Set.empty)
          res2 <- tested.createUser(user, Set.empty)
        } yield {
          res1 shouldBe Right(createdUser("user1"))
          res2 shouldBe Left(UserExists(user.id))
        }
      }
    }

    "find a freshly created user" in {
      testIt { tested =>
        val user = newUser("user1")
        for {
          res1 <- tested.createUser(user, Set.empty)
          user1 <- tested.getUser(user.id)
        } yield {
          res1 shouldBe Right(createdUser("user1"))
          user1 shouldBe res1
        }
      }
    }

    "not find a non-existent user" in {
      testIt { tested =>
        val userId: Ref.UserId = "user1"
        for {
          user1 <- tested.getUser(userId)
        } yield {
          user1 shouldBe Left(UserNotFound(userId))
        }
      }
    }
    "not find a deleted user" in {
      testIt { tested =>
        val user = newUser("user1")
        for {
          res1 <- tested.createUser(user, Set.empty)
          user1 <- tested.getUser("user1")
          res2 <- tested.deleteUser("user1")
          user2 <- tested.getUser("user1")
        } yield {
          res1 shouldBe Right(createdUser("user1"))
          user1 shouldBe res1
          res2 shouldBe Right(())
          user2 shouldBe Left(UserNotFound("user1"))
        }
      }
    }
    "allow recreating a deleted user" in {
      testIt { tested =>
        val user = newUser("user1")
        for {
          res1 <- tested.createUser(user, Set.empty)
          res2 <- tested.deleteUser(user.id)
          res3 <- tested.createUser(user, Set.empty)
        } yield {
          res1 shouldBe Right(createdUser("user1"))
          res2 shouldBe Right(())
          res3 shouldBe Right(createdUser("user1"))
        }

      }
    }
    "fail to delete a non-existent user" in {
      testIt { tested =>
        for {
          res1 <- tested.deleteUser("user1")
        } yield {
          res1 shouldBe Left(UserNotFound("user1"))
        }
      }
    }



    "list created users" in {
      testIt { tested =>
        for {
          _ <- tested.createUser(newUser("user1"), Set.empty)
          _ <- tested.createUser(newUser("user2"), Set.empty)
          _ <- tested.createUser(newUser("user3"), Set.empty)
          _ <- tested.createUser(newUser("user4"), Set.empty)
          list1 <- tested.listUsers(fromExcl = None, maxResults = 3)
          _ = list1 shouldBe Right(
            UsersPage(
              Seq(
                createdUser("user1"),
                createdUser("user2"),
                createdUser("user3"),
              )
            )
          )
          list2 <- tested.listUsers(
            fromExcl = list1.getOrElse(fail("Expecting a Right()")).lastUserIdOption,
            maxResults = 4,
          )
          _ = list2 shouldBe Right(UsersPage(Seq(createdUser("user4"))))
        } yield {
          succeed
        }
      }
    }
    "not list deleted users" in {
      testIt { tested =>
        for {
          res1 <- tested.createUser(newUser("user1"), Set.empty)
          res2 <- tested.createUser(newUser("user2"), Set.empty)
          users1 <- tested.listUsers(fromExcl = None, maxResults = 10000)
          res3 <- tested.deleteUser("user1")
          users2 <- tested.listUsers(fromExcl = None, maxResults = 10000)
        } yield {
          res1 shouldBe Right(createdUser("user1"))
          res2 shouldBe Right(createdUser("user2"))
          users1 shouldBe Right(
            UsersPage(
              Seq(
                createdUser("user1"),
                createdUser("user2"),
              )
            )
          )
          res3 shouldBe Right(())
          // TODO pbatko
          users2 shouldBe Right(UsersPage(Seq(createdUser("user2"))))

        }
      }
    }
  }

  "user rights management" - {
    import UserRight._
    "listUserRights should find the rights of a freshly created user" in {
      testIt { tested =>
        for {
          res1 <- tested.createUser(newUser("user1"), Set.empty)
          rights1 <- tested.listUserRights("user1")
          user2 <- tested.createUser(
            newUser("user2"),
            Set(ParticipantAdmin, CanActAs("party1"), CanReadAs("party2")),
          )
          rights2 <- tested.listUserRights("user2")
        } yield {
          res1 shouldBe Right(createdUser("user1"))
          rights1 shouldBe Right(Set.empty)
          user2 shouldBe Right(createdUser("user2"))
          rights2 shouldBe Right(
            Set(ParticipantAdmin, CanActAs("party1"), CanReadAs("party2"))
          )
        }
      }
    }
    "listUserRights should fail on non-existent user" in {
      testIt { tested =>
        for {
          rights1 <- tested.listUserRights("user1")
        } yield {
          rights1 shouldBe Left(UserNotFound("user1"))
        }
      }
    }
    "grantUserRights should add new rights" in {
      testIt { tested =>
        for {
          res1 <- tested.createUser(newUser("user1"), Set.empty)
          rights1 <- tested.grantRights("user1", Set(ParticipantAdmin))
          rights2 <- tested.grantRights("user1", Set(ParticipantAdmin))
          rights3 <- tested.grantRights("user1", Set(CanActAs("party1"), CanReadAs("party2")))
          rights4 <- tested.listUserRights("user1")
        } yield {
          res1 shouldBe Right(createdUser("user1"))
          rights1 shouldBe Right(Set(ParticipantAdmin))
          rights2 shouldBe Right(Set.empty)
          rights3 shouldBe Right(
            Set(CanActAs("party1"), CanReadAs("party2"))
          )
          rights4 shouldBe Right(
            Set(ParticipantAdmin, CanActAs("party1"), CanReadAs("party2"))
          )
        }
      }
    }
    "grantRights should fail on non-existent user" in {
      testIt { tested =>
        for {
          rights1 <- tested.grantRights("user1", Set.empty)
        } yield {
          rights1 shouldBe Left(UserNotFound("user1"))
        }

      }
    }
    "revokeRights should revoke rights" in {
      testIt { tested =>
        for {
          res1 <- tested.createUser(
            newUser("user1"),
            Set(ParticipantAdmin, CanActAs("party1"), CanReadAs("party2")),
          )
          rights1 <- tested.listUserRights("user1")
          rights2 <- tested.revokeRights("user1", Set(ParticipantAdmin))
          rights3 <- tested.revokeRights("user1", Set(ParticipantAdmin))
          rights4 <- tested.listUserRights("user1")
          rights5 <- tested.revokeRights("user1", Set(CanActAs("party1"), CanReadAs("party2")))
          rights6 <- tested.listUserRights("user1")
        } yield {
          res1 shouldBe Right(createdUser("user1"))
          rights1 shouldBe Right(
            Set(ParticipantAdmin, CanActAs("party1"), CanReadAs("party2"))
          )
          rights2 shouldBe Right(Set(ParticipantAdmin))
          rights3 shouldBe Right(Set.empty)
          rights4 shouldBe Right(Set(CanActAs("party1"), CanReadAs("party2")))
          rights5 shouldBe Right(
            Set(CanActAs("party1"), CanReadAs("party2"))
          )
          rights6 shouldBe Right(Set.empty)
        }
      }
    }
    "revokeRights should fail on non-existent user" in {
      testIt { tested =>
        for {
          rights1 <- tested.revokeRights("user1", Set.empty)
        } yield {
          rights1 shouldBe Left(UserNotFound("user1"))
        }
      }
    }
  }

}
