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

import com.springcard.pcsclike.utils.RotateLeftOneByte
import com.springcard.pcsclike.utils.RotateRightOneByte
import com.springcard.pcsclike.utils.XOR
import com.springcard.pcsclike.utils.toHexString
import java.lang.Exception
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.xor
import kotlin.random.Random
import timber.log.Timber

internal class CcidSecure(private val secureConnectionParameters: CcidSecureParameters) {

  private val protocolCode: Byte = 0x00
  private val useRandom = true

  private lateinit var sessionEncKey: MutableList<Byte>
  private lateinit var sessionMacKey: MutableList<Byte>
  private lateinit var sessionSendIV: MutableList<Byte>
  private lateinit var sessionRecvIV: MutableList<Byte>

  enum class ProtocolOpcode(val value: Byte) {
    Success(0x00),
    Authenticate(0x0A),
    Following(0xFF.toByte())
  }

  fun decryptCcidBuffer(frame: CcidResponse): CcidResponse {

    /* Extract the CMAC */
    val receivedCmac = frame.raw.takeLast(8)
    frame.raw = frame.raw.dropLast(8).toMutableList()

    /* Extract the data */
    var data = frame.raw.drop(10).toMutableList()

    Timber.d("   >     (crypted data) ${data.toHexString()}")

    /* Decipher the data */
    data = aesCbcDecrypt(sessionEncKey, sessionRecvIV, data)

    Timber.d("   >      (padded data) ${data.toHexString()}")

    var dataLen = data.size
    while (dataLen > 0 && data[dataLen - 1] == 0x00.toByte()) dataLen--

    if (dataLen == 0 || data[dataLen - 1] != 0x80.toByte()) {
      val msg = "Padding is invalid (decryption failed/wrong session key?)"
      Timber.e(msg)
      throw Exception(msg)
    }
    dataLen -= 1
    data = data.take(dataLen).toMutableList()

    Timber.d("   >       (plain data) ${data.toHexString()}")

    /* Extract the header and re-create a valid buffer */
    frame.raw = frame.raw.take(10).toMutableList()
    frame.length = data.size
    frame.raw.addAll(data)

    /* Compute the CMAC */
    val computedCmac = computeCmac(sessionMacKey, sessionRecvIV, frame.raw)

    Timber.d("   >${frame.raw.toHexString()} -> CMAC=${computedCmac.take(8).toHexString()}")

    if (receivedCmac != computedCmac.take(8)) {
      val msg = "CMAC is invalid (wrong session key?)"
      Timber.e(msg)
      throw Exception(msg)
    }

    sessionRecvIV = computedCmac

    return frame
  }

  fun encryptCcidBuffer(frame: CcidCommand): CcidCommand {

    /* Compute the CMAC of the plain buffer */
    val cmac = computeCmac(sessionMacKey, sessionSendIV, frame.raw)

    Timber.d("   <${frame.raw.toHexString()} -> CMAC=${cmac.take(8).toMutableList().toHexString()}")

    /* Extract the data */
    var data = frame.raw.drop(10).toMutableList()

    Timber.d("   <       (plain data) ${data.toHexString()}")

    /* Cipher the data */
    data.add(0x80.toByte())
    while (data.size % 16 != 0) {
      data.add(0x00)
    }

    Timber.d("   <      (padded data) ${data.toHexString()}")

    data = aesCbcEncrypt(sessionEncKey, sessionSendIV, data)

    Timber.d("   <     (crypted data) ${data.toHexString()}")

    /* Update the length */
    frame.length = data.size + 8
    frame.ciphered = true

    /* Re-create the buffer */
    frame.raw = frame.raw.take(10).toMutableList()
    frame.raw.addAll(data)
    frame.raw.addAll(cmac.take(8))

    sessionSendIV = cmac

    return frame
  }

  private fun getRandom(length: Int): MutableList<Byte> {
    var result = mutableListOf<Byte>()
    if (!useRandom) {
      for (i in 0 until length) {
        result.add((0xA0 or (i and 0x0F)).toByte())
      }
    } else {
      result = Random.nextBytes(length).toMutableList()
    }
    return result
  }

  private fun computeCmac(
      key: MutableList<Byte>,
      iv: MutableList<Byte>,
      buffer: MutableList<Byte>
  ): MutableList<Byte> {
    var cmac: MutableList<Byte>
    var actualLength: Int = buffer.size + 1

    cmac = iv

    Timber.d("Compute CMAC")

    while ((actualLength % 16) != 0) actualLength++

    for (i in 0 until actualLength step 16) {
      val block = mutableListOf<Byte>()

      for (j in 0 until 16) {
        when {
          (i + j) < buffer.size -> block.add(buffer[i + j])
          (i + j) == buffer.size -> block.add(0x80.toByte())
          else -> block.add(0x00)
        }
      }

      Timber.d("\tBlock=${block.toHexString()}, IV=${cmac.toHexString()}, key=${key.toHexString()}")

      for (j in 0 until 16) block[j] = block[j].xor(cmac[j])

      Timber.d("\tBlock XOR=${block.toHexString()}")

      cmac = aesEcbEncrypt(key, block)

      Timber.d("\t\t-> ${cmac.toHexString()}")
    }

    return cmac
  }

  private fun aesCbcEncrypt(
      key: MutableList<Byte>,
      iv: MutableList<Byte>,
      buffer: MutableList<Byte>
  ): MutableList<Byte> {
    val cipher = Cipher.getInstance("AES/CBC/NoPadding")
    val keyCipher = SecretKeySpec(key.toByteArray(), 0, key.size, "AES")
    val initVector = IvParameterSpec(iv.toByteArray())
    cipher.init(Cipher.ENCRYPT_MODE, keyCipher, initVector)
    return cipher.doFinal(buffer.toByteArray()).toMutableList()
  }

  private fun aesCbcDecrypt(
      key: MutableList<Byte>,
      iv: MutableList<Byte>,
      buffer: MutableList<Byte>
  ): MutableList<Byte> {
    val cipher = Cipher.getInstance("AES/CBC/NoPadding")
    val keyCipher = SecretKeySpec(key.toByteArray(), 0, key.size, "AES")
    val initVector = IvParameterSpec(iv.toByteArray())
    cipher.init(Cipher.DECRYPT_MODE, keyCipher, initVector)
    return cipher.doFinal(buffer.toByteArray()).toMutableList()
  }

  private fun aesEcbEncrypt(key: MutableList<Byte>, buffer: MutableList<Byte>): MutableList<Byte> {
    val cipher = Cipher.getInstance("AES/ECB/NoPadding")
    val keyCipher = SecretKeySpec(key.toByteArray(), 0, key.size, "AES")
    cipher.init(Cipher.ENCRYPT_MODE, keyCipher)
    return cipher.doFinal(buffer.toByteArray()).toMutableList()
  }

  private fun aesEcbDecrypt(key: MutableList<Byte>, buffer: MutableList<Byte>): MutableList<Byte> {
    val cipher = Cipher.getInstance("AES/ECB/NoPadding")
    val keyCipher = SecretKeySpec(key.toByteArray(), 0, key.size, "AES")
    cipher.init(Cipher.DECRYPT_MODE, keyCipher)
    return cipher.doFinal(buffer.toByteArray()).toMutableList()
  }

  private fun cleanupAuthentication() {
    sessionEncKey = mutableListOf()
    sessionMacKey = mutableListOf()
    sessionSendIV = mutableListOf()
    sessionRecvIV = mutableListOf()
  }

  private var rndA = mutableListOf<Byte>()
  private var rndB = mutableListOf<Byte>()

  fun hostAuthCmd(): ByteArray {
    cleanupAuthentication()

    val keySelect = secureConnectionParameters.keyIndex.value
    val keyValue = secureConnectionParameters.keyValue

    Timber.d("Running AES mutual authentication using key 0x${String.format("%02X", keySelect)}")

    /* Generate host nonce */
    rndA = getRandom(16)

    Timber.d("key=${keyValue.toHexString()}")
    Timber.d("rndA=${rndA.toHexString()}")

    /* Host->Device AUTHENTICATE command */
    /* --------------------------------- */

    val cmdAuthenticate = mutableListOf<Byte>()

    cmdAuthenticate.add(protocolCode)
    cmdAuthenticate.add(ProtocolOpcode.Authenticate.value)
    cmdAuthenticate.add(0x01) /* Version & mode = AES128 */
    cmdAuthenticate.add(keySelect)

    Timber.d("   <                    ${cmdAuthenticate.toHexString()}")

    return cmdAuthenticate.toByteArray()
  }

  fun deviceRespStep1(response: ByteArray): Boolean {

    val rspStep1 = response.toMutableList()

    Timber.d("   >                    ${rspStep1.toHexString()}")

    /* Device->Host Authentication Step 1 */
    /* ---------------------------------- */

    if (rspStep1.size < 1) {
      Timber.d("Authentication failed at step 1 (response is too short)")
      return false
    }

    if (rspStep1[0] != ProtocolOpcode.Following.value) {
      Timber.d(
          "Authentication failed at step 1 (the device has reported an error: 0x${String.format("%02X", rspStep1[0])})")
      return false
    }

    if (rspStep1.size != 17) {
      Timber.d("Authentication failed at step 1 (response does not have the expected format)")
      return false
    }

    return true
  }

  fun hostCmdStep2(rspStep1: MutableList<Byte>): ByteArray {
    val t = rspStep1.slice(1..16).toMutableList()
    rndB = aesEcbDecrypt(secureConnectionParameters.keyValue, t)

    Timber.d("rndB=${rndB.toHexString()}")

    /* Host->Device Authentication Step 2 */
    /* ---------------------------------- */

    val cmdStep2 = mutableListOf<Byte>()

    cmdStep2.add(protocolCode)
    cmdStep2.add(ProtocolOpcode.Following.value)
    cmdStep2.addAll(aesEcbEncrypt(secureConnectionParameters.keyValue, rndA))
    cmdStep2.addAll(
        aesEcbEncrypt(
            secureConnectionParameters.keyValue,
            rndB.toByteArray().RotateLeftOneByte().toMutableList()))

    Timber.d("   <                    ${cmdStep2.toHexString()}")

    return cmdStep2.toByteArray()
  }

  fun deviceRespStep3(response: ByteArray): Boolean {

    val rspStep3 = response.toMutableList()

    Timber.d("   >                    ${rspStep3.toHexString()}")

    /* Device->Host Authentication Step 3 */
    /* ---------------------------------- */

    if (rspStep3.size < 1) {
      Timber.d("Authentication failed at step 3")
      return false
    }

    if (rspStep3[0] != ProtocolOpcode.Success.value) {
      Timber.d(
          "Authentication failed at step 3 (the device has reported an error: 0x${String.format("%02X", rspStep3[0])})")
      return false
    }

    if (rspStep3.size != 17) {
      Timber.d("Authentication failed at step 3 (response does not have the expected format)")
      return false
    }

    var t = rspStep3.slice(1..16).toMutableList()
    t = aesEcbDecrypt(secureConnectionParameters.keyValue, t)
    t = t.toByteArray().RotateRightOneByte().toMutableList()

    if (t != rndA) {
      Timber.d("${t.toHexString()}!=${rndA.toHexString()}")
      Timber.d("Authentication failed at step 3 (device's cryptogram is invalid)")
      return false
    }

    /* Session keys and first init vector */
    /* ---------------------------------- */

    val sv1 = mutableListOf<Byte>()
    sv1.addAll(0, rndA.slice(0..3))
    sv1.addAll(4, rndB.slice(0..3))
    sv1.addAll(8, rndA.slice(8..11))
    sv1.addAll(12, rndB.slice(8..11))

    Timber.d("SV1=${sv1.toHexString()}")

    val sv2 = mutableListOf<Byte>()
    sv2.addAll(0, rndA.slice(4..7))
    sv2.addAll(4, rndB.slice(4..7))
    sv2.addAll(8, rndA.slice(12..15))
    sv2.addAll(12, rndB.slice(12..15))

    Timber.d("SV2=${sv2.toHexString()}")

    sessionEncKey = aesEcbEncrypt(secureConnectionParameters.keyValue, sv1)

    Timber.d("Kenc=${sessionEncKey.toHexString()}")

    sessionMacKey = aesEcbEncrypt(secureConnectionParameters.keyValue, sv2)

    Timber.d("Kmac=${sessionMacKey.toHexString()}")

    t = XOR(rndA, rndB)
    t = aesEcbEncrypt(sessionMacKey, t)

    Timber.d("IV0=${t.toHexString()}")

    sessionSendIV = t
    sessionRecvIV = t

    return true
  }
}
