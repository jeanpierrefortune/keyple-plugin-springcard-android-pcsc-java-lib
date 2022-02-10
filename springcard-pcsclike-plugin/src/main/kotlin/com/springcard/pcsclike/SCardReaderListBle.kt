/* **************************************************************************************
 * Copyright (c) 2018-2019 SpringCard - https://www.springcard.com/
 *
 * See the NOTICE file(s) distributed with this work for additional information
 * regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ************************************************************************************** */
package com.springcard.pcsclike

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.springcard.keyple.plugin.BuildConfig
import com.springcard.pcsclike.ccid.CcidHandler
import com.springcard.pcsclike.ccid.CcidSecureParameters
import com.springcard.pcsclike.communication.BleLayer
import timber.log.Timber

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class SCardReaderListBle
internal constructor(layerDevice: BluetoothDevice, callbacks: SCardReaderListCallback) :
    SCardReaderList(layerDevice as Any, callbacks) {

  override fun create(ctx: Context) {
    Timber.i("PcscLikeLibrary, Lib rev = ${BuildConfig.LIBRARY_PACKAGE_NAME}")
    if (layerDevice is BluetoothDevice) {
      commLayer = BleLayer(this, layerDevice)
      commLayer.connect(ctx)
    }
  }

  override fun create(ctx: Context, secureConnexionParameters: CcidSecureParameters) {
    Timber.i("PcscLikeLibrary, Lib rev = ${BuildConfig.LIBRARY_PACKAGE_NAME}")
    if (layerDevice is BluetoothDevice) {
      commLayer = BleLayer(this, layerDevice)
      ccidHandler = CcidHandler(this, secureConnexionParameters)
      commLayer.connect(ctx)
    }
  }
}
