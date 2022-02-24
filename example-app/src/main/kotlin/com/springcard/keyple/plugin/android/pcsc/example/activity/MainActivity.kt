/*
 * Copyright (c)2022 SpringCard - www.springcard.com.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */
package com.springcard.keyple.plugin.android.pcsc.example.activity

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
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
import com.springcard.keyple.plugin.android.pcsc.AndroidPcscPluginFactory
import com.springcard.keyple.plugin.android.pcsc.example.R
import com.springcard.keyple.plugin.android.pcsc.example.adapter.EventAdapter
import com.springcard.keyple.plugin.android.pcsc.example.dialog.PermissionDeniedDialog
import com.springcard.keyple.plugin.android.pcsc.example.model.EventModel
import com.springcard.keyple.plugin.android.pcsc.example.util.PermissionHelper
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

/** Activity launched on app start up that display the only screen available on this example app. */
class MainActivity : AppCompatActivity(), EventNotifierSpi {
  /** Variables for event window */
  private lateinit var adapter: RecyclerView.Adapter<*>
  private lateinit var layoutManager: RecyclerView.LayoutManager
  private val events = arrayListOf<EventModel>()

  private val readerManager: ReadersManager = ReadersManager(this)
  private val areReadersInitialized = AtomicBoolean(false)

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

    // Check whether readers are already initialized (return from background) or not (first launch)
    if (!areReadersInitialized.get()) {
      val builder = AlertDialog.Builder(this)
      builder.setTitle("Interface selection")
      builder.setMessage("Please choose the type of interface")

      builder.setPositiveButton("USB") { dialog, which ->
        Toast.makeText(applicationContext, "USB", Toast.LENGTH_SHORT).show()
        addActionEvent("Waiting for USB reader...")
        areReadersInitialized.set(readerManager.initReaders(AndroidPcscPluginFactory.Type.Link.USB))
      }

      builder.setNegativeButton("BLE") { dialog, which ->
        Toast.makeText(applicationContext, "BLE", Toast.LENGTH_SHORT).show()
        addActionEvent("Waiting for BLE reader...")
        areReadersInitialized.set(readerManager.initReaders(AndroidPcscPluginFactory.Type.Link.BLE))
      }
      builder.show()
    } else {
      addActionEvent("Start card detection")
      readerManager.startCardDetection()
    }
  }

  /** Called when the activity (screen) is destroyed or put in background */
  override fun onPause() {
    if (areReadersInitialized.get()) {
      addActionEvent("Stopping card detection")
      readerManager.stopCardDetection()
    }
    super.onPause()
  }

  /** Called when the activity (screen) is destroyed */
  override fun onDestroy() {
    readerManager.cleanUp()
    super.onDestroy()
  }

  override fun onBackPressed() {
    if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
      drawerLayout.closeDrawer(GravityCompat.START)
    } else {
      super.onBackPressed()
    }
  }

  override fun onRequestPermissionsResult(
      requestCode: Int,
      permissions: Array<out String>,
      grantResults: IntArray
  ) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
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

  override fun notifyHeader(header: String) {
    addHeaderEvent(header)
  }

  override fun notifyAction(action: String) {
    addActionEvent(action)
  }

  override fun notifyResult(result: String) {
    addResultEvent(result)
  }

//  fun checkBluetooth() {
//    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
////      val PERMISSION_REQUEST_FINE_LOCATION = 5
////      /* Android Permission check */
////      /* As of Android M (6.0) and above, location permission is required for the app          to get BLE scan results.                                  */
////      /* The main motivation behind having to explicitly require the users to grant          this permission is to protect users’ privacy.                */
////      /* A BLE scan can often unintentionally reveal the user’s location to          unscrupulous app developers who scan for specific BLE beacons,       */
////      /* or some BLE device may advertise location-specific information. Before          Android 10, ACCESS_COARSE_LOCATION can be used to gain access   */
////      /* to BLE scan results, but we recommend using ACCESS_FINE_LOCATION instead          since it works for all versions of Android.                   */
////      if (activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) !=
////        PackageManager.PERMISSION_GRANTED) {
////        activity.requestPermissions(
////          arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), PERMISSION_REQUEST_FINE_LOCATION)
////      }
//      if(!PermissionHelper.checkPermission(this, arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION))) {
//          PermissionHelper.checkPermission(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
//        }
//    }
//
//    /* Set up BLE */
//    val REQUEST_ENABLE_BT = 6
//    /* Ensures Bluetooth is available on the device and it is enabled. If not, */
//    /* displays a dialog requesting user permission to enable Bluetooth. */
//
//    bluetoothAdapter?.takeIf { it.isDisabled }?.apply {
//      val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
//      this.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
//    }
//  }
}
