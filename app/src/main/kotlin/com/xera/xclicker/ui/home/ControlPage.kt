package com.xera.xclicker.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import com.xera.xclicker.a11y.setA11yServiceEnabled
import com.xera.xclicker.permission.appOpsRestrictedFlow
import com.xera.xclicker.permission.writeSecureSettingsState
import com.xera.xclicker.service.A11yService
import com.xera.xclicker.ui.AuthA11yRoute
import com.xera.xclicker.ui.component.PerfIcon
import com.xera.xclicker.ui.share.LocalMainViewModel
import com.xera.xclicker.ui.style.EmptyHeight
import com.xera.xclicker.ui.style.itemHorizontalPadding
import com.xera.xclicker.ui.style.itemVerticalPadding
import com.xera.xclicker.util.ShortUrlSet
import com.xera.xclicker.util.throttle

@Serializable
data object HomeRoute : NavKey

@Composable
fun ControlPage() {
    val mainVm = LocalMainViewModel.current
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
    ) { contentPadding ->
        val a11yRunning by A11yService.isRunning.collectAsState()
        val appOpsRestricted by appOpsRestrictedFlow.collectAsState()
        val writeSecureSettings by writeSecureSettingsState.stateFlow.collectAsState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(contentPadding)
                .padding(horizontal = itemHorizontalPadding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (appOpsRestricted) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {
                            this.onClick(label = "前往解除限制页面", action = null)
                        },
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    onClick = throttle {
                        mainVm.navigateWebPage(ShortUrlSet.URL2)
                    },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(itemVerticalPadding),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PerfIcon(imageVector = PerfIcon.WarningAmber)
                        Text(
                            modifier = Modifier.weight(1f),
                            text = "检测到权限受限制，请前往解除",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        PerfIcon(imageVector = PerfIcon.KeyboardArrowRight)
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
            
            val isRunning = a11yRunning
            val onCheckedChange: (Boolean) -> Unit = { newEnabled ->
                if (newEnabled) {
                    if (!writeSecureSettings) {
                        mainVm.navigatePage(AuthA11yRoute)
                    } else {
                        setA11yServiceEnabled(true)
                    }
                } else {
                    if (writeSecureSettings) {
                        setA11yServiceEnabled(false)
                    } else {
                        A11yService.instance?.disableSelf()
                    }
                }
            }

            // Title
            Text(
                text = "无障碍模式",
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Status Text
            Text(
                text = if (isRunning) "当前状态：已开启" else "当前状态：已关闭",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (isRunning) Color(0xFF4CAF50) else Color(0xFF999999),
                modifier = Modifier.padding(bottom = 48.dp)
            )

            // Custom Switch (HTML styled)
            val thumbOffset by animateDpAsState(
                targetValue = if (isRunning) 50.dp else 0.dp,
                animationSpec = tween(durationMillis = 350)
            )
            val trackColor by animateColorAsState(
                targetValue = if (isRunning) Color(0xFF4CAF50) else Color(0xFF333333),
                animationSpec = tween(durationMillis = 350)
            )

            Box(
                modifier = Modifier
                    .size(width = 110.dp, height = 60.dp)
                    .clip(CircleShape)
                    .background(trackColor)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onCheckedChange(!isRunning) }
            ) {
                Box(
                    modifier = Modifier
                        .padding(6.dp)
                        .offset(x = thumbOffset)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .shadow(8.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.15f), spotColor = Color.Black.copy(alpha = 0.15f))
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            IconButton(
                onClick = throttle { mainVm.navigatePage(SettingsRoute) },
                modifier = Modifier.size(60.dp)
            ) {
                Icon(
                    imageVector = PerfIcon.Settings,
                    contentDescription = "设置",
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(EmptyHeight))
        }
    }
}
