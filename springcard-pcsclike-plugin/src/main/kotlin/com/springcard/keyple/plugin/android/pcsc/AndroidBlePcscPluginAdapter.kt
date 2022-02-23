/*
 * Copyright (c)2022 SpringCard - www.springcard.com.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */
package com.springcard.keyple.plugin.android.pcsc

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import com.springcard.keyple.plugin.android.pcsc.spi.DeviceScannerSpi
import com.springcard.pcsclike.SCardReaderList
import com.springcard.pcsclike.communication.GattAttributesD600
import com.springcard.pcsclike.communication.GattAttributesSpringCore
import timber.log.Timber

/** Provides the specific means to manage BLE devices. */
internal class AndroidBlePcscPluginAdapter(context: Context) :
    AbstractAndroidPcscPluginAdapter(context) {
  private var bluetoothDeviceList: MutableMap<String, BluetoothDevice> = mutableMapOf()
  private var bluetoothDeviceInfoMap: MutableMap<String, DeviceInfo> = mutableMapOf()
  private lateinit var bluetoothScanner: BluetoothLeScanner
  private lateinit var deviceScannerSpi: DeviceScannerSpi
  private var isContinuesScan: Boolean = false
  private var scanThread: Thread? = null
  private var handler: Handler = Handler(Looper.getMainLooper())
  private var stopOnFirstDeviceDiscovered: Boolean = false
  private val bluetoothAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    bluetoothManager.adapter
  }

  override fun scanDevices(
      timeout: Long,
      stopOnFirstDeviceDiscovered: Boolean,
      deviceScannerSpi: DeviceScannerSpi
  ) {
    this.stopOnFirstDeviceDiscovered = stopOnFirstDeviceDiscovered
    this.deviceScannerSpi = deviceScannerSpi
    bluetoothScanner = this.bluetoothAdapter!!.bluetoothLeScanner
    scanBleDevices(false, timeout * 1000)
  }

  override fun connectToDevice(identifier: String) {
    Timber.d("Connection to BLE device: ${identifier}.")
    val bluetoothDevice = bluetoothDeviceList[identifier]
    if (bluetoothDevice != null) {
      SCardReaderList.create(context, bluetoothDevice, scardCallbacks)
    } else {
      throw IllegalStateException("Device with address $identifier not found")
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

    bluetoothScanner.startScan(scanFilters, settingsBuilt, leScanCallback)
    Timber.d("BLE scanning started...")
  }

  private val leScanCallback =
      object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
          val bleDeviceInfo: DeviceInfo =
              if (result.scanRecord!!.deviceName != null) {
                DeviceInfo(
                    result.device.address,
                    "${result.scanRecord!!.deviceName!!} ${result.rssi}",
                    result.device.name)
              } else {
                DeviceInfo(
                    result.device.address,
                    "${result.device.address} ${result.rssi}",
                    result.device.name)
              }
          if (callbackType == ScanSettings.CALLBACK_TYPE_ALL_MATCHES) {
            if (!bluetoothDeviceInfoMap.containsKey(bleDeviceInfo.identifier)) {
              Timber.d("BLE device added: ${bleDeviceInfo.toString()}")
              bluetoothDeviceInfoMap[bleDeviceInfo.identifier] = bleDeviceInfo
              bluetoothDeviceList[bleDeviceInfo.identifier] = result.device
              if (stopOnFirstDeviceDiscovered) {
                // shorten timer
                handler.removeCallbacks(notifyScanResults)
                handler.postDelayed(notifyScanResults, 0)
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
   * TODO update this Scan The BLE Device Check the available BLE devices in the Surrounding If the
   * device is Already scanning then stop Scanning Else start Scanning and check 10 seconds Send the
   * available devices as a callback to the system Finish Scanning after 10 Seconds
   */
  private fun scanBleDevices(isContinuesScan: Boolean, scanDelay: Long) {
    try {
      this.isContinuesScan = isContinuesScan

      if (scanThread != null) {
        Timber.d("BLE scan Thread already running.")
        return
      }
      Timber.d("Scan BLE devices for ${scanDelay} ms.")
      scanThread = Thread(scanRunnable)
      scanThread!!.start()

      /**
       * Stop Scanning after a Period of Time Set a 10 Sec delay time and Stop Scanning collect all
       * the available devices in the 10 Second
       */
      if (!isContinuesScan) {
        handler.postDelayed(notifyScanResults, scanDelay) // Delay Period
      }
    } catch (e: Exception) {
      Timber.e(e)
    }
  }

  private val scanRunnable = Runnable { scan() }

  private val notifyScanResults =
      Runnable {
        Timber.d(
            "Notifying scan results (${bluetoothDeviceInfoMap.size} device(s) found), stop BLE scanning.")
        bluetoothScanner.stopScan(object : ScanCallback() {})
        deviceScannerSpi.onDeviceDiscovered(bluetoothDeviceInfoMap.values)
      }
}
