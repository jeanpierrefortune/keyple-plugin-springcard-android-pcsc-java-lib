/*
 * Copyright (c) 2018-2018-2018 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */
package com.springcard.pcsclike

import android.content.Context
import android.hardware.usb.UsbDevice
import com.springcard.keyple.plugin.BuildConfig
import com.springcard.pcsclike.ccid.CcidSecureParameters
import com.springcard.pcsclike.communication.UsbLayer
import timber.log.Timber

class SCardReaderListUsb
internal constructor(layerDevice: UsbDevice, callbacks: SCardReaderListCallback) :
    SCardReaderList(layerDevice as Any, callbacks) {

  override fun create(ctx: Context) {
    Timber.i("Lib rev = ${BuildConfig.LIBRARY_PACKAGE_NAME}")
    if (layerDevice is UsbDevice) {
      commLayer = UsbLayer(this, layerDevice)
      commLayer.connect(ctx)
    }
  }

  override fun create(ctx: Context, secureConnexionParameters: CcidSecureParameters) {
    Timber.i("Lib rev = ${BuildConfig.LIBRARY_PACKAGE_NAME}")
    throw NotImplementedError(
        "Cannot create SCardReaderListUsb with secure parameters for the moment")
  }
}
