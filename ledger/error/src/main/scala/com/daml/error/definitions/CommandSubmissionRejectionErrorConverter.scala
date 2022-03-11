// Copyright (c) 2022 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.daml.error.definitions

import com.daml.error.{BaseError, ContextualizedErrorLogger}
import com.daml.lf.engine.Error.{Interpretation, Package, Preprocessing, Validation}
import com.daml.lf.engine.{Error => LfError}
import com.daml.lf.interpretation.{Error => LfInterpretationError}
import io.grpc.StatusRuntimeException

object CommandSubmissionRejectionErrorConverter {

  def convert(cause: ErrorCause)(implicit errorLogger: ContextualizedErrorLogger): StatusRuntimeException = {
    cause match {
      case ErrorCause.DamlLf(error) => processLfError(error)
      case x: ErrorCause.LedgerTime =>
        LedgerApiErrors.CommandExecution.FailedToDetermineLedgerTime
          .Reject(s"Could not find a suitable ledger time after ${x.retries} retries")
          .asGrpcErrorFromContext
    }
  }

  private def processPackageError(err: LfError.Package.Error)(implicit errorLogger: ContextualizedErrorLogger): BaseError = err match {
    case e: Package.Internal => LedgerApiErrors.InternalError.PackageInternal(e)
    case Package.Validation(validationError) =>
      LedgerApiErrors.CommandExecution.Package.PackageValidationFailed
        .Reject(validationError.pretty)
    case Package.MissingPackage(packageId, context) =>
      LedgerApiErrors.RequestValidation.NotFound.Package
        .InterpretationReject(packageId, context)
    case Package.AllowedLanguageVersion(packageId, languageVersion, allowedLanguageVersions) =>
      LedgerApiErrors.CommandExecution.Package.AllowedLanguageVersions.Error(
        packageId,
        languageVersion,
        allowedLanguageVersions,
      )
    case e: Package.SelfConsistency =>
      LedgerApiErrors.InternalError.PackageSelfConsistency(e)
  }

  private def processPreprocessingError(err: LfError.Preprocessing.Error)(implicit errorLogger: ContextualizedErrorLogger): BaseError = err match {
    case e: Preprocessing.Internal => LedgerApiErrors.InternalError.Preprocessing(e)
    case e => LedgerApiErrors.CommandExecution.Preprocessing.PreprocessingFailed.Reject(e)
  }

  private def processValidationError(err: LfError.Validation.Error)(implicit errorLogger: ContextualizedErrorLogger): BaseError = err match {
    // we shouldn't see such errors during submission
    case e: Validation.ReplayMismatch => LedgerApiErrors.InternalError.Validation(e)
  }

  private def processDamlException(
                            err: com.daml.lf.interpretation.Error,
                            renderedMessage: String,
                            detailMessage: Option[String],
                          )(implicit errorLogger: ContextualizedErrorLogger): BaseError = {
    // detailMessage is only suitable for server side debugging but not for the user, so don't pass except on internal errors

    err match {
      case LfInterpretationError.ContractNotFound(cid) =>
        LedgerApiErrors.ConsistencyErrors.ContractNotFound
          .Reject(renderedMessage, cid)
      case LfInterpretationError.ContractKeyNotFound(key) =>
        LedgerApiErrors.CommandExecution.Interpreter.LookupErrors.ContractKeyNotFound
          .Reject(renderedMessage, key)
      case _: LfInterpretationError.FailedAuthorization =>
        LedgerApiErrors.CommandExecution.Interpreter.AuthorizationError
          .Reject(renderedMessage)
      case e: LfInterpretationError.ContractNotActive =>
        LedgerApiErrors.CommandExecution.Interpreter.ContractNotActive
          .Reject(renderedMessage, e)
      case _: LfInterpretationError.LocalContractKeyNotVisible =>
        LedgerApiErrors.CommandExecution.Interpreter.GenericInterpretationError
          .Error(renderedMessage)
      case LfInterpretationError.DuplicateContractKey(key) =>
        LedgerApiErrors.ConsistencyErrors.DuplicateContractKey
          .RejectWithContractKeyArg(renderedMessage, key)
      case _: LfInterpretationError.UnhandledException =>
        LedgerApiErrors.CommandExecution.Interpreter.GenericInterpretationError.Error(
          renderedMessage + detailMessage.fold("")(x => ". Details: " + x)
        )
      case _: LfInterpretationError.UserError =>
        LedgerApiErrors.CommandExecution.Interpreter.GenericInterpretationError
          .Error(renderedMessage)
      case _: LfInterpretationError.TemplatePreconditionViolated =>
        LedgerApiErrors.CommandExecution.Interpreter.GenericInterpretationError
          .Error(renderedMessage)
      case _: LfInterpretationError.CreateEmptyContractKeyMaintainers =>
        LedgerApiErrors.CommandExecution.Interpreter.InvalidArgumentInterpretationError
          .Error(
            renderedMessage
          )
      case _: LfInterpretationError.FetchEmptyContractKeyMaintainers =>
        LedgerApiErrors.CommandExecution.Interpreter.InvalidArgumentInterpretationError
          .Error(
            renderedMessage
          )
      case _: LfInterpretationError.WronglyTypedContract =>
        LedgerApiErrors.CommandExecution.Interpreter.InvalidArgumentInterpretationError
          .Error(
            renderedMessage
          )
      case _: LfInterpretationError.ContractDoesNotImplementInterface =>
        LedgerApiErrors.CommandExecution.Interpreter.InvalidArgumentInterpretationError
          .Error(
            renderedMessage
          )
      case LfInterpretationError.NonComparableValues =>
        LedgerApiErrors.CommandExecution.Interpreter.InvalidArgumentInterpretationError
          .Error(
            renderedMessage
          )
      case _: LfInterpretationError.ContractIdInContractKey =>
        LedgerApiErrors.CommandExecution.Interpreter.InvalidArgumentInterpretationError
          .Error(
            renderedMessage
          )
      case LfInterpretationError.Limit(_) =>
        LedgerApiErrors.CommandExecution.Interpreter.InvalidArgumentInterpretationError
          .Error(
            renderedMessage
          )
      case _: LfInterpretationError.ContractIdComparability =>
        LedgerApiErrors.CommandExecution.Interpreter.InvalidArgumentInterpretationError
          .Error(
            renderedMessage
          )
      case _: LfInterpretationError.ChoiceGuardFailed =>
        LedgerApiErrors.CommandExecution.Interpreter.InvalidArgumentInterpretationError
          .Error(
            renderedMessage
          )
    }
  }

  private def processInterpretationError(
                                  err: LfError.Interpretation.Error,
                                  detailMessage: Option[String],
                                )(implicit errorLogger: ContextualizedErrorLogger): BaseError =
    err match {
      case Interpretation.Internal(location, message, _) =>
        LedgerApiErrors.InternalError.Interpretation(location, message, detailMessage)
      case m @ Interpretation.DamlException(error) =>
        processDamlException(error, m.message, detailMessage)
    }

  private def processLfError(error: LfError)(implicit errorLogger: ContextualizedErrorLogger): StatusRuntimeException = {
    val transformed = error match {
      case LfError.Package(packageError) => processPackageError(packageError)
      case LfError.Preprocessing(processingError) => processPreprocessingError(processingError)
      case LfError.Interpretation(interpretationError, detailMessage) =>
        processInterpretationError(interpretationError, detailMessage)
      case LfError.Validation(validationError) => processValidationError(validationError)
      case e
        if e.message.contains(
          "requires authorizers"
        ) => // Keeping this around as a string match as daml is not yet generating LfError.InterpreterErrors.Validation
        LedgerApiErrors.CommandExecution.Interpreter.AuthorizationError.Reject(e.message)
    }
    transformed.asGrpcErrorFromContext
  }
}


