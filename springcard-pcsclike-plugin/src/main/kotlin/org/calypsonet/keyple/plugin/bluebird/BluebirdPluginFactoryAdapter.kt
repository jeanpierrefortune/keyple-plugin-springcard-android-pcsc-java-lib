/* **************************************************************************************
 * Copyright (c) 2021 Calypso Networks Association https://calypsonet.org/
 *
 * See the NOTICE file(s) distributed with this work for additional information
 * regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ************************************************************************************** */
package org.calypsonet.keyple.plugin.bluebird

import android.app.Activity
import org.calypsonet.keyple.plugin.bluebird.reader.BluebirdReader
import org.eclipse.keyple.core.common.CommonApiProperties
import org.eclipse.keyple.core.plugin.PluginApiProperties
import org.eclipse.keyple.core.plugin.ReaderIOException
import org.eclipse.keyple.core.plugin.spi.PluginFactorySpi
import org.eclipse.keyple.core.plugin.spi.PluginSpi

internal class BluebirdPluginFactoryAdapter internal constructor() : BluebirdPluginFactory,
    PluginFactorySpi {

    @Throws(ReaderIOException::class)
    suspend fun init(activity: Activity): BluebirdPluginFactoryAdapter {
        val started = BluebirdReader.init(activity)
        return if (started == true) {
            this
        } else {
            throw ReaderIOException("Could not init Bluebird Adapter")
        }
    }

    override fun getPluginName(): String = BluebirdPlugin.PLUGIN_NAME

    override fun getPlugin(): PluginSpi = BluebirdPluginAdapter()

    override fun getCommonApiVersion(): String = CommonApiProperties.VERSION

    override fun getPluginApiVersion(): String = PluginApiProperties.VERSION
}
