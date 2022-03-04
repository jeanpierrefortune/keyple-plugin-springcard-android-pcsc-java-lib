/*
 * Copyright (c)2022 SpringCard - www.springcard.com.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */
package com.springcard.keyple.plugin.android.pcsc

import org.eclipse.keyple.core.common.KeyplePluginExtensionFactory

/**
 * Factory of Android Pcsc plugin.
 *
 * @since 1.0.0
 */
interface AndroidPcscPluginFactory : KeyplePluginExtensionFactory {
  enum class DeviceType {
    USB,
    BLE
  }
}
