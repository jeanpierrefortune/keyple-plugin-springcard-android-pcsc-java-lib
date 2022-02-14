/*
 * Copyright (c) 2018-2018-2018 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */
package com.springcard.keyple.plugin.android.pcsc

import android.content.Context

object AndroidPcscPluginFactoryProvider {
  fun getFactory(context: Context): AndroidPcscPluginFactory {
    return AndroidPcscPluginFactoryAdapter(context)
  }
}
