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

internal class AndroidPcscPluginFactoryAdapter
internal constructor(val type: AndroidPcscPluginFactory.Type.Link, val context: Context) :
    AndroidPcscPluginFactory, PluginFactorySpi {

  override fun getPluginName(): String = AndroidPcscPlugin.PLUGIN_NAME

  override fun getPlugin(): PluginSpi =
      when (type) {
        AndroidPcscPluginFactory.Type.Link.BLE -> AndroidBlePcscPluginAdapter(context)
        AndroidPcscPluginFactory.Type.Link.USB -> AndroidUsbPcscPluginAdapter(context)
      }

  override fun getCommonApiVersion(): String = CommonApiProperties.VERSION

  override fun getPluginApiVersion(): String = PluginApiProperties.VERSION
}
