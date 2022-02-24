/*
 * Copyright (c)2022 SpringCard - www.springcard.com.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */
package com.springcard.keyple.plugin.android.pcsc.example.activity

/**
 * Bridges the gap between logic processing and the graphical interface to display various kinds of messages.
 */
internal interface EventNotifierSpi {
  fun notifyHeader(header: String)

  fun notifyAction(action: String)

  fun notifyResult(result: String)
}
