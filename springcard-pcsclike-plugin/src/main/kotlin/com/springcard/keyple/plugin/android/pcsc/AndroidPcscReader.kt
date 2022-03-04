/*
 * Copyright (c)2022 SpringCard - www.springcard.com.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */
package com.springcard.keyple.plugin.android.pcsc

import org.eclipse.keyple.core.common.KeypleReaderExtension

/**
 * Android Pcsc reader
 *
 * @since 1.0.0
 */
interface AndroidPcscReader : KeypleReaderExtension {

  /**
   * Set the type of reader.
   * @param contactless True if the reader is contactless type.
   * @since 1.0.0
   */
  fun setContactless(contactless: Boolean): AndroidPcscReader
}
