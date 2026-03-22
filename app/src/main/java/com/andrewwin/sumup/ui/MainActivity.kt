package com.andrewwin.sumup.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import com.andrewwin.sumup.ui.theme.SumUpTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalTextToolbar
import android.os.Build
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.core.os.LocaleListCompat
import com.andrewwin.sumup.data.local.entities.AppLanguage

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val mainViewModel: MainViewModel = hiltViewModel()
            val prefs by mainViewModel.userPreferences.collectAsState()

            LaunchedEffect(prefs.appLanguage) {
                val langTag = when (prefs.appLanguage) {
                    AppLanguage.UK -> "uk"
                    AppLanguage.EN -> "en"
                }
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(langTag))
            }

            SumUpTheme(themeMode = prefs.appThemeMode) {
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
