package com.daml.ledger.api.v1.transaction_service

trait TransactionServiceAkkaGrpc extends TransactionServiceGrpc.TransactionService with AutoCloseable {
  protected implicit def esf: com.daml.grpc.adapter.ExecutionSequencerFactory
  protected implicit def mat: akka.stream.Materializer

  protected val killSwitch = akka.stream.KillSwitches.shared("TransactionServiceKillSwitch 49994190226764")
  protected val closed = new java.util.concurrent.atomic.AtomicBoolean(false)
  protected def closingError = new io.grpc.StatusRuntimeException(io.grpc.Status.UNAVAILABLE.withDescription("Server is shutting down")) with scala.util.control.NoStackTrace
  def close(): Unit = {
    if (closed.compareAndSet(false, true)) killSwitch.abort(closingError)
  }

  def getTransactions(
                       request: com.daml.ledger.api.v1.transaction_service.GetTransactionsRequest,
                       responseObserver: _root_.io.grpc.stub.StreamObserver[com.daml.ledger.api.v1.transaction_service.GetTransactionsResponse]
                     ): Unit = {
    if (closed.get()) {
      responseObserver.onError(closingError)
    } else {
      val sink = com.daml.grpc.adapter.server.akka.ServerAdapter.toSink(responseObserver)
      getTransactionsSource(request).via(killSwitch.flow).runWith(sink)
      ()
    }
  }
  protected def getTransactionsSource(request: com.daml.ledger.api.v1.transaction_service.GetTransactionsRequest): akka.stream.scaladsl.Source[com.daml.ledger.api.v1.transaction_service.GetTransactionsResponse, akka.NotUsed]

  def getTransactionTrees(
                           request: com.daml.ledger.api.v1.transaction_service.GetTransactionsRequest,
                           responseObserver: _root_.io.grpc.stub.StreamObserver[com.daml.ledger.api.v1.transaction_service.GetTransactionTreesResponse]
                         ): Unit = {
    if (closed.get()) {
      responseObserver.onError(closingError)
    } else {
      val sink = com.daml.grpc.adapter.server.akka.ServerAdapter.toSink(responseObserver)
      getTransactionTreesSource(request).via(killSwitch.flow).runWith(sink)
      ()
    }
  }
  protected def getTransactionTreesSource(request: com.daml.ledger.api.v1.transaction_service.GetTransactionsRequest): akka.stream.scaladsl.Source[com.daml.ledger.api.v1.transaction_service.GetTransactionTreesResponse, akka.NotUsed]


}