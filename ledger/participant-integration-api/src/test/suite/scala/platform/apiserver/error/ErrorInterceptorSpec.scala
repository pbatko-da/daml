package com.daml.platform.apiserver.error

import java.net.{InetAddress, InetSocketAddress}

import com.daml.grpc.adapter.utils.implementations.HelloService_AkkaImplementation
import com.daml.ledger.api.testing.utils.AkkaBeforeAndAfterAll
import com.daml.ledger.resources.{Resource, ResourceContext, ResourceOwner, TestResourceContext}
import com.daml.platform.apiserver.services.GrpcClientResource
import com.daml.platform.hello.{HelloRequest, HelloResponse, HelloServiceGrpc}
import com.daml.platform.testing.StreamConsumer
import com.daml.ports.Port
import io.grpc.netty.NettyServerBuilder
import io.grpc.{BindableService, Channel, Server, ServerInterceptor, Status, StatusRuntimeException}
import org.scalatest.Checkpoints
import org.scalatest.concurrent.Eventually
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Future

final class ErrorInterceptorSpec
  extends AsyncFlatSpec
  with AkkaBeforeAndAfterAll
  with Matchers
  with Eventually
  with TestResourceContext
  with Checkpoints {

  import ErrorInterceptorSpec._

  behavior of classOf[ErrorInterceptor].getSimpleName

  ignore should "do it server unary" in {
    val response: Future[HelloResponse] = server(
      tested = new ErrorInterceptor(),
      service = new HelloService_AkkaImplementation()
    ).use { channel: Channel =>
      HelloServiceGrpc.stub(channel).fails(HelloRequest(1))
    }

    recoverToExceptionIf[StatusRuntimeException]{
      response
    }.map { t: StatusRuntimeException =>
      val status = Status.fromThrowable(t)
      status.getCode shouldBe Status.Code.INTERNAL
      status.getDescription shouldBe "An error occurred. Please contact the operator and inquire about the request <no-correlation-id>"
    }

  }

  // TODO error codes: handle errors from akka streaming services
  // See com.daml.protoc.plugins.akka.AkkaGrpcServicePrinter.closureUtils
  // for
  it should "do it server streaming" in {
    val response: Future[Vector[HelloResponse]] = server(
      tested = new ErrorInterceptor(),
      service = new HelloService_AkkaImplementation(){
        override protected def responses(request: HelloRequest): List[HelloResponse] = {
          throw new IllegalArgumentException("Failure from inside an Akka stream")
        }
      }
    ).use { channel: Channel =>
      val streamConsumer = new StreamConsumer[HelloResponse](observer => HelloServiceGrpc.stub(channel).serverStreaming(HelloRequest(1), observer))
      streamConsumer.all()
      // GrpcTransactionService extends TransactionServiceAkkaGrpc
    }

    recoverToExceptionIf[StatusRuntimeException]{
      response
    }.map { t: StatusRuntimeException =>
      val status = Status.fromThrowable(t)
      status.getCode shouldBe Status.Code.INTERNAL
      status.getDescription shouldBe "An error occurred. Please contact the operator and inquire about the request <no-correlation-id>"
    }

  }

}

object ErrorInterceptorSpec {

  def server(tested: ErrorInterceptor, service: BindableService): ResourceOwner[Channel] = {
    for {
      server <- serverOwner(interceptor = tested, service = service)
      channel <- GrpcClientResource.owner(Port(server.getPort))
    } yield channel
  }

  private def serverOwner(
                           interceptor: ServerInterceptor,
                           service: BindableService,
                         ): ResourceOwner[Server] =
    new ResourceOwner[Server] {
      def acquire()(implicit context: ResourceContext): Resource[Server] =
        Resource(Future {
          val server =
            NettyServerBuilder
              .forAddress(new InetSocketAddress(InetAddress.getLoopbackAddress, 0))
              .directExecutor()
              .intercept(interceptor)
              .addService(service)
              .build()
          server.start()
          server
        })(server => Future(server.shutdown().awaitTermination()))
    }
}