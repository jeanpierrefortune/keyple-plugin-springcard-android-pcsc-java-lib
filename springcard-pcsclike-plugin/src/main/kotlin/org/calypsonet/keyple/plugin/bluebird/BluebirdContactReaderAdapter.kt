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

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.calypsonet.keyple.plugin.bluebird.reader.BluebirdReader
import org.calypsonet.keyple.plugin.bluebird.utils.suspendCoroutineWithTimeout
import org.eclipse.keyple.core.plugin.spi.reader.ReaderSpi
import org.eclipse.keyple.core.plugin.spi.reader.observable.state.processing.DontWaitForCardRemovalDuringProcessingSpi
import org.eclipse.keyple.core.plugin.spi.reader.observable.state.removal.WaitForCardRemovalNonBlockingSpi
import org.eclipse.keyple.core.util.ByteArrayUtil
import timber.log.Timber

/**
 * Keyple SE Reader's Implementation for the Bluebird (SAM access) reader
 */
@Suppress("INVISIBLE_ABSTRACT_MEMBER_FROM_SUPER_WARNING")
@ExperimentalCoroutinesApi
class BluebirdContactReaderAdapter :
    BluebirdContactReader,
    ReaderSpi,
    DontWaitForCardRemovalDuringProcessingSpi,
    WaitForCardRemovalNonBlockingSpi {

    private val bluebirdReader = BluebirdReader.getInstance()

    /**
     * This property is used to force 'compute protocol' by Keyple library
     */
    private var physicalChannelOpen = false

    @Throws(IllegalStateException::class)
    override fun transmitApdu(apduIn: ByteArray?): ByteArray? {
        var apduResponse: ByteArray? = byteArrayOf()

        if (org.calypsonet.keyple.plugin.bluebird.utils.BackgroundThreadExecutor.isUiThread()) {
            throw IllegalStateException("APDU exchange must NOT be done on main UI thread")
        }

        try {
            Timber.d("SAM-BB-APDU - transmitApdu apduIn : ${ByteArrayUtil.toHex(apduIn)}")
            runBlocking {
                apduResponse = executeApduAsync(apduIn)
            }
        } catch (e: Exception) {
            Timber.e("transmitApdu error $e")
            throw IllegalStateException(e)
        }

        Timber.d("SAM-BB-APDU - transmitApdu apduResponse : ${ByteArrayUtil.toHex(apduResponse)}")
        return apduResponse
    }

    private suspend fun executeApduAsync(apduIn: ByteArray?): ByteArray? {
        if (apduIn == null) {
            return ByteArray(0)
        }
        bluebirdReader.setSamResponseChannel(Channel(Channel.UNLIMITED))

        return suspendCoroutineWithTimeout(APDU_TIMEOUT) { continuation ->
            val handler = CoroutineExceptionHandler { _, exception ->
                Timber.e("error SAM connection : $exception")
                handleResponseApduResult(
                    result = null,
                    throwable = exception,
                    continuation = continuation
                )
            }

            GlobalScope.launch(handler) {
                withContext(Dispatchers.IO) {
                    bluebirdReader.sendCommandToSam(apduIn)

                    for (resultApdu in bluebirdReader.getSamResponseChannel()!!) {
                        handleResponseApduResult(resultApdu, null, continuation)
                    }
                }
            }
        }
    }

    private fun handleResponseApduResult(
        result: ByteArray?,
        throwable: Throwable?,
        continuation: CancellableContinuation<ByteArray>
    ) {
        if (continuation.isActive) {
            bluebirdReader.getSamResponseChannel()?.close()
            bluebirdReader.setSamResponseChannel(null)

            result?.let {
                continuation.resume(it)
            }
            throwable?.let {
                continuation.resumeWithException(it)
            }
        }
    }

    /**
     * @see ReaderSpi.getPowerOnData
     */
    override fun getPowerOnData(): String {
        return ByteArrayUtil.toHex(bluebirdReader.atr)
    }

    /**
     * @see ReaderSpi.closePhysicalChannel
     */
    @Throws(IllegalStateException::class)
    override fun closePhysicalChannel() {
        physicalChannelOpen = false
    }

    /**
     * @see ReaderSpi.openPhysicalChannel
     */
    override fun openPhysicalChannel() {
        physicalChannelOpen = true
    }

    /**
     * @see ReaderSpi.isPhysicalChannelOpen
     */
    override fun isPhysicalChannelOpen(): Boolean {
        return (bluebirdReader.isSamPhysicalChannelOpen() &&
                physicalChannelOpen)
    }

    /**
     * @see ReaderSpi.checkCardPresence
     */
    override fun checkCardPresence(): Boolean {
        return bluebirdReader.checkSamCardPresence()
    }

    /**
     * @see ReaderSpi.isContactless
     */
    override fun isContactless(): Boolean {
        return false
    }

    /**
     * @see ReaderSpi.getName
     */
    override fun getName(): String = BluebirdContactReader.READER_NAME

    /**
     * @see ReaderSpi.onUnregister
     */
    override fun onUnregister() {
        // Do nothing -> all unregister operations are handled by BluebirdReader
    }

    companion object {
        private const val APDU_TIMEOUT: Long = 1000
    }
}
