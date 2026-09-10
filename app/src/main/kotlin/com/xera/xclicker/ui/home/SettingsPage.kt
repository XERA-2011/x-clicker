package com.xera.xclicker.ui.home

import android.view.KeyEvent
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import com.xera.xclicker.MainActivity
import com.xera.xclicker.R
import com.xera.xclicker.store.storeFlow
import com.xera.xclicker.ui.component.CustomOutlinedTextField
import com.xera.xclicker.ui.component.FullscreenDialog
import com.xera.xclicker.ui.component.PerfCustomIconButton
import com.xera.xclicker.ui.component.PerfIcon
import com.xera.xclicker.ui.AboutRoute
import com.xera.xclicker.ui.component.PerfIconButton
import com.xera.xclicker.ui.component.PerfTopAppBar
import com.xera.xclicker.ui.component.SettingItem
import com.xera.xclicker.ui.component.TextListDialog
import com.xera.xclicker.ui.component.TextMenu
import com.xera.xclicker.ui.component.TextSwitch
import com.xera.xclicker.ui.component.autoFocus
import com.xera.xclicker.ui.component.updateDialogOptions
import com.xera.xclicker.ui.component.useScrollBehaviorState
import com.xera.xclicker.ui.component.waitResult
import com.xera.xclicker.ui.share.LocalMainViewModel
import com.xera.xclicker.ui.share.asMutableState
import com.xera.xclicker.ui.style.EmptyHeight
import com.xera.xclicker.ui.style.iconTextSize
import com.xera.xclicker.ui.style.itemHorizontalPadding
import com.xera.xclicker.ui.style.titleItemPadding
import com.xera.xclicker.util.AndroidTarget
import com.xera.xclicker.util.findOption
import com.xera.xclicker.util.launchAsFn
import com.xera.xclicker.util.mapState
import com.xera.xclicker.util.openAppDetailsSettings
import com.xera.xclicker.util.saveFileToDownloads
import com.xera.xclicker.util.shareFile
import com.xera.xclicker.util.throttle
import com.xera.xclicker.util.toast

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object SettingsRoute : NavKey

@Composable
fun SettingsPage() {
    val mainVm = LocalMainViewModel.current
    val context = LocalActivity.current as MainActivity
    val store by storeFlow.collectAsState()
    val vm = viewModel<HomeVm>()

    var showToastInputDlg by vm.showToastInputDlgFlow.asMutableState()

    if (showToastInputDlg) {
        var value by remember {
            mutableStateOf(store.actionToast)
        }
        val maxCharLen = 64
        AlertDialog(
            properties = DialogProperties(dismissOnClickOutside = false),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "触发提示")
                    PerfIconButton(
                        imageVector = PerfIcon.HelpOutline,
                        contentDescription = "文案规则",
                        onClickLabel = "打开文案规则弹窗",
                        onClick = throttle {
                            showToastInputDlg = false
                            val confirmAction = {
                                mainVm.dialogFlow.value = null
                                showToastInputDlg = true
                            }
                            mainVm.dialogFlow.updateDialogOptions(
                                title = "文案规则",
                                text = $$"触发文案支持变量替换，规则如下\n${1} 子规则名称\n${2} 规则名称\n${3} 触发次数\n\n示例模板\n${1}/${2}/${3}\n\n替换结果\n子规则a/规则A/3",
                                confirmAction = confirmAction,
                                onDismissRequest = confirmAction,
                            )
                        },
                    )
                }
            },
            text = {
                OutlinedTextField(
                    value = value,
                    placeholder = {
                        Text(text = "请输入提示内容")
                    },
                    onValueChange = {
                        value = it.take(maxCharLen)
                    },
                    supportingText = {
                        Text(
                            text = "${value.length} / $maxCharLen",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End,
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .autoFocus()
                )
            },
            onDismissRequest = { showToastInputDlg = false },
            confirmButton = {
                TextButton(enabled = value.isNotEmpty(), onClick = {
                    if (value != storeFlow.value.actionToast) {
                        storeFlow.update { it.copy(actionToast = value) }
                        toast("更新成功")
                    }
                    showToastInputDlg = false
                }) {
                    Text(text = "确认")
                }
            },
            dismissButton = {
                TextButton(onClick = { showToastInputDlg = false }) {
                    Text(text = "取消")
                }
            }
        )
    }

    val scrollKey = rememberSaveable { mutableIntStateOf(0) }
    val (scrollBehavior, scrollState) = useScrollBehaviorState(scrollKey)
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection).fillMaxSize(),
        topBar = {
            PerfTopAppBar(
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    PerfIconButton(
                        imageVector = PerfIcon.ArrowBack,
                        contentDescription = "返回",
                        onClick = { mainVm.popPage() }
                    )
                },
                title = {
                    Text(
                        text = "设置",
                    )
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .padding(contentPadding)
        ) {

            Text(
                text = "常规",
                modifier = Modifier.titleItemPadding(showTop = false),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            SettingItem(title = "订阅管理", onClick = {
                mainVm.navigatePage(SubsManageRoute)
            })

            SettingItem(title = "应用管理", onClick = {
                mainVm.navigatePage(AppListRoute)
            })

            val showToastSettingsDlg by vm.showToastSettingsDlgFlow.asMutableState()
            TextSwitch(
                title = "触发提示",
                subtitle = store.actionToast,
                checked = store.toastWhenClick,
                onClickLabel = "打开触发提示弹窗",
                onClick = {
                    showToastInputDlg = true
                },
                suffixIcon = {
                    PerfCustomIconButton(
                        size = 32.dp,
                        iconSize = 20.dp,
                        onClickLabel = "打开提示设置弹窗",
                        onClick = { vm.showToastSettingsDlgFlow.update { !it } },
                        id = R.drawable.ic_page_info,
                        contentDescription = "提示设置",
                        tint = if (showToastSettingsDlg) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    )
                },
                onCheckedChange = {
                    storeFlow.value = store.copy(
                        toastWhenClick = it
                    )
                })

            AnimatedVisibility(visible = showToastSettingsDlg) {
                Column {
                    TextSwitch(
                        title = "提示样式",
                        subtitle = "使用系统样式",
                        suffix = "查看限制",
                        onSuffixClick = {
                            mainVm.dialogFlow.updateDialogOptions(
                                title = "限制说明",
                                text = "系统 Toast 存在频率限制, 触发过于频繁会被系统强制不显示\n\n如果只使用开屏一类低频率规则可使用系统提示, 否则建议关闭此项使用自定义样式提示",
                            )
                        },
                        checked = store.useSystemToast,
                        onCheckedChange = {
                            storeFlow.value = store.copy(
                                useSystemToast = it
                            )
                        })

                }
            }

            TextSwitch(
                title = "后台隐藏",
                subtitle = "在「最近任务」隐藏卡片",
                checked = store.excludeFromRecents,
                onCheckedChange = vm.viewModelScope.launchAsFn<Boolean> {
                    if (it) {
                        mainVm.dialogFlow.waitResult(
                            title = "后台隐藏",
                            text = "隐藏卡片后可能导致部分设备无法给任务卡片加锁后台，建议先加锁后再隐藏，若已加锁或没有锁后台机制请继续",
                            confirmText = "继续",
                        )
                    }
                    storeFlow.value = store.copy(
                        excludeFromRecents = !store.excludeFromRecents
                    )
                })



            Text(
                text = "其他",
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )


            SettingItem(title = "关于", onClick = {
                mainVm.navigatePage(AboutRoute)
            })

            Spacer(modifier = Modifier.height(EmptyHeight))
        }
    }
}


