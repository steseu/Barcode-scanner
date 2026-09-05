package com.barcodebridge.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import com.barcodebridge.app.ui.navigation.BarcodeBridgeApp
import com.barcodebridge.app.ui.theme.BarcodeBridgeTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BarcodeBridgeTheme(darkTheme = isSystemInDarkTheme()) {
                BarcodeBridgeApp()
            }
        }
    }
}
