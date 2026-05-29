package com.xera.xclicker.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import com.xera.xclicker.db.DbSet
import com.xera.xclicker.store.storeFlow
import com.xera.xclicker.ui.share.BaseViewModel
import com.xera.xclicker.ui.share.asMutableStateFlow
import com.xera.xclicker.ui.share.useSubsAppFilter
import com.xera.xclicker.util.AppSortOption
import com.xera.xclicker.util.findOption


class SubsCategoryGroupVm(val route: SubsCategoryGroupRoute) : BaseViewModel() {
    val subsFlow = mapSafeSubs(route.subsId)
    val categoryFlow = subsFlow.mapNew { it.getSafeCategory(route.categoryKey) }
    val subsConfigsFlow = DbSet.subsConfigDao.querySubsGroupTypeConfig(route.subsId)
        .stateInit(emptyList())
    val categoryConfigFlow =
        DbSet.categoryConfigDao.queryCategoryConfig(route.subsId, route.categoryKey).stateInit(null)
    val showEditCategoryFlow = MutableStateFlow(false)

    val sortTypeFlow = storeFlow.asMutableStateFlow(
        getter = { AppSortOption.objects.findOption(it.subsCategorySort) },
        setter = {
            storeFlow.value.copy(subsCategorySort = it.value)
        }
    )
    val appGroupTypeFlow = storeFlow.asMutableStateFlow(
        getter = { it.subsCategoryGroupType },
        setter = { storeFlow.value.copy(subsCategoryGroupType = it) },
    )
    val showBlockAppFlow = storeFlow.asMutableStateFlow(
        getter = { it.subsCategoryShowBlock },
        setter = { storeFlow.value.copy(subsCategoryShowBlock = it) },
    )

    private val rawAppsFlow = subsFlow.mapNew { it.getCategoryApps(route.categoryKey) }

    val appsFlow = useSubsAppFilter(
        subsId = route.subsId,
        appsFlow = subsFlow.mapNew { it.getCategoryApps(route.categoryKey) },
        sortTypeFlow = sortTypeFlow,
        appGroupTypeFlow = appGroupTypeFlow,
        showBlockAppFlow = showBlockAppFlow,
    )

    val showAllAppFlow = combine(rawAppsFlow, appsFlow) { list1, list2 ->
        list1.size == list2.size
    }.stateInit(false)


}
