// Copyright (c) 2022 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.platform.apiserver.update

import com.daml.ledger.api.domain.{ParticipantParty, User}
import com.daml.ledger.participant.state.index.v2._
import com.daml.lf.data.Ref
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

case class ParsedUpdatePath(path: List[String], modifier: UpdatePathModifier)  {
  def toRawString: String = path.mkString(".") + modifier.toRawString
}

object ParsedUpdatePath {
  // TODO pbatko: Error handling
  def parse(rawPath: String): ParsedUpdatePath = {
    // TODO um-for-hub: Document that "!" is a special character in update paths
    val (path, modifier) = rawPath.split('!') match {
      case Array(path) => (path, UpdatePathModifier.NoModifier)
      case Array(path, "merge") => (path, UpdatePathModifier.Merge)
      case Array(path, "replace") => (path, UpdatePathModifier.Replace)
      case _ => sys.error("TODO pbatko: Error")
    }
    ParsedUpdatePath(
      path.split('.').toList,
      modifier,
    )
  }
}

trait UpdateKind
object UpdateKind {
  case object Merge extends UpdateKind
  case object Replace extends UpdateKind
}

object UpdateMapper {
  sealed trait UpdateMaskError extends RuntimeException
  type Result[T] = Either[UpdateMaskError, T]

//  def parseUpdateMask2(updateMask: FieldMask): Seq[UpdatePath] = {
//
//    updateMask.paths.map { updatePath =>
//      val (path, updateSemantics) = updatePath.split('!') match {
//        case Array(path, "merge") => (path, UpdatePathModifier.Merge)
//        case Array(path, "replace") => (path, UpdatePathModifier.Replace)
//        case Array(path) => (path, UpdatePathModifier.Merge)
//        case other => sys.error(s"TODO invalid update path ${other.mkString("[", ",", "]")}")
//      }
//      UpdatePath(
//        path = path.split('.').toSeq,
//        modifier = updateSemantics,
//      )
//    }
//  }

  // TODO pbatko: Use more specifc errors
  final case object GenericUpdateMaskError extends UpdateMaskError
  final case class ExplicitMergeUpdateModifierNotAllowedOnAnnotationsMap(updatePath: String) extends UpdateMaskError
  final case class ExplicitMergeUpdateModifierNotAllowedOnPrimitiveField(updatePath: String) extends UpdateMaskError
  final case class UpdateMaskPathPointsToInvalidField(updatePath: String) extends UpdateMaskError
  final case class UpdateMaskPathHasUnrecognizedUpdateModifier(updatePath: String) extends UpdateMaskError

  private def noUpdate[A]: Result[Option[A]] = Right(None)

  /** A field (e.g 'foo.bar.baz') is set to be updated if in the update mask:
    * - its exact path is specified (e.g. 'foo.bar.baz'),
    * - a prefix of its exact path is specified (e.g. 'foo.bar' or 'foo') and it's new value is non-default.
    *
    * Corollary: To delete a field (i.e. set it to a default value) you need to specify its exact path.
    */
  def toUserUpdate(user: User, updateMask: FieldMask): Either[UpdateMaskError, UserUpdate] = {
    // TODO pbatko: Validate separately and earlier
//    val annotationsPath: String = Seq(FieldNames.UpdateUserRequest.user, FieldNames.User.metadata, FieldNames.Metadata.annotations).mkString(".")
//    val annotationsPathMerge = `annotationsPath` + "!merge"
//    val annotationsPathReplace = `annotationsPath` + "!replace"
    //
//      case `annotationsPathMerge` => {
//        explicitMerge = true
//        annotationsPath
//      }
//      case `annotationsPathReplace` => {
//        replaceAnnotations = true
//        annotationsPath
//      }
//      case o => o

    val rawPaths: Seq[String] = updateMask.paths
    val parsedPaths: Seq[ParsedUpdatePath] = rawPaths.map { rawPath: String =>
      ParsedUpdatePath.parse(rawPath)
    }
    val updateTrie = UpdatePathsTrie.fromPaths(parsedPaths)
    val annotationsPath = List(
      FieldNames.UpdateUserRequest.user,
      FieldNames.User.metadata,
      FieldNames.Metadata.annotations,
    )
    val primaryPartyPath = List(FieldNames.UpdateUserRequest.user, FieldNames.User.primaryParty)
    val isDeactivatePath = List(FieldNames.UpdateUserRequest.user, FieldNames.User.isDeactivated)
    val annotationsUpdateResult: Result[Option[AnnotationsUpdate]] =
      updateTrie
        .findMatch(annotationsPath)
        .fold(noUpdate[AnnotationsUpdate])(matchResult =>
          resolveAnnotationsUpdate(
            newValue = user.metadata.annotations,
            updateMatchResult = matchResult,
            modifier = matchResult.updatePathModifier,
          )
        )
    val primaryPartyUpdateResult: Result[Option[Option[Ref.Party]]] =
      updateTrie
        .findMatch(primaryPartyPath)
        .fold(noUpdate[Option[Ref.Party]])(matchResult =>
          resolvePrimitiveFieldUpdate(
            updateMatchResult = matchResult,
            modifier = matchResult.updatePathModifier,
            defaultValue = None,
            newValue = user.primaryParty,
          )
        )
    val isDeactivatedUpdateResult: Result[Option[Boolean]] =
      updateTrie
        .findMatch(isDeactivatePath)
        .fold(noUpdate[Boolean])(matchResult =>
          resolvePrimitiveFieldUpdate(
            updateMatchResult = matchResult,
            modifier = matchResult.updatePathModifier,
            defaultValue = false,
            newValue = user.isDeactivated,
          )
        )
    for {
      annotationsUpdate <- annotationsUpdateResult
      primaryPartyUpdate <- primaryPartyUpdateResult
      isDeactivatedUpdate <- isDeactivatedUpdateResult
    } yield {
      UserUpdate(
        id = user.id,
        primaryPartyUpdateO = primaryPartyUpdate,
        isDeactivatedUpdateO = isDeactivatedUpdate,
        metadataUpdate = ObjectMetaUpdate(
          resourceVersionO = user.metadata.resourceVersionO,
          annotationsUpdateO = annotationsUpdate,
        ),
      )
    }
  }

  private def resolveAnnotationsUpdate(
      newValue: Map[String, String],
      updateMatchResult: MatchResult,
      modifier: UpdatePathModifier,
  ): Result[Option[AnnotationsUpdate]] = {
    val isDefaultValue = newValue.isEmpty
    def mergeUpdate = Right(Some(AnnotationsUpdate.Merge.fromNonEmpty(newValue)))
    val replaceUpdate = Right(Some(AnnotationsUpdate.Replace(newValue)))
    if (updateMatchResult.isExact) {
      modifier match {
        case UpdatePathModifier.NoModifier =>
          if (isDefaultValue) replaceUpdate else mergeUpdate
        case UpdatePathModifier.Merge =>
          if (isDefaultValue) Left(ExplicitMergeUpdateModifierNotAllowedOnAnnotationsMap(updateMatchResult.matchedPath.toRawString))
          else mergeUpdate
        case UpdatePathModifier.Replace =>
          replaceUpdate
      }
    } else {
      modifier match {
        case UpdatePathModifier.NoModifier | UpdatePathModifier.Merge =>
          if (isDefaultValue) noUpdate else mergeUpdate
        case UpdatePathModifier.Replace =>
          replaceUpdate
      }
    }
  }

  private def resolvePrimitiveFieldUpdate[A](
      updateMatchResult: MatchResult,
      modifier: UpdatePathModifier,
      defaultValue: A,
      newValue: A,
  ): Result[Option[A]] = {
    val isDefaultValue = newValue == defaultValue
    val some = Right(Some(newValue))
    if (updateMatchResult.isExact) {
      modifier match {
        case UpdatePathModifier.NoModifier | UpdatePathModifier.Replace => some
        case UpdatePathModifier.Merge =>
          if (isDefaultValue) Left(ExplicitMergeUpdateModifierNotAllowedOnPrimitiveField(updateMatchResult.matchedPath.toRawString)) else some
      }
    } else {
      modifier match {
        case UpdatePathModifier.NoModifier | UpdatePathModifier.Merge =>
          if (isDefaultValue) noUpdate else some
        case UpdatePathModifier.Replace => some
      }
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
