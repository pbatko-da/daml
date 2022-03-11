package com.daml.error.annotations

import scala.annotation.StaticAnnotation

// Use these annotations to add more information to the documentation for an error on the website
case class Explanation(explanation: String) extends StaticAnnotation
