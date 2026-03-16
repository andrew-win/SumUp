package com.andrewwin.sumup.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.andrewwin.sumup.ui.theme.SumUpTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalTextToolbar
import android.os.Build

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SumUpTheme {
                val isMiuiDevice = Build.MANUFACTURER.equals("Xiaomi", true) ||
                    Build.BRAND.equals("Xiaomi", true) ||
                    Build.BRAND.equals("Redmi", true) ||
                    Build.BRAND.equals("POCO", true)
                if (isMiuiDevice) {
                    CompositionLocalProvider(LocalTextToolbar provides NoOpTextToolbar) {
                        MainScreen()
                    }
                } else {
                    MainScreen()
                }
            }
        }
    }
}
