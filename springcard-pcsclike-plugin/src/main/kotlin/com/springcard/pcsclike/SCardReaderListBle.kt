/*
 * Copyright (c) 2018-2018-2018 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */
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
