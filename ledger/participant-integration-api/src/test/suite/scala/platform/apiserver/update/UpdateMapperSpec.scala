package com.daml.platform.apiserver.update

import com.daml.ledger.api.domain.{ObjectMeta, User}
import com.daml.ledger.participant.state.index.v2.{ObjectMetaUpdate, UserUpdate}
import com.daml.ledger.participant.state.index.v2.AnnotationsUpdate.{Merge, Replace}
import com.daml.lf.data.Ref
import com.daml.platform.apiserver.update.UpdateMapper.UpdateMaskError
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
  def newNonDefaultUser(
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

  // TODO test !merge path with default value

  "map to user updates" - {
    val user = newNonDefaultUser(
      primaryParty = None,
      isDeactivated = false,
    )

    "basic mapping" - {
      val expected = UserUpdate(
        id = userId1,
        // NOTE: This field is absent in the update mask
        primaryPartyUpdateO = Some(None),
        // NOTE: This field is present in the update mask
        isDeactivatedUpdateO = Some(false),
        metadataUpdate = ObjectMetaUpdate(
          resourceVersionO = None,
          // NOTE: This nested field present in the update mask
          annotationsUpdateO = Some(
            Merge.fromNonEmpty(
              Map(
                "a" -> "b"
              )
            )
          ),
        ),
      )

      "1) with all individual fields to update listed in the update mask" in {
        UpdateMapper.toUserUpdate(user, FieldMask(Seq("user.is_deactivated", "user.primary_party", "user.metadata.annotations")))
          .value shouldBe expected
      }

      "2) with metadata.annotations not listed explicitly" in {
        UpdateMapper.toUserUpdate(user, FieldMask(Seq("user.is_deactivated", "user.primary_party", "user.metadata")))
          .value shouldBe expected
      }
    }

    "map api request to update - merge user and reset is_deactivated" - {
      val deactivatedUser = newNonDefaultUser(
        // NOTE: resetting this field and updating all other fields
        isDeactivated = false,
      )
      val expected = UserUpdate(
        id = userId1,
        primaryPartyUpdateO = Some(Some(party1)),
        isDeactivatedUpdateO = Some(false),
        metadataUpdate = ObjectMetaUpdate(
          resourceVersionO = None,
          annotationsUpdateO = Some(
            Merge.fromNonEmpty(
              Map(
                "a" -> "b"
              )
            )
          ),
        ),
      )

      "1) minimal field mask" in {
        UpdateMapper.toUserUpdate(deactivatedUser, FieldMask(Seq("user", "user.is_deactivated")))
          .value shouldBe expected
      }

      "2) not so minimal field mask" in {
        UpdateMapper.toUserUpdate(deactivatedUser, FieldMask(Seq("user", "user.metadata", "user.is_deactivated")))
          .value shouldBe expected
      }
      "3) also not so minimal field mask" in {
        UpdateMapper.toUserUpdate(deactivatedUser, FieldMask(Seq("user.primary_party", "user.metadata", "user.is_deactivated")))
          .value shouldBe expected
      }
      "4) field mask with exact field paths" in {
        UpdateMapper.toUserUpdate(deactivatedUser, FieldMask(Seq("user.primary_party", "user.metadata.annotations", "user.is_deactivated")))
          .value shouldBe expected
      }
    }

    "test use of '!replace' and '!merge' modifiers" - {
      val user = newNonDefaultUser()
      val userWithNonEmptyAnnotations = newNonDefaultUser()
      val userWithEmptyAnnotations = newNonDefaultUser(annotations = Map.empty)

      "'!replace' modifier can be used only on metadata annotations" in {
        val expected = UserUpdate(
          id = userId1,
          metadataUpdate = ObjectMetaUpdate(
            resourceVersionO = None,
            annotationsUpdateO = Some(Replace(Map("a" -> "b"))),
          ),
        )
        UpdateMapper.toUserUpdate(userWithNonEmptyAnnotations, FieldMask(Seq("user.metadata.annotations!replace")))
          .value shouldBe expected
        UpdateMapper.toUserUpdate(userWithEmptyAnnotations, FieldMask(Seq("user.metadata.annotations!replace")))
          .value shouldBe expected
        UpdateMapper.toUserUpdate(user, FieldMask(Seq("user.metadata!replace")))
          .left.value shouldBe a[UpdateMaskError]
        UpdateMapper.toUserUpdate(user, FieldMask(Seq("user.primary_party!replace")))
          .left.value shouldBe a[UpdateMaskError]
        UpdateMapper.toUserUpdate(user, FieldMask(Seq("user.dummy!replace")))
          .left.value shouldBe a[UpdateMaskError]
      }

      "'!merge' modifier can be used only on metadata annotations with non-default value" in {
        val expected = UserUpdate(
          id = userId1,
          metadataUpdate = ObjectMetaUpdate(
            resourceVersionO = None,
            annotationsUpdateO = Some(Merge.fromNonEmpty(Map("a" -> "b"))),
          ),
        )
        UpdateMapper.toUserUpdate(userWithNonEmptyAnnotations, FieldMask(Seq("user.metadata.annotations!merge")))
          .value shouldBe expected
        UpdateMapper.toUserUpdate(userWithEmptyAnnotations, FieldMask(Seq("user.metadata.annotations!merge")))
          .left.value shouldBe a[UpdateMaskError]
        UpdateMapper.toUserUpdate(user, FieldMask(Seq("user.metadata!merge")))
          .left.value shouldBe a[UpdateMaskError]
        UpdateMapper.toUserUpdate(user, FieldMask(Seq("user!merge")))
          .left.value shouldBe a[UpdateMaskError]
        UpdateMapper.toUserUpdate(user, FieldMask(Seq("user.primary_party!merge")))
          .left.value shouldBe a[UpdateMaskError]
        UpdateMapper.toUserUpdate(user, FieldMask(Seq("user.dummy!merge")))
          .left.value shouldBe a[UpdateMaskError]
      }

      "raise an error when an unsupported modifier like syntax is used" in {
        UpdateMapper.toUserUpdate(user, FieldMask(Seq("user!badmodifier")))
          .left.value shouldBe a[UpdateMaskError]
        UpdateMapper.toUserUpdate(user, FieldMask(Seq("user.metadata.annotations!alsobad")))
          .left.value shouldBe a[UpdateMaskError]
      }

    }

    "test invalid update masks" - {
      val user = newNonDefaultUser()

      // TODO pbatko: Expect more specific error code
      "produce no update when no paths in the field mask" in {
        UpdateMapper.toUserUpdate(user, FieldMask(Seq()))
          .left.value shouldBe a[UpdateMaskError]
      }

      "produce an error when field masks lists unknown field" in {
        UpdateMapper.toUserUpdate(user, FieldMask(Seq("user", "some_other_field")))
          .left.value shouldBe a[UpdateMaskError]
      }

    }
  }
}
