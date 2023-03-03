package com.tutorial.bluetooth.utils

import android.os.Build
import androidx.annotation.RequiresApi

object Contracts {
    /**
     * Variable
     */
    const val PERMISSION_CODE = 0

    /**
     * Permission
     */
    const val PERMISSION_FINE_LOCATION =  android.Manifest.permission.ACCESS_FINE_LOCATION
    const val PERMISSION_COARSE_LOCATION =  android.Manifest.permission.ACCESS_COARSE_LOCATION
    const val PERMISSION_BLUETOOTH_ADMIN = android.Manifest.permission.BLUETOOTH_ADMIN
    @RequiresApi(Build.VERSION_CODES.S)
    const val PERMISSION_BLUETOOTH_CONNECT = android.Manifest.permission.BLUETOOTH_CONNECT

    val location_permission = arrayOf(PERMISSION_FINE_LOCATION, PERMISSION_COARSE_LOCATION)
}