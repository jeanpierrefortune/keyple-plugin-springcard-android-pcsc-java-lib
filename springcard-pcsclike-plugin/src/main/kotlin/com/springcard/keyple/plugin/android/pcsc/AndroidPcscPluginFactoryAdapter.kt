/*
 * Copyright (c)2022 SpringCard - www.springcard.com.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */
package com.springcard.keyple.plugin.android.pcsc

import android.content.Context
import org.eclipse.keyple.core.common.CommonApiProperties
import org.eclipse.keyple.core.plugin.PluginApiProperties
import org.eclipse.keyple.core.plugin.spi.PluginFactorySpi
import org.eclipse.keyple.core.plugin.spi.PluginSpi

/** Implementation of the AndroidPcscPluginFactory */
internal class AndroidPcscPluginFactoryAdapter
internal constructor(val type: AndroidPcscPluginFactory.Type.Link, val context: Context) :
    AndroidPcscPluginFactory, PluginFactorySpi {

  val name = "${AndroidPcscPlugin.PLUGIN_NAME}_${type.name}"

  override fun getPluginName():String {
      return name
  }

  override fun getPlugin(): PluginSpi =
      when (type) {
        AndroidPcscPluginFactory.Type.Link.BLE -> AndroidBlePcscPluginAdapter(name, context)
        AndroidPcscPluginFactory.Type.Link.USB -> AndroidUsbPcscPluginAdapter(name, context)
      }

  override fun getCommonApiVersion(): String = CommonApiProperties.VERSION

  override fun getPluginApiVersion(): String = PluginApiProperties.VERSION
}
