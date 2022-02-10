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

import com.springcard.pcsclike.SCardReader
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.eclipse.keyple.core.plugin.spi.reader.ConfigurableReaderSpi
import org.eclipse.keyple.core.plugin.spi.reader.observable.ObservableReaderSpi
import org.eclipse.keyple.core.plugin.spi.reader.observable.state.insertion.WaitForCardInsertionBlockingSpi
import org.eclipse.keyple.core.plugin.spi.reader.observable.state.processing.DontWaitForCardRemovalDuringProcessingSpi
import org.eclipse.keyple.core.plugin.spi.reader.observable.state.removal.WaitForCardRemovalBlockingSpi

/** Keyple SE Reader's Implementation for the Bluebird (SAM access) reader */
@Suppress("INVISIBLE_ABSTRACT_MEMBER_FROM_SUPER_WARNING")
@ExperimentalCoroutinesApi
class AndroidPcscReaderAdapter(val sCardReader: SCardReader) :
    AndroidPcscReader,
    ConfigurableReaderSpi,
    ObservableReaderSpi,
    WaitForCardInsertionBlockingSpi,
    DontWaitForCardRemovalDuringProcessingSpi,
    WaitForCardRemovalBlockingSpi {

  override fun getName(): String {
    TODO("Not yet implemented")
  }

  override fun openPhysicalChannel() {
    TODO("Not yet implemented")
  }

  override fun closePhysicalChannel() {
    TODO("Not yet implemented")
  }

  override fun isPhysicalChannelOpen(): Boolean {
    TODO("Not yet implemented")
  }

  override fun checkCardPresence(): Boolean {
    TODO("Not yet implemented")
  }

  override fun getPowerOnData(): String {
    TODO("Not yet implemented")
  }

  override fun transmitApdu(apduIn: ByteArray?): ByteArray {
    TODO("Not yet implemented")
  }

  override fun isContactless(): Boolean {
    TODO("Not yet implemented")
  }

  override fun onUnregister() {
    TODO("Not yet implemented")
  }

  override fun onStartDetection() {
    TODO("Not yet implemented")
  }

  override fun onStopDetection() {
    TODO("Not yet implemented")
  }

  override fun isProtocolSupported(readerProtocol: String?): Boolean {
    TODO("Not yet implemented")
  }

  override fun activateProtocol(readerProtocol: String?) {
    TODO("Not yet implemented")
  }

  override fun deactivateProtocol(readerProtocol: String?) {
    TODO("Not yet implemented")
  }

  override fun isCurrentProtocol(readerProtocol: String?): Boolean {
    TODO("Not yet implemented")
  }

  override fun waitForCardRemoval() {
    TODO("Not yet implemented")
  }

  override fun stopWaitForCardRemoval() {
    TODO("Not yet implemented")
  }

  override fun waitForCardInsertion() {
    TODO("Not yet implemented")
  }

  override fun stopWaitForCardInsertion() {
    TODO("Not yet implemented")
  }

  //    private val reader = null // AndroidPcscReader.getInstance()
  //
  //    /**
  //     * This property is used to force 'compute protocol' by Keyple library
  //     */
  //    private var physicalChannelOpen = false
  //
  //    @Throws(IllegalStateException::class)
  //    override fun transmitApdu(apduIn: ByteArray?): ByteArray? {
  //        var apduResponse: ByteArray? = byteArrayOf()
  //
  //        if (BackgroundThreadExecutor.isUiThread()) {
  //            throw IllegalStateException("APDU exchange must NOT be done on main UI thread")
  //        }
  //
  //        try {
  //            Timber.d("transmitApdu apduIn : ${ByteArrayUtil.toHex(apduIn)}")
  //            runBlocking {
  //                apduResponse = executeApduAsync(apduIn)
  //            }
  //        } catch (e: Exception) {
  //            Timber.e("transmitApdu error $e")
  //            throw IllegalStateException(e)
  //        }
  //
  //        Timber.d("transmitApdu apduResponse : ${ByteArrayUtil.toHex(apduResponse)}")
  //        return apduResponse
  //    }
  //
  //    private suspend fun executeApduAsync(apduIn: ByteArray?): ByteArray? {
  //        if (apduIn == null) {
  //            return ByteArray(0)
  //        }
  //        /*reader.setSamResponseChannel(Channel(Channel.UNLIMITED))*/
  //
  //        return suspendCoroutineWithTimeout(APDU_TIMEOUT) { continuation ->
  //            val handler = CoroutineExceptionHandler { _, exception ->
  //                Timber.e("error SAM connection : $exception")
  //                handleResponseApduResult(
  //                    result = null,
  //                    throwable = exception,
  //                    continuation = continuation
  //                )
  //            }
  //
  //            GlobalScope.launch(handler) {
  //                /*withContext(Dispatchers.IO) {
  //                    // reader.sendCommandToSam(apduIn)
  //
  //                    for (resultApdu in reader.getSamResponseChannel()!!) {
  //                        handleResponseApduResult(resultApdu, null, continuation)
  //                    }
  //                }*/
  //            }
  //        }
  //    }
  //
  //    private fun handleResponseApduResult(
  //        result: ByteArray?,
  //        throwable: Throwable?,
  //        continuation: CancellableContinuation<ByteArray>
  //    ) {
  //        if (continuation.isActive) {
  //            /*reader.getSamResponseChannel()?.close()
  //            reader.setSamResponseChannel(null)*/
  //
  //            result?.let {
  //                continuation.resume(it)
  //            }
  //            throwable?.let {
  //                continuation.resumeWithException(it)
  //            }
  //        }
  //    }
  //
  //    /**
  //     * @see ReaderSpi.getPowerOnData
  //     */
  //    override fun getPowerOnData(): String {
  //        return "" // ByteArrayUtil.toHex(reader.atr)
  //    }
  //
  //    /**
  //     * @see ReaderSpi.closePhysicalChannel
  //     */
  //    @Throws(IllegalStateException::class)
  //    override fun closePhysicalChannel() {
  //        physicalChannelOpen = false
  //    }
  //
  //    /**
  //     * @see ReaderSpi.openPhysicalChannel
  //     */
  //    override fun openPhysicalChannel() {
  //        physicalChannelOpen = true
  //    }
  //
  //    /**
  //     * @see ReaderSpi.isPhysicalChannelOpen
  //     */
  //    override fun isPhysicalChannelOpen(): Boolean {
  //        return false /*(reader.isSamPhysicalChannelOpen() &&
  //                physicalChannelOpen)*/
  //    }
  //
  //    /**
  //     * @see ReaderSpi.checkCardPresence
  //     */
  //    override fun checkCardPresence(): Boolean {
  //        return false // reader.checkSamCardPresence()
  //    }
  //
  //    /**
  //     * @see ReaderSpi.isContactless
  //     */
  //    override fun isContactless(): Boolean {
  //        return false
  //    }
  //
  //    /**
  //     * @see ReaderSpi.getName
  //     */
  //    override fun getName(): String {
  //        return name
  //    }
  //
  //    /**
  //     * @see ReaderSpi.onUnregister
  //     */
  //    override fun onUnregister() {
  //        // Do nothing -> all unregister operations are handled by BluebirdReader
  //    }
  //
  //    override fun onStartDetection() {
  //        TODO("Not yet implemented")
  //    }
  //
  //    override fun onStopDetection() {
  //        TODO("Not yet implemented")
  //    }
  //
  //    override fun isProtocolSupported(readerProtocol: String?): Boolean {
  //        TODO("Not yet implemented")
  //    }
  //
  //    override fun activateProtocol(readerProtocol: String?) {
  //        TODO("Not yet implemented")
  //    }
  //
  //    override fun deactivateProtocol(readerProtocol: String?) {
  //        TODO("Not yet implemented")
  //    }
  //
  //    override fun isCurrentProtocol(readerProtocol: String?): Boolean {
  //        TODO("Not yet implemented")
  //    }
  //
  //    companion object {
  //        private const val APDU_TIMEOUT: Long = 1000
  //    }
  //
  //    override fun waitForCardRemoval() {
  //        TODO("Not yet implemented")
  //    }
  //
  //    override fun stopWaitForCardRemoval() {
  //        TODO("Not yet implemented")
  //    }
}
