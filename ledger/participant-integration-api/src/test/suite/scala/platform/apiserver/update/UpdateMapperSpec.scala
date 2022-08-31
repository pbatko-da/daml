// Copyright (c) 2022 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.platform.apiserver.update

import com.daml.ledger.api.domain.{ObjectMeta, User}
import com.daml.ledger.participant.state.index.v2.{AnnotationsUpdate, ObjectMetaUpdate, UserUpdate}
import com.daml.ledger.participant.state.index.v2.AnnotationsUpdate.{Merge, Replace}
import com.daml.lf.data.Ref
import com.google.protobuf.field_mask.FieldMask
import org.scalatest.EitherValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

// TODO pbatko: Test update Replace
// TODO pbatko: Test explicit !merge, !replace, !bad, !misplaced-on-wrong-field
class UpdateMapperSpec extends AnyFreeSpec with Matchers with EitherValues {

  private val userId1: Ref.UserId = Ref.UserId.assertFromString("u1")
  private val party1 = Ref.Party.assertFromString("party")

  // NOTE: By default modifiable fields of this user are set to non-default (in proto3 sense) values.
  def newUserWithNonDefaultValues(
      id: Ref.UserId = userId1,
      primaryParty: Option[Ref.Party] = Some(party1),
      isDeactivated: Boolean = true,
      annotations: Map[String, String] = Map("a" -> "b"),
  ): User = User(
    id = id,
    primaryParty = primaryParty,
    isDeactivated = isDeactivated,
    metadata = ObjectMeta(
      resourceVersionO = None,
      annotations = annotations,
    ),
  )

  def makeUser(
      id: Ref.UserId = userId1,
      primaryParty: Option[Ref.Party] = None,
      isDeactivated: Boolean = false,
      annotations: Map[String, String] = Map.empty,
  ): User = User(
    id = id,
    primaryParty = primaryParty,
    isDeactivated = isDeactivated,
    metadata = ObjectMeta(
      resourceVersionO = None,
      annotations = annotations,
    ),
  )

  def makeUserUpdate(
      id: Ref.UserId = userId1,
      primaryPartyUpdateO: Option[Option[Ref.Party]] = None,
      isDeactivatedUpdateO: Option[Boolean] = None,
      annotationsUpdateO: Option[AnnotationsUpdate] = None,
  ): UserUpdate = UserUpdate(
    id = id,
    primaryPartyUpdateO = primaryPartyUpdateO,
    isDeactivatedUpdateO = isDeactivatedUpdateO,
    metadataUpdate = ObjectMetaUpdate(
      resourceVersionO = None,
      annotationsUpdateO = annotationsUpdateO,
    ),
  )

  val emptyUserUpdate: UserUpdate = makeUserUpdate()

  // TODO test !merge path with default value

  "map to user updates" - {
    "basic mapping" - {
      val user = makeUser(
        primaryParty = None,
        isDeactivated = false,
        annotations = Map("a" -> "b"),
      )
      val expected = makeUserUpdate(
        primaryPartyUpdateO = Some(None),
        isDeactivatedUpdateO = Some(false),
        annotationsUpdateO = Some(Merge.fromNonEmpty(Map("a" -> "b"))),
      )
      "1) with all individual fields to update listed in the update mask" in {
        UpdateMapper
          .toUserUpdate(user, FieldMask(Seq("user.is_deactivated", "user.primary_party", "user.metadata.annotations")))
          .value shouldBe expected
      }
      "2) with metadata.annotations not listed explicitly" in {
        UpdateMapper
          .toUserUpdate(user, FieldMask(Seq("user.is_deactivated", "user.primary_party", "user.metadata")))
          .value shouldBe expected
      }
    }

    "map api request to update - merge user and reset is_deactivated" - {
      val user = makeUser(
        // non-default value
        primaryParty = Some(party1),
        // default value
        isDeactivated = false,
        // non-default value
        annotations = Map("a" -> "b"),
      )
      val expected = makeUserUpdate(
        primaryPartyUpdateO = Some(Some(party1)),
        isDeactivatedUpdateO = Some(false),
        annotationsUpdateO = Some(Merge.fromNonEmpty(Map("a" -> "b"))),
      )
      "1) minimal field mask" in {
        UpdateMapper
          .toUserUpdate(user, FieldMask(Seq("user", "user.is_deactivated")))
          .value shouldBe expected
      }
      "2) not so minimal field mask" in {
        UpdateMapper
          .toUserUpdate(user, FieldMask(Seq("user", "user.metadata", "user.is_deactivated")))
          .value shouldBe expected
      }
      "3) also not so minimal field mask" in {
        UpdateMapper
          .toUserUpdate(user, FieldMask(Seq("user.primary_party", "user.metadata", "user.is_deactivated")))
          .value shouldBe expected
      }
      "4) field mask with exact field paths" in {
        UpdateMapper
          .toUserUpdate(user, FieldMask(Seq("user.primary_party", "user.metadata.annotations", "user.is_deactivated")))
          .value shouldBe expected
      }
    }

    "produce no update when no paths in the field mask" in {
      val user = makeUser(
        primaryParty = None,
        isDeactivated = false,
        annotations = Map("a" -> "b"),
      )
      UpdateMapper.toUserUpdate(user, FieldMask(Seq())).value.isNoUpdate shouldBe true
    }

    "test use of update modifiers" - {
      "when exact path match on a primitive field" in {
        val userWithParty = makeUser(primaryParty = Some(party1))
        val userWithoutParty = makeUser()
        UpdateMapper
          .toUserUpdate(userWithParty, FieldMask(Seq("user.primary_party!replace")))
          .value shouldBe makeUserUpdate(primaryPartyUpdateO = Some(Some(party1)))
        UpdateMapper
          .toUserUpdate(userWithoutParty, FieldMask(Seq("user.primary_party!replace")))
          .value shouldBe makeUserUpdate(primaryPartyUpdateO = Some(None))
        UpdateMapper
          .toUserUpdate(userWithParty, FieldMask(Seq("user.primary_party!merge")))
          .value shouldBe makeUserUpdate(primaryPartyUpdateO = Some(Some(party1)))
        UpdateMapper
          .toUserUpdate(userWithoutParty, FieldMask(Seq("user.primary_party!merge")))
          .left.value shouldBe UpdateMapper.ExplicitMergeUpdateModifierNotAllowedOnPrimitiveField("user.primary_party!merge")
        UpdateMapper
          .toUserUpdate(userWithParty, FieldMask(Seq("user.primary_party")))
          .value shouldBe makeUserUpdate(primaryPartyUpdateO = Some(Some(party1)))
        UpdateMapper
          .toUserUpdate(userWithoutParty, FieldMask(Seq("user.primary_party")))
          .value shouldBe makeUserUpdate(primaryPartyUpdateO = Some(None))
      }

      "when exact path match on the metadata annotations field" in {
        val userWithAnnotations = makeUser(annotations = Map("a" -> "b"))
        val userWithoutAnnotations = makeUser()
        UpdateMapper
          .toUserUpdate(userWithAnnotations, FieldMask(Seq("user.metadata.annotations!replace")))
          .value shouldBe makeUserUpdate(annotationsUpdateO = Some(Replace(Map("a" -> "b"))))
        UpdateMapper
          .toUserUpdate(userWithoutAnnotations, FieldMask(Seq("user.metadata.annotations!replace")))
          .value shouldBe makeUserUpdate(annotationsUpdateO = Some(Replace(Map.empty)))
        UpdateMapper
          .toUserUpdate(userWithAnnotations, FieldMask(Seq("user.metadata.annotations!merge")))
          .value shouldBe makeUserUpdate(annotationsUpdateO =
          Some(Merge.fromNonEmpty(Map("a" -> "b"))))
        UpdateMapper
          .toUserUpdate(userWithoutAnnotations, FieldMask(Seq("user.metadata.annotations!merge")))
          .left.value shouldBe UpdateMapper.ExplicitMergeUpdateModifierNotAllowedOnAnnotationsMap("user.metadata.annotations!merge")
        UpdateMapper
          .toUserUpdate(userWithAnnotations, FieldMask(Seq("user.metadata.annotations")))
          .value shouldBe makeUserUpdate(annotationsUpdateO = Some(Merge.fromNonEmpty(Map("a" -> "b"))))
        UpdateMapper
          .toUserUpdate(userWithoutAnnotations, FieldMask(Seq("user.metadata.annotations")))
          .value shouldBe makeUserUpdate(annotationsUpdateO = Some(Replace(Map.empty)))
      }

      "when inexact path match for a primitive field" in {
        val userWithParty = makeUser(primaryParty = Some(party1))
        val userWithoutParty = makeUser()
        UpdateMapper
          .toUserUpdate(userWithParty, FieldMask(Seq("user!replace")))
          .value shouldBe makeUserUpdate(
          primaryPartyUpdateO = Some(Some(party1)),
          isDeactivatedUpdateO = Some(false),
          annotationsUpdateO = Some(Replace(Map.empty)),
        )
        UpdateMapper
          .toUserUpdate(userWithoutParty, FieldMask(Seq("user!replace")))
          .value shouldBe makeUserUpdate(
          primaryPartyUpdateO = Some(None),
          isDeactivatedUpdateO = Some(false),
          annotationsUpdateO = Some(Replace(Map.empty)),
        )
        UpdateMapper
          .toUserUpdate(userWithParty, FieldMask(Seq("user!merge")))
          .value shouldBe makeUserUpdate(primaryPartyUpdateO = Some(Some(party1)))
        UpdateMapper
          .toUserUpdate(userWithoutParty, FieldMask(Seq("user!merge")))
          .value shouldBe emptyUserUpdate
        UpdateMapper
          .toUserUpdate(userWithParty, FieldMask(Seq("user")))
          .value shouldBe makeUserUpdate(primaryPartyUpdateO = Some(Some(party1)))
        UpdateMapper
          .toUserUpdate(userWithoutParty, FieldMask(Seq("user")))
          .value shouldBe emptyUserUpdate
      }

      "when inexact path match on metadata annotations field" in {
        val userWithAnnotations = makeUser(annotations = Map("a" -> "b"))
        val userWithoutAnnotations = makeUser()
        UpdateMapper
          .toUserUpdate(userWithAnnotations, FieldMask(Seq("user!replace")))
          .value shouldBe makeUserUpdate(
          primaryPartyUpdateO = Some(None),
          isDeactivatedUpdateO = Some(false),
          annotationsUpdateO = Some(Replace(Map("a" -> "b"))))
        UpdateMapper
          .toUserUpdate(userWithoutAnnotations, FieldMask(Seq("user!replace")))
          .value shouldBe makeUserUpdate(
          primaryPartyUpdateO = Some(None),
          isDeactivatedUpdateO = Some(false),
          annotationsUpdateO = Some(Replace(Map.empty)))
        UpdateMapper
          .toUserUpdate(userWithAnnotations, FieldMask(Seq("user!merge")))
          .value shouldBe makeUserUpdate(
          annotationsUpdateO = Some(Merge.fromNonEmpty(Map("a" -> "b"))))
        UpdateMapper
          .toUserUpdate(userWithoutAnnotations, FieldMask(Seq("user!merge")))
          .value shouldBe emptyUserUpdate
        UpdateMapper
          .toUserUpdate(userWithAnnotations, FieldMask(Seq("user")))
          .value shouldBe makeUserUpdate(
          annotationsUpdateO = Some(Merge.fromNonEmpty(Map("a" -> "b"))))
        UpdateMapper
          .toUserUpdate(userWithoutAnnotations, FieldMask(Seq("user")))
          .value shouldBe emptyUserUpdate
      }

      "the longest matching path is matched" in {
        val user = makeUser(
          annotations = Map("a" -> "b"),
          primaryParty = Some(party1),
        )
        UpdateMapper
          .toUserUpdate(user, FieldMask(Seq("user!replace", "user.metadata!replace", "user.metadata.annotations!merge")))
          .value shouldBe makeUserUpdate(
          primaryPartyUpdateO = Some(Some(party1)),
          isDeactivatedUpdateO = Some(false),
          annotationsUpdateO = Some(Merge.fromNonEmpty(Map("a" -> "b"))),
        )
        UpdateMapper
          .toUserUpdate(user, FieldMask(Seq("user!replace", "user.metadata!replace", "user.metadata.annotations")))
          .value shouldBe makeUserUpdate(
          primaryPartyUpdateO = Some(Some(party1)),
          isDeactivatedUpdateO = Some(false),
          annotationsUpdateO = Some(Merge.fromNonEmpty(Map("a" -> "b"))),
        )
        UpdateMapper
          .toUserUpdate(user, FieldMask(Seq("user!merge", "user.metadata", "user.metadata.annotations!replace")))
          .value shouldBe makeUserUpdate(
          primaryPartyUpdateO = Some(Some(party1)),
          annotationsUpdateO = Some(Replace(Map("a" -> "b"))),
        )
      }

      "when update modifier on a dummy field" in {
        val user = makeUser(primaryParty = Some(party1))
        UpdateMapper
          .toUserUpdate(user, FieldMask(Seq("user.dummy!replace")))
          .left.value shouldBe UpdateMapper.UpdateMaskPathPointsToInvalidField("user.dummy!replace")
      }

      "raise an error when an unsupported modifier like syntax is used" in {
        val user = makeUser(primaryParty = Some(party1))
        UpdateMapper
          .toUserUpdate(user, FieldMask(Seq("user!badmodifier")))
          .left.value shouldBe UpdateMapper.UpdateMaskPathHasUnrecognizedUpdateModifier("user!badmodifier")
        UpdateMapper
          .toUserUpdate(user, FieldMask(Seq("user.metadata.annotations!alsobad")))
          .left.value shouldBe UpdateMapper.UpdateMaskPathHasUnrecognizedUpdateModifier("user.metadata.annotations!alsobad")
      }
    }
  }

  "test invalid update paths" - {
    val user = makeUser(primaryParty = Some(party1))

    "produce an error when field masks lists unknown field" in {
      UpdateMapper
        .toUserUpdate(user, FieldMask(Seq("some_unknown_field")))
        .left.value shouldBe UpdateMapper.UpdateMaskPathPointsToInvalidField("some_unknown_field")
      UpdateMapper
        .toUserUpdate(user, FieldMask(Seq("user", "some_unknown_field")))
        .left.value shouldBe UpdateMapper.UpdateMaskPathPointsToInvalidField("some_unknown_field")
      UpdateMapper
        .toUserUpdate(user, FieldMask(Seq("user", "user.some_unknown_field")))
        .left.value shouldBe UpdateMapper.UpdateMaskPathPointsToInvalidField("some_unknown_field")
    }
    "produce an error when attempting to update resource version" in {
      UpdateMapper
        .toUserUpdate(user, FieldMask(Seq("user.metadata.annotations")))
        .left.value shouldBe UpdateMapper.UpdateMaskPathPointsToInvalidField("user.metadata.annotations")
    }
  }
}
