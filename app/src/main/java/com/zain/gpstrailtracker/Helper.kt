package com.zain.gpstrailtracker

import android.Manifest.permission
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE

import androidx.core.content.ContextCompat

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.os.Build.VERSION

import android.os.Environment

import android.os.Build.VERSION.SDK_INT
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.content.Context

import androidx.core.app.ActivityCompat.startActivityForResult

import android.content.Intent
import android.net.Uri

import androidx.test.core.app.ApplicationProvider.getApplicationContext

import android.os.Build.VERSION.SDK_INT
import android.provider.Settings
import java.lang.Exception


object Helper {

    const val READ_REQUEST=100
    const val WRITE_REQUEST=200


    //check and request storage read write permissions
    fun isStoragePermissionGranted(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < 23) {
            return true
        }
        return if (Build.VERSION.SDK_INT >= 26) {
            if (activity.checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE") == PackageManager.PERMISSION_GRANTED) {
                return true
            }
            ActivityCompat.requestPermissions(
                activity,
                arrayOf("android.permission.WRITE_EXTERNAL_STORAGE"),
                WRITE_REQUEST
            )
            false
        } else if (activity.checkSelfPermission("android.permission.READ_EXTERNAL_STORAGE") == PackageManager.PERMISSION_GRANTED && activity.checkSelfPermission(
                "android.permission.WRITE_EXTERNAL_STORAGE"
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            true
        } else {
            if (activity.checkSelfPermission("android.permission.READ_EXTERNAL_STORAGE") != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf("android.permission.READ_EXTERNAL_STORAGE"),
                    READ_REQUEST
                )
                false
            } else if (activity.checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE") != PackageManager.PERMISSION_GRANTED) {
                false
            } else {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf("android.permission.WRITE_EXTERNAL_STORAGE"),
                    WRITE_REQUEST
                )
                false
            }
        }
    }


}