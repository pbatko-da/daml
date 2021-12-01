package com.daml.ledger.api.v1.testing.time_service

trait TimeServiceAkkaGrpc extends TimeServiceGrpc.TimeService with AutoCloseable {
  protected implicit def esf: com.daml.grpc.adapter.ExecutionSequencerFactory
  protected implicit def mat: akka.stream.Materializer

  protected val killSwitch = akka.stream.KillSwitches.shared("TimeServiceKillSwitch 49994183592456")
  protected val closed = new java.util.concurrent.atomic.AtomicBoolean(false)
  protected def closingError = new io.grpc.StatusRuntimeException(io.grpc.Status.UNAVAILABLE.withDescription("Server is shutting down")) with scala.util.control.NoStackTrace

  def close(): Unit = {
    if (closed.compareAndSet(false, true)) killSwitch.abort(closingError)
  }

  def getTime(
               request: com.daml.ledger.api.v1.testing.time_service.GetTimeRequest,
               responseObserver: _root_.io.grpc.stub.StreamObserver[com.daml.ledger.api.v1.testing.time_service.GetTimeResponse]
             ): Unit = {
    if (closed.get()) {
      responseObserver.onError(closingError)
    } else {
      val sink = com.daml.grpc.adapter.server.akka.ServerAdapter.toSink(responseObserver)
      getTimeSource(request).via(killSwitch.flow).runWith(sink)
      ()
    }
  }

  protected def getTimeSource(request: com.daml.ledger.api.v1.testing.time_service.GetTimeRequest): akka.stream.scaladsl.Source[com.daml.ledger.api.v1.testing.time_service.GetTimeResponse, akka.NotUsed]


}