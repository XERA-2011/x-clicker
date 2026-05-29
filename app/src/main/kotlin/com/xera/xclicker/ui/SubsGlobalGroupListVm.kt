package com.xera.xclicker.ui

import kotlinx.coroutines.flow.MutableStateFlow
import com.xera.xclicker.db.DbSet
import com.xera.xclicker.ui.component.ShowGroupState
import com.xera.xclicker.ui.share.BaseViewModel

class SubsGlobalGroupListVm(val route: SubsGlobalGroupListRoute) : BaseViewModel() {
    val subsRawFlow = mapSafeSubs(route.subsItemId)

    val subsConfigsFlow = DbSet.subsConfigDao.queryGlobalGroupTypeConfig(route.subsItemId)
        .stateInit(emptyList())

    val isSelectedModeFlow = MutableStateFlow(false)
    val selectedDataSetFlow = MutableStateFlow(emptySet<ShowGroupState>())
    val focusGroupFlow = route.focusGroupKey?.let {
        MutableStateFlow<Triple<Long, String?, Int>?>(
            Triple(
                route.subsItemId,
                null,
                route.focusGroupKey
            )
        )
    }
}