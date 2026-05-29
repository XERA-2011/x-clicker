package com.xera.xclicker.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import com.xera.xclicker.MainActivity
import com.xera.xclicker.store.blockMatchAppListFlow
import com.xera.xclicker.ui.component.MultiTextField
import com.xera.xclicker.ui.component.PerfIcon
import com.xera.xclicker.ui.component.PerfIconButton
import com.xera.xclicker.ui.component.PerfTopAppBar
import com.xera.xclicker.ui.component.waitResult
import com.xera.xclicker.ui.share.LocalMainViewModel
import com.xera.xclicker.ui.style.scaffoldPadding
import com.xera.xclicker.util.launchAsFn
import com.xera.xclicker.util.throttle
import com.xera.xclicker.util.toast

@Serializable
data object EditBlockAppListRoute : NavKey

@Composable
fun EditBlockAppListPage() {
    val mainVm = LocalMainViewModel.current
    val context = LocalActivity.current as MainActivity
    val vm = viewModel<EditBlockAppListVm>()
    val onBack = throttle(vm.viewModelScope.launchAsFn {
        if (vm.getChangedSet() != null) {
            context.justHideSoftInput()
            mainVm.dialogFlow.waitResult(
                title = "提示",
                text = "当前内容未保存，是否放弃编辑？",
            )
        } else {
            context.hideSoftInput()
        }
        mainVm.popPage()
    })
    BackHandler(onBack = onBack)
    Scaffold(modifier = Modifier, topBar = {
        PerfTopAppBar(
            modifier = Modifier.fillMaxWidth(),
            navigationIcon = {
                PerfIconButton(
                    imageVector = PerfIcon.ArrowBack,
                    onClick = onBack,
                )
            },
            title = { Text(text = "应用白名单") },
            actions = {
                PerfIconButton(
                    imageVector = PerfIcon.Save,
                    onClick = throttle(vm.viewModelScope.launchAsFn {
                        val newSet = vm.getChangedSet()
                        if (newSet != null) {
                            blockMatchAppListFlow.value = newSet
                            toast("更新成功")
                        } else {
                            toast("未修改")
                        }
                        context.hideSoftInput()
                        mainVm.popPage()
                    })
                )
            }
        )
    }) { contentPadding ->
        MultiTextField(
            modifier = Modifier.scaffoldPadding(contentPadding),
            textFlow = vm.textFlow,
            indicatorSize = vm.indicatorSizeFlow.collectAsState().value
        )
    }
}