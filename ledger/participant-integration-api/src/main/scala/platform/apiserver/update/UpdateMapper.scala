// Copyright (c) 2022 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.platform.apiserver.update

import com.daml.ledger.api.domain.{ParticipantParty, User}
import com.daml.ledger.participant.state.index.v2._
import com.google.protobuf.field_mask.FieldMask

// TODO um-for-hub: Get field names from files generated from proto files instead of hardcoding them
object FieldNames {
  object UpdateUserRequest {
    val user = "user"
  }
  object User {
    val primaryParty = "primary_party"
    val isDeactivated = "is_deactivated"
    val metadata = "metadata"
  }
  object Metadata {
    val annotations = "annotations"
  }

  object UpdatePartyDetailsRequest {
    val partyDetails = "party_details"
  }
  object PartyDetails {
    val localMetadata = "local_metadata"
  }

}

object UpdateMapper {

  def parseUpdateMask2(updateMask: FieldMask): Seq[UpdatePath] = {

    updateMask.paths.map { updatePath =>
      val (path, updateSemantics) = updatePath.split('!') match {
        case Array(path, "merge") => (path, UpdatePathModifier.Merge)
        case Array(path, "replace") => (path, UpdatePathModifier.Replace)
        case Array(path) => (path, UpdatePathModifier.Merge)
        case other => sys.error(s"TODO invalid update path ${other.mkString("[", ",", "]")}")
      }
      UpdatePath(
        path = path.split('.').toSeq,
        modifier = updateSemantics,
      )
    }
  }

  private def parseUpdateMask(updateMaskPaths: Seq[String]): Seq[Seq[String]] = {
    updateMaskPaths.map { path =>
      path.split('.').toSeq
    }
  }

  sealed trait UpdateMaskError extends RuntimeException
  // TODO pbatko: Use more specifc errors
  final case object GenericUpdateMaskError extends UpdateMaskError

  /** A field (e.g 'foo.bar.baz') is set to be updated if in the update mask:
    * - its exact path is specified (e.g. 'foo.bar.baz'),
    * - a prefix of its exact path is specified (e.g. 'foo.bar' or 'foo') and it's new value is non-default.
    *
    * Corollary: To delete a field (i.e. set it to a default value) you need to specify its exact path.
    */
  def toUserUpdate(user: User, updateMask: FieldMask): Either[UpdateMaskError, UserUpdate] = {
    val paths: Seq[String] = updateMask.paths

    val annotationsPath: String = Seq(FieldNames.UpdateUserRequest.user, FieldNames.User.metadata, FieldNames.Metadata.annotations).mkString(".")
    val annotationsPathMerge = `annotationsPath` + "!merge"
    val annotationsPathReplace = `annotationsPath` + "!replace"

    var replaceAnnotations = false
    var explicitMerge = false
    val paths2 = paths.map {
      case `annotationsPathMerge` => {
        explicitMerge = true
        annotationsPath
      }
      case `annotationsPathReplace` => {
        replaceAnnotations = true
        annotationsPath
      }
      case o => o
    }

    val trie = UpdatePathsTrie.fromPaths(parseUpdateMask(paths2))
    val userSubTrieO: Option[UpdatePathsTrie] =
      trie.subtree(
        Seq(
          FieldNames.UpdateUserRequest.user
        )
      )

    if (userSubTrieO.isEmpty) {
      // TODO pbatko: This is an empty (no-op) update so consider modeling it as None
      Left(GenericUpdateMaskError)
    } else {
      val userSubTrie = userSubTrieO.get

      val annotationsUpdate: Option[AnnotationsUpdate] = userSubTrie
        .determineUpdate[Map[String, String]](
          Seq(
            FieldNames.User.metadata,
            FieldNames.Metadata.annotations,
          )
        )(
          newValueCandidate = user.metadata.annotations,
          defaultValue = Map.empty,
        )
        .map { updateValue =>
          if (replaceAnnotations) {
            AnnotationsUpdate.Replace(updateValue)
          } else {
            if (explicitMerge) {
              AnnotationsUpdate.Merge
                .apply(updateValue)
                .getOrElse(
                  sys.error("TODO tried to do merge update with the default value!")
                )
            } else {
              if (updateValue.isEmpty) {
                AnnotationsUpdate.Replace(updateValue)
              } else {
                AnnotationsUpdate.Merge.fromNonEmpty(updateValue)
              }
            }
          }
        }

      val update = UserUpdate(
        id = user.id,
        primaryPartyUpdateO = userSubTrie.determineUpdate(
          Seq(
            FieldNames.User.primaryParty
          )
        )(
          newValueCandidate = user.primaryParty,
          defaultValue = None,
        ),
        isDeactivatedUpdateO = userSubTrie.determineUpdate(
          Seq(
            FieldNames.User.isDeactivated
          )
        )(
          newValueCandidate = user.isDeactivated,
          defaultValue = false,
        ),
        metadataUpdate = ObjectMetaUpdate(
          resourceVersionO = user.metadata.resourceVersionO,
          annotationsUpdateO = annotationsUpdate,
        ),
      )
      Right(update)
    }
  }

  // TODO pbatko: Validate correct tries: Construct the fullest tree and implement isRootedSubtree operation
// TODO pbatko: Test it
  // TODO pbatko: Share code with user update mapping
  def toParticipantPartyUpdate(
      partyRecord: ParticipantParty.PartyRecord,
      updateMask: FieldMask,
  ): PartyRecordUpdate = {
    ???
//
//    val paths: Seq[String] = updateMask.paths
//
//    val annotationsPath = Seq(
//      FieldNames.UpdatePartyDetailsRequest.partyDetails,
//      FieldNames.PartyDetails.localMetadata,
//      FieldNames.Metadata.annotations,
//    ).mkString(".")
//    val annotationsPathMerge = `annotationsPath` + "!merge"
//    val annotationsPathReplace = `annotationsPath` + "!replace"
//
//    var replaceAnnotations = false
//    var explicitMerge = false
//    val paths2 = paths.map {
//      case `annotationsPathMerge` => {
//        explicitMerge = true
//        annotationsPath
//      }
//      case `annotationsPathReplace` => {
//        replaceAnnotations = true
//        annotationsPath
//      }
//      case o => o
//    }
//
//    val trie = UpdateMaskTrie_mut.fromPaths(parseUpdateMask(paths2))
//    val userSubTrieO: Option[UpdateMaskTrie_mut] =
//      trie.subtree(
//        Seq(
//          FieldNames.UpdatePartyDetailsRequest.partyDetails
//        )
//      )
//
//    if (userSubTrieO.isEmpty) {
//      // TODO pbatko: This is an empty (no-op) update so consider modeling it as None
//      PartyRecordUpdate(
//        party = partyRecord.party,
//        metadataUpdate = ObjectMetaUpdate(
//          resourceVersionO = partyRecord.metadata.resourceVersionO,
//          annotationsUpdateO = None,
//        ),
//      )
//    } else {
//      val userSubTrie = userSubTrieO.get
//
//      val annotationsUpdate: Option[AnnotationsUpdate] = userSubTrie
//        .determineUpdate[Map[String, String]](
//          Seq(
//            FieldNames.PartyDetails.localMetadata,
//            FieldNames.Metadata.annotations,
//          )
//        )(
//          newValueCandidate = partyRecord.metadata.annotations,
//          defaultValue = Map.empty[String, String],
//        )
//        .map { updateValue: Map[String, String] =>
//          val x: AnnotationsUpdate = if (replaceAnnotations) {
//            AnnotationsUpdate.Replace(updateValue)
//          } else {
//            if (explicitMerge) {
//              AnnotationsUpdate.Merge
//                .apply(updateValue)
//                .getOrElse(
//                  sys.error("TODO tried to do merge update with the default value!")
//                )
//            } else {
//              if (updateValue.isEmpty) {
//                AnnotationsUpdate.Replace(updateValue)
//              } else {
//                AnnotationsUpdate.Merge.fromNonEmpty(updateValue)
//              }
//            }
//          }
//          x
//        }
//
//      PartyRecordUpdate(
//        party = partyRecord.party,
//        metadataUpdate = ObjectMetaUpdate(
//          resourceVersionO = partyRecord.metadata.resourceVersionO,
//          annotationsUpdateO = annotationsUpdate,
//        ),
//      )
//    }
  }
}
