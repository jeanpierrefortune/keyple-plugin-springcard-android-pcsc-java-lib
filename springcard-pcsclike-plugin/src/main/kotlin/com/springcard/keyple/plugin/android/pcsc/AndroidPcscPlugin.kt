/* **************************************************************************************
 * Copyright (c) 2022 SpringCard - https://www.springcard.com/
 *
 * See the NOTICE file(s) distributed with this work for additional information
 * regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ************************************************************************************** */
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
