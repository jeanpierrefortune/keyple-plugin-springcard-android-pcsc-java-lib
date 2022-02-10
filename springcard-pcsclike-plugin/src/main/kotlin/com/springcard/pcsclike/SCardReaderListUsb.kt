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

import android.content.Context
import android.hardware.usb.UsbDevice
import com.springcard.keyple.plugin.BuildConfig
import com.springcard.pcsclike.ccid.CcidSecureParameters
import com.springcard.pcsclike.communication.UsbLayer
import timber.log.Timber

class SCardReaderListUsb
internal constructor(layerDevice: UsbDevice, callbacks: SCardReaderListCallback) :
    SCardReaderList(layerDevice as Any, callbacks) {
  private val TAG: String
    get() = this::class.java.simpleName

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
