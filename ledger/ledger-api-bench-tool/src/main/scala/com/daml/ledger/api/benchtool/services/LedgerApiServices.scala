// Copyright (c) 2022 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.ledger.api.benchtool.services

import com.daml.ledger.api.benchtool.TokenUtils
import com.daml.ledger.api.benchtool.submission.CommandSubmitter
import com.daml.ledger.api.v1.admin.user_management_service.UserManagementServiceGrpc
import io.grpc.Channel

import scala.concurrent.{ExecutionContext, Future}

class LedgerApiServices(
                         channel: Channel,
                         val ledgerId: String,
                         userId: String,
                         enableUserBasedAuthorization: Boolean,
                       ) {

  private val authorizationToken = Option.when(enableUserBasedAuthorization)(TokenUtils.getToken(userId))
  val activeContractsService = new ActiveContractsService(channel, ledgerId, authorizationToken = authorizationToken)
  val commandService = new CommandService(channel, token = authorizationToken)
  val commandCompletionService =
    new CommandCompletionService(channel, ledgerId, userId = userId, authorizationToken = authorizationToken)
  val packageManagementService = new PackageManagementService(channel, authorizationToken = authorizationToken)
  val partyManagementService = new PartyManagementService(channel, authorizationToken = authorizationToken)
  val transactionService = new TransactionService(channel, ledgerId, authorizationToken = authorizationToken)
  val userManagementService: UserManagementServiceGrpc.UserManagementServiceStub =
    CommandSubmitter.authedService(authorizationToken)(UserManagementServiceGrpc.stub(channel))

}

object LedgerApiServices {
  def forChannel(
                  enableUserBasedAuthorization: Boolean,
                  channel: Channel
                )(implicit ec: ExecutionContext): Future[String => LedgerApiServices] = {
    val ledgerIdentityService: LedgerIdentityService =
      new LedgerIdentityService(channel, token = Some(TokenUtils.adminToken))
    ledgerIdentityService
      .fetchLedgerId()
      .map(ledgerId =>
        (userId: String) =>
          new LedgerApiServices(
            channel,
            ledgerId,
            enableUserBasedAuthorization = enableUserBasedAuthorization,
            userId = userId,
          )
      )
  }
}
