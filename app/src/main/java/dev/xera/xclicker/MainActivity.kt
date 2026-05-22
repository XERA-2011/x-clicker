package dev.xera.xclicker

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
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
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel

    // 文件选择器：用于导入规则 JSON 文件
    private val getJsonFile = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val json = reader.readText()
                    viewModel.importRules(json)
                }
            } catch (e: Exception) {
                Toast.makeText(this, "读取规则文件失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

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
                var showImportDialog by remember { mutableStateOf(false) }
                var showUrlDialog by remember { mutableStateOf(false) }
                var urlInput by remember { mutableStateOf("https://gkd667.vv.ax/gkd.json5") }

                // 监听导入结果并弹出 Toast 提示
                LaunchedEffect(uiState.importResult) {
                    uiState.importResult?.let { result ->
                        Toast.makeText(this@MainActivity, result, Toast.LENGTH_LONG).show()
                        viewModel.clearImportResult()
                    }
                }

                // 导入选择对话框
                if (showImportDialog) {
                    AlertDialog(
                        onDismissRequest = { showImportDialog = false },
                        title = { Text("导入规则") },
                        text = { Text("您可以从系统剪贴板导入规则文本，或者选择本地的 JSON 规则文件。") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showImportDialog = false
                                    importFromClipboard()
                                }
                            ) {
                                Text("从剪贴板导入")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    showImportDialog = false
                                    getJsonFile.launch("application/json")
                                }
                            ) {
                                Text("选择本地文件")
                            }
                            TextButton(
                                onClick = {
                                    showImportDialog = false
                                    showUrlDialog = true
                                }
                            ) {
                                Text("从网络链接导入")
                            }
                        }
                    )
                }

                if (showUrlDialog) {
                    AlertDialog(
                        onDismissRequest = { showUrlDialog = false },
                        title = { Text("从网络链接导入") },
                        text = {
                            OutlinedTextField(
                                value = urlInput,
                                onValueChange = { urlInput = it },
                                label = { Text("输入规则订阅链接") },
                                singleLine = true
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showUrlDialog = false
                                    if (urlInput.isNotBlank()) {
                                        viewModel.importRulesFromUrl(urlInput)
                                    }
                                }
                            ) {
                                Text("导入")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showUrlDialog = false }) {
                                Text("取消")
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
                            onImportRules = { showImportDialog = true }
                        )
                        Screen.Settings -> SettingsScreen(
                            uiState = uiState,
                            onBack = { viewModel.navigateTo(Screen.Home) },
                            onSetGlobalDelay = { viewModel.setGlobalDelay(it) },
                            onImportRules = { showImportDialog = true },
                            onExportRules = { exportRulesToClipboard() },
                            onClearRules = { viewModel.clearRules() }
                        )
                    }
                }
            }
        }
    }

    // 从剪贴板读取并导入规则
    private fun importFromClipboard() {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = clipboard.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val text = clipData.getItemAt(0).text?.toString()
                if (!text.isNullOrBlank()) {
                    viewModel.importRules(text)
                } else {
                    Toast.makeText(this, "剪贴板中没有文本内容", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "剪贴板为空，请先复制规则文本", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "从剪贴板读取失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // 导出规则到剪贴板
    private fun exportRulesToClipboard() {
        try {
            val json = viewModel.exportRules()
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = android.content.ClipData.newPlainText("x-clicker rules", json)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "规则已成功导出并复制到剪贴板！", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "导出规则失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
