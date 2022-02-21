/*
 * Copyright (c) 2018-2018-2018 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */
package com.springcard.keyple.plugin.android.pcsc.spi

import com.springcard.keyple.plugin.android.pcsc.DeviceInfo

interface DeviceScannerSpi {
  fun onDeviceDiscovered(bluetoothDeviceInfoList: MutableCollection<DeviceInfo>)
}
