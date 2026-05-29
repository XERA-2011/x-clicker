package com.xera.xclicker.ui

import com.xera.xclicker.MainViewModel
import com.xera.xclicker.ui.share.BaseViewModel

class CrashReportVm : BaseViewModel() {
    val crashDataList = MainViewModel.instance.run {
        val v = tempCrashDataList
        tempCrashDataList = emptyList()
        v
    }
}