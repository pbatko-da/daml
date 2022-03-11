package com.daml.error.annotations

import scala.annotation.StaticAnnotation

case class DeprecatedDocs(deprecation: String) extends StaticAnnotation
