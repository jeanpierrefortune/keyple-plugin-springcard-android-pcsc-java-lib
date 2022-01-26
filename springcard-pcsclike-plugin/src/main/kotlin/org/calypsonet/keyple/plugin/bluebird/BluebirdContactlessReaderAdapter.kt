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

import java.util.concurrent.atomic.AtomicBoolean
import org.calypsonet.keyple.plugin.bluebird.reader.BluebirdReader
import org.calypsonet.keyple.plugin.bluebird.reader.NfcResultError
import org.calypsonet.keyple.plugin.bluebird.reader.NfcResultSuccess
import org.calypsonet.keyple.plugin.bluebird.reader.Tag
import org.eclipse.keyple.core.plugin.CardIOException
import org.eclipse.keyple.core.plugin.ReaderIOException
import org.eclipse.keyple.core.plugin.WaitForCardInsertionAutonomousReaderApi
import org.eclipse.keyple.core.plugin.spi.reader.ConfigurableReaderSpi
import org.eclipse.keyple.core.plugin.spi.reader.ReaderSpi
import org.eclipse.keyple.core.plugin.spi.reader.observable.ObservableReaderSpi
import org.eclipse.keyple.core.plugin.spi.reader.observable.state.insertion.WaitForCardInsertionAutonomousSpi
import org.eclipse.keyple.core.plugin.spi.reader.observable.state.processing.DontWaitForCardRemovalDuringProcessingSpi
import org.eclipse.keyple.core.plugin.spi.reader.observable.state.removal.WaitForCardRemovalNonBlockingSpi
import org.eclipse.keyple.core.util.ByteArrayUtil
import timber.log.Timber

internal class BluebirdContactlessReaderAdapter :
    ObservableReaderSpi,
    ConfigurableReaderSpi,
    BluebirdContactlessReader,
    WaitForCardInsertionAutonomousSpi,
    DontWaitForCardRemovalDuringProcessingSpi,
    WaitForCardRemovalNonBlockingSpi {

    private var waitForCardInsertionAutonomousApi: WaitForCardInsertionAutonomousReaderApi? = null

    private val isCardDiscovered = AtomicBoolean(false)

    private var lastTagTime: Long = 0
    private var lastTagData: String? = null

    private val protocols = listOf(
        BluebirdSupportContactlessProtocols.NFC_A_BB,
        BluebirdSupportContactlessProtocols.NFC_B_BB,
        BluebirdSupportContactlessProtocols.NFC_INNO_BB,
        BluebirdSupportContactlessProtocols.NFC_ALL
    )

    private var currentTag: Tag? = null
    private var currentProtocol: BluebirdSupportContactlessProtocols? = null
    private var isConnected = false

    private val bluebirdReader = BluebirdReader.getInstance()

    /**
     * Callback function invoked when a @[Tag] is detected
     *
     * @param tag : detected [Tag]
     */
    private fun onTagDiscovered(tag: Tag?) {
        Timber.i("Received Tag Discovered event $tag")
        tag?.let {
            try {
                Timber.i("Getting tag data")

                this.currentTag = tag
                lastTagTime = System.currentTimeMillis()
                lastTagData = ByteArrayUtil.toHex(tag.data)

                isCardDiscovered.set(true)
                waitForCardInsertionAutonomousApi?.onCardInserted()
            } catch (e: ReaderIOException) {
                Timber.e(e)
            }
        }
    }

    /** Method called when the card detection is started by the Keyple Plugin */
    override fun onStartDetection() {
        if (currentProtocol == null) {
            throw IllegalStateException("onStartDetection - No contactless protocol activated")
        }
        bluebirdReader.startScan(currentProtocol!!) { nfcResult ->
            if (nfcResult is NfcResultSuccess) {
                val tagData = ByteArrayUtil.toHex(nfcResult.tag.data)
                /*
                 * We check that a new tag has been detected
                 */
                if (checkDetectedTag(tagData)) {
                    lastTagTime = System.currentTimeMillis()
                    lastTagData = tagData
                    onTagDiscovered(nfcResult.tag)
                }
            } else if (nfcResult is NfcResultError) {
                throw IllegalStateException(
                    nfcResult.error.message
                        ?: "onStartDetection - Error when starting NFC detection"
                )
            }
        }
    }

    /** Method called when the card detection is stopped by the Keyple Plugin */
    override fun onStopDetection() {
        bluebirdReader.stopScan()
    }

    /**
     * Check if detected tag is a new one
     * or if enough time has passed between 2 detections of the same tag
     *
     * @param tagData [String]
     * @return true if tag is authorized
     */
    private fun checkDetectedTag(tagData: String?): Boolean {
        return when {
            lastTagData.isNullOrEmpty() -> {
                return true
            }
            tagData == lastTagData -> {
                System.currentTimeMillis() - lastTagTime >= SAME_TAG_ALLOWED_DELAY
            }
            else -> {
                true
            }
        }
    }

    /**
     * @see ReaderSpi.isCurrentProtocol
     */
    override fun isCurrentProtocol(readerProtocolName: String?): Boolean {
        return currentProtocol?.let {
            it.key == readerProtocolName
        } ?: false
    }

    /**
     * @see ReaderSpi.isProtocolSupported
     */
    override fun isProtocolSupported(readerProtocol: String?): Boolean {
        val bluebirdReaderProtocol =
            BluebirdSupportContactlessProtocols.findEnumByKey(readerProtocol!!)
        return !readerProtocol.isNullOrEmpty() && protocols.contains(bluebirdReaderProtocol)
    }

    /**
     * @see ReaderSpi.checkCardPresence
     */
    override fun checkCardPresence(): Boolean {
        return currentTag != null
    }

    override fun getName(): String = BluebirdContactlessReader.READER_NAME

    /**
     * @see ReaderSpi.activateProtocol
     */
    override fun activateProtocol(readerProtocol: String?) {
        readerProtocol?.let {
            val protocol = BluebirdSupportContactlessProtocols.findEnumByKey(it)
            if (protocols.contains(protocol)) {
                currentProtocol = protocol
            } else {
                throw IllegalArgumentException("activateReaderProtocol - Activate protocol error : not allowed")
            }
        }
            ?: throw IllegalArgumentException("activateReaderProtocol - Activate protocol error : null protocol")
    }

    /**
     * @see ReaderSpi.deactivateProtocol
     */
    override fun deactivateProtocol(readerProtocol: String?) {
        currentProtocol.let {
            currentProtocol == null
        }
    }

    /**
     * @see ReaderSpi.transmitApdu
     */
    override fun transmitApdu(apduIn: ByteArray?): ByteArray? {
        if (apduIn == null) {
            return ByteArray(0)
        }

        var responseAPDU: ByteArray? = null
        Timber.d("BB-NFC - transmit command : ${ByteArrayUtil.toHex(apduIn)}")
        if (isConnected) {
            val resp = bluebirdReader.sendCommandToNfc(apduIn)
            Timber.d("BB-NFC - transmit result : ${ByteArrayUtil.toHex(resp)}")
            responseAPDU = resp
                ?: throw CardIOException("transmitApdu - IO error, APDU response null")
        }
        return responseAPDU
    }

    /**
     * @see ReaderSpi.isContactless
     */
    override fun isContactless(): Boolean {
        return true
    }

    /**
     * @see ReaderSpi.onUnregister
     */
    override fun onUnregister() {
        // Do nothing -> all unregister operations are handled by BluebirdReader
    }

    /**
     * @see ReaderSpi.getPowerOnData
     */
    override fun getPowerOnData(): String = lastTagData ?: ""

    /**
     * @see ReaderSpi.openPhysicalChannel
     */
    override fun openPhysicalChannel() {
        if (isConnected) return
        try {
            Timber.d("openPhysicalChannel - connect to tag")
            isConnected = bluebirdReader.connect()
        } catch (e: Exception) {
            throw IllegalStateException("openPhysicalChannel - Error while connect to tag : $e")
        }
    }

    /**
     * @see ReaderSpi.isPhysicalChannelOpen
     */
    override fun isPhysicalChannelOpen(): Boolean {
        return isConnected
    }

    /**
     * @see WaitForCardInsertionAutonomousSpi.connect
     */
    override fun connect(waitForCardInsertionAutonomousReaderApi: WaitForCardInsertionAutonomousReaderApi?) {
        waitForCardInsertionAutonomousApi = waitForCardInsertionAutonomousReaderApi
    }

    /**
     * @see ReaderSpi.closePhysicalChannel
     */
    override fun closePhysicalChannel() {
        if (!isConnected) {
            return
        }
        bluebirdReader.disconnect()
        isConnected = false
    }

    companion object {
        private const val SAME_TAG_ALLOWED_DELAY = 3000
    }
}
