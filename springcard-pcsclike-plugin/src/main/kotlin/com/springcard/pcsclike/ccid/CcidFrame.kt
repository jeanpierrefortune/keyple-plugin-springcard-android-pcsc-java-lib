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
package com.springcard.pcsclike.ccid

import com.springcard.pcsclike.utils.bytes
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.experimental.and
import kotlin.experimental.inv
import kotlin.experimental.or
import timber.log.Timber

internal abstract class CcidFrame {

  /* Array containing the whole data */
  /* initialize with header (10 first bytes) to zero */
  var raw = mutableListOf<Byte>(0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
    internal set

  /* Two parts of the frame */

  val header: ByteArray
    get() {
      return raw.slice(0 until HEADER_SIZE).toByteArray()
    }

  var payload: ByteArray
    get() {
      return raw.slice(HEADER_SIZE until raw.size).toByteArray()
    }
    protected set(newPayload) {
      // keep old header and append new payload
      val headerSaved = header.toList()
      raw = mutableListOf<Byte>()
      raw.addAll(headerSaved)
      raw.addAll(newPayload.toList())

      // Update length
      length = newPayload.size
    }

  /* Different fields of the header and the payload */

  var code: Byte
    get() {
      return raw[0]
    }
    protected set(value) {
      raw[0] = value
    }

  var ciphered: Boolean
    get() {
      return raw[4] == CIPHERED_BIT
    }
    internal set(value) {
      if (value) raw[4] = raw[4] or CIPHERED_BIT else raw[4] = raw[4] and CIPHERED_BIT.inv()
    }

  var length: Int
    get() {
      val lengthArray = raw.slice(1..4).toByteArray()
      lengthArray[3] = lengthArray[3] and CIPHERED_BIT.inv()
      val buffer = ByteBuffer.wrap(lengthArray)
      buffer.order(ByteOrder.LITTLE_ENDIAN)

      val expectedSize = buffer.int

      if (expectedSize != payload.size) {
        Timber.w(
            "Warning, Size specified ($expectedSize) does not match size of payload (${payload.size})")
        return HEADER_SIZE
      }
      return expectedSize
    }
    set(value) {
      val lengthArray = value.bytes()
      raw[1] = lengthArray[0]
      raw[2] = lengthArray[1]
      raw[3] = lengthArray[2]
      raw[4] = (raw[4] and CIPHERED_BIT) or lengthArray[3]
    }

  var slotNumber: Byte
    get() {
      return raw[5]
    }
    protected set(value) {
      raw[5] = value
    }

  var sequenceNumber: Byte
    get() {
      return raw[6]
    }
    internal set(value) {
      raw[6] = value
    }

  companion object {
    const val CIPHERED_BIT: Byte = 0x80.toByte()
    const val HEADER_SIZE = 10
  }
}

internal class CcidCommand(cmdCode: CommandCode, slotNb: Byte, data: ByteArray) : CcidFrame() {

  enum class CommandCode(var value: Byte) {
    PC_To_RDR_IccPowerOn(0x62.toByte()),
    PC_To_RDR_IccPowerOff(0x63.toByte()),
    PC_To_RDR_GetSlotStatus(0x65.toByte()),
    PC_To_RDR_Escape(0x6B.toByte()),
    PC_To_RDR_XfrBlock(0x6F.toByte()),
  }

  init {
    code = cmdCode.value
    slotNumber = slotNb
    payload = data
    sequenceNumber = 0
  }

  val parameters: ByteArray
    get() {
      return raw.slice(7 until HEADER_SIZE).toByteArray()
    }
}

internal class CcidResponse(rawFrame: ByteArray) : CcidFrame() {

  enum class ResponseCode(var value: Byte) {
    RDR_To_PC_DataBlock(0x80.toByte()),
    RDR_To_PC_SlotStatus(0x81.toByte()),
    RDR_To_PC_Escape(0x83.toByte())
  }

  init {
    raw = rawFrame.toMutableList()
    if (raw.size == 0) {
      raw = mutableListOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
    }
  }

  val slotStatus: Byte
    get() {
      return raw[7]
    }

  val slotError: Byte
    get() {
      return raw[8]
    }
}
