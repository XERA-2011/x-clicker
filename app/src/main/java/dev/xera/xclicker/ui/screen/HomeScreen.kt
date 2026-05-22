package dev.xera.xclicker.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.xera.xclicker.data.model.AppRuleSet
import dev.xera.xclicker.service.XClickerService
import dev.xera.xclicker.ui.UiState
import dev.xera.xclicker.ui.component.ActionLogView
import dev.xera.xclicker.ui.component.RuleItem
import dev.xera.xclicker.ui.component.StatusCard
import dev.xera.xclicker.ui.theme.GradientEnd
import dev.xera.xclicker.ui.theme.GradientMid
import dev.xera.xclicker.ui.theme.GradientStart
import dev.xera.xclicker.util.AccessibilityHelper

@Composable
fun HomeScreen(
    uiState: UiState,
    onNavigateToSettings: () -> Unit,
    onImportRules: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isAccessibilityEnabled = remember { mutableStateOf(false) }

    // 检查无障碍服务状态
    LaunchedEffect(uiState.isServiceRunning) {
        isAccessibilityEnabled.value = AccessibilityHelper.isAccessibilityServiceEnabled(
            context, XClickerService::class.java
        )
    }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── 顶部标题区域 ──
        item {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn() + slideInVertically { -it / 2 }
            ) {
                Column(modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)) {
                    Text(
                        text = "x-clicker",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "轻量自动化点击引擎",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ── 无障碍服务引导卡片 ──
        if (!isAccessibilityEnabled.value) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                    )
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "⚠️ 无障碍服务未启用",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "请先在系统设置中启用 x-clicker 无障碍服务，才能使用自动点击功能。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { AccessibilityHelper.openAccessibilitySettings(context) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("前往设置")
                        }
                    }
                }
            }
        }

        // ── 状态卡片 ──
        item {
            StatusCard(
                isRunning = uiState.isServiceRunning,
                ruleCount = uiState.ruleCount,
                onToggle = {
                    AccessibilityHelper.openAccessibilitySettings(context)
                }
            )
        }

        // ── 动作日志卡片 ──
        item {
            ActionLogView()
        }

        // ── 操作按钮区 ──
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledTonalButton(
                    onClick = onImportRules,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("导入规则")
                }
                FilledTonalButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("设置")
                }
            }
        }

        // ── 规则列表标题 ──
        item {
            Text(
                text = "已加载规则",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // ── 规则列表 ──
        if (uiState.rules.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Text(
                        text = "暂无规则\n请导入李跳跳格式的规则文件",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp)
                    )
                }
            }
        } else {
            itemsIndexed(
                items = uiState.rules,
                key = { _, item -> item.packageHash }
            ) { _, ruleSet ->
                RuleItem(appRuleSet = ruleSet)
            }
        }

        // 底部间距
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}
