/*
 * Copyright (c)2022 SpringCard - www.springcard.com.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */
package com.springcard.keyple.plugin.android.pcsc

enum class AndroidPcscSupportContactlessProtocols constructor(val key: String, val value: Int) {
  ISO_A("ISO_A", 0x01),
  ISO_B("ISO_B", 0x02),
  ISO_B_PRIME("ISO_B_PRIME", 0x04),
  ALL("ALL", 0x01 or 0x02 or 0x04)
}
