// Copyright (c) 2022 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.platform.apiserver.error

import com.daml.error.definitions.{DamlContextualizedErrorLogger, LedgerApiErrors}
import com.daml.error.ErrorsAssertions
import com.daml.ledger.api.testing.utils.AkkaBeforeAndAfterAll
import com.daml.ledger.resources.TestResourceContext
import com.daml.platform.testing.LogCollectorAssertions
import org.scalatest.Checkpoints
import org.scalatest.concurrent.Eventually
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers

final class ErrorInterceptorSpec2
    extends AsyncFreeSpec
    with AkkaBeforeAndAfterAll
    with Matchers
    with Eventually
    with TestResourceContext
    with Checkpoints
    with LogCollectorAssertions
    with ErrorsAssertions {

  classOf[ErrorInterceptor].getSimpleName - {
    "do it" in {

      val e = new RuntimeException("ALA 1")
      e.printStackTrace()
      e.printStackTrace()
      e.getStackTrace
      val _ = LedgerApiErrors.InternalError.UnexpectedOrUnknownException(e)(
        DamlContextualizedErrorLogger.forTesting(getClass)
      )

      throw new RuntimeException("ALA 2")
    }

  }

//  private def exerciseUnaryFutureEndpoint(
//      helloService: BindableService
//  ): Future[StatusRuntimeException] = {
//    val response: Future[HelloResponse] = server(
//      tested = new ErrorInterceptor(),
//      service = helloService,
//    ).use { channel =>
//      HelloServiceGrpc.stub(channel).single(HelloRequest(1))
//    }
//    recoverToExceptionIf[StatusRuntimeException] {
//      response
//    }
//  }
//
//  private def exerciseStreamingAkkaEndpoint(
//      helloService: BindableService
//  ): Future[StatusRuntimeException] = {
//    val response: Future[Vector[HelloResponse]] = server(
//      tested = new ErrorInterceptor(),
//      service = helloService,
//    ).use { channel =>
//      val streamConsumer = new StreamConsumer[HelloResponse](observer =>
//        HelloServiceGrpc.stub(channel).serverStreaming(HelloRequest(1), observer)
//      )
//      streamConsumer.all()
//    }
//    recoverToExceptionIf[StatusRuntimeException] {
//      response
//    }
//  }
//
//  private def assertSecuritySanitizedError(actual: StatusRuntimeException): Assertion = {
//    assertError(
//      actual,
//      expectedStatusCode = Status.Code.INTERNAL,
//      expectedMessage =
//        "An error occurred. Please contact the operator and inquire about the request <no-correlation-id>",
//      expectedDetails = Seq(),
//    )
//    Assertions.succeed
//  }
//
//  private def assertFooMissingError(
//      actual: StatusRuntimeException,
//      expectedMsg: String,
//  ): Assertion = {
//    assertError(
//      actual,
//      expectedStatusCode = FooMissingErrorCode.category.grpcCode.get,
//      expectedMessage = s"FOO_MISSING_ERROR_CODE(11,0): Foo is missing: $expectedMsg",
//      expectedDetails =
//        Seq(ErrorDetails.ErrorInfoDetail("FOO_MISSING_ERROR_CODE", Map("category" -> "11"))),
//    )
//    Assertions.succeed
//  }

}
