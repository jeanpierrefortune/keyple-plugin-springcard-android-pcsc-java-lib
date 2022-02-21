/*
 * Copyright (c) 2018-2018-2018 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */
package com.springcard.keyple.plugin.android.pcsc

import com.springcard.keyple.plugin.android.pcsc.spi.DeviceScannerSpi
import org.eclipse.keyple.core.common.KeyplePluginExtension

interface AndroidPcscPlugin : KeyplePluginExtension {

  companion object {
    const val PLUGIN_NAME = "AndroidPcscPlugin"
  }

  fun scanDevices(
      timeout: Long,
      stopOnFirstDeviceDiscovered: Boolean,
      deviceScannerSpi: DeviceScannerSpi
  )

  fun connectToDevice(identifier: String)
}
