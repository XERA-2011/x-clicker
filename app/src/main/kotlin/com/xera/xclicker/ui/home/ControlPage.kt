package com.xera.xclicker.ui.home

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import com.xera.xclicker.MainActivity
import com.xera.xclicker.R
import com.xera.xclicker.data.SubsConfig
import com.xera.xclicker.permission.appOpsRestrictedFlow
import com.xera.xclicker.permission.writeSecureSettingsState
import com.xera.xclicker.service.A11yService

import com.xera.xclicker.store.actualA11yScopeAppList
import com.xera.xclicker.store.storeFlow
import com.xera.xclicker.ui.ActionLogRoute
import com.xera.xclicker.ui.ActivityLogRoute
import com.xera.xclicker.ui.AppConfigRoute
import com.xera.xclicker.ui.AuthA11yRoute
import com.xera.xclicker.ui.WebViewRoute
import com.xera.xclicker.ui.component.GroupNameText
import com.xera.xclicker.ui.component.PerfIcon
import com.xera.xclicker.ui.component.PerfIconButton
import com.xera.xclicker.ui.component.PerfSwitch
import com.xera.xclicker.ui.component.PerfTopAppBar
import com.xera.xclicker.ui.component.textSize
import com.xera.xclicker.ui.component.useScrollBehaviorState
import com.xera.xclicker.ui.share.LocalMainViewModel
import com.xera.xclicker.ui.style.EmptyHeight
import com.xera.xclicker.ui.style.itemHorizontalPadding
import com.xera.xclicker.ui.style.itemVerticalPadding
import com.xera.xclicker.ui.style.surfaceCardColors
import com.xera.xclicker.util.HOME_PAGE_URL
import com.xera.xclicker.util.ShortUrlSet
import com.xera.xclicker.util.latestRecordDescFlow
import com.xera.xclicker.util.latestRecordFlow
import com.xera.xclicker.util.launchAsFn
import com.xera.xclicker.util.throttle

@Composable
fun useControlPage(): ScaffoldExt {
    val context = LocalActivity.current as MainActivity
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<HomeVm>()
    val scrollKey = rememberSaveable { mutableIntStateOf(0) }
    val (scrollBehavior, scrollState) = useScrollBehaviorState(scrollKey)
    LaunchedEffect(null) {
        mainVm.resetPageScrollEvent.collect {
            if (it == BottomNavItem.Control) {
                scrollKey.intValue++
            }
        }
    }
    return ScaffoldExt(
        navItem = BottomNavItem.Control,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            PerfTopAppBar(scrollBehavior = scrollBehavior, title = {
                Text(
                    text = stringResource(R.string.app_name)
                )
            }, actions = {
                PerfIconButton(
                    imageVector = PerfIcon.RocketLaunch,
                    onClickLabel = "前往工作模式页面",
                    contentDescription = "工作模式",
                    onClick = throttle {
                        mainVm.navigatePage(AuthA11yRoute)
                    },
                )
            })
        }) { contentPadding ->
        val store by storeFlow.collectAsState()

        val a11yRunning by A11yService.isRunning.collectAsState()
        val writeSecureSettings by writeSecureSettingsState.stateFlow.collectAsState()

        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .padding(contentPadding)
                .padding(horizontal = itemHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(itemHorizontalPadding / 2)
        ) {
            if (appOpsRestrictedFlow.collectAsState().value) {
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
            }
            PageSwitchItemCard(
                imageVector = PerfIcon.Memory,
                title = "服务状态",
                subtitle = if (a11yRunning) {
                    "无障碍正在运行"
                } else if (mainVm.a11yServiceEnabledFlow.collectAsState().value) {
                    "无障碍发生故障"
                } else if (writeSecureSettings) {
                    "无障碍已关闭"
                } else {
                    "无障碍未授权"
                },
                checked = a11yRunning,
                onCheckedChange = { newEnabled ->
                    if (newEnabled) {
                        if (!writeSecureSettingsState.value) {
                            mainVm.navigatePage(AuthA11yRoute)
                        } else {
                            com.xera.xclicker.a11y.setA11yServiceEnabled(true)
                        }
                    } else {
                        if (writeSecureSettingsState.value) {
                            com.xera.xclicker.a11y.setA11yServiceEnabled(false)
                        } else {
                            com.xera.xclicker.service.A11yService.instance?.disableSelf()
                        }
                    }
                },
            )



            Spacer(modifier = Modifier.height(EmptyHeight))
        }
    }
}


@Composable
private fun PageItemCard(
    imageVector: ImageVector,
    title: String,
    subtitle: String,
    onClickLabel: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                this.onClick(label = onClickLabel, action = null)
            },
        shape = MaterialTheme.shapes.large,
        colors = surfaceCardColors,
        onClick = throttle(fn = onClick)
    ) {
        IconTextCard(
            imageVector = imageVector,
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PageSwitchItemCard(
    imageVector: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val onClick = throttle { onCheckedChange(!checked) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                this.onClick(label = "切换$title", action = null)
            },
        shape = MaterialTheme.shapes.large,
        colors = surfaceCardColors,
        onClick = onClick,
    ) {
        IconTextCard(
            imageVector = imageVector,
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            PerfSwitch(
                checked = checked,
                onCheckedChange = null,
            )
        }
    }
}

@Composable
private fun IconTextCard(
    imageVector: ImageVector, content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(itemVerticalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PerfIcon(
            imageVector = imageVector,
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(8.dp)
                .size(24.dp),
            tint = MaterialTheme.colorScheme.primary,
            contentDescription = null,
        )
        Spacer(modifier = Modifier.width(itemHorizontalPadding))
        content()
    }
}

@Composable
private fun ServerStatusCard() {
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<HomeVm>()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                onClick(label = "不执行操作", action = null)
            }, shape = RoundedCornerShape(20.dp), colors = surfaceCardColors, onClick = {}) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = itemVerticalPadding,
                    end = itemVerticalPadding,
                    top = itemVerticalPadding,
                    bottom = itemVerticalPadding / 2
                ), verticalAlignment = Alignment.CenterVertically
        ) {
            PerfIcon(
                imageVector = PerfIcon.Equalizer,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(8.dp)
                    .size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(itemHorizontalPadding))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "数据概览",
                    style = MaterialTheme.typography.bodyLarge,
                )
                val usedSubsItemCount by vm.usedSubsItemCountFlow.collectAsState()
                AnimatedVisibility(usedSubsItemCount > 0) {
                    Text(
                        text = "已开启 $usedSubsItemCount 条订阅",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = itemVerticalPadding)
        ) {
            val subsStatus by vm.subsStatusFlow.collectAsState()
            AnimatedVisibility(subsStatus.isNotEmpty()) {
                Text(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    text = subsStatus,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val latestRecordDesc by latestRecordDescFlow.collectAsState()
            if (latestRecordDesc != null) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .clickable(onClickLabel = "前往应用的规则汇总页面", onClick = throttle {
                            latestRecordFlow.value?.let {
                                mainVm.navigatePage(
                                    AppConfigRoute(
                                        appId = it.appId, focusLog = it
                                    )
                                )
                            }
                        })
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                    ) {
                        GroupNameText(
                            modifier = Modifier.fillMaxWidth(),
                            preText = "最近触发: ",
                            isGlobal = latestRecordFlow.collectAsState().value?.groupType == SubsConfig.GlobalGroupType,
                            text = latestRecordDesc ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    PerfIcon(
                        imageVector = PerfIcon.KeyboardArrowRight,
                        modifier = Modifier.textSize(style = MaterialTheme.typography.bodyMedium),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(modifier = Modifier.height(itemVerticalPadding))
        }
    }
}
