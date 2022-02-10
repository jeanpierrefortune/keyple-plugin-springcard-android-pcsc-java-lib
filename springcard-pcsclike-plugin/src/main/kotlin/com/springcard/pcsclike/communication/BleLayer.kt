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

import android.bluetooth.BluetoothDevice
import android.os.Build
import androidx.annotation.RequiresApi
import com.springcard.pcsclike.SCardReaderList

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
internal class BleLayer(scardReaderList: SCardReaderList, bluetoothDevice: BluetoothDevice) :
    CommunicationLayer(scardReaderList) {

  override var lowLayer = BleLowLevel(scardReaderList, bluetoothDevice) as LowLevelLayer

  override fun wakeUp() {
    scardReaderList.enterExclusive()
    scardReaderList.machineState.setNewState(State.WakingUp)
    /* Subscribe to Service changed to wake-up device */
    (lowLayer as BleLowLevel).enableNotifOnCcidStatus()
  }
}
