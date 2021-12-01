
package io.grpc.health.v1.health

trait HealthAkkaGrpc extends HealthGrpc.Health with AutoCloseable {
  protected implicit def esf: com.daml.grpc.adapter.ExecutionSequencerFactory
  protected implicit def mat: akka.stream.Materializer

  protected val killSwitch = akka.stream.KillSwitches.shared("HealthKillSwitch 49994203771183")
  protected val closed = new java.util.concurrent.atomic.AtomicBoolean(false)
  protected def closingError = new io.grpc.StatusRuntimeException(io.grpc.Status.UNAVAILABLE.withDescription("Server is shutting down")) with scala.util.control.NoStackTrace
  def close(): Unit = {
    if (closed.compareAndSet(false, true)) killSwitch.abort(closingError)
  }

  def watch(
             request: io.grpc.health.v1.health.HealthCheckRequest,
             responseObserver: _root_.io.grpc.stub.StreamObserver[io.grpc.health.v1.health.HealthCheckResponse]
           ): Unit = {
    if (closed.get()) {
      responseObserver.onError(closingError)
    } else {
      val sink = com.daml.grpc.adapter.server.akka.ServerAdapter.toSink(responseObserver)
      watchSource(request).via(killSwitch.flow).runWith(sink)
      ()
    }
  }
  protected def watchSource(request: io.grpc.health.v1.health.HealthCheckRequest): akka.stream.scaladsl.Source[io.grpc.health.v1.health.HealthCheckResponse, akka.NotUsed]


}