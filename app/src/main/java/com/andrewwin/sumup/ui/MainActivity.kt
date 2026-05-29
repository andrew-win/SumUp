package com.andrewwin.sumup.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.andrewwin.sumup.domain.entities.settings.AppLanguage
import com.andrewwin.sumup.ui.theme.SumUpTheme
import dagger.hilt.android.AndroidEntryPoint

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
                MainScreen()
            }
        }
    }
}





