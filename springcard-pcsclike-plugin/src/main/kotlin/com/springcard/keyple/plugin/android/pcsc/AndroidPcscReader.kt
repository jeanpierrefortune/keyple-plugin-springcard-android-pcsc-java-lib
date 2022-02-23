/*
 * Copyright (c)2022 SpringCard - www.springcard.com.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */
package com.springcard.keyple.plugin.android.pcsc

import org.eclipse.keyple.core.common.KeypleReaderExtension

interface AndroidPcscReader : KeypleReaderExtension {

  companion object {
    const val READER_NAME = "AndroidPcscReader"
    const val WAIT_RESPONSE_TIMEOUT: Long = 5000
    const val WAIT_CARD_CONNECT_TIMEOUT: Long = 5000
  }
}
