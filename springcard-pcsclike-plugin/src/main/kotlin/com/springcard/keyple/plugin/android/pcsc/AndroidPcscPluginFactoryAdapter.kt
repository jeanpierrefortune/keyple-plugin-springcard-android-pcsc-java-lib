/*
 * Copyright (c) 2018-2018-2018 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */
package com.springcard.keyple.plugin.android.pcsc

import android.content.Context
import org.eclipse.keyple.core.common.CommonApiProperties
import org.eclipse.keyple.core.plugin.PluginApiProperties
import org.eclipse.keyple.core.plugin.spi.PluginFactorySpi
import org.eclipse.keyple.core.plugin.spi.PluginSpi

internal class AndroidPcscPluginFactoryAdapter internal constructor(var context: Context) :
    AndroidPcscPluginFactory, PluginFactorySpi {

  //    @Throws(ReaderIOException::class)
  //    suspend fun init(activity: Activity): AndroidPcscPluginFactoryAdapter {
  //        // TODO init reader:
  //        /* val started = AndroidPcscReader.init(activity)
  //        return if (started == true) {
  //            this
  //        } else {
  //            throw ReaderIOException("Could not init Bluebird Adapter")
  //        }
  //        */
  //        return this
  //    }

  override fun getPluginName(): String = AndroidPcscPlugin.PLUGIN_NAME

  override fun getPlugin(): PluginSpi = AndroidPcscPluginAdapter(context)

  override fun getCommonApiVersion(): String = CommonApiProperties.VERSION

  override fun getPluginApiVersion(): String = PluginApiProperties.VERSION
}
