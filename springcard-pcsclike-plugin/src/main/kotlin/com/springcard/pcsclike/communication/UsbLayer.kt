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
package com.springcard.pcsclike.communication

import android.hardware.usb.UsbDevice
import com.springcard.pcsclike.SCardReaderList

internal class UsbLayer(scardReaderList: SCardReaderList, usbDevice: UsbDevice) :
    CommunicationLayer(scardReaderList) {

  override var lowLayer = UsbLowLevel(scardReaderList, usbDevice) as LowLevelLayer

  override fun wakeUp() {
    throw NotImplementedError("Error, wakeUp() method is not available in USB")
  }
}
