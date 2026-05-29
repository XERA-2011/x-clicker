package com.xera.xclicker.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import com.xera.xclicker.db.DbSet
import com.xera.xclicker.ui.share.BaseViewModel

class SubsCategoryVm(val route: SubsCategoryRoute) : BaseViewModel() {
    val subsRawFlow = mapSafeSubs(route.subsItemId)

    val categoryConfigsFlow = DbSet.categoryConfigDao.queryConfig(route.subsItemId)
        .stateInit(emptyList())

    val categoryConfigMapFlow = categoryConfigsFlow.map { it.associateBy { c -> c.categoryKey } }
        .stateInit(emptyMap())

    val showAddCategoryFlow = MutableStateFlow(false)
}