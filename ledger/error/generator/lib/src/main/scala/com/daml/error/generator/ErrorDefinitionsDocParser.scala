// Copyright (c) 2022 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.error.generator

import java.lang.reflect.Modifier

import com.daml.error._
import org.reflections.Reflections

import scala.jdk.CollectionConverters._
import scala.reflect.runtime.{universe => ru}

/** Utility that indexes all error code implementations.
  */
object ErrorDefinitionsDocParser {

  case class DeprecatedItem(message: String, since: String)

  private case class ErrorCodeAnnotations(
      deprecation: Option[DeprecatedItem],
      explanation: Option[Explanation],
      resolution: Option[Resolution],
  )

  private case class ErrorGroupAnnotations(
      explanation: Option[Explanation]
  )

  private val runtimeMirror: ru.Mirror = ru.runtimeMirror(getClass.getClassLoader)

  private val ScalaDeprecatedTypeName = classOf[deprecated].getTypeName.replace("scala.", "")
  private val ExplanationTypeName = classOf[Explanation].getTypeName.replace("$", ".")
  private val ResolutionTypeName = classOf[Resolution].getTypeName.replace("$", ".")
  private val DescriptionTypeName = classOf[Description].getTypeName.replace("$", ".")
  private val RetryStrategyTypeName = classOf[RetryStrategy].getTypeName.replace("$", ".")

  private val DefaultPackagePrefixes: Array[String] = Array("com.daml")

  def getErrorCodeItems(
      searchPackagePrefixes: Array[String] = DefaultPackagePrefixes
  ): Seq[ErrorCodeDocItem] = {
    val errorCodes = findInstancesOf[ErrorCode](searchPackagePrefixes)
    errorCodes.view.map(_.id).groupBy(identity).collect {
      case (code, occurrences) if occurrences.size > 1 =>
        sys.error(
          s"Error code $code is used ${occurrences.size} times but we require each error code to be unique!"
        )
    }
    errorCodes
      .map { errorCode =>
        val annotations = parseErrorCodeAnnotations(errorCode)
        ErrorCodeDocItem(
          className = errorCode.getClass.getName,
          category = simpleClassName(errorCode.category),
          hierarchicalGrouping = errorCode.parent,
          conveyance = errorCode.errorConveyanceDocString,
          code = errorCode.id,
          deprecation = annotations.deprecation,
          explanation = annotations.explanation,
          resolution = annotations.resolution,
        )
      }
      .sortBy(_.code)
  }

  def getErrorGroupItems(
      searchPackagePrefixes: Array[String] = DefaultPackagePrefixes
  ): Seq[ErrorGroupDocItem] = {
    val errorGroups = findInstancesOf[ErrorGroup](searchPackagePrefixes)
    errorGroups.view.map(_.errorClass).groupBy(identity).collect {
      case (group, occurrences) if occurrences.size > 1 =>
        sys.error(
          s"There are ${occurrences.size} groups named $group but we require each group class name to be unique! "
        )
    }
    errorGroups.map { errorGroup =>
      ErrorGroupDocItem(
        errorClass = errorGroup.errorClass,
        className = errorGroup.fullClassName,
        explanation = parseErrorGroupAnnotations(errorGroup).explanation,
      )
    }
  }

  def getErrorCategoryItem(errorCategory: ErrorCategory): ErrorCategoryDocItem = {
    val mirroredType = runtimeMirror.reflect(errorCategory)
    val annotations: Seq[ru.Annotation] = mirroredType.symbol.annotations
    val description = new SettableOnce[String]
    val resolution = new SettableOnce[String]
    val retryStrategy = new SettableOnce[String]
    annotations.foreach { annotation =>
      lazy val parsedValue = parseAnnotationValue(annotation.tree)
      getAnnotationTypeName(annotation) match {
        case DescriptionTypeName =>
          description.set(parsedValue, DescriptionTypeName)
        case ResolutionTypeName =>
          resolution.set(parsedValue, ResolutionTypeName)
        case RetryStrategyTypeName =>
          retryStrategy.set(parsedValue, RetryStrategyTypeName)
        case otherAnnotationTypeName =>
          throw new IllegalStateException(
            s"Unexpected annotation of type: $otherAnnotationTypeName"
          )
      }
    }
    ErrorCategoryDocItem(
      description = description.get,
      resolution = resolution.get,
      retryStrategy = retryStrategy.get,
    )
  }

  private def parseErrorCodeAnnotations(errorCode: ErrorCode): ErrorCodeAnnotations = {
    val mirroredType = runtimeMirror.reflect(errorCode)
    val annotations: Seq[ru.Annotation] = mirroredType.symbol.annotations
    val deprecatedItem = new SettableOnce[DeprecatedItem]
    val explanation = new SettableOnce[Explanation]
    val resolution = new SettableOnce[Resolution]
    annotations.foreach { annotation =>
      getAnnotationTypeName(annotation) match {
        case ExplanationTypeName =>
          explanation.set(
            Explanation(parseAnnotationValue(annotation.tree)),
            context = ExplanationTypeName,
          )
        case ResolutionTypeName =>
          resolution.set(
            Resolution(parseAnnotationValue(annotation.tree)),
            context = ResolutionTypeName,
          )
        case ScalaDeprecatedTypeName =>
          val messageArg = annotation.tree.children(1).asInstanceOf[ru.NamedArg]
          val sinceArg = annotation.tree.children(2).asInstanceOf[ru.NamedArg]
          val message = getLiteralStringValue(messageArg.rhs.asInstanceOf[ru.Literal])
          val since = getLiteralStringValue(sinceArg.rhs.asInstanceOf[ru.Literal])
          deprecatedItem.set(
            DeprecatedItem(message = message, since = since),
            ScalaDeprecatedTypeName,
          )
        case otherAnnotationTypeName =>
          throw new IllegalStateException(
            s"Unexpected annotation of type: $otherAnnotationTypeName"
          )
      }
    }
    ErrorCodeAnnotations(
      deprecation = deprecatedItem.get,
      explanation = explanation.get,
      resolution = resolution.get,
    )
  }

  private def parseErrorGroupAnnotations(errorGroup: ErrorGroup): ErrorGroupAnnotations = {
    val mirroredType = runtimeMirror.reflect(errorGroup)
    val annotations = mirroredType.symbol.annotations
    val explanation = new SettableOnce[Explanation]
    annotations.foreach { annotation =>
      getAnnotationTypeName(annotation) match {
        case ExplanationTypeName =>
          explanation.set(Explanation(parseAnnotationValue(annotation.tree)), ExplanationTypeName)
        case otherAnnotationTypeName =>
          throw new IllegalStateException(
            s"Unexpected annotation of type: ${otherAnnotationTypeName}"
          )
      }
    }
    ErrorGroupAnnotations(
      explanation = explanation.get
    )
  }

  private def findInstancesOf[T: ru.TypeTag](packagePrefixes: Array[String]): Seq[T] =
    new Reflections(packagePrefixes)
      .getSubTypesOf(runtimeMirror.runtimeClass(ru.typeOf[T]))
      .asScala
      .view
      .collect {
        case clazz if !Modifier.isAbstract(clazz.getModifiers) =>
          clazz.getDeclaredField("MODULE$").get(clazz).asInstanceOf[T]
      }
      .toSeq

  private def simpleClassName(any: Any): String =
    any.getClass.getSimpleName.replace("$", "")

  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
  private def parseAnnotationValue(tree: ru.Tree): String = {
    val arg = tree.children(1).asInstanceOf[ru.Literal]
    getLiteralStringValue(arg)
  }

  private def getAnnotationTypeName(annotation: ru.Annotation): String =
    annotation.tree.tpe.toString

  private def getLiteralStringValue(l: ru.Literal): String = {
    try {
      l.value.value.asInstanceOf[String].stripMargin
    } catch {
      case e: RuntimeException =>
        throw new RuntimeException(
          "Failed to process description (description needs to be a constant-string. i.e. don't apply stripMargin here ...): " + l.toString,
          e,
        )
    }
  }

  private class SettableOnce[T >: Null <: AnyRef] {
    private var v: Option[T] = None
    def set(v: T, context: String): Unit = {
      if (this.v.nonEmpty)
        sys.error(s"Duplicate $context detected. A value |$v| is already present.")
      this.v = Some(v)
    }
    def get: Option[T] = {
      this.v
    }
  }

}
