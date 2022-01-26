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
package org.calypsonet.keyple.plugin.bluebird.reader

import android.annotation.SuppressLint
import android.content.Context
import com.bluebird.extnfc.ExtNfcReader
import com.bluebird.payment.sam.SamInterface
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.calypsonet.keyple.plugin.bluebird.BluebirdSupportContactlessProtocols
import org.calypsonet.keyple.plugin.bluebird.utils.suspendCoroutineWithTimeout
import timber.log.Timber

class BluebirdReader {

    private var nfcReader: ExtNfcReader? = null
    private var nfcReceiver: BluebirdNfcReceiver? = null

    private var samInterface: SamInterface? = null
    private var bluebirdSamHandler: BluebirdSamHandler? = null

    var atr: ByteArray? = null

    fun getSamResponseChannel(): Channel<ByteArray?>? = bluebirdSamHandler?.responseChannel
    fun setSamResponseChannel(channel: Channel<ByteArray?>?) {
        bluebirdSamHandler?.responseChannel = channel
    }

    @Throws(IllegalStateException::class)
    fun sendCommandToSam(command: ByteArray?) {
        val result = samInterface?.device_SendCommand(command)

        if (result != null && result < 0) {
            val errorMsg = when (result) {
                SamInterface.SAM_COMMAND_NOT_SUPPORT -> "SAM_COMMAND_NOT_SUPPORT"
                SamInterface.SAM_COMMAND_CONFLICT -> "SAM_COMMAND_CONFLICT"
                SamInterface.SAM_COMMAND_BAD_CLASS -> "SAM_COMMAND_BAD_CLASS"
                SamInterface.SAM_COMMAND_BAD_PROTOCOL -> "SAM_COMMAND_BAD_PROTOCOL"
                SamInterface.SAM_COMMAND_BAD_TCK -> "SAM_COMMAND_BAD_TCK"
                SamInterface.SAM_COMMAND_BAD_TS -> "SAM_COMMAND_BAD_TS"
                SamInterface.SAM_COMMAND_HARDWARE_ERROR -> "SAM_COMMAND_HARDWARE_ERROR"
                SamInterface.SAM_COMMAND_OVERRUN -> "SAM_COMMAND_OVERRUN"
                SamInterface.SAM_COMMAND_PARITY_ERROR -> "SAM_COMMAND_PARITY_ERROR"
                else -> "SAM unknown error occurred"
            }
            Timber.e("sendCommandToSam error : $errorMsg")
            throw IllegalStateException(errorMsg)
        }
    }

    /**
     * Connect NFC reader
     */
    fun connect(): Boolean {
        var isConnected = false
        if (nfcReader != null) {
            isConnected = nfcReader!!.connect() == ExtNfcReader.ResultCode.SUCCESS
        }
        return isConnected
    }

    /**
     * Disconnect NFC reader
     */
    fun disconnect() {
        nfcReader?.disconnect()
    }

    /**
     * Transmit APDU to NFC reader
     */
    fun sendCommandToNfc(command: ByteArray): ByteArray? {
        var apduResult: ByteArray? = null
        if (nfcReader != null) {
            val transmitResult = nfcReader!!.transmit(command)
            transmitResult?.let { result ->
                apduResult = result.mData
            }
        }
        return apduResult
    }

    /**
     * Start NFC reader and receiver scan
     */
    fun startScan(
        protocol: BluebirdSupportContactlessProtocols,
        listener: (NfcResult) -> Unit
    ) {
        try {
            Timber.d("Bluebird reader - startScan")

            if (nfcReceiver != null && nfcReader != null) {

                nfcReceiver!!.setListener(listener)
                nfcReader!!.cardTypeForScan = protocol.value

                var startScanResultCode = nfcReader!!.startScan()
                /*
                 * If the reader is already started -> stop it then re-start it
                 */
                if (startScanResultCode == ExtNfcReader.ResultCode.ERROR_ALREADY_ON_SCANNING) {
                    startScanResultCode = nfcReader!!.stopScan()

                    if (startScanResultCode == ExtNfcReader.ResultCode.SUCCESS) {
                        startScanResultCode = nfcReader!!.startScan()
                    }
                }

                if (startScanResultCode == ExtNfcReader.ResultCode.SUCCESS) {
                    /*
                     * NFC reader started successfully -> register nfc broadcast receiver for tag listening
                     */
                    if (!nfcReceiver!!.isRegistered) {
                        nfcReceiver!!.registerScanReceiver()
                    }
                } else {
                    /*
                     * An error occurred during NFC reader start
                     */
                    listener(NfcResultError(Throwable("Bluebird reader - startScan error : $startScanResultCode")))
                }
            }
        } catch (ex: Exception) {
            Timber.e(ex)
            listener(NfcResultError(Throwable("Bluebird reader - Start scan - message : ${ex.message}")))
        }
    }

    /**
     * Stop NFC receiver scan
     */
    fun stopScan() {
        Timber.d("Bluebird reader - Stop scan")
        if (nfcReceiver != null && nfcReceiver!!.isRegistered) {
            nfcReceiver!!.unregisterScanReceiver()
        }
    }

    fun isSamPhysicalChannelOpen(): Boolean {
        return (samInterface != null &&
                bluebirdSamHandler != null &&
                bluebirdSamHandler?.initDone ?: false)
    }

    fun checkSamCardPresence(): Boolean {
        return samInterface?.let {
            val status = it.device_GetStatus()
            status >= 0
        } ?: false
    }

    companion object {

        private const val APDU_TIMEOUT: Long = 1000

        private val isInitDone = AtomicBoolean(false)
        private var bluebirdReader: BluebirdReader? = null

        /**
         * Init the reader, is called when instantiating this plugin's factory
         */
        @SuppressLint("WrongConstant")
        @Throws(Exception::class)
        suspend fun init(context: Context): Boolean? {
            Timber.d("Init Bluebird reader")
            var isEnabled: Boolean? = false

            if (!isInitDone.get()) {
                Timber.d("Start Contactless (NFC) reader Init")
                /*
                 * Init NFC reader and receiver
                 */
                var reader: ExtNfcReader? = null
                var receiver: BluebirdNfcReceiver? = null
                try {
                    reader =
                        context.getSystemService(ExtNfcReader.READER_SERVICE_NAME) as ExtNfcReader
                    Timber.d("NFC reader : $reader")

                    reader.enable(true)
                    receiver = BluebirdNfcReceiver(context)
                    Timber.d("NFC receiver : $receiver")

                    isEnabled = reader.isEnabled
                } catch (e: NoClassDefFoundError) {
                    Timber.e("Bluebird reader init failed - $e")
                }

                Timber.d("NFC reader enabled : $isEnabled")

                if (isEnabled == true) {
                    bluebirdReader = BluebirdReader()
                    bluebirdReader?.nfcReader = reader
                    bluebirdReader?.nfcReceiver = receiver

                    isInitDone.set(true)
                } else {
                    throw IllegalStateException("Bluebird Adapter not started.")
                }

                /*
                 * Init SAM interface
                 */
                Timber.d("Start Contact (SAM) reader Init")
                bluebirdReader?.let {
                    if (it.bluebirdSamHandler == null) {
                        it.bluebirdSamHandler = BluebirdSamHandler()
                    }

                    if (it.samInterface == null) {
                        it.samInterface = SamInterface(it.bluebirdSamHandler)
                    }
                }

                delay(150)

                launchSamInit()
            } else {
                isEnabled = true
            }

            return isEnabled
        }

        /**
         * Launch SAM interface init using SuspendCoroutine
         */
        private suspend fun launchSamInit() {
            Timber.d("BluebirdReader - launchInit")
            bluebirdReader?.bluebirdSamHandler?.initChannel = Channel(Channel.UNLIMITED)

            val initDone: Boolean? = suspendCoroutineWithTimeout(APDU_TIMEOUT) { continuation ->
                val handler = CoroutineExceptionHandler { _, exception ->
                    Timber.e("error SAM connection : $exception")
                    handleSamInitResult(null, continuation)
                }

                GlobalScope.launch(handler) {
                    withContext(Dispatchers.IO) {
                        val openResponse = bluebirdReader?.samInterface?.device_Open()

                        Timber.d("openResponse : $openResponse")
                        if (openResponse == null || openResponse < 0) {
                            throw IllegalStateException("An error occurred during SAM opening")
                        }

                        for (result in bluebirdReader?.bluebirdSamHandler?.initChannel!!) {
                            handleSamInitResult(result, continuation)
                        }
                    }
                }
            }

            Timber.d("BluebirdReader - launchInit - initDone : $initDone")
        }

        /**
         * Handle result of SAM init
         * @param result [ByteArray]
         */
        private fun handleSamInitResult(
            result: ByteArray?,
            continuation: CancellableContinuation<Boolean>
        ) {
            if (continuation.isActive) {
                bluebirdReader?.bluebirdSamHandler?.initChannel?.close()
                bluebirdReader?.bluebirdSamHandler?.initChannel = null
                bluebirdReader?.atr = result
                continuation.resume(result != null)
            }
        }

        /**
         * Get Reader instance
         */
        @Throws(IllegalStateException::class)
        fun getInstance(): BluebirdReader {
            Timber.d("Get Instance")
            if (bluebirdReader == null || !isInitDone.get()) {
                throw IllegalStateException("Bluebird reader not initiated")
            }
            return bluebirdReader!!
        }

        /**
         * Reset the instance
         */
        fun clearInstance() {
            Timber.d("Clear Bluebird reader instance")

            getInstance()
                .let {
                    /*
                     * Clear NFC reader
                     */
                    it.nfcReader?.let { reader ->
                        if (reader.isEnabled) {
                            reader.disconnect()
                            reader.enable(false)
                        }
                    }
                    it.nfcReader = null

                    /*
                     * Clear NFC receiver
                     */
                    it.nfcReceiver?.let { receiver ->
                        if (receiver.isRegistered) {
                            receiver.unregisterScanReceiver()
                        }
                    }
                    it.nfcReceiver = null

                    isInitDone.set(false)

                    /*
                     * Clear SAM interface and handler
                     */
                    disconnectSam(it)
                }

            bluebirdReader = null
        }

        /**
         * Disconnect and clear Bluebird SAM interface and handler
         */
        private fun disconnectSam(bluebirdReader: BluebirdReader) {
            Timber.d("BluebirdReader - disconnectSam")
            bluebirdReader.samInterface?.let {
                val closed = it.device_Close()
                val isSamDisconnected = closed >= 0
                Timber.d("BluebirdReader - disconnectSam - isSamDisconnected : $isSamDisconnected")
                if (!isSamDisconnected) {
                    throw IllegalStateException("An error occured when closing SAM session")
                }
            }

            bluebirdReader.samInterface = null
            bluebirdReader.bluebirdSamHandler?.let {
                it.initChannel = null
            }
            bluebirdReader.bluebirdSamHandler = null
        }
    }
}
