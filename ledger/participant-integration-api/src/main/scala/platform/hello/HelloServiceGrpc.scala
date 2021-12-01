//package com.daml.platform.hello
//
//object HelloServiceGrpc {
//  val METHOD_SINGLE: _root_.io.grpc.MethodDescriptor[com.daml.platform.hello.HelloRequest, com.daml.platform.hello.HelloResponse] =
//    _root_.io.grpc.MethodDescriptor.newBuilder()
//      .setType(_root_.io.grpc.MethodDescriptor.MethodType.UNARY)
//      .setFullMethodName(_root_.io.grpc.MethodDescriptor.generateFullMethodName("com.daml.platform.HelloService", "Single"))
//      .setSampledToLocalTracing(true)
//      .setRequestMarshaller(_root_.scalapb.grpc.Marshaller.forMessage[com.daml.platform.hello.HelloRequest])
//      .setResponseMarshaller(_root_.scalapb.grpc.Marshaller.forMessage[com.daml.platform.hello.HelloResponse])
//      .setSchemaDescriptor(_root_.scalapb.grpc.ConcreteProtoMethodDescriptorSupplier.fromMethodDescriptor(com.daml.platform.hello.HelloProto.javaDescriptor.getServices().get(0).getMethods().get(0)))
//      .build()
//
//  val METHOD_SERVER_STREAMING: _root_.io.grpc.MethodDescriptor[com.daml.platform.hello.HelloRequest, com.daml.platform.hello.HelloResponse] =
//    _root_.io.grpc.MethodDescriptor.newBuilder()
//      .setType(_root_.io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
//      .setFullMethodName(_root_.io.grpc.MethodDescriptor.generateFullMethodName("com.daml.platform.HelloService", "ServerStreaming"))
//      .setSampledToLocalTracing(true)
//      .setRequestMarshaller(_root_.scalapb.grpc.Marshaller.forMessage[com.daml.platform.hello.HelloRequest])
//      .setResponseMarshaller(_root_.scalapb.grpc.Marshaller.forMessage[com.daml.platform.hello.HelloResponse])
//      .setSchemaDescriptor(_root_.scalapb.grpc.ConcreteProtoMethodDescriptorSupplier.fromMethodDescriptor(com.daml.platform.hello.HelloProto.javaDescriptor.getServices().get(0).getMethods().get(1)))
//      .build()
//
//  val METHOD_FAILS: _root_.io.grpc.MethodDescriptor[com.daml.platform.hello.HelloRequest, com.daml.platform.hello.HelloResponse] =
//    _root_.io.grpc.MethodDescriptor.newBuilder()
//      .setType(_root_.io.grpc.MethodDescriptor.MethodType.UNARY)
//      .setFullMethodName(_root_.io.grpc.MethodDescriptor.generateFullMethodName("com.daml.platform.HelloService", "Fails"))
//      .setSampledToLocalTracing(true)
//      .setRequestMarshaller(_root_.scalapb.grpc.Marshaller.forMessage[com.daml.platform.hello.HelloRequest])
//      .setResponseMarshaller(_root_.scalapb.grpc.Marshaller.forMessage[com.daml.platform.hello.HelloResponse])
//      .setSchemaDescriptor(_root_.scalapb.grpc.ConcreteProtoMethodDescriptorSupplier.fromMethodDescriptor(com.daml.platform.hello.HelloProto.javaDescriptor.getServices().get(0).getMethods().get(2)))
//      .build()
//
//  val SERVICE: _root_.io.grpc.ServiceDescriptor =
//    _root_.io.grpc.ServiceDescriptor.newBuilder("com.daml.platform.HelloService")
//      .setSchemaDescriptor(new _root_.scalapb.grpc.ConcreteProtoFileDescriptorSupplier(com.daml.platform.hello.HelloProto.javaDescriptor))
//      .addMethod(METHOD_SINGLE)
//      .addMethod(METHOD_SERVER_STREAMING)
//      .addMethod(METHOD_FAILS)
//      .build()
//
//  trait HelloService extends _root_.scalapb.grpc.AbstractService {
//    override def serviceCompanion = HelloService
//    def single(request: com.daml.platform.hello.HelloRequest): scala.concurrent.Future[com.daml.platform.hello.HelloResponse]
//    def serverStreaming(request: com.daml.platform.hello.HelloRequest, responseObserver: _root_.io.grpc.stub.StreamObserver[com.daml.platform.hello.HelloResponse]): _root_.scala.Unit
//    def fails(request: com.daml.platform.hello.HelloRequest): scala.concurrent.Future[com.daml.platform.hello.HelloResponse]
//  }
//
//  object HelloService extends _root_.scalapb.grpc.ServiceCompanion[HelloService] {
//    implicit def serviceCompanion: _root_.scalapb.grpc.ServiceCompanion[HelloService] = this
//    def javaDescriptor: _root_.com.google.protobuf.Descriptors.ServiceDescriptor = com.daml.platform.hello.HelloProto.javaDescriptor.getServices().get(0)
//    def scalaDescriptor: _root_.scalapb.descriptors.ServiceDescriptor = com.daml.platform.hello.HelloProto.scalaDescriptor.services(0)
//    def bindService(serviceImpl: HelloService, executionContext: scala.concurrent.ExecutionContext): _root_.io.grpc.ServerServiceDefinition =
//      _root_.io.grpc.ServerServiceDefinition.builder(SERVICE)
//        .addMethod(
//          METHOD_SINGLE,
//          _root_.io.grpc.stub.ServerCalls.asyncUnaryCall(new _root_.io.grpc.stub.ServerCalls.UnaryMethod[com.daml.platform.hello.HelloRequest, com.daml.platform.hello.HelloResponse] {
//            override def invoke(request: com.daml.platform.hello.HelloRequest, observer: _root_.io.grpc.stub.StreamObserver[com.daml.platform.hello.HelloResponse]): _root_.scala.Unit =
//              serviceImpl.single(request).onComplete(scalapb.grpc.Grpc.completeObserver(observer))(
//                executionContext)
//          }))
//        .addMethod(
//          METHOD_SERVER_STREAMING,
//          _root_.io.grpc.stub.ServerCalls.asyncServerStreamingCall(new _root_.io.grpc.stub.ServerCalls.ServerStreamingMethod[com.daml.platform.hello.HelloRequest, com.daml.platform.hello.HelloResponse] {
//            override def invoke(request: com.daml.platform.hello.HelloRequest, observer: _root_.io.grpc.stub.StreamObserver[com.daml.platform.hello.HelloResponse]): _root_.scala.Unit =
//              serviceImpl.serverStreaming(request, observer)
//          }))
//        .addMethod(
//          METHOD_FAILS,
//          _root_.io.grpc.stub.ServerCalls.asyncUnaryCall(new _root_.io.grpc.stub.ServerCalls.UnaryMethod[com.daml.platform.hello.HelloRequest, com.daml.platform.hello.HelloResponse] {
//            override def invoke(request: com.daml.platform.hello.HelloRequest, observer: _root_.io.grpc.stub.StreamObserver[com.daml.platform.hello.HelloResponse]): _root_.scala.Unit =
//              serviceImpl.fails(request).onComplete(scalapb.grpc.Grpc.completeObserver(observer))(
//                executionContext)
//          }))
//        .build()
//  }
//
//  trait HelloServiceBlockingClient {
//    def serviceCompanion = HelloService
//    def single(request: com.daml.platform.hello.HelloRequest): com.daml.platform.hello.HelloResponse
//    def serverStreaming(request: com.daml.platform.hello.HelloRequest): scala.collection.Iterator[com.daml.platform.hello.HelloResponse]
//    def fails(request: com.daml.platform.hello.HelloRequest): com.daml.platform.hello.HelloResponse
//  }
//
//  class HelloServiceBlockingStub(channel: _root_.io.grpc.Channel, options: _root_.io.grpc.CallOptions = _root_.io.grpc.CallOptions.DEFAULT) extends _root_.io.grpc.stub.AbstractStub[HelloServiceBlockingStub](channel, options) with HelloServiceBlockingClient {
//    override def single(request: com.daml.platform.hello.HelloRequest): com.daml.platform.hello.HelloResponse = {
//      _root_.scalapb.grpc.ClientCalls.blockingUnaryCall(channel, METHOD_SINGLE, options, request)
//    }
//
//    override def serverStreaming(request: com.daml.platform.hello.HelloRequest): scala.collection.Iterator[com.daml.platform.hello.HelloResponse] = {
//      _root_.scalapb.grpc.ClientCalls.blockingServerStreamingCall(channel, METHOD_SERVER_STREAMING, options, request)
//    }
//
//    override def fails(request: com.daml.platform.hello.HelloRequest): com.daml.platform.hello.HelloResponse = {
//      _root_.scalapb.grpc.ClientCalls.blockingUnaryCall(channel, METHOD_FAILS, options, request)
//    }
//
//    override def build(channel: _root_.io.grpc.Channel, options: _root_.io.grpc.CallOptions): HelloServiceBlockingStub = new HelloServiceBlockingStub(channel, options)
//  }
//
//  class HelloServiceStub(channel: _root_.io.grpc.Channel, options: _root_.io.grpc.CallOptions = _root_.io.grpc.CallOptions.DEFAULT) extends _root_.io.grpc.stub.AbstractStub[HelloServiceStub](channel, options) with HelloService {
//    override def single(request: com.daml.platform.hello.HelloRequest): scala.concurrent.Future[com.daml.platform.hello.HelloResponse] = {
//      _root_.scalapb.grpc.ClientCalls.asyncUnaryCall(channel, METHOD_SINGLE, options, request)
//    }
//
//    override def serverStreaming(request: com.daml.platform.hello.HelloRequest, responseObserver: _root_.io.grpc.stub.StreamObserver[com.daml.platform.hello.HelloResponse]): _root_.scala.Unit = {
//      _root_.scalapb.grpc.ClientCalls.asyncServerStreamingCall(channel, METHOD_SERVER_STREAMING, options, request, responseObserver)
//    }
//
//    override def fails(request: com.daml.platform.hello.HelloRequest): scala.concurrent.Future[com.daml.platform.hello.HelloResponse] = {
//      _root_.scalapb.grpc.ClientCalls.asyncUnaryCall(channel, METHOD_FAILS, options, request)
//    }
//
//    override def build(channel: _root_.io.grpc.Channel, options: _root_.io.grpc.CallOptions): HelloServiceStub = new HelloServiceStub(channel, options)
//  }
//
//  def bindService(serviceImpl: HelloService, executionContext: scala.concurrent.ExecutionContext): _root_.io.grpc.ServerServiceDefinition = HelloService.bindService(serviceImpl, executionContext)
//
//  def blockingStub(channel: _root_.io.grpc.Channel): HelloServiceBlockingStub = new HelloServiceBlockingStub(channel)
//
//  def stub(channel: _root_.io.grpc.Channel): HelloServiceStub = new HelloServiceStub(channel)
//
//  def javaDescriptor: _root_.com.google.protobuf.Descriptors.ServiceDescriptor = com.daml.platform.hello.HelloProto.javaDescriptor.getServices().get(0)
//
//}