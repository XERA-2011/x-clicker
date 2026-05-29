package com.xera.xclicker.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import com.xera.xclicker.MainActivity
import com.xera.xclicker.R

import com.xera.xclicker.store.blockA11yAppListFlow
import com.xera.xclicker.store.storeFlow
import com.xera.xclicker.ui.component.AnimatedBooleanContent
import com.xera.xclicker.ui.component.AnimatedIconButton
import com.xera.xclicker.ui.component.AnimationFloatingActionButton
import com.xera.xclicker.ui.component.AppBarTextField
import com.xera.xclicker.ui.component.AppCheckBoxCard
import com.xera.xclicker.ui.component.EmptyText
import com.xera.xclicker.ui.component.MenuGroupCard
import com.xera.xclicker.ui.component.MenuItemCheckbox
import com.xera.xclicker.ui.component.MenuItemRadioButton
import com.xera.xclicker.ui.component.MultiTextField
import com.xera.xclicker.ui.component.PerfIcon
import com.xera.xclicker.ui.component.PerfIconButton
import com.xera.xclicker.ui.component.PerfTopAppBar
import com.xera.xclicker.ui.component.autoFocus
import com.xera.xclicker.ui.component.isFullVisible
import com.xera.xclicker.ui.component.useListScrollState
import com.xera.xclicker.ui.component.waitResult
import com.xera.xclicker.ui.icon.BackCloseIcon
import com.xera.xclicker.ui.icon.LockOpenRight
import com.xera.xclicker.ui.share.ListPlaceholder
import com.xera.xclicker.ui.share.LocalMainViewModel
import com.xera.xclicker.ui.share.asMutableState
import com.xera.xclicker.ui.share.noRippleClickable
import com.xera.xclicker.ui.style.EmptyHeight
import com.xera.xclicker.ui.style.scaffoldPadding
import com.xera.xclicker.util.AppGroupOption
import com.xera.xclicker.util.AppListString
import com.xera.xclicker.util.AppSortOption
import com.xera.xclicker.util.launchAsFn
import com.xera.xclicker.util.switchItem
import com.xera.xclicker.util.throttle
import com.xera.xclicker.util.toast

@Serializable
data object BlockA11yAppListRoute : NavKey

@Composable
fun BlockA11yAppListPage() {
    val store by storeFlow.collectAsState()
    val mainVm = LocalMainViewModel.current
    val context = LocalActivity.current as MainActivity
    val vm = viewModel<BlockA11yAppListVm>()
    val appInfos by vm.appInfosFlow.collectAsState()
    val searchStr by vm.searchStrFlow.collectAsState()
    var showSearchBar by vm.showSearchBarFlow.asMutableState()
    var editable by vm.editableFlow.asMutableState()
    val (scrollBehavior, listState) = useListScrollState(vm.resetKey, canScroll = { !editable })
    BackHandler(editable, vm.viewModelScope.launchAsFn {
        context.justHideSoftInput()
        if (vm.textChanged) {
            mainVm.dialogFlow.waitResult(
                title = "提示",
                text = "当前内容未保存，是否放弃编辑？",
            )
        }
        editable = false
    })
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            PerfTopAppBar(
                scrollBehavior = scrollBehavior,
                canScroll = !editable && !store.blockA11yAppListFollowMatch,
                navigationIcon = {
                    IconButton(
                        onClick = throttle(vm.viewModelScope.launchAsFn {
                            if (editable) {
                                if (vm.textChanged) {
                                    context.justHideSoftInput()
                                    mainVm.dialogFlow.waitResult(
                                        title = "提示",
                                        text = "当前内容未保存，是否放弃编辑？",
                                    )
                                }
                                editable = !editable
                            } else {
                                context.hideSoftInput()
                                mainVm.popPage()
                            }
                        })
                    ) {
                        BackCloseIcon(backOrClose = !editable)
                    }
                },
                title = {
                    val firstShowSearchBar = remember { showSearchBar }
                    if (showSearchBar) {
                        BackHandler {
                            if (!context.justHideSoftInput()) {
                                showSearchBar = false
                            }
                        }
                        AppBarTextField(
                            value = searchStr,
                            onValueChange = { newValue ->
                                vm.searchStrFlow.value = newValue.trim()
                            },
                            hint = "请输入应用名称/ID",
                            modifier = if (firstShowSearchBar) Modifier else Modifier.autoFocus(),
                        )
                    } else {
                        val titleModifier = Modifier
                            .noRippleClickable(
                                onClick = throttle {
                                    vm.resetKey.intValue++
                                }
                            )
                        Text(
                            modifier = titleModifier,
                            text = "无障碍白名单",
                        )
                    }
                },
                actions = {
                    AnimatedBooleanContent(
                        targetState = editable,
                        contentAlignment = Alignment.TopEnd,
                        contentTrue = {
                            PerfIconButton(
                                imageVector = PerfIcon.Save,
                                onClick = throttle {
                                    if (vm.textChanged) {
                                        blockA11yAppListFlow.value =
                                            AppListString.decode(vm.textFlow.value)
                                        toast("更新成功")
                                    } else {
                                        toast("未修改")
                                    }
                                    context.justHideSoftInput()
                                    editable = false
                                },
                            )
                        },
                        contentFalse = {
                            Row {
                                PerfIconButton(
                                    imageVector = if (store.blockA11yAppListFollowMatch) PerfIcon.Lock else LockOpenRight,
                                    contentDescription = if (store.blockA11yAppListFollowMatch) "已设置为跟随应用白名单" else "已设置为独立无障碍白名单",
                                    onClickLabel = "切换模式",
                                    onClick = throttle {
                                        showSearchBar = false
                                        storeFlow.update { it.copy(blockA11yAppListFollowMatch = !it.blockA11yAppListFollowMatch) }
                                    }
                                )

                                var expanded by remember { mutableStateOf(false) }
                                AnimatedVisibility(!store.blockA11yAppListFollowMatch) {
                                    Row {
                                        AnimatedIconButton(
                                            onClick = throttle {
                                                if (showSearchBar) {
                                                    if (vm.searchStrFlow.value.isEmpty()) {
                                                        showSearchBar = false
                                                    } else {
                                                        vm.searchStrFlow.value = ""
                                                    }
                                                } else {
                                                    showSearchBar = true
                                                }
                                            },
                                            id = R.drawable.ic_anim_search_close,
                                            atEnd = showSearchBar,
                                        )
                                        PerfIconButton(imageVector = PerfIcon.Sort, onClick = {
                                            expanded = true
                                        })
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .wrapContentSize(Alignment.TopStart)
                                ) {
                                    DropdownMenu(
                                        expanded = expanded,
                                        onDismissRequest = { expanded = false }
                                    ) {
                                        MenuGroupCard(inTop = true, title = "排序") {
                                            var sortType by vm.sortTypeFlow.asMutableState()
                                            AppSortOption.objects.forEach { option ->
                                                MenuItemRadioButton(
                                                    text = option.label,
                                                    selected = sortType == option,
                                                    onClick = { sortType = option },
                                                )
                                            }
                                        }
                                        MenuGroupCard(inTop = true, title = "筛选") {
                                            var appGroupType by vm.appGroupTypeFlow.asMutableState()
                                            AppGroupOption.normalObjects.forEach { option ->
                                                val newValue = option.invert(appGroupType)
                                                MenuItemCheckbox(
                                                    enabled = newValue != 0,
                                                    text = option.label,
                                                    checked = option.include(appGroupType),
                                                    onClick = { appGroupType = newValue },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        },
                    )
                })
        },
        floatingActionButton = {
            AnimationFloatingActionButton(
                visible = !editable && scrollBehavior.isFullVisible && !store.blockA11yAppListFollowMatch,
                onClickLabel = "进入白名单文本编辑模式",
                onClick = {
                    editable = !editable
                },
                imageVector = PerfIcon.Edit,
                contentDescription = "编辑白名单文本"
            )
        },
    ) { contentPadding ->
        if (store.blockA11yAppListFollowMatch) {
            Column(
                modifier = Modifier.scaffoldPadding(contentPadding),
            ) {
                Spacer(modifier = Modifier.height(EmptyHeight))
                Text(
                    text = "已设置为跟随应用白名单",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        } else if (editable) {
            MultiTextField(
                modifier = Modifier.scaffoldPadding(contentPadding),
                textFlow = vm.textFlow,
                immediateFocus = true,
                placeholderText = "请输入应用ID列表\n示例:\ncom.android.systemui\ncom.android.settings",
                indicatorSize = vm.indicatorSizeFlow.collectAsState().value,
            )
        } else {
            val blockA11yAppList by blockA11yAppListFlow.collectAsState()
            LazyColumn(
                modifier = Modifier.scaffoldPadding(contentPadding),
                state = listState,
            ) {
                items(appInfos, { it.id }) { appInfo ->
                    AppCheckBoxCard(
                        appInfo = appInfo,
                        checked = blockA11yAppList.contains(appInfo.id),
                        onCheckedChange = {
                            blockA11yAppListFlow.update {
                                it.switchItem(appInfo.id)
                            }
                        },
                    )
                }
                item(ListPlaceholder.KEY, ListPlaceholder.TYPE) {
                    Spacer(modifier = Modifier.height(EmptyHeight))
                    if (appInfos.isEmpty() && searchStr.isNotEmpty()) {
                        EmptyText(text = "暂无搜索结果")
                        Spacer(modifier = Modifier.height(EmptyHeight / 2))
                    }
                }
            }
        }
    }
}
