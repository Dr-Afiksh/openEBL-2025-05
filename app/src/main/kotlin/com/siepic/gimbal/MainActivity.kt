package com.siepic.gimbal

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.siepic.gimbal.ui.GimbalScreen
import com.siepic.gimbal.ui.theme.GimbalTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GimbalTheme {
                var granted by remember { mutableStateOf(hasRequiredPermissions()) }
                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { result ->
                    granted = result.values.all { it }
                }
                GimbalScreen(
                    permissionsGranted = granted,
                    onRequestPermissions = { launcher.launch(requiredPermissions()) },
                )
            }
        }
    }

    private fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private fun hasRequiredPermissions(): Boolean = requiredPermissions().all {
        checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}
