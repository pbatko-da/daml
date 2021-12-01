package com.daml.platform.apiserver.error

import com.daml.error.{BaseError, DamlContextualizedErrorLogger}
import com.daml.error.definitions.LedgerApiErrors
import com.daml.logging.{ContextualizedLogger, LoggingContext}
import io.grpc.ForwardingServerCall.SimpleForwardingServerCall
import io.grpc.{ForwardingServerCallListener, Metadata, ServerCall, ServerCallHandler, ServerInterceptor, Status}

final class ErrorInterceptor extends ServerInterceptor {

  private val logger = ContextualizedLogger.get(getClass)
  private val emptyLoggingContext = LoggingContext.newLoggingContext(identity)

  override def interceptCall[ReqT, RespT](call: ServerCall[ReqT, RespT],
                                          headers: Metadata,
                                          next: ServerCallHandler[ReqT, RespT]): ServerCall.Listener[ReqT] = {

    val forwardingCall = new SimpleForwardingServerCall[ReqT, RespT](call) {

      override def close(status: Status, trailers: Metadata): Unit = {

        // Trying to detect the fourth case from scalapb.grpc.Grpc.completeObserver:
        // ```
        // case scala.util.Failure(e)
        // ```
        // where e is neither StatusException nor StatusRuntimeException.
        // However, because the original exception got converted into a StatusException
        // we don't have an easy way to determine its type.
        // Thus, we operate under the assumptions:
        // - If the server threw a StatusException or StatusRuntimeException we assume that error was created by means
        //   of the self-service error codes infrastructure.
        //   - In particular the exceptions represent Status.INTERNAL then its description and metadata are already
        //     sanitized wrt security sensitive information.
        // - If the server threw other exception we assume it know it wasn't created by means of self-service error code
        //   infrastructure. Also, we know that its been mapped to StatusException with Status.INTERNAL by means of
        //   `scalapb.grpc.Grpc.completeObserver`.
        //   - In particular its description is not sanitized.
        //   - TODO pbatko: What about its metadata? Looks to be always empty
        //   - TODO pbatko: What is the metadata for INTERNAL ssec if 1) correl-id is present 2) correl-id is absent
        //
        // TODO pbatko: How to discern between Daml vs. >internal gRPC impl< exceptions?
        //              Should modify exception internal to gRPC impl?.
        //
        try {
        if ((status.getCode == Status.Code.INTERNAL
          || status.getCode == Status.Code.UNKNOWN)
          && !BaseError.isSanitizedSecuritySensitiveMessage(status.getDescription)) {
          val recreatedException = status.asRuntimeException(trailers)
          val selfServiceException = LedgerApiErrors.InternalError.UnexpectedOrUnknownException(t = recreatedException)(
            new DamlContextualizedErrorLogger(logger, emptyLoggingContext, None)
          ).asGrpcError
          // Retrieving status and metadata in the same way as in `io.grpc.stub.ServerCalls.ServerCallStreamObserverImpl.onError`.
          val newMetadata = Option(Status.trailersFromThrowable(selfServiceException)).getOrElse(new Metadata())
          val newStatus = Status.fromThrowable(selfServiceException)
          super.close(newStatus, newMetadata)
        } else {
          super.close(status, trailers)
        }
//        } catch {
          //
        } finally {

        }

      }

      override def isCancelled: Boolean = {
        super.isCancelled
      }

      override def sendMessage(message: RespT): Unit = {
        super.sendMessage(message)
      }


    }
    val listener = next.startCall(forwardingCall, headers)
    new ErrorListener(
      delegate = listener,
      call = call
    )
  }


}


class ErrorListener[ReqT, RespT](delegate: ServerCall.Listener[ReqT],
                                 call: ServerCall[ReqT, RespT])
  extends ForwardingServerCallListener.SimpleForwardingServerCallListener[ReqT](delegate) {

  override def onHalfClose(): Unit = {
    try {
      super.onHalfClose()
    } catch {
      case t: Throwable => call.close(Status.UNIMPLEMENTED
        .withCause(t)
        .withDescription("BOB123 onHalfClose"), new Metadata());
    }

  }

  override def onCancel(): Unit = {
    try {
      super.onCancel()
    } catch {
      case t: Throwable => call.close(Status.UNIMPLEMENTED
        .withCause(t)
        .withDescription("BOB123 onCancel"), new Metadata());
    }
  }

  override def onComplete(): Unit = {
    try {
      super.onComplete()
    } catch {
      case t: Throwable => call.close(Status.UNIMPLEMENTED
        .withCause(t)
        .withDescription("BOB123 onComplete"), new Metadata());
    }
  }

  override def onReady(): Unit = {
    try {
      super.onReady()
    } catch {
      case t: Throwable => call.close(Status.UNIMPLEMENTED
        .withCause(t)
        .withDescription("BOB123 onReady"), new Metadata());
    }
  }

  override def onMessage(message: ReqT): Unit = {
    try {
      super.onMessage(message)
    } catch {
      case t: Throwable => call.close(Status.UNIMPLEMENTED
        .withCause(t)
        .withDescription("BOB123 onMessage"), new Metadata());
    }
  }


}