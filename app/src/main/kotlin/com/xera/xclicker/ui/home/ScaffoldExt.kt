package com.xera.xclicker.ui.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.xera.xclicker.ui.component.PerfTopAppBar

data class ScaffoldExt(
    val navItem: BottomNavItem,
    val modifier: Modifier = Modifier,
    val topBar: @Composable () -> Unit = {
        PerfTopAppBar(title = {
            Text(
                text = navItem.label,
            )
        })
    },
    val floatingActionButton: @Composable () -> Unit = {},
    val content: @Composable (PaddingValues) -> Unit
)

