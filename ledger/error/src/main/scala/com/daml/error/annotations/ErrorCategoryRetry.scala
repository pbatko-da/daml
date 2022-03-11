package com.daml.error.annotations

import scala.concurrent.duration.Duration

/** Default retryability information
 *
 * Every error category has a default retryability classification.
 * An error code may adjust the retry duration.
 */
case class ErrorCategoryRetry(duration: Duration)
