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
import com.daml.ledger.api.testtool.suites.v1_8.PartyManagementServiceIT
import com.daml.ledger.api.v1.admin.object_meta.ObjectMeta
import com.daml.ledger.api.v1.admin.party_management_service.{
  AllocatePartyRequest,
  GetPartiesRequest,
  PartyDetails,
}

import scala.concurrent.{ExecutionContext, Future}

trait ObjectMetaTestsForPartyManagementService extends ObjectMetaTests with ObjectMetaTestsBase {
  self: PartyManagementServiceIT =>

  type Resource = PartyDetails
  type ResourceId = String

  override def getId(resource: Resource): ResourceId = resource.party

  override def annotationsUpdateRequestFieldPath: String =
    "party_details.local_metadata.annotations"

  override private[object_meta] def resourceVersionUpdatePath: String =
    "local_metadata.resource_version"

  override private[object_meta] def annotationsUpdatePath: String =
    "local_metadata.annotations"

  override private[object_meta] def resourceIdPath = "party"

  override def extractAnnotations(resource: Resource): Map[String, String] =
    resource.getLocalMetadata.annotations

  override def extractMetadata(resource: Resource): ObjectMeta =
    resource.getLocalMetadata

  override def setResourceVersion(resource: Resource, resourceVersion: String): Resource =
    resource.update(_.localMetadata.resourceVersion := resourceVersion)

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
      withFreshParty(
        annotations = annotations
      ) { partyDetails =>
        body(ec)(ledger)(partyDetails)
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
    val req = AllocatePartyRequest(localMetadata = Some(ObjectMeta(annotations = annotations)))
    ledger
      .allocateParty(req)
      .map(extractUpdatedAnnotations)
  }

  override private[object_meta] def fetchNewestAnnotations(
      resource: Resource
  )(implicit ec: ExecutionContext, ledger: ParticipantTestContext): Future[Map[String, String]] = {
    ledger
      .getParties(GetPartiesRequest(parties = Seq(resource.party)))
      .map(_.partyDetails.head.getLocalMetadata.annotations)
  }

  override def updateAnnotations(resource: Resource, annotations: Map[String, String])(implicit
      ec: ExecutionContext,
      ledger: ParticipantTestContext,
  ): Future[Map[String, String]] = {
    update(
      resource = resource,
      annotations = annotations,
      updatePaths = Seq("local_metadata.annotations"),
    ).map(_.annotations)
  }

  override def updateAnnotationsWithShortUpdatePath(
      resource: Resource,
      annotations: Map[String, String],
  )(implicit
      ec: ExecutionContext,
      ledger: ParticipantTestContext,
  ): Future[Map[String, String]] = {
    update(
      resource = resource,
      annotations = annotations,
      updatePaths = Seq("local_metadata"),
    ).map(_.annotations)
  }

  override private[object_meta] def update(
      resource: Resource,
      annotations: Map[String, String],
      resourceVersion: String = "",
      updatePaths: Seq[String],
  )(implicit ec: ExecutionContext, ledger: ParticipantTestContext): Future[ObjectMeta] = {
    val req = updateRequest(
      party = resource.party,
      annotations = annotations,
      resourceVersion = resourceVersion,
      updatePaths = updatePaths,
    )
    ledger
      .updatePartyDetails(req)
      .map(_.getPartyDetails.getLocalMetadata)
  }

  override def assertConcurrentUserUpdateDetectedError(
      id: ResourceId,
      t: Throwable,
  ): Unit = {
    assertGrpcError(
      t = t,
      errorCode = LedgerApiErrors.Admin.PartyManagement.ConcurrentPartyDetailsUpdateDetected,
      exceptionMessageSubstring = Some(
        s"ABORTED: CONCURRENT_PARTY_DETAILS_UPDATE_DETECTED(2,0): Update operation for party '${id}' failed due to a concurrent update to the same party"
      ),
    )
  }

  override def invalidUpdateRequestErrorDescription(
      id: ResourceId,
      badFieldPath: String,
  ): ExpectedErrorDescription = ExpectedErrorDescription(
    errorCode = LedgerApiErrors.Admin.PartyManagement.InvalidUpdatePartyDetailsRequest,
    exceptionMessageSubstring = Some(
      s"INVALID_ARGUMENT: INVALID_PARTY_DETAILS_UPDATE_REQUEST(8,0): Update operation for party '${id}' failed due to: The update path: '${badFieldPath}' points to an unknown field."
    ),
  )

}
