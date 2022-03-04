/*
 * Copyright (c) 2018-2018-2018 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */
package com.springcard.keyple.plugin.android.pcsc

import android.bluetooth.BluetoothAdapter
import com.springcard.keyple.plugin.android.pcsc.spi.BleDeviceScannerSpi
import org.eclipse.keyple.core.common.KeyplePluginExtension

interface AndroidPcscPlugin : KeyplePluginExtension {

  companion object {
    const val PLUGIN_NAME = "AndroidPcscPlugin"
    enum class Link {
      USB,
      BLE
    }
  }

  fun scanBleReaders(
      bluetoothAdapter: BluetoothAdapter,
      timeout: Long,
      stopOnFirstDeviceDiscovered: Boolean,
      bleDeviceScannerSpi: BleDeviceScannerSpi
  )

  fun connectToBleDevice(address: String)
}
