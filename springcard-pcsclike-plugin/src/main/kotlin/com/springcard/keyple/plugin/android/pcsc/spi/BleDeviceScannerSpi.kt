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
package com.springcard.keyple.plugin.android.pcsc.spi

import com.springcard.keyple.plugin.android.pcsc.BleDeviceInfo

interface BleDeviceScannerSpi {
  fun onDeviceDiscovered(bluetoothDeviceInfoList: MutableCollection<BleDeviceInfo>)
}
