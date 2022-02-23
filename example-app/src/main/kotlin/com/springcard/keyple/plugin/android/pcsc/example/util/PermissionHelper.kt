/*
 * Copyright (c)2022 SpringCard - www.springcard.com.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */
package com.springcard.keyple.plugin.android.pcsc.example.util

import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object PermissionHelper {

  const val MY_PERMISSIONS_REQUEST_ALL = 1000

  private fun isPermissionGranted(activity: Activity, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(activity, permission) ==
        PackageManager.PERMISSION_GRANTED
  }

  fun checkPermission(context: Activity, permissions: Array<String>): Boolean {
    val permissionDenied = permissions.filter { !isPermissionGranted(context, it) }

    if (permissionDenied.isNotEmpty()) {
      var position = 0
      val permissionsToAsk = arrayOfNulls<String>(permissionDenied.size)
      for (permission in permissionDenied) {
        permissionsToAsk[position] = permission
        position++
      }
      ActivityCompat.requestPermissions(context, permissionsToAsk, MY_PERMISSIONS_REQUEST_ALL)
      return false
    }
    return true
  }
}
