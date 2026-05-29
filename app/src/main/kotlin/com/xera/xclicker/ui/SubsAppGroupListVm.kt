package com.xera.xclicker.ui

import kotlinx.coroutines.flow.MutableStateFlow
import com.xera.xclicker.db.DbSet
import com.xera.xclicker.ui.component.ShowGroupState
import com.xera.xclicker.ui.share.BaseViewModel

class SubsAppGroupListVm(val route: SubsAppGroupListRoute) : BaseViewModel() {

    val subsFlow = mapSafeSubs(route.subsItemId)
    val subsAppFlow = subsFlow.mapNew { it.getApp(route.appId) }

    val subsConfigsFlow = DbSet.subsConfigDao.queryAppGroupTypeConfig(route.subsItemId, route.appId)
        .stateInit(emptyList())

    val categoryConfigsFlow = DbSet.categoryConfigDao.queryConfig(route.subsItemId)
        .stateInit(emptyList())


    val isSelectedModeFlow = MutableStateFlow(false)
    val selectedDataSetFlow = MutableStateFlow(emptySet<ShowGroupState>())

    val focusGroupFlow = route.focusGroupKey?.let {
        MutableStateFlow<Triple<Long, String?, Int>?>(
            Triple(
                route.subsItemId,
                route.appId,
                route.focusGroupKey
            )
        )
    }
}