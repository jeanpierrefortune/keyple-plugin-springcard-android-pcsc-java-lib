/*
 * Copyright (c)2022 SpringCard - www.springcard.com.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */
package com.springcard.keyple.plugin.android.pcsc.example.activity

import android.Manifest
import android.app.AlertDialog
import android.app.ProgressDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.springcard.keyple.plugin.android.pcsc.AndroidPcscPlugin
import com.springcard.keyple.plugin.android.pcsc.AndroidPcscPluginFactory
import com.springcard.keyple.plugin.android.pcsc.AndroidPcscPluginFactoryProvider
import com.springcard.keyple.plugin.android.pcsc.AndroidPcscSupportContactlessProtocols
import com.springcard.keyple.plugin.android.pcsc.DeviceInfo
import com.springcard.keyple.plugin.android.pcsc.example.R
import com.springcard.keyple.plugin.android.pcsc.example.adapter.EventAdapter
import com.springcard.keyple.plugin.android.pcsc.example.dialog.PermissionDeniedDialog
import com.springcard.keyple.plugin.android.pcsc.example.model.EventModel
import com.springcard.keyple.plugin.android.pcsc.example.util.CalypsoClassicInfo
import com.springcard.keyple.plugin.android.pcsc.example.util.PermissionHelper
import com.springcard.keyple.plugin.android.pcsc.spi.DeviceScannerSpi
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.calypsonet.terminal.calypso.card.CalypsoCard
import org.calypsonet.terminal.calypso.transaction.CardSecuritySetting
import org.calypsonet.terminal.reader.CardReaderEvent
import org.calypsonet.terminal.reader.ObservableCardReader
import org.calypsonet.terminal.reader.selection.CardSelectionManager
import org.calypsonet.terminal.reader.spi.CardReaderObserverSpi
import org.eclipse.keyple.card.calypso.CalypsoExtensionService
import org.eclipse.keyple.core.common.KeyplePluginExtensionFactory
import org.eclipse.keyple.core.service.ConfigurableReader
import org.eclipse.keyple.core.service.KeyplePluginException
import org.eclipse.keyple.core.service.ObservablePlugin
import org.eclipse.keyple.core.service.ObservableReader
import org.eclipse.keyple.core.service.Plugin
import org.eclipse.keyple.core.service.PluginEvent
import org.eclipse.keyple.core.service.SmartCardServiceProvider
import org.eclipse.keyple.core.service.spi.PluginObserverSpi
import org.eclipse.keyple.core.util.ByteArrayUtil
import timber.log.Timber

/** Activity launched on app start up that display the only screen available on this example app. */
class MainActivity :
    AppCompatActivity(), PluginObserverSpi, CardReaderObserverSpi, DeviceScannerSpi {
  /** Variables for event window */
  private lateinit var adapter: RecyclerView.Adapter<*>
  private lateinit var layoutManager: RecyclerView.LayoutManager
  private val events = arrayListOf<EventModel>()

  private lateinit var androidPcscPlugin: Plugin
  private lateinit var cardReader: ObservableReader
  private val calypsoCardExtensionProvider: CalypsoExtensionService =
      CalypsoExtensionService.getInstance()
  private lateinit var cardSelectionManager: CardSelectionManager
  private lateinit var cardProtocol: AndroidPcscSupportContactlessProtocols
  private var cardSecuritySettings: CardSecuritySetting? = null

  private val areReadersInitialized = AtomicBoolean(false)

  private lateinit var progress: ProgressDialog

  private val bluetoothAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
    val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    bluetoothManager.adapter
  }

  private val BluetoothAdapter.isDisabled: Boolean
    get() = !isEnabled

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    setSupportActionBar(toolbar)
    supportActionBar?.title = "Keyple Demo"
    supportActionBar?.subtitle = "SpringCard AndroidPcsc Plugin"

    /** Init recycler view */
    adapter = EventAdapter(events)
    layoutManager = LinearLayoutManager(this)
    eventRecyclerView.layoutManager = layoutManager
    eventRecyclerView.adapter = adapter
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  }

  private fun showAlertDialog(t: Throwable, finish: Boolean = false, cancelable: Boolean = true) {
    val builder = androidx.appcompat.app.AlertDialog.Builder(this@MainActivity)
    builder.setTitle(R.string.alert_dialog_title)
    builder.setMessage(getString(R.string.alert_dialog_message, t.message))
    if (finish) {
      builder.setNegativeButton(R.string.quit) { _, _ -> finish() }
    }
    val dialog = builder.create()
    dialog.setCancelable(cancelable)
    dialog.show()
  }

  private fun clearEvents() {
    events.clear()
    adapter.notifyDataSetChanged()
  }

  private fun addHeaderEvent(message: String) {
    events.add(EventModel(EventModel.TYPE_HEADER, message))
    updateList()
    Timber.d("Header: %s", message)
  }

  private fun addActionEvent(message: String) {
    events.add(EventModel(EventModel.TYPE_ACTION, message))
    updateList()
    Timber.d("Action: %s", message)
  }

  private fun addResultEvent(message: String) {
    events.add(EventModel(EventModel.TYPE_RESULT, message))
    updateList()
    Timber.d("Result: %s", message)
  }

  private fun updateList() {
    CoroutineScope(Dispatchers.Main).launch {
      adapter.notifyDataSetChanged()
      adapter.notifyItemInserted(events.lastIndex)
      eventRecyclerView.smoothScrollToPosition(events.size - 1)
    }
  }

  /** Called when the activity (screen) is first displayed or resumed from background */
  override fun onResume() {
    super.onResume()

    progress = ProgressDialog(this)
    progress.setMessage(getString(R.string.please_wait))
    progress.setCancelable(false)

    // Check whether readers are already initialized (return from background) or not (first launch)
    if (!areReadersInitialized.get()) {
      val builder = AlertDialog.Builder(this)
      builder.setTitle("Interface selection")
      builder.setMessage("Please choose the type of interface")
      //      builder.setPositiveButton("OK", DialogInterface.OnClickListener(function = x))

      builder.setPositiveButton("USB") { dialog, which ->
        Toast.makeText(applicationContext, "USB", Toast.LENGTH_SHORT).show()
        progress.show()
        addActionEvent("Enabling USB Reader mode")
        initReaders(AndroidPcscPluginFactory.Type.Link.USB)
      }

      builder.setNegativeButton("BLE") { dialog, which ->
        Toast.makeText(applicationContext, "BLE", Toast.LENGTH_SHORT).show()
        progress.show()
        addActionEvent("Enabling BLE Reader mode")
        initReaders(AndroidPcscPluginFactory.Type.Link.BLE)
      }
      builder.show()
    } else {
      addActionEvent("Start card Read Write Mode")
      cardReader.startCardDetection(ObservableCardReader.DetectionMode.REPEATING)
    }
  }

  /** Initializes the card reader (Contact Reader) and SAM reader (Contactless Reader) */
  private fun initReaders(link: AndroidPcscPluginFactory.Type.Link) {
    Timber.d("initReaders")
    // Connexion to AndroidPcsc lib take time, we've added a callback to this factory.
    GlobalScope.launch {
      val pluginFactory: KeyplePluginExtensionFactory?
      try {
        Timber.d("Create plugin factory for link ${link.name}")
        pluginFactory =
            withContext(Dispatchers.IO) {
              AndroidPcscPluginFactoryProvider.getFactory(link, applicationContext)
            }
      } catch (e: Exception) {
        withContext(Dispatchers.Main) { showAlertDialog(e, finish = true, cancelable = false) }
        return@launch
      }

      // Get the instance of the SmartCardService (Singleton pattern)
      val smartCardService = SmartCardServiceProvider.getService()

      // check the card extension, any version inconsistencies will be logged
      smartCardService.checkCardExtension(calypsoCardExtensionProvider)

      // Register the AndroidPcsc with SmartCardService, get the corresponding generic Plugin in
      // return
      androidPcscPlugin = smartCardService.registerPlugin(pluginFactory)

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val PERMISSION_REQUEST_FINE_LOCATION = 5
        /* Android Permission check */
        /* As of Android M (6.0) and above, location permission is required for the app          to get BLE scan results.                                  */
        /* The main motivation behind having to explicitly require the users to grant          this permission is to protect users’ privacy.                */
        /* A BLE scan can often unintentionally reveal the user’s location to          unscrupulous app developers who scan for specific BLE beacons,       */
        /* or some BLE device may advertise location-specific information. Before          Android 10, ACCESS_COARSE_LOCATION can be used to gain access   */
        /* to BLE scan results, but we recommend using ACCESS_FINE_LOCATION instead          since it works for all versions of Android.                   */
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED) {
          requestPermissions(
              arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), PERMISSION_REQUEST_FINE_LOCATION)
        }
      }

      /* Set up BLE */
      val REQUEST_ENABLE_BT = 6
      /* Ensures Bluetooth is available on the device and it is enabled. If not, */
      /* displays a dialog requesting user permission to enable Bluetooth. */

      bluetoothAdapter?.takeIf { it.isDisabled }?.apply {
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
      }

      addActionEvent("Scanning compliant ${link.name} devices...")
      androidPcscPlugin
          .getExtension(AndroidPcscPlugin::class.java)
          .scanDevices(
              2,
              true,
              this@MainActivity,
          )

      (androidPcscPlugin as ObservablePlugin).setPluginObservationExceptionHandler { pluginName, e
        ->
        Timber.e("An unexpected reader error occurred: $pluginName : $e")
      }

      (androidPcscPlugin as ObservablePlugin).addObserver(this@MainActivity)

      withContext(Dispatchers.Main) { progress.dismiss() }
    }
  }

  /** Called when the activity (screen) is destroyed or put in background */
  override fun onPause() {
    if (areReadersInitialized.get()) {
      addActionEvent("Stopping card Read Write Mode")
      // Stop NFC card detection
      cardReader.stopCardDetection()
    }
    super.onPause()
  }

  /** Called when the activity (screen) is destroyed */
  override fun onDestroy() {
    cardReader?.let {
      // stop propagating the reader events
      cardReader.removeObserver(this)
    }

    // Unregister the AndroidPcsc plugin
    SmartCardServiceProvider.getService().plugins.forEach {
      SmartCardServiceProvider.getService().unregisterPlugin(it.name)
    }

    super.onDestroy()
  }

  override fun onBackPressed() {
    if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
      drawerLayout.closeDrawer(GravityCompat.START)
    } else {
      super.onBackPressed()
    }
  }

  override fun onReaderEvent(readerEvent: CardReaderEvent?) {
    addResultEvent("New ReaderEvent received : ${readerEvent?.type?.name}")

    CoroutineScope(Dispatchers.Main).launch {
      when (readerEvent?.type) {
        CardReaderEvent.Type.CARD_MATCHED -> {
          val selectionsResult =
              cardSelectionManager.parseScheduledCardSelectionsResponse(
                  readerEvent.scheduledCardSelectionsResponse)
          val calypsoCard = selectionsResult.activeSmartCard as CalypsoCard
          addResultEvent(
              "Card ${ByteArrayUtil.toHex(calypsoCard.applicationSerialNumber)} detected with DFNAME: ${ByteArrayUtil.toHex(calypsoCard.dfName)}")
          val efEnvironmentHolder =
              calypsoCard.getFileBySfi(CalypsoClassicInfo.SFI_EnvironmentAndHolder)
          addResultEvent(
              "Environment and Holder file:\n${
              ByteArrayUtil.toHex(
                efEnvironmentHolder.data.content
              )
            }")
          GlobalScope.launch(Dispatchers.IO) {
            try {
              CardManager.runCardTransaction(cardReader, calypsoCard, cardSecuritySettings)
              val counter =
                  calypsoCard
                      .getFileBySfi(CalypsoClassicInfo.SFI_Counter1)
                      .data
                      .getContentAsCounterValue(CalypsoClassicInfo.RECORD_NUMBER_1.toInt())
              val eventLog =
                  ByteArrayUtil.toHex(
                      calypsoCard.getFileBySfi(CalypsoClassicInfo.SFI_EventLog).data.content)
              addResultEvent("Counter value: $counter")
              addResultEvent("EventLog file:\n$eventLog")
            } catch (e: KeyplePluginException) {
              Timber.e(e)
              addResultEvent("Exception: ${e.message}")
            } catch (e: Exception) {
              Timber.e(e)
              addResultEvent("Exception: ${e.message}")
            }
          }
          cardReader.finalizeCardProcessing()
        }
        CardReaderEvent.Type.CARD_INSERTED -> {
          addResultEvent("Card detected but AID didn't match with ${CalypsoClassicInfo.AID}")
          cardReader.finalizeCardProcessing()
        }
        CardReaderEvent.Type.CARD_REMOVED -> {
          addResultEvent("Card removed")
        }
        else -> {
          // Do nothing
        }
      }
    }
  }

  override fun onRequestPermissionsResult(
      requestCode: Int,
      permissions: Array<out String>,
      grantResults: IntArray
  ) {
    when (requestCode) {
      PermissionHelper.MY_PERMISSIONS_REQUEST_ALL -> {
        val storagePermissionGranted =
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        if (!storagePermissionGranted) {
          PermissionDeniedDialog().apply {
            show(supportFragmentManager, PermissionDeniedDialog::class.java.simpleName)
          }
        }
        return
      }
      // Add other 'when' lines to check for other
      // permissions this app might request.
      else -> {
        // Ignore all other requests.
      }
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
      if (pluginEvent.type == PluginEvent.Type.READER_CONNECTED) {
        for (readerName in pluginEvent.readerNames) {
          if (readerName.toUpperCase().contains("CONTACTLESS")) {
            onCardReaderConnected(readerName)
          } else if (readerName.toUpperCase().contains("SAM")) {
            onSamReaderConnected(readerName)
          }
        }
      }
      if (pluginEvent.type == PluginEvent.Type.READER_DISCONNECTED) {
        addActionEvent("Reader '${pluginEvent.readerNames.first()}' connected.")
      }
      // handle reader disconnection here (PluginEvent.Type.READER_DISCONNECTED)
    }
  }

  private fun onCardReaderConnected(readerName: String) {

    addActionEvent("Card reader '$readerName' connected.")

    // Get and configure the card reader
    cardReader = androidPcscPlugin.getReader(readerName) as ObservableReader
    cardReader.setReaderObservationExceptionHandler { pluginName, readerName, e ->
      Timber.e("An unexpected reader error occurred: $pluginName:$readerName : $e")
    }

    // Set the current activity as Observer of the card reader
    cardReader.addObserver(this@MainActivity)

    cardProtocol = AndroidPcscSupportContactlessProtocols.ALL
    // Activate protocols for the card reader
    (cardReader as ConfigurableReader).activateProtocol(cardProtocol.key, cardProtocol.key)

    areReadersInitialized.set(true)

    cardReader.startCardDetection(ObservableCardReader.DetectionMode.REPEATING)

    addActionEvent("Prepare Calypso card Selection with AID: ${CalypsoClassicInfo.AID}")
    cardSelectionManager = CardManager.initiateScheduledCardSelection(cardReader)
  }

  private fun onSamReaderConnected(readerName: String) {
    addActionEvent("SAM reader '$readerName' connected.")

    CardManager.setupCardResourceService(
        androidPcscPlugin, readerName, CalypsoClassicInfo.SAM_PROFILE_NAME)

    cardSecuritySettings = CardManager.getSecuritySettings()
  }

  override fun onDeviceDiscovered(deviceInfoList: MutableCollection<DeviceInfo>) {
    for (bleDeviceInfo in deviceInfoList) {
      Timber.i("Discovered devices: $bleDeviceInfo")
    }
    addActionEvent("Device discovery is finished.\n${deviceInfoList.size} device(s) discovered.")
    for (deviceInfo in deviceInfoList) {
      addActionEvent("Device: " + deviceInfo.textInfo)
    }
    // connect to first discovered device (we should ask the user)
    if (deviceInfoList.isNotEmpty()) {
      val device = deviceInfoList.first()
      androidPcscPlugin
          .getExtension(AndroidPcscPlugin::class.java)
          .connectToDevice(device.identifier)
    }
  }
}
