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

import com.springcard.pcsclike.communication.State

/**
 * Represents a channel You can get this object with a call to reader.cardConnect()
 *
 * @property parent Points to an [SCardReader] object
 * @property atr Card’s ATR
 */
class SCardChannel internal constructor(val parent: SCardReader) {

  var atr: ByteArray = ByteArray(0)
    internal set

  /**
   * Transmit a C-APDU to the card, receive the R-APDU in response (in the callback)
   * @param command The C-APDU to send to the card
   *
   * @throws Exception if the device is sleeping, there is a command already processing, the slot
   * number exceed 255
   */
  fun transmit(command: ByteArray) {

    if (parent.parent.isSleeping) {
      parent.parent.postCallback {
        parent.parent.callbacks.onReaderListError(
            parent.parent, SCardError(SCardError.ErrorCodes.BUSY, "Error: Device is sleeping"))
      }
      return
    }

    /* Update to new state and lock machine state if necessary */
    parent.parent.enterExclusive()
    parent.parent.machineState.setNewState(State.WritingCmdAndWaitingResp)
    /* Build the frame */
    val ccidCmd = parent.parent.ccidHandler.scardTransmit(parent.index.toByte(), command)

    /* Send the frame */
    parent.parent.commLayer.writeCommand(ccidCmd)

    /* NB: The lock will be exited when the response will arrive */
  }

  /**
   * Disconnect from the card (close the communication channel + power down)
   *
   * @throws Exception if the device is sleeping, there is a command already processing, the slot
   * number exceed 255
   */
  fun disconnect() {

    if (parent.parent.isSleeping) {
      parent.parent.postCallback {
        parent.parent.callbacks.onReaderListError(
            parent.parent, SCardError(SCardError.ErrorCodes.BUSY, "Error: Device is sleeping"))
      }
      return
    }

    parent.parent.enterExclusive()
    parent.parent.machineState.setNewState(State.WritingCmdAndWaitingResp)
    val ccidCmd = parent.parent.ccidHandler.scardDisconnect(parent.index.toByte())
    parent.parent.commLayer.writeCommand(ccidCmd)
  }

  /**
   * Counterpart to PC/SC’s SCardReconnect, same as [SCardReader.cardConnect]
   *
   * @throws Exception the device is sleeping, there is a command already processing, the slot
   * number exceed 255
   */
  fun reconnect() {
    parent.cardConnect()
  }
}
