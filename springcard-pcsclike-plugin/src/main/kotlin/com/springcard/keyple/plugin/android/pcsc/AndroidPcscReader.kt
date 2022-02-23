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
  }

  fun setContactless(contactless: Boolean): AndroidPcscReader
}
