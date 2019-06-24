package com.ted.waveform.waveform.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat

class PermissionManager {

    companion object {

        const val REQUEST_AUDIO_PERMISSION_CODE = 1

        fun requestRecordPermission(activity: Activity) {
            if (!checkRecordStoragePermissions(activity)) {
                requestRecordStoragePermissions(activity)
            }
        }

        // WRITE_EXTERNAL_STORAGE 와 RECORD_AUDIO 퍼미션 체크
        private fun checkRecordStoragePermissions(context: Context): Boolean {
            val result = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            val result1 = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            return result == PackageManager.PERMISSION_GRANTED && result1 == PackageManager.PERMISSION_GRANTED
        }

        // WRITE_EXTERNAL_STORAGE 와 RECORD_AUDIO 퍼미션 권한 요청
        private fun requestRecordStoragePermissions(activity: Activity) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQUEST_AUDIO_PERMISSION_CODE
            )
        }
    }


}