package com.kavir.remote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel

val KBg = Color(0xFF0A0710)
val KPanel = Color(0xFF15101F)
val KPanel2 = Color(0xFF1D1530)
val KPurple = Color(0xFF5B2A86)
val KRed = Color(0xFFE0243A)
val KOrange = Color(0xFFFF8A1F)
val KMute = Color(0xFF9A8FB0)
val KOk = Color(0xFF35D07F)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KavirTheme { Root() } }
    }
}

@Composable
fun KavirTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = KOrange, onPrimary = Color.Black, secondary = KPurple, tertiary = KRed,
            background = KBg, surface = KPanel, surfaceVariant = KPanel2,
            onBackground = Color(0xFFF1ECF8), onSurface = Color(0xFFF1ECF8), onSurfaceVariant = KMute
        )
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(Modifier.fillMaxSize().background(KBg), color = KBg) { content() }
        }
    }
}

@Composable
fun Root() {
    val vm: MainVM = viewModel()
    if (vm.paired) MainScreen(vm, Modifier.systemBarsPadding()) else ConnectScreen(vm, Modifier.systemBarsPadding())
}
