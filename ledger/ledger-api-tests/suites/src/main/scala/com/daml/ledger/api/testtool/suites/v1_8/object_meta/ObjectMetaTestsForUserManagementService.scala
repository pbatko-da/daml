// Copyright (c) 2022 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.ledger.api.testtool.suites.v1_8.object_meta

import com.daml.error.definitions.LedgerApiErrors
import com.daml.ledger.api.testtool.infrastructure.Allocation.{
  NoParties,
  Participant,
  Participants,
  allocate,
}
import com.daml.ledger.api.testtool.infrastructure.Assertions.assertGrpcError
import com.daml.ledger.api.testtool.infrastructure.ExpectedErrorDescription
import com.daml.ledger.api.testtool.infrastructure.participant.ParticipantTestContext
import com.daml.ledger.api.testtool.suites.v1_8.UserManagementServiceIT
import com.daml.ledger.api.v1.admin.object_meta.ObjectMeta
import com.daml.ledger.api.v1.admin.user_management_service.{
  CreateUserRequest,
  GetUserRequest,
  User,
}

import scala.concurrent.{ExecutionContext, Future}

trait ObjectMetaTestsForUserManagementService extends ObjectMetaTests with ObjectMetaTestsBase {
  self: UserManagementServiceIT =>

  type Resource = User
  type ResourceId = String

  override def getId(resource: Resource): ResourceId = resource.id

  override def annotationsUpdateRequestFieldPath: String = "user.metadata.annotations"

  override private[object_meta] def annotationsUpdatePath: String =
    "metadata.annotations"

  override private[object_meta] def resourceVersionUpdatePath = "metadata.resource_version"

  override private[object_meta] def resourceIdPath = "id"

  override def extractAnnotations(resource: Resource): Map[String, String] =
    resource.getMetadata.annotations

  override def extractMetadata(resource: Resource): ObjectMeta =
    resource.getMetadata

  override def setResourceVersion(resource: Resource, resourceVersion: String): Resource =
    resource.update(_.metadata.resourceVersion := resourceVersion)

  override def testWithFreshResource(
      shortIdentifier: String,
      description: String,
  )(
      annotations: Map[String, String] = Map.empty
  )(
      body: ExecutionContext => ParticipantTestContext => Resource => Future[Unit]
  ): Unit = {
    test(
      shortIdentifier = shortIdentifier,
      description = description,
      partyAllocation = allocate(NoParties),
    )(implicit ec => { case Participants(Participant(ledger)) =>
      withFreshUser(
        annotations = annotations
      ) { user =>
        body(ec)(ledger)(user)
      }(ledger, ec)
    })
  }

  override def testWithoutResource(shortIdentifier: String, description: String)(
      body: ExecutionContext => ParticipantTestContext => Future[Unit]
  ): Unit = {
    test(
      shortIdentifier = shortIdentifier,
      description = description,
      partyAllocation = allocate(NoParties),
    )(implicit ec => { case Participants(Participant(ledger)) =>
      body(ec)(ledger)
    })
  }

  override def createResourceWithAnnotations(
      annotations: Map[String, String]
  )(implicit ec: ExecutionContext, ledger: ParticipantTestContext): Future[Map[String, String]] = {
    val userId = ledger.nextUserId()
    val req = CreateUserRequest(
      user = Some(
        newUser(
          id = userId,
          annotations = annotations,
        )
      )
    )
    ledger.userManagement
      .createUser(req)
      .map(extractAnnotations)
  }

  override private[object_meta] def fetchNewestAnnotations(
      resource: Resource
  )(implicit ec: ExecutionContext, ledger: ParticipantTestContext): Future[Map[String, String]] = {
    ledger.userManagement
      .getUser(GetUserRequest(userId = resource.id))
      .map(_.user.get.getMetadata.annotations)
  }

  override def updateAnnotations(resource: Resource, annotations: Map[String, String])(implicit
      ec: ExecutionContext,
      ledger: ParticipantTestContext,
  ): Future[Map[String, String]] =
    update(
      resource = resource,
      annotations = annotations,
      updatePaths = Seq("metadata.annotations"),
    ).map(_.annotations)

  override def updateAnnotationsWithShortUpdatePath(
      resource: User,
      annotations: Map[String, String],
  )(implicit ec: ExecutionContext, ledger: ParticipantTestContext): Future[Map[String, String]] = {
    update(
      resource = resource,
      annotations = annotations,
      updatePaths = Seq("metadata"),
    ).map(_.annotations)
  }

  override private[object_meta] def update(
      resource: Resource,
      annotations: Map[String, String],
      resourceVersion: String = "",
      updatePaths: Seq[String],
  )(implicit ec: ExecutionContext, ledger: ParticipantTestContext): Future[ObjectMeta] = {
    val req = updateRequest(
      id = resource.id,
      annotations = annotations,
      resourceVersion = resourceVersion,
      updatePaths = updatePaths,
    )
    ledger.userManagement
      .updateUser(req)
      .map(_.getUser.getMetadata)
  }

  override def assertConcurrentUserUpdateDetectedError(
      id: ResourceId,
      t: Throwable,
  ): Unit = {
    assertGrpcError(
      t = t,
      errorCode = LedgerApiErrors.Admin.UserManagement.ConcurrentUserUpdateDetected,
      exceptionMessageSubstring = Some(
        s"ABORTED: CONCURRENT_USER_UPDATE_DETECTED(2,0): Update operation for user '${id}' failed due to a concurrent update to the same user"
      ),
    )
  }

  override def invalidUpdateRequestErrorDescription(
      id: ResourceId,
      badFieldPath: String,
  ): ExpectedErrorDescription = ExpectedErrorDescription(
    errorCode = LedgerApiErrors.Admin.UserManagement.InvalidUpdateUserRequest,
    exceptionMessageSubstring = Some(
      s"INVALID_ARGUMENT: INVALID_USER_UPDATE_REQUEST(8,0): Update operation for user id '${id}' failed due to: The update path: '${badFieldPath}' points to an unknown field."
    ),
  )

}
