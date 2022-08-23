package com.daml.platform.partymanagement

import com.daml.api.util.TimeProvider

trait EpochMicrosecondsMethodMixin {

  protected val timeProvider: TimeProvider

  protected def epochMicroseconds(): Long = {
    val now = timeProvider.getCurrentTime
    (now.getEpochSecond * 1000 * 1000) + (now.getNano / 1000)
  }

}
