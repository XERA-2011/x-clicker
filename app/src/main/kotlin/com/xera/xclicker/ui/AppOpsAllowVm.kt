package com.xera.xclicker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.xera.xclicker.permission.appOpsRestrictStateList

class AppOpsAllowVm : ViewModel() {
    val showCopyDlgFlow = MutableStateFlow(false)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                appOpsRestrictStateList.forEach { it.updateAndGet() }
                delay(1000)
            }
        }
    }
}