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

  /**
   * Starts the USB or BLE device scanning. The scan stops either at the first compatible device
   * found or after the time specified by timeout. In all cases, the results are returned
   * asynchronously to the caller who must provide an object implementing DeviceScannerSpi.
   * @param timeout The maximum scan time.
   * @param stopOnFirstDeviceDiscovered True to stop the scan as soon as a compatible device is
   * found.
   * @param deviceScannerSpi An object implementing the callback method.
   */
  fun scanDevices(
      timeout: Long,
      stopOnFirstDeviceDiscovered: Boolean,
      deviceScannerSpi: DeviceScannerSpi
  )

  /**
   * Connects to the device whose identifier is provided as a parameter.
   * @param identifier The identifier of the device to connect to.
   */
  fun connectToDevice(identifier: String)
}
