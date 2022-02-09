// Copyright (c) 2022 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.ledger.api.benchtool

import java.util.concurrent.{ArrayBlockingQueue, Executor, SynchronousQueue, ThreadPoolExecutor, TimeUnit}

import com.daml.jwt.JwtSigner
import com.daml.jwt.domain.DecodedJwt
import com.daml.ledger.api.auth.{AuthServiceJWTCodec, AuthServiceJWTPayload, StandardJWTPayload}
import com.daml.ledger.api.benchtool.config.{Config, ConfigMaker, WorkflowConfig}
import com.daml.ledger.api.benchtool.services.LedgerApiServices
import com.daml.ledger.api.benchtool.submission.{CommandSubmitter, Names}
import com.daml.ledger.api.tls.TlsConfiguration
import com.daml.ledger.resources.{ResourceContext, ResourceOwner}
import io.grpc.Channel
import io.grpc.netty.{NegotiationType, NettyChannelBuilder}
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

object TokenUtils {

  val participantAdminUserId = "participant_admin"
  val adminToken: String = getToken(participantAdminUserId)

  def getToken(userId: String): String = {
    // TODO copied from com.daml.platform.sandbox.SandboxRequiringAuthorizationFuns.toHeader
    val jwtHeader = """{"alg": "HS256", "typ": "JWT"}"""
    val jwtSecret: String = "secret" // UUID.randomUUID.toString

    //    val signatoryUserId = scalaz.Tag.unwrap(signatory)

    def toHeader(payload: AuthServiceJWTPayload, secret: String = jwtSecret): String =
      signed(payload, secret)

    def signed(payload: AuthServiceJWTPayload, secret: String): String =
      JwtSigner.HMAC256
        .sign(DecodedJwt(jwtHeader, AuthServiceJWTCodec.compactPrint(payload)), secret)
        .getOrElse(sys.error("Failed to generate token"))
        .value

    val signatoryJwtPayload = StandardJWTPayload(
      participantId = None,
      userId = userId,
      exp = None,
    )
    val token = toHeader(signatoryJwtPayload)
    token
  }

}

object LedgerApiBenchTool {

  def main(args: Array[String]): Unit = {
    import scala.concurrent.ExecutionContext.Implicits.global
    ConfigMaker.make(args) match {
      case Left(error) =>
        logger.error(s"Configuration error: ${error.details}")
      case Right(config: Config) =>
        logger.info(s"Starting benchmark with configuration:\n${prettyPrint(config)}")
        val result = run(config)(ExecutionContext.Implicits.global)
          .map {
            case Right(()) =>
              logger.info(s"Benchmark finished successfully.")
            case Left(error) =>
              logger.info(s"Benchmark failed: $error")
          }
          .recover { case ex =>
            logger.error(s"ledger-api-bench-tool failure: ${ex.getMessage}", ex)
            sys.exit(1)
          }(scala.concurrent.ExecutionContext.Implicits.global)
        Await.result(result, atMost = Duration.Inf)
        ()
    }
  }


  private def run(config: Config)(implicit ec: ExecutionContext): Future[Either[String, Unit]] = {
    implicit val resourceContext: ResourceContext = ResourceContext(ec)

    val names = new Names

    apiServicesOwner(config).use {
      servicesForUserId: (String => LedgerApiServices) =>

        def benchmarkStep(
                           streamConfigs: List[WorkflowConfig.StreamConfig],
                         ): Future[Either[String, Unit]] =
          if (streamConfigs.isEmpty) {
            Future.successful(
              Right(logger.info(s"No streams defined. Skipping the benchmark step."))
            )
          } else {
            Benchmark.run(
              streamConfigs = streamConfigs,
              reportingPeriod = config.reportingPeriod,
              apiServices = servicesForUserId(names.benchtoolUserId),
              metricsReporter = config.metricsReporter,
            )
          }


        def submissionStep(
                            submissionConfig: Option[WorkflowConfig.SubmissionConfig],
                          ): Future[Option[CommandSubmitter.SubmissionSummary]] =
          submissionConfig match {
            case None =>
              logger.info(s"No submission defined. Skipping.")
              Future.successful(None)
            case Some(submissionConfig) =>
              val submitter = CommandSubmitter(
                names = names,
                servicesForUserId = servicesForUserId,
                adminServices = servicesForUserId(TokenUtils.participantAdminUserId),
              )
              submitter
                .submit(
                  config = submissionConfig,
                  maxInFlightCommands = config.maxInFlightCommands,
                  submissionBatchSize = config.submissionBatchSize,
                )
                .map(Some(_))
          }



        for {
          summary <- submissionStep(
            submissionConfig = config.workflow.submission,
          )
          streams = config.workflow.streams.map(
            ConfigEnricher.enrichStreamConfig(_, summary)
          )
          _ = logger.info(
            s"Stream configs adapted after the submission step: ${prettyPrint(streams)}"
          )
          benchmarkResult <- benchmarkStep(
            streams,
          )
        } yield benchmarkResult
    }
  }

  private def apiServicesOwner(
                                config: Config
                              )(implicit ec: ExecutionContext): ResourceOwner[String => LedgerApiServices] =
    for {
      executorService <- threadPoolExecutorOwner(config.concurrency)
      channel <- channelOwner(config.ledger, config.tls, executorService)
      services <- ResourceOwner.forFuture(() => LedgerApiServices.forChannel(
        channel = channel,
        // TODO pbatko
        enableUserBasedAuthorization = true,
      ))
    } yield services

  private def channelOwner(
                            ledger: Config.Ledger,
                            tls: TlsConfiguration,
                            executor: Executor,
                          ): ResourceOwner[Channel] = {
    logger.info(
      s"Setting up a managed channel to a ledger at: ${ledger.hostname}:${ledger.port}..."
    )
    val MessageChannelSizeBytes: Int = 32 * 1024 * 1024 // 32 MiB
    val ShutdownTimeout: FiniteDuration = 5.seconds

    val channelBuilder = NettyChannelBuilder
      .forAddress(ledger.hostname, ledger.port)
      .executor(executor)
      .maxInboundMessageSize(MessageChannelSizeBytes)
      .usePlaintext()

    if (tls.enabled) {
      tls.client().map { sslContext =>
        logger.info(s"Setting up a managed channel with transport security...")
        channelBuilder
          .useTransportSecurity()
          .sslContext(sslContext)
          .negotiationType(NegotiationType.TLS)
      }
    }

    ResourceOwner.forChannel(channelBuilder, ShutdownTimeout)
  }

  private def threadPoolExecutorOwner(
                                       config: Config.Concurrency
                                     ): ResourceOwner[ThreadPoolExecutor] =
    ResourceOwner.forExecutorService(() =>
      new ThreadPoolExecutor(
        config.corePoolSize,
        config.maxPoolSize,
        config.keepAliveTime,
        TimeUnit.SECONDS,
        if (config.maxQueueLength == 0) new SynchronousQueue[Runnable]()
        else new ArrayBlockingQueue[Runnable](config.maxQueueLength),
      )
    )

  private val logger: Logger = LoggerFactory.getLogger(getClass)
  private val printer = pprint.PPrinter.BlackWhite

  private def prettyPrint(x: Any): String = printer(x).toString()
}
