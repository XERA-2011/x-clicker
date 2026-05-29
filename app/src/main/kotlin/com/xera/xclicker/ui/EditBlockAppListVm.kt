package com.xera.xclicker.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import com.xera.xclicker.store.blockMatchAppListFlow
import com.xera.xclicker.ui.share.BaseViewModel
import com.xera.xclicker.util.AppListString

class EditBlockAppListVm : BaseViewModel() {

    val textFlow = MutableStateFlow(
        AppListString.encode(
            blockMatchAppListFlow.value,
            append = true,
        )
    )

    val indicatorSizeFlow = textFlow.debounce(500).map {
        AppListString.decode(it).size
    }.stateInit(0)

    fun getChangedSet(): Set<String>? {
        val newSet = AppListString.decode(textFlow.value)
        if (blockMatchAppListFlow.value != newSet) {
            return newSet
        }
        return null
    }

}