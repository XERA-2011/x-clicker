package com.xera.xclicker.ui.share

import androidx.compose.runtime.staticCompositionLocalOf
import com.xera.xclicker.MainViewModel

val LocalMainViewModel = staticCompositionLocalOf<MainViewModel> {
    error("not found MainViewModel")
}

val LocalDarkTheme = staticCompositionLocalOf { false }

val LocalIsTalkbackEnabled = staticCompositionLocalOf {
    false
}
