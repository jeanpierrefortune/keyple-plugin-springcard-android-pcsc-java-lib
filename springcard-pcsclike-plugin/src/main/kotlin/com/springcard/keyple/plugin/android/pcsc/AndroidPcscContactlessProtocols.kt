/*
 * Copyright (c) 2018-2018-2018 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */
package com.springcard.keyple.plugin.android.pcsc

enum class AndroidPcscSupportContactlessProtocols constructor(val key: String, val value: Int) {
  NFC_A_BB("NFC_A", 0x01),
  NFC_B_BB("NFC_B", 0x02),
  NFC_INNO_BB("NFC_INNO", 0x04),
  NFC_ALL("NFC_ALL", 0x01 or 0x02 or 0x04);

  companion object {
    fun findEnumByKey(key: String): AndroidPcscSupportContactlessProtocols {
      for (value in values()) {
        if (value.key == key) {
          return value
        }
      }
      throw IllegalStateException("AndroidPcscSupportContactlessProtocols '$key' is not defined")
    }

    fun findEnumByValue(value: Int): AndroidPcscSupportContactlessProtocols {
      for (contractPriorityEnum in values()) {
        if (contractPriorityEnum.value == value) {
          return contractPriorityEnum
        }
      }
      throw IllegalStateException("AndroidPcscSupportContactlessProtocols is not defined")
    }
  }
}
