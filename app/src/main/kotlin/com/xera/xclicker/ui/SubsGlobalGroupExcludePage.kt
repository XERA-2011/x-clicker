package com.xera.xclicker.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import com.xera.xclicker.MainActivity
import com.xera.xclicker.R
import com.xera.xclicker.a11y.launcherAppId
import com.xera.xclicker.data.ExcludeData
import com.xera.xclicker.data.RawSubscription
import com.xera.xclicker.data.SubsConfig
import com.xera.xclicker.db.DbSet
import com.xera.xclicker.store.blockMatchAppListFlow
import com.xera.xclicker.ui.component.AnimatedBooleanContent
import com.xera.xclicker.ui.component.AnimatedIconButton
import com.xera.xclicker.ui.component.AnimationFloatingActionButton
import com.xera.xclicker.ui.component.AppBarTextField
import com.xera.xclicker.ui.component.AppIcon
import com.xera.xclicker.ui.component.AppNameText
import com.xera.xclicker.ui.component.EmptyText
import com.xera.xclicker.ui.component.InnerDisableSwitch
import com.xera.xclicker.ui.component.MenuGroupCard
import com.xera.xclicker.ui.component.MenuItemCheckbox
import com.xera.xclicker.ui.component.MenuItemRadioButton
import com.xera.xclicker.ui.component.MultiTextField
import com.xera.xclicker.ui.component.PerfIcon
import com.xera.xclicker.ui.component.PerfIconButton
import com.xera.xclicker.ui.component.PerfSwitch
import com.xera.xclicker.ui.component.PerfTopAppBar
import com.xera.xclicker.ui.component.TowLineText
import com.xera.xclicker.ui.component.autoFocus
import com.xera.xclicker.ui.component.isFullVisible
import com.xera.xclicker.ui.component.useListScrollState
import com.xera.xclicker.ui.component.waitResult
import com.xera.xclicker.ui.icon.BackCloseIcon
import com.xera.xclicker.ui.icon.ResetSettings
import com.xera.xclicker.ui.share.ListPlaceholder
import com.xera.xclicker.ui.share.LocalMainViewModel
import com.xera.xclicker.ui.share.asMutableState
import com.xera.xclicker.ui.share.noRippleClickable
import com.xera.xclicker.ui.style.EmptyHeight
import com.xera.xclicker.ui.style.itemPadding
import com.xera.xclicker.ui.style.scaffoldPadding
import com.xera.xclicker.util.AppGroupOption
import com.xera.xclicker.util.AppSortOption
import com.xera.xclicker.util.launchAsFn
import com.xera.xclicker.util.systemAppsFlow
import com.xera.xclicker.util.throttle
import com.xera.xclicker.util.toast

@Serializable
data class SubsGlobalGroupExcludeRoute(
    val subsItemId: Long,
    val groupKey: Int,
) : NavKey

@Composable
fun SubsGlobalGroupExcludePage(route: SubsGlobalGroupExcludeRoute) {
    val subsItemId = route.subsItemId
    val groupKey = route.groupKey

    val mainVm = LocalMainViewModel.current
    val context = LocalActivity.current as MainActivity
    val vm = viewModel { SubsGlobalGroupExcludeVm(route) }
    val subs = vm.subsFlow.collectAsState().value
    val group = vm.groupFlow.collectAsState().value ?: return
    val excludeData = vm.excludeDataFlow.collectAsState().value
    val showAppInfos = vm.showAppInfosFlow.collectAsState().value

    var searchStr by vm.searchStrFlow.asMutableState()
    var editable by vm.editableFlow.asMutableState()

    var showSearchBar by rememberSaveable {
        mutableStateOf(false)
    }
    LaunchedEffect(key1 = showSearchBar, block = {
        if (!showSearchBar) {
            searchStr = ""
        }
    })
    val (scrollBehavior, listState) = useListScrollState(
        vm.resetKey,
        canScroll = { !editable }
    )

    BackHandler(editable, onBack = throttle(vm.viewModelScope.launchAsFn {
        context.justHideSoftInput()
        if (vm.changedValue != null) {
            mainVm.dialogFlow.waitResult(
                title = "提示",
                text = "当前内容未保存，是否放弃编辑？",
            )
        }
        editable = false
    }))

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            PerfTopAppBar(
                scrollBehavior = scrollBehavior,
                canScroll = !editable,
                navigationIcon = {
                    IconButton(onClick = throttle(vm.viewModelScope.launchAsFn {
                        if (vm.editableFlow.value) {
                            editable = false
                            context.justHideSoftInput()
                        } else {
                            context.hideSoftInput()
                            mainVm.popPage()
                        }
                    })) {
                        BackCloseIcon(backOrClose = !editable)
                    }
                },
                title = {
                    if (showSearchBar) {
                        BackHandler {
                            if (!context.justHideSoftInput()) {
                                showSearchBar = false
                            }
                        }
                        AppBarTextField(
                            value = searchStr,
                            onValueChange = { newValue ->
                                searchStr = newValue.trim()
                            },
                            hint = "请输入应用名称/ID",
                            modifier = Modifier.autoFocus(),
                        )
                    } else {
                        TowLineText(
                            title = group.name,
                            subtitle = "编辑禁用",
                            modifier = Modifier.noRippleClickable { vm.resetKey.intValue++ }
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
                                onClick = throttle(vm.viewModelScope.launchAsFn {
                                    val newExclude = vm.changedValue
                                    if (newExclude != null) {
                                        val subsConfig = (vm.subsConfigFlow.value ?: SubsConfig(
                                            type = SubsConfig.GlobalGroupType,
                                            subsId = subsItemId,
                                            groupKey = groupKey,
                                        )).copy(
                                            exclude = newExclude.stringify()
                                        )
                                        DbSet.subsConfigDao.insert(subsConfig)
                                        toast("更新成功")
                                    } else {
                                        toast("未修改")
                                    }
                                    context.justHideSoftInput()
                                    editable = false
                                }),
                            )
                        },
                        contentFalse = {
                            Row {
                                AnimatedIconButton(
                                    onClick = {
                                        if (showSearchBar) {
                                            if (searchStr.isEmpty()) {
                                                showSearchBar = false
                                            } else {
                                                searchStr = ""
                                            }
                                        } else {
                                            showSearchBar = true
                                        }
                                    },
                                    id = R.drawable.ic_anim_search_close,
                                    atEnd = showSearchBar,
                                )
                                var expanded by remember { mutableStateOf(false) }
                                PerfIconButton(
                                    imageVector = PerfIcon.Sort,
                                    onClick = {
                                        expanded = true
                                    },
                                )
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
                                                    onClick = { sortType = option }
                                                )
                                            }
                                        }
                                        MenuGroupCard(title = "分组") {
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
                                        MenuGroupCard(title = "筛选") {
                                            MenuItemCheckbox(
                                                text = "内置禁用",
                                                stateFlow = vm.showInnerDisabledAppFlow,
                                            )
                                            MenuItemCheckbox(
                                                text = "白名单",
                                                stateFlow = vm.showBlockAppFlow,
                                            )
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
                visible = !editable && scrollBehavior.isFullVisible,
                onClick = {
                    editable = !editable
                },
                imageVector = PerfIcon.Edit,
                contentDescription = "编辑禁用名单"
            )
        }
    ) { contentPadding ->
        if (editable) {
            MultiTextField(
                modifier = Modifier.scaffoldPadding(contentPadding),
                textFlow = vm.excludeTextFlow,
                immediateFocus = true,
                placeholderText = tipText,
            )
        } else {
            LazyColumn(
                modifier = Modifier.scaffoldPadding(contentPadding),
                state = listState,
            ) {
                items(showAppInfos, { it.id }) { appInfo ->

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .itemPadding(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AppIcon(appId = appInfo.id)
                        Column(
                            modifier = Modifier.weight(1f),
                        ) {
                            AppNameText(appInfo = appInfo)
                            Text(
                                text = appInfo.id,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val blockMatch =
                            blockMatchAppListFlow.collectAsState().value.contains(appInfo.id)
                        if (blockMatch) {
                            PerfIcon(
                                modifier = Modifier
                                    .padding(2.dp)
                                    .size(20.dp),
                                imageVector = PerfIcon.Block,
                                tint = MaterialTheme.colorScheme.secondary,
                            )
                        }
                        val checked = getGlobalGroupChecked(
                            subs,
                            excludeData,
                            group,
                            appInfo.id
                        )
                        if (checked != null) {
                            PerfSwitch(
                                key = appInfo.id,
                                checked = checked,
                                onCheckedChange = vm.viewModelScope.launchAsFn { newChecked ->
                                    val subsConfig = (vm.subsConfigFlow.value ?: SubsConfig(
                                        type = SubsConfig.GlobalGroupType,
                                        subsId = subsItemId,
                                        groupKey = groupKey,
                                    )).copy(
                                        exclude = excludeData.copy(
                                            appIds = excludeData.appIds.toMutableMap()
                                                .apply {
                                                    set(appInfo.id, !newChecked)
                                                })
                                            .stringify()
                                    )
                                    DbSet.subsConfigDao.insert(subsConfig)
                                },
                                thumbContent = if (excludeData.appIds.contains(appInfo.id)) ({
                                    PerfIcon(
                                        imageVector = ResetSettings,
                                        modifier = Modifier.size(8.dp)
                                    )
                                }) else null,
                            )
                        } else {
                            InnerDisableSwitch()
                        }
                    }
                }
                item(ListPlaceholder.KEY, ListPlaceholder.TYPE) {
                    Spacer(modifier = Modifier.height(EmptyHeight))
                    if (showAppInfos.isEmpty() && searchStr.isNotEmpty()) {
                        EmptyText(text = if (vm.appFilter.showAllAppFlow.collectAsState().value) "暂无搜索结果" else "暂无搜索结果，或修改筛选")
                        Spacer(modifier = Modifier.height(EmptyHeight / 2))
                    }
                }
            }
        }
    }
}

// null - 内置禁用
// true - 启用
// false - 禁用
fun getGlobalGroupChecked(
    subscription: RawSubscription,
    excludeData: ExcludeData,
    group: RawSubscription.RawGlobalGroup,
    appId: String,
): Boolean? {
    if (subscription.getGlobalGroupInnerDisabled(group, appId)) {
        return null
    }
    excludeData.appIds[appId]?.let { return !it }
    if (group.appIdEnable[appId] == true) return true
    if (appId == launcherAppId) {
        return group.matchLauncher ?: false
    }
    if (systemAppsFlow.value.contains(appId)) {
        return group.matchSystemApp ?: false
    }
    return group.matchAnyApp ?: true
}

private val tipText = """
以换行或英文逗号分割每条禁用
示例1-禁用单个页面
appId/activityId
示例2-禁用整个应用(移除/)
appId
示例3-开启此应用(前置!)
!appId
""".trimIndent()