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

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.calypsonet.keyple.plugin.bluebird.reader.BluebirdReader
import org.eclipse.keyple.core.plugin.spi.ObservablePluginSpi
import org.eclipse.keyple.core.plugin.spi.reader.ReaderSpi

/**
 * Handle native Readers mapped for Keyple
 */
internal class BluebirdPluginAdapter : BluebirdPlugin, ObservablePluginSpi {

    companion object {
        private const val MONITORING_CYCLE_DURATION_MS = 1000
    }

    private lateinit var seReaders: ConcurrentHashMap<String, ReaderSpi>

    @ExperimentalCoroutinesApi
    override fun searchAvailableReaders(): MutableSet<ReaderSpi> {

        seReaders = ConcurrentHashMap()

        val sam = BluebirdContactReaderAdapter()
        seReaders[sam.name] = sam

        val nfc = BluebirdContactlessReaderAdapter()
        seReaders[nfc.name] = nfc

        return seReaders.map {
            it.value
        }.toMutableSet()
    }

    override fun searchReader(readerName: String?): ReaderSpi? {
        return if (seReaders.containsKey(readerName)) {
            seReaders[readerName]!!
        } else {
            null
        }
    }

    override fun searchAvailableReaderNames(): MutableSet<String> {
        return seReaders.map {
            it.key
        }.toMutableSet()
    }

    override fun getMonitoringCycleDuration(): Int {
        return MONITORING_CYCLE_DURATION_MS
    }

    override fun getName(): String = BluebirdPlugin.PLUGIN_NAME

    override fun onUnregister() {
        BluebirdReader.clearInstance()
    }
}
