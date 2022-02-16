/*
 * Copyright (c) 2018-2018-2018 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */
package com.springcard.keyple.plugin.android.pcsc.example.util

import android.content.Context
import android.os.Build

fun Context.getColorResource(id: Int): Int {
  return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
    resources.getColor(id, null)
  } else {
    resources.getColor(id)
  }
}
