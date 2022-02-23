/*
 * Copyright (c)2022 SpringCard - www.springcard.com.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */
package com.springcard.keyple.plugin.android.pcsc

import org.eclipse.keyple.core.common.KeyplePluginExtensionFactory

interface AndroidPcscPluginFactory : KeyplePluginExtensionFactory {
  companion object Type {
    enum class Link {
      USB,
      BLE
    }
  }
}
