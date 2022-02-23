/*
 * Copyright (c)2022 SpringCard - www.springcard.com.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */
package com.springcard.keyple.plugin.android.pcsc.example.activity

import android.Manifest
import android.app.ProgressDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.springcard.keyple.plugin.android.pcsc.AndroidPcscPlugin
import com.springcard.keyple.plugin.android.pcsc.AndroidPcscPluginFactory
import com.springcard.keyple.plugin.android.pcsc.AndroidPcscPluginFactoryProvider
import com.springcard.keyple.plugin.android.pcsc.DeviceInfo
import com.springcard.keyple.plugin.android.pcsc.example.R
import com.springcard.keyple.plugin.android.pcsc.example.util.CalypsoClassicInfo
import com.springcard.keyple.plugin.android.pcsc.spi.DeviceScannerSpi
import org.calypsonet.terminal.reader.CardReaderEvent
import org.calypsonet.terminal.reader.ObservableCardReader
import org.calypsonet.terminal.reader.spi.CardReaderObserverSpi
import org.eclipse.keyple.card.calypso.CalypsoExtensionService
import org.eclipse.keyple.core.common.KeyplePluginExtensionFactory
import org.eclipse.keyple.core.service.ObservablePlugin
import org.eclipse.keyple.core.service.ObservableReader
import org.eclipse.keyple.core.service.PluginEvent
import org.eclipse.keyple.core.service.SmartCardServiceProvider
import org.eclipse.keyple.core.service.spi.PluginObserverSpi
import timber.log.Timber

internal class ReadersManager(private val activity: MainActivity) :
    DeviceScannerSpi, PluginObserverSpi, CardReaderObserverSpi {

  private lateinit var androidPcscPlugin: ObservablePlugin
  private lateinit var cardReader: ObservableReader
  private val cardManager: CardManager = CardManager(activity)

  private val bluetoothAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
    val bluetoothManager = activity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    bluetoothManager.adapter
  }

  private val BluetoothAdapter.isDisabled: Boolean
    get() = !isEnabled

  private val calypsoCardExtensionProvider: CalypsoExtensionService =
      CalypsoExtensionService.getInstance()

  private fun showAlertDialog(t: Throwable, finish: Boolean = false, cancelable: Boolean = true) {
    val builder = androidx.appcompat.app.AlertDialog.Builder(activity)
    builder.setTitle(R.string.alert_dialog_title)
    builder.setMessage(activity.getString(R.string.alert_dialog_message, t.message))
    if (finish) {
      builder.setNegativeButton(R.string.quit) { _, _ -> activity.finish() }
    }
    val dialog = builder.create()
    dialog.setCancelable(cancelable)
    dialog.show()
  }

  /** Initializes the card reader (Contact Reader) and SAM reader (Contactless Reader) */
  fun initReaders(link: AndroidPcscPluginFactory.Type.Link): Boolean {

    Timber.d("initReaders")
    val progress = ProgressDialog(activity)
    progress.setMessage(activity.getString(R.string.please_wait))
    progress.setCancelable(false)
    progress.show()
    // Connexion to AndroidPcsc lib take time, we've added a callback to this factory.
    val pluginFactory: KeyplePluginExtensionFactory?
    try {
      Timber.d("Create plugin factory for link %s", link.name)
      pluginFactory = AndroidPcscPluginFactoryProvider.getFactory(link, activity)
    } catch (e: Exception) {
      showAlertDialog(e, finish = true, cancelable = false)
      activity.notifyResult("Unable to create plugin factory.")
      return false
    }

    // Get the instance of the SmartCardService (Singleton pattern)
    val smartCardService = SmartCardServiceProvider.getService()

    // check the card extension, any version inconsistencies will be logged
    smartCardService.checkCardExtension(calypsoCardExtensionProvider)

    // Register the AndroidPcsc with SmartCardService, get the corresponding generic Plugin in
    // return
    androidPcscPlugin = smartCardService.registerPlugin(pluginFactory) as ObservablePlugin

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      val PERMISSION_REQUEST_FINE_LOCATION = 5
      /* Android Permission check */
      /* As of Android M (6.0) and above, location permission is required for the app          to get BLE scan results.                                  */
      /* The main motivation behind having to explicitly require the users to grant          this permission is to protect users’ privacy.                */
      /* A BLE scan can often unintentionally reveal the user’s location to          unscrupulous app developers who scan for specific BLE beacons,       */
      /* or some BLE device may advertise location-specific information. Before          Android 10, ACCESS_COARSE_LOCATION can be used to gain access   */
      /* to BLE scan results, but we recommend using ACCESS_FINE_LOCATION instead          since it works for all versions of Android.                   */
      if (activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) !=
          PackageManager.PERMISSION_GRANTED) {
        activity.requestPermissions(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), PERMISSION_REQUEST_FINE_LOCATION)
      }
    }

    /* Set up BLE */
    val REQUEST_ENABLE_BT = 6
    /* Ensures Bluetooth is available on the device and it is enabled. If not, */
    /* displays a dialog requesting user permission to enable Bluetooth. */

    bluetoothAdapter?.takeIf { it.isDisabled }?.apply {
      val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
      activity.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
    }

    androidPcscPlugin
        .getExtension(AndroidPcscPlugin::class.java)
        .scanDevices(
            2,
            true,
            this,
        )

    androidPcscPlugin.setPluginObservationExceptionHandler { pluginName, e ->
      Timber.e(e, "An unexpected plugin error occurred in '%s':", pluginName)
    }

    androidPcscPlugin.addObserver(this)

    progress.dismiss()

    return true
  }

  override fun onDeviceDiscovered(deviceInfoList: MutableCollection<DeviceInfo>) {
    for (bleDeviceInfo in deviceInfoList) {
      Timber.i("Discovered devices: %s", bleDeviceInfo)
    }
    activity.notifyResult(
        "Device discovery is finished.\n${deviceInfoList.size} device(s) discovered.")
    for (deviceInfo in deviceInfoList) {
      activity.notifyResult("Device: " + deviceInfo.textInfo)
    }
    // connect to first discovered device (we should ask the user)
    if (deviceInfoList.isNotEmpty()) {
      val device = deviceInfoList.first()
      androidPcscPlugin
          .getExtension(AndroidPcscPlugin::class.java)
          .connectToDevice(device.identifier)
    }
  }

  fun startCardDetection() {
    cardReader.startCardDetection(ObservableCardReader.DetectionMode.REPEATING)
  }

  fun stopCardDetection() {
    cardReader.stopCardDetection()
  }

  fun cleanUp() {
    cardReader?.let {
      // stop propagating the reader events
      cardReader.removeObserver(this)
    }
    // Unregister all plugins
    SmartCardServiceProvider.getService().plugins.forEach {
      SmartCardServiceProvider.getService().unregisterPlugin(it.name)
    }
  }

  override fun onPluginEvent(pluginEvent: PluginEvent?) {
    if (pluginEvent != null) {
      var logMessage =
          "Plugin Event: plugin=${pluginEvent.pluginName}, event=${pluginEvent.type?.name}"
      for (readerName in pluginEvent.readerNames) {
        logMessage += ", reader=$readerName"
      }
      Timber.d(logMessage)
      activity.notifyAction("Set up reader(s).")
      var cardReaderAvailable = false
      var samReaderAvailable = false
      if (pluginEvent.type == PluginEvent.Type.READER_CONNECTED) {
        for (readerName in pluginEvent.readerNames) {
          if (readerName.toUpperCase().contains("CONTACTLESS")) {
            cardReaderAvailable = true
            onCardReaderConnected(readerName)
          } else if (readerName.toUpperCase().contains("SAM")) {
            samReaderAvailable = true
            onSamReaderConnected(readerName)
          }
        }
      }
      if (!samReaderAvailable) {
        activity.notifyResult("No SAM reader available. Continue without security")
      }
      if (cardReaderAvailable) {
        activity.notifyHeader("Waiting for a card...")
      }
      if (pluginEvent.type == PluginEvent.Type.READER_DISCONNECTED) {
        activity.notifyAction("Reader '${pluginEvent.readerNames.first()}' connected.")
      }
    }
  }

  private fun onCardReaderConnected(readerName: String) {
    cardReader = androidPcscPlugin.getReader(readerName) as ObservableReader

    cardReader.setReaderObservationExceptionHandler { pluginName, readerName, e ->
      Timber.e("An unexpected reader error occurred: %s:%s", pluginName, readerName)
    }

    cardReader.addObserver(this)

    cardReader.startCardDetection(ObservableCardReader.DetectionMode.REPEATING)

    cardManager.initiateScheduledCardSelection(cardReader)
  }

  private fun onSamReaderConnected(readerName: String) {
    cardManager.setupSecurityService(
        androidPcscPlugin, readerName, CalypsoClassicInfo.SAM_PROFILE_NAME)
  }

  override fun onReaderEvent(readerEvent: CardReaderEvent?) {
    cardManager.onReaderEvent(readerEvent)
  }
}
