package com.xera.xclicker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import com.xera.xclicker.ui.component.PerfIcon
import com.xera.xclicker.ui.component.PerfIconButton
import com.xera.xclicker.ui.component.PerfTopAppBar
import com.xera.xclicker.ui.share.LocalMainViewModel
import com.xera.xclicker.util.openA11ySettings

@Serializable
data object AuthA11yRoute : NavKey

@Composable
fun AuthA11yPage() {
    val mainVm = LocalMainViewModel.current
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), topBar = {
        PerfTopAppBar(scrollBehavior = scrollBehavior, navigationIcon = {
            PerfIconButton(
                imageVector = PerfIcon.ArrowBack,
                onClick = {
                    mainVm.popPage()
                })
        }, title = {
            Text(text = "工作模式")
        })
    }) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = "无障碍模式")
            Text(text = "请前往系统设置，在「已下载的应用」或「无障碍」服务列表中，找到 xClicker 并开启。")
            TextButton(
                onClick = { openA11ySettings() }
            ) {
                Text(text = "前往授权")
            }
        }
    }
}
