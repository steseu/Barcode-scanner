package com.barcodebridge.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.barcodebridge.app.ui.navigation.BarcodeBridgeApp
import com.barcodebridge.app.ui.theme.BarcodeBridgeTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Extends [AppCompatActivity] (not ComponentActivity) because the per-app
 * language feature is delivered through `AppCompatDelegate` on API < 33 -
 * with a plain ComponentActivity the in-app language switch would silently
 * do nothing on Android 12 and older.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BarcodeBridgeTheme {
                BarcodeBridgeApp()
            }
        }
    }
}
