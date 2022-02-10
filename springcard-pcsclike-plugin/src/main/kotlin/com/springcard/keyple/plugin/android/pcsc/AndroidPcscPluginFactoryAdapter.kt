/* **************************************************************************************
 * Copyright (c) 2022 SpringCard - https://www.springcard.com/
 *
 * See the NOTICE file(s) distributed with this work for additional information
 * regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ************************************************************************************** */
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
