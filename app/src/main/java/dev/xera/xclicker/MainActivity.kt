package dev.xera.xclicker

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.xera.xclicker.ui.MainViewModel
import dev.xera.xclicker.ui.Screen
import dev.xera.xclicker.ui.screen.HomeScreen
import dev.xera.xclicker.ui.screen.SettingsScreen
import dev.xera.xclicker.ui.theme.XClickerTheme

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as XClickerApp).container
        viewModel = ViewModelProvider(
            this,
            MainViewModel.factory(container)
        )[MainViewModel::class.java]

        setContent {
            XClickerTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                var showUrlDialog by remember { mutableStateOf(false) }
                var urlInput by remember { mutableStateOf("https://gkd667.vv.ax/gkd.json5") }

                // 监听导入结果，如果结束则自动关闭输入框
                LaunchedEffect(uiState.importResultTitle) {
                    if (uiState.importResultTitle != null) {
                        showUrlDialog = false
                    }
                }

                // 弹出最终的结果弹窗提示
                if (uiState.importResultTitle != null) {
                    AlertDialog(
                        onDismissRequest = { viewModel.clearImportResult() },
                        title = { Text(uiState.importResultTitle!!) },
                        text = { Text(uiState.importResultMsg ?: "") },
                        confirmButton = {
                            TextButton(onClick = { viewModel.clearImportResult() }) {
                                Text("确定")
                            }
                        }
                    )
                }

                if (showUrlDialog) {
                    AlertDialog(
                        onDismissRequest = { if (!uiState.isImporting) showUrlDialog = false },
                        title = { Text("从网络链接导入规则") },
                        text = {
                            if (uiState.isImporting) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    androidx.compose.material3.CircularProgressIndicator()
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("正在下载并解析规则，请稍候...")
                                }
                            } else {
                                OutlinedTextField(
                                    value = urlInput,
                                    onValueChange = { urlInput = it },
                                    label = { Text("输入规则订阅链接") },
                                    singleLine = true,
                                    enabled = !uiState.isImporting
                                )
                            }
                        },
                        confirmButton = {
                            if (!uiState.isImporting) {
                                TextButton(
                                    onClick = {
                                        if (urlInput.isNotBlank()) {
                                            viewModel.importRulesFromUrl(urlInput)
                                        }
                                    }
                                ) {
                                    Text("导入")
                                }
                            }
                        },
                        dismissButton = {
                            if (!uiState.isImporting) {
                                TextButton(onClick = { showUrlDialog = false }) {
                                    Text("取消")
                                }
                            }
                        }
                    )
                }

                Crossfade(
                    targetState = uiState.currentScreen,
                    animationSpec = tween(300),
                    label = "screenTransition"
                ) { screen ->
                    when (screen) {
                        Screen.Home -> HomeScreen(
                            uiState = uiState,
                            onNavigateToSettings = { viewModel.navigateTo(Screen.Settings) },
                            onImportRules = { showUrlDialog = true }
                        )
                        Screen.Settings -> SettingsScreen(
                            uiState = uiState,
                            onBack = { viewModel.navigateTo(Screen.Home) },
                            onSetGlobalDelay = { viewModel.setGlobalDelay(it) },
                            onImportRules = { showUrlDialog = true },
                            onExportRules = { exportRulesToClipboard() },
                            onClearRules = { viewModel.clearRules() }
                        )
                    }
                }
            }
        }
    }

    // 导出规则到剪贴板
    private fun exportRulesToClipboard() {
        Toast.makeText(this, "精简版 GKD 架构不再支持导出，请直接分享源订阅链接", Toast.LENGTH_LONG).show()
    }
}
