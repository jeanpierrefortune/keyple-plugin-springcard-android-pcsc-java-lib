/*
 * Copyright (c) 2018-2018-2018 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */
package com.springcard.keyple.plugin.pcsclike.example

import android.app.Application
import androidx.multidex.MultiDex
import timber.log.Timber

class DemoApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    MultiDex.install(this)
    Timber.plant(Timber.DebugTree())
  }
}
