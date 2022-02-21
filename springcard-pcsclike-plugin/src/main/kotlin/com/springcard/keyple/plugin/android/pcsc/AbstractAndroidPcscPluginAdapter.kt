/*
 * Copyright (c) 2018-2018-2018 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */
package com.springcard.keyple.plugin.android.pcsc

import android.content.Context
import com.springcard.keyple.plugin.android.pcsc.spi.DeviceScannerSpi
import com.springcard.pcsclike.SCardChannel
import com.springcard.pcsclike.SCardError
import com.springcard.pcsclike.SCardReader
import com.springcard.pcsclike.SCardReaderList
import com.springcard.pcsclike.SCardReaderListCallback
import org.eclipse.keyple.core.plugin.spi.ObservablePluginSpi
import org.eclipse.keyple.core.plugin.spi.reader.ReaderSpi
import timber.log.Timber

/** Handle native Readers mapped for Keyple */
internal abstract class AbstractAndroidPcscPluginAdapter(var context: Context) :
    AndroidPcscPlugin, ObservablePluginSpi {
  companion object {
    private const val MONITORING_CYCLE_DURATION_MS = 1000
  }

  private var sCardReaders: MutableMap<String, SCardReader> = mutableMapOf()
  private val readerSpis: MutableMap<String, AndroidPcscReaderAdapter> = mutableMapOf()

  abstract override fun scanDevices(
      timeout: Long,
      stopOnFirstDeviceDiscovered: Boolean,
      deviceScannerSpi: DeviceScannerSpi
  )

  abstract override fun connectToDevice(identifier: String)

  override fun getName(): String {
    return AndroidPcscPlugin.PLUGIN_NAME
  }

  override fun getMonitoringCycleDuration(): Int {
    return MONITORING_CYCLE_DURATION_MS
  }

  override fun searchAvailableReaders(): MutableSet<ReaderSpi> {
    for (sCardReader in sCardReaders.values) {
      readerSpis.put(sCardReader.name, AndroidPcscReaderAdapter(sCardReader))
    }
    return readerSpis.values.toMutableSet()
  }

  override fun searchAvailableReaderNames(): MutableSet<String> {
    return sCardReaders.keys
  }

  override fun searchReader(readerName: String): ReaderSpi? {
    for ((name, sCardReader) in sCardReaders) {
      if (readerName == name) {
        val reader = AndroidPcscReaderAdapter(sCardReader)
        readerSpis[sCardReader.name] = reader
        return reader
      }
    }
    return null
  }

  override fun onUnregister() {
    TODO("Not yet implemented")
  }

  var scardCallbacks: SCardReaderListCallback =
      object : SCardReaderListCallback() {
        override fun onReaderListCreated(readerList: SCardReaderList) {
          for (i in 0 until readerList.slotCount) {
            Timber.e("Add reader: ${readerList.slots[i]}")
            readerList.getReader(i)?.let { sCardReaders.put(it.name, it) }
          }
        }

        override fun onReaderListClosed(readerList: SCardReaderList?) {
          Timber.e("onReaderListClosed")
          sCardReaders.clear()
        }

        override fun onControlResponse(readerList: SCardReaderList, response: ByteArray) {
          Timber.e("onControlResponse")
        }

        override fun onReaderStatus(
            slot: SCardReader,
            cardPresent: Boolean,
            cardConnected: Boolean
        ) {
          Timber.e(
              "onReaderStatus: reader=${slot.name}, cardPresent=$cardPresent, cardConnected=$cardConnected")
          readerSpis[slot.name]?.onCardPresenceChange(cardPresent)
        }

        override fun onCardConnected(channel: SCardChannel) {
          Timber.e("onCardConnected: reader=${channel.parent.name}")
          readerSpis[channel.parent.name]?.onCardConnected()
        }

        override fun onCardDisconnected(channel: SCardChannel) {
          Timber.e("onCardDisconnected")
        }

        override fun onTransmitResponse(channel: SCardChannel, response: ByteArray) {
          Timber.e("onTransmitResponse")
          readerSpis[channel.parent.name]?.onCardResponseReceived(response)
        }

        override fun onReaderListError(readerList: SCardReaderList?, error: SCardError) {
          Timber.e("onReaderListError")
        }

        override fun onReaderOrCardError(readerOrCard: Any, error: SCardError) {
          Timber.e("onReaderOrCardError")
        }

        override fun onReaderListState(readerList: SCardReaderList, isInLowPowerMode: Boolean) {
          Timber.e("onReaderListState")
        }
      }
}
