// Copyright (c) 2022 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.ledger.api.auth

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.{ActorSystem, Cancellable}
import com.daml.error.DamlContextualizedErrorLogger
import com.daml.error.definitions.LedgerApiErrors
import com.daml.ledger.api.auth.interceptor.AuthorizationInterceptor
import com.daml.ledger.participant.state.index.v2.UserManagementStore
import com.daml.lf.data.Ref
import com.daml.logging.{ContextualizedLogger, LoggingContext}
import com.daml.platform.server.api.validation.ErrorFactories
import io.grpc.StatusRuntimeException
import io.grpc.stub.ServerCallStreamObserver

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}

private[auth] final class OngoingAuthorizationObserver[A](
                                                           observer: ServerCallStreamObserver[A],
                                                           originalClaims: ClaimSet.Claims,
                                                           nowF: () => Instant,
                                                           errorFactories: ErrorFactories,
                                                           userManagementStore: UserManagementStore,
                                                           implicit val ec: ExecutionContext,
                                                           claimsFreshnessCheckDelayInSeconds: Int,
                                                         )(implicit loggingContext: LoggingContext)
  extends ServerCallStreamObserver[A] {

  // TODO: what execution context
  private val actorSystem = ActorSystem("streamAuth")

  private val logger = ContextualizedLogger.get(getClass)
  private val errorLogger = new DamlContextualizedErrorLogger(logger, loggingContext, None)

  private val shouldAbort = new AtomicBoolean(false)
  private val userRightsRefreshInProgress = new AtomicBoolean(false)
  @volatile private var lastUserInfoRefreshStartTime = Instant.EPOCH

  private val delay = Duration(claimsFreshnessCheckDelayInSeconds.toLong, TimeUnit.SECONDS)

  private lazy val userId = originalClaims.applicationId.fold[Ref.UserId](
    throw new RuntimeException(
      "Claims were resolved from a user but userId (applicationId) is missing in the claims."
    )
  )(Ref.UserId.assertFromString)

  private val cancellable: Cancellable = actorSystem.scheduler.scheduleWithFixedDelay(initialDelay = delay, delay = delay)(runnable = checkUserRights _)

  private def checkUserRights(): Unit = {
    userRightsRefreshInProgress.set(true)
    lastUserInfoRefreshStartTime = nowF()
    userManagementStore
      .listUserRights(userId)
      .onComplete {
        case Failure(_) => shouldAbort.set(true)
        case Success(Left(_)) => shouldAbort.set(true)
        case Success(Right(userRights)) =>
          if (!shouldAbort.get()) {
            val updatedClaims = AuthorizationInterceptor.convertUserRightsToClaims(userRights)
            shouldAbort.compareAndSet(false, updatedClaims.toSet != originalClaims.claims.toSet)
          }
      }
    userRightsRefreshInProgress.set(false)
  }

  override def isCancelled: Boolean = observer.isCancelled

  override def setOnCancelHandler(runnable: Runnable): Unit = observer.setOnCancelHandler(runnable)

  override def setCompression(s: String): Unit = observer.setCompression(s)

  override def isReady: Boolean = observer.isReady

  override def setOnReadyHandler(runnable: Runnable): Unit = observer.setOnReadyHandler(runnable)

  override def disableAutoInboundFlowControl(): Unit = observer.disableAutoInboundFlowControl()

  override def request(i: Int): Unit = observer.request(i)

  override def setMessageCompression(b: Boolean): Unit = observer.setMessageCompression(b)

  override def onNext(v: A): Unit =
    authorize match {
      case Right(_) => observer.onNext(v)
      case Left(statusRuntimeException) =>
        observer.onError(statusRuntimeException)
    }

  override def onError(throwable: Throwable): Unit = {
    cancelAuthCheck()
    observer.onError(throwable)
  }

  override def onCompleted(): Unit = {
    cancelAuthCheck()
    observer.onCompleted()
  }

  private def authorize: Either[StatusRuntimeException, Unit] = {
    val now = nowF()
    for {
      _ <- originalClaims.notExpired(now).left.map(authorizationError =>
        errorFactories.permissionDenied(authorizationError.reason)(errorLogger)
      )
      _ <- if ({

        originalClaims.resolvedFromUser && (shouldAbort.get() || longs) {
          // Terminate the stream, so that clients will restart their streams
          // and claims will be rechecked precisely.
          Left(LedgerApiErrors.AuthorizationChecks.StaleUserManagementBasedStreamClaims
            .Reject()(errorLogger)
            .asGrpcError)
        }
      } else Right(())
    } yield {
      ()
    }
  }

  val longs = userRightsRefreshInProgress.get() && now.isAfter(
    lastUserInfoRefreshStartTime
      .plus(2 * claimsFreshnessCheckDelayInSeconds.toLong, ChronoUnit.SECONDS))
  private def timed
  private def cancelAuthCheck(): Unit = {
    cancellable.cancel()
    if (!cancellable.isCancelled) {
      logger.debug(s"Failed to cancel stream authorization task")
    }
  }

}
