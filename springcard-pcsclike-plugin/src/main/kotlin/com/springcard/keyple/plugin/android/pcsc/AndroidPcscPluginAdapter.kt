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

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import com.springcard.keyple.plugin.android.pcsc.spi.BleDeviceScannerSpi
import com.springcard.pcsclike.SCardChannel
import com.springcard.pcsclike.SCardError
import com.springcard.pcsclike.SCardReader
import com.springcard.pcsclike.SCardReaderList
import com.springcard.pcsclike.SCardReaderListCallback
import com.springcard.pcsclike.communication.GattAttributesD600
import com.springcard.pcsclike.communication.GattAttributesSpringCore
import org.eclipse.keyple.core.plugin.spi.ObservablePluginSpi
import org.eclipse.keyple.core.plugin.spi.reader.ReaderSpi
import timber.log.Timber

/** Handle native Readers mapped for Keyple */
internal class AndroidPcscPluginAdapter(var context: Context) :
    AndroidPcscPlugin, ObservablePluginSpi {
  companion object {
    private const val MONITORING_CYCLE_DURATION_MS = 1000
  }

  private var bluetoothDeviceList: MutableMap<String, BluetoothDevice> = mutableMapOf()
  private var bluetoothDeviceInfoMap: MutableMap<String, BleDeviceInfo> = mutableMapOf()
  private var sCardReaders: MutableMap<String, SCardReader> = mutableMapOf()
  private lateinit var bluetoothScanner: BluetoothLeScanner
  private lateinit var bleDeviceScannerSpi: BleDeviceScannerSpi
  private var mIsContinuesScan: Boolean = false
  private var mScanThread: Thread? = null
  private var mHandler: Handler = Handler(Looper.getMainLooper())
  private var stopOnFirstDeviceDiscovered: Boolean = false
  private val readerSpis: MutableMap<String, AndroidPcscReaderAdapter> = mutableMapOf()

  override fun scanBleReaders(
      bluetoothAdapter: BluetoothAdapter,
      timeout: Long,
      stopOnFirstDeviceDiscovered: Boolean,
      bleDeviceScannerSpi: BleDeviceScannerSpi
  ) {
    this.stopOnFirstDeviceDiscovered = stopOnFirstDeviceDiscovered
    this.bleDeviceScannerSpi = bleDeviceScannerSpi
    bluetoothScanner = bluetoothAdapter.bluetoothLeScanner
    scanBleDevice(false, timeout * 1000)
  }

  override fun connectToBleDevice(address: String) {
    Timber.d("Connection to BLE device: ${address}.")
    val bluetoothDevice = bluetoothDeviceList[address]
    if (bluetoothDevice != null) {
      SCardReaderList.create(context, bluetoothDevice, scardCallbacks)
    } else {
      throw IllegalStateException("Device with address $address not found")
    }
  }

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

  //    companion object {
  //        private const val MONITORING_CYCLE_DURATION_MS = 1000
  //    }
  //
  //    private lateinit var readers: ConcurrentHashMap<String, ReaderSpi>
  //
  //    @ExperimentalCoroutinesApi
  //    override fun searchAvailableReaders(): MutableSet<ReaderSpi> {
  //
  //        readers = ConcurrentHashMap()
  //
  //        // TODO search and initialize readers here
  //
  //        return readers.map {
  //            it.value
  //        }.toMutableSet()
  //    }
  //
  //    override fun searchReader(readerName: String?): ReaderSpi? {
  //        return if (readers.containsKey(readerName)) {
  //            readers[readerName]!!
  //        } else {
  //            null
  //        }
  //    }
  //
  //    override fun searchAvailableReaderNames(): MutableSet<String> {
  //        return readers.map {
  //            it.key
  //        }.toMutableSet()
  //    }
  //
  //    override fun getMonitoringCycleDuration(): Int {
  //        return MONITORING_CYCLE_DURATION_MS
  //    }
  //
  //    override fun getName(): String = AndroidPcscPlugin.PLUGIN_NAME
  //
  //    override fun onUnregister() {
  //        // TODO action to do when the plugin is unregistered
  //    }

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

  private fun scan() {
    /* Scan settings */
    val settings = ScanSettings.Builder()
    settings.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
    // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
    //  settings.setCallbackType(ScanSettings.CALLBACK_TYPE_MATCH_LOST)
    // }
    val settingsBuilt = settings.build()

    /* Filter for SpringCard service */
    val scanFilters = ArrayList<ScanFilter>()
    try {
      val scanFilterD600 =
          ScanFilter.Builder()
              .setServiceUuid(
                  ParcelUuid(GattAttributesD600.UUID_SPRINGCARD_RFID_SCAN_PCSC_LIKE_SERVICE))
              .build()
      val scanFilterSpringCorePlain =
          ScanFilter.Builder()
              .setServiceUuid(
                  ParcelUuid(GattAttributesSpringCore.UUID_SPRINGCARD_CCID_PLAIN_SERVICE))
              .build()
      val scanFilterSpringCoreBonded =
          ScanFilter.Builder()
              .setServiceUuid(
                  ParcelUuid(GattAttributesSpringCore.UUID_SPRINGCARD_CCID_BONDED_SERVICE))
              .build()
      scanFilters.add(scanFilterD600)
      scanFilters.add(scanFilterSpringCorePlain)
      scanFilters.add(scanFilterSpringCoreBonded)
    } catch (e: Exception) {
      Timber.e(e)
    }

    /* Reset devices list anyway */
    //        deviceList.clear()
    //        bleDeviceList.clear()
    //        adapter?.notifyDataSetChanged()

    bluetoothScanner.startScan(scanFilters, settingsBuilt, leScanCallback)
    Timber.d("BLE scanning started...")
    //          progressBarScanning?.visibility = ProgressBar.VISIBLE
    //          mainActivity.logInfo("Scan started")
  }

  private val leScanCallback =
      object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
          val bleDeviceInfo: BleDeviceInfo =
              if (result.scanRecord!!.deviceName != null) {
                BleDeviceInfo(
                    result.device.address, result.scanRecord!!.deviceName!!, result.rssi.toString())
              } else {
                BleDeviceInfo(result.device.address, result.device.address, result.rssi.toString())
              }
          if (callbackType == ScanSettings.CALLBACK_TYPE_ALL_MATCHES) {
            if (!bluetoothDeviceInfoMap.containsKey(bleDeviceInfo.address)) {
              Timber.d("BLE device added: ${bleDeviceInfo.toString()}")
              bluetoothDeviceInfoMap[bleDeviceInfo.address] = bleDeviceInfo
              bluetoothDeviceList[bleDeviceInfo.address] = result.device
              if (stopOnFirstDeviceDiscovered) {
                // shorten timer
                mHandler.removeCallbacks(mTerminateScan)
                mHandler.postDelayed(mTerminateScan, 0)
              }
            }
          } else if (callbackType == ScanSettings.CALLBACK_TYPE_MATCH_LOST &&
              bluetoothDeviceInfoMap.containsKey(result.device.address)) {
            Timber.d("BLE device removed: ${result.device.address}")
            bluetoothDeviceList.remove(result.device.address)
            bluetoothDeviceInfoMap.remove(result.device.address)
          }
        }
      }

  /**
   * Scan The BLE Device Check the available BLE devices in the Surrounding If the device is Already
   * scanning then stop Scanning Else start Scanning and check 10 seconds Send the available devices
   * as a callback to the system Finish Scanning after 10 Seconds
   */
  fun scanBleDevice(isContinuesScan: Boolean, scanDelay: Long) {
    try {
      mIsContinuesScan = isContinuesScan

      if (mScanThread != null) {
        Timber.d("BLE scan Thread already running.")
        return
      }
      Timber.d("Scan BLE devices for ${scanDelay} ms.")
      mScanThread = Thread(mScanRunnable)
      mScanThread!!.start()

      /**
       * Stop Scanning after a Period of Time Set a 10 Sec delay time and Stop Scanning collect all
       * the available devices in the 10 Second
       */
      if (!isContinuesScan) {
        mHandler.postDelayed(mTerminateScan, scanDelay) // Delay Period
      }
    } catch (e: Exception) {
      Timber.e(e)
    }
  }

  private val mScanRunnable = Runnable { scan() }

  private val mTerminateScan =
      Runnable {
        Timber.d("Stop scanning.")
        // Set a delay time to Scanning
        bluetoothScanner.stopScan(object : ScanCallback() {})

        bleDeviceScannerSpi.onDeviceDiscovered(bluetoothDeviceInfoMap.values)
      }
}
