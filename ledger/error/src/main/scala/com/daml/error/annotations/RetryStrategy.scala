package com.daml.error.annotations

import scala.annotation.StaticAnnotation

case class RetryStrategy(retryStrategy: String) extends StaticAnnotation
