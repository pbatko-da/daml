// Copyright (c) 2022 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.ledger.api.testtool.suites.v1_8.object_meta

import java.nio.charset.StandardCharsets

import com.daml.error.definitions.LedgerApiErrors
import com.daml.ledger.api.testtool.infrastructure.Assertions.{assertEquals, _}

import scala.concurrent.Future

// Contains tests common to all resource that support (contain) ObjectMeta metadata
trait ObjectMetaTests { self: ObjectMetaTestsBase =>
  private val maxAnnotationsSizeInBytes = 256 * 1024
  private val largeString = "a" * maxAnnotationsSizeInBytes
  private val largestAllowedValue = "a" * (maxAnnotationsSizeInBytes - 1)
  assertEquals(largeString.getBytes(StandardCharsets.UTF_8).length, maxAnnotationsSizeInBytes)

  private val annotationsOverSizeLimit = Map("a" -> largestAllowedValue, "c" -> "d")
  private val annotationsBelowSizeLimitBecauseNotCountingEmptyValuedKeys =
    Map("a" -> largestAllowedValue, "cc" -> "")

  private def getAnnotationsBytes(annotations: Map[String, String]): Int =
    annotations.iterator.map { case (k, v) =>
      k.getBytes(StandardCharsets.UTF_8).length + v.getBytes(StandardCharsets.UTF_8).length
    }.sum

  assertEquals(
    "comparing annotation sizes",
    getAnnotationsBytes(annotationsOverSizeLimit),
    getAnnotationsBytes(annotationsBelowSizeLimitBecauseNotCountingEmptyValuedKeys),
  )

  private val invalidKey = ".aaaa.management.daml/foo_"
  private val validKey = "0-aaaa.management.daml/foo"

  testWithFreshResource(
    "InvalidUpdateRequestsUnknownFieldPath",
    "Failing update requests when the update mask contains a path to an unknown field",
  )()(implicit ec =>
    implicit ledger =>
      resource =>
        for {
          _ <- update(
            resource = resource,
            annotations = Map.empty,
            updatePaths = Seq("unknown_field"),
          ).mustFailWith(
            "fail 1",
            invalidUpdateRequestErrorDescription(
              id = getId(resource),
              badFieldPath = "unknown_field",
            ),
          )
          _ <- update(
            resource = resource,
            annotations = Map.empty,
            updatePaths = Seq("aaa!bbb"),
          ).mustFailWith(
            "fail 2",
            invalidUpdateRequestErrorDescription(
              id = getId(resource),
              badFieldPath = "aaa!bbb",
            ),
          )
          _ <- update(
            resource = resource,
            annotations = Map.empty,
            updatePaths = Seq(""),
          ).mustFailWith(
            "fail 3",
            invalidUpdateRequestErrorDescription(
              id = getId(resource),
              badFieldPath = "",
            ),
          )
        } yield ()
  )

  testWithFreshResource(
    "UpdatingResourceWithConcurrentChangeControl",
    "Updating resource with concurrent change control",
  )()(implicit ec =>
    implicit ledger =>
      resource => {
        val rv1 = extractMetadata(resource).resourceVersion
        for {
          // Update with concurrent change detection disabled
          rv2: String <- update(
            resource = resource,
            annotations = Map(
              "k1" -> "v1",
              "k2" -> "v2",
            ),
            resourceVersion = "",
            updatePaths = Seq(annotationsUpdatePath),
          ).map { metadata =>
            assertEquals(
              metadata.annotations,
              Map("k1" -> "v1", "k2" -> "v2"),
            )

            assertValidResourceVersionString(rv1, "new resource")
            val rv2 = metadata.resourceVersion
            assertValidResourceVersionString(rv2, "updated resource")
            assert(
              rv1 != rv2,
              s"User's resource versions before and after an update must be different but were the same: '$rv2'",
            )
            rv2
          }
          // Update with concurrent change detection enabled, but resourceVersion is outdated
          error <- update(
            resource = resource,
            annotations = Map(
              "k1" -> "v1",
              "k2" -> "v2",
            ),
            resourceVersion = rv1,
            updatePaths = Seq(annotationsUpdatePath),
          ).mustFail(
            "update a user using an out-of-date resource version for concurrent change detection"
          )
          _ = assertConcurrentUserUpdateDetectedError(
            id = getId(resource),
            t = error,
          )
          // Update with concurrent change detection enabled and up-to-date resource version
          _ <- update(
            resource = resource,
            annotations = Map(
              "k1" -> "v1a",
              "k2" -> "",
              "k3" -> "v3",
            ),
            resourceVersion = rv2,
            updatePaths = Seq(annotationsUpdatePath),
          ).map { metadata =>
            assertEquals(
              metadata.annotations,
              Map("k1" -> "v1a", "k3" -> "v3"),
            )
            val rv3 = metadata.resourceVersion
            assert(
              rv2 != rv3,
              s"User's resource versions before and after an update must be different but were the same: '$rv2'",
            )
            assertValidResourceVersionString(
              rv3,
              "updating a resource user with concurrent change control enabled",
            )
          }
        } yield ()
      }
  )

  testWithFreshResource(
    "RaceConditionUpdateUserAnnotations",
    "Tests scenario of multiple concurrent update annotations calls for the same user",
  )()(implicit ec =>
    implicit ledger =>
      resource => {
        val attempts = (1 to 10).toVector
        for {
          _ <- Future.traverse(attempts) { attemptNo =>
            updateAnnotations(
              resource = resource,
              annotations = Map(s"key$attemptNo" -> "a"),
            )
          }
          annotations <- fetchNewestAnnotations(resource = resource)
        } yield {
          assertEquals(
            annotations,
            Map(
              "key1" -> "a",
              "key2" -> "a",
              "key3" -> "a",
              "key4" -> "a",
              "key5" -> "a",
              "key6" -> "a",
              "key7" -> "a",
              "key8" -> "a",
              "key9" -> "a",
              "key10" -> "a",
            ),
          )
        }
      }
  )

  testWithFreshResource(
    "AllowSpecifyingResourceVersionAndResourceIdInUpdateMask",
    "Allow specifying resource_version and resource's id fields in the update mask",
  )()(implicit ec =>
    implicit ledger =>
      resource =>
        update(
          resource = resource,
          annotations = Map.empty,
          resourceVersion = "",
          updatePaths = Seq(resourceIdPath, resourceVersionUpdatePath),
        )
          .map { objectMeta =>
            assertEquals(
              objectMeta.annotations,
              extractAnnotations(resource),
            )
          }
  )

  testWithFreshResource(
    "UpdateAnnotationsWithNonEmptyMap",
    "Update annotations using update paths with a non-empty map",
  )(annotations = Map("k1" -> "v1", "k2" -> "v2", "k3" -> "v3"))(implicit ec =>
    implicit ledger =>
      resource =>
        for {
          _ <-
            updateAnnotations(
              resource = resource,
              annotations = Map("k1" -> "v1a", "k3" -> "", "k4" -> "v4", "k5" -> ""),
            ).map { annotations =>
              assertEquals(
                "updating annotations",
                annotations,
                Map("k1" -> "v1a", "k2" -> "v2", "k4" -> "v4"),
              )
            }
          _ <-
            updateAnnotationsWithShortUpdatePath(
              resource = resource,
              annotations = Map("k1" -> "v1a", "k3" -> "", "k4" -> "v4", "k5" -> ""),
            ).map { annotations =>
              assertEquals(
                "updating annotations with short update path",
                annotations,
                Map("k1" -> "v1a", "k2" -> "v2", "k4" -> "v4"),
              )
            }
        } yield ()
  )

  testWithFreshResource(
    "UpdateAnnotationsWithEmptyMap",
    "Update annotations using update paths with the empty map",
  )(annotations = Map("k1" -> "v1", "k2" -> "v2", "k3" -> "v3"))(implicit ec =>
    implicit ledger =>
      resource =>
        for {
          _ <- updateAnnotations(
            resource = resource,
            annotations = Map.empty,
          )
            .map { annotations =>
              assertEquals(
                "updating annotations",
                annotations,
                Map("k1" -> "v1", "k2" -> "v2", "k3" -> "v3"),
              )
            }
          _ <- updateAnnotationsWithShortUpdatePath(
            resource = resource,
            annotations = Map.empty,
          )
            .map { annotations =>
              assertEquals(
                "updating annotations with short update path",
                annotations,
                Map("k1" -> "v1", "k2" -> "v2", "k3" -> "v3"),
              )
            }
        } yield ()
  )

  testWithFreshResource(
    "TestAnnotationsKeySyntaxOnUpdate",
    "Test annotations' key syntax on update",
  )(annotations = Map(validKey -> "a"))(implicit ec =>
    implicit ledger =>
      resource =>
        updateAnnotations(
          resource = resource,
          annotations = Map(invalidKey -> "a"),
        ).mustFailWith(
          "bad annotations key syntax on a user update",
          errorCode = LedgerApiErrors.RequestValidation.InvalidArgument,
          exceptionMessageSubstring = Some(
            "INVALID_ARGUMENT: INVALID_ARGUMENT(8,0): The submitted command has invalid arguments: Key prefix segment '.aaaa.management.daml' has invalid syntax"
          ),
        )
  )

  testWithFreshResource(
    "TestAnnotationsKeySyntaxOnUpdateEvenWhenDeletingNonExistentKey",
    "Test annotations' key syntax on update even when deleting non-existent key",
  )()(implicit ec =>
    implicit ledger =>
      resource =>
        updateAnnotations(
          resource = resource,
          annotations = Map(invalidKey -> "a"),
        ).mustFailWith(
          "bad annotations key syntax on a user update",
          errorCode = LedgerApiErrors.RequestValidation.InvalidArgument,
          exceptionMessageSubstring = Some(
            "INVALID_ARGUMENT: INVALID_ARGUMENT(8,0): The submitted command has invalid arguments: Key prefix segment '.aaaa.management.daml' has invalid syntax"
          ),
        )
  )

  testWithoutResource(
    "TestAnnotationsKeySyntax",
    "Test annotations' key syntax",
  )(implicit ec => { implicit ledger =>
    createResourceWithAnnotations(
      annotations = Map(invalidKey -> "a")
    ).mustFailWith(
      "bad annotations key syntax on user creation",
      errorCode = LedgerApiErrors.RequestValidation.InvalidArgument,
      exceptionMessageSubstring = Some(
        "INVALID_ARGUMENT: INVALID_ARGUMENT(8,0): The submitted command has invalid arguments: Key prefix segment '.aaaa.management.daml' has invalid syntax"
      ),
    )
  })

  testWithFreshResource(
    "NotCountingEmptyValuedKeysToTheSizeLimitOnUpdate",
    "Do not count the keys that are to be deleted towards the annotations size limit",
  )() { implicit ec => implicit ledger => resource =>
    updateAnnotations(
      resource = resource,
      annotations = annotationsBelowSizeLimitBecauseNotCountingEmptyValuedKeys,
    ).map { annotations =>
      assertEquals(
        "updating and not exceeding annotations limit because deletions are not counted towards the limit",
        annotations,
        Map("a" -> largestAllowedValue),
      )
    }
  }

  testWithFreshResource(
    "TestAnnotationsSizeLimitsOnUpdate",
    "Test annotations' size limit on update",
  )(annotations = Map("a" -> largestAllowedValue)) { implicit ec => implicit ledger => resource =>
    updateAnnotations(
      resource = resource,
      annotations = Map("a" -> largeString),
    )
      .mustFailWith(
        "total size of annotations, in a user update call, is over 256kb",
        errorCode = LedgerApiErrors.RequestValidation.InvalidArgument,
        exceptionMessageSubstring = Some(
          s"INVALID_ARGUMENT: INVALID_ARGUMENT(8,0): The submitted command has invalid arguments: annotations from field '${annotationsUpdateRequestFieldPath}' are larger than the limit of 256kb"
        ),
      )
  }

  testWithoutResource(
    "TestAnnotationsSizeLimitsOnCreation",
    "Test annotations' size limit on creation",
  ) { implicit ec => implicit ledger =>
    createResourceWithAnnotations(annotations = Map("a" -> largeString))
      .mustFailWith(
        "total size of annotations exceeds 256kb max limit",
        errorCode = LedgerApiErrors.RequestValidation.InvalidArgument,
        exceptionMessageSubstring = Some(
          s"INVALID_ARGUMENT: INVALID_ARGUMENT(8,0): The submitted command has invalid arguments: annotations from field '${annotationsUpdateRequestFieldPath}' are larger than the limit of 256kb"
        ),
      )
  }

}
