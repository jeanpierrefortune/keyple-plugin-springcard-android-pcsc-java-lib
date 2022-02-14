/*
 * Copyright (c) 2018-2018-2018 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */
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
