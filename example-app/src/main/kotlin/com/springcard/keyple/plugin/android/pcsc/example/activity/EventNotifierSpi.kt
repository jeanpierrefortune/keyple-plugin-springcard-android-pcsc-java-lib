/*
 * Copyright (c)2022 SpringCard - www.springcard.com.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */
package com.springcard.keyple.plugin.android.pcsc.example.activity

interface EventNotifierSpi {
  fun notifyHeader(header: String)

  fun notifyAction(action: String)

  fun notifyResult(result: String)
}
