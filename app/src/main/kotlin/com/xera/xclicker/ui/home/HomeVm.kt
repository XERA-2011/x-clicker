package com.xera.xclicker.ui.home

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import com.xera.xclicker.store.actionCountFlow
import com.xera.xclicker.store.blockMatchAppListFlow
import com.xera.xclicker.store.storeFlow
import com.xera.xclicker.ui.share.BaseViewModel
import com.xera.xclicker.ui.share.asMutableStateFlow
import com.xera.xclicker.ui.share.useAppFilter
import com.xera.xclicker.util.AppSortOption
import com.xera.xclicker.util.EMPTY_RULE_TIP
import com.xera.xclicker.util.findOption
import com.xera.xclicker.util.getSubsStatus
import com.xera.xclicker.util.ruleSummaryFlow
import com.xera.xclicker.util.usedSubsEntriesFlow

class HomeVm : BaseViewModel() {

    val subsStatusFlow by lazy {
        combine(ruleSummaryFlow, actionCountFlow) { ruleSummary, count ->
            getSubsStatus(ruleSummary, count)
        }.stateInit(EMPTY_RULE_TIP)
    }

    val usedSubsItemCountFlow = usedSubsEntriesFlow.mapNew { it.size }

    val sortTypeFlow = storeFlow.asMutableStateFlow(
        getter = { AppSortOption.objects.findOption(it.appSort) },
        setter = {
            storeFlow.value.copy(appSort = it.value)
        }
    )
    val showBlockAppFlow = storeFlow.asMutableStateFlow(
        getter = { it.showBlockApp },
        setter = {
            storeFlow.value.copy(showBlockApp = it)
        }
    )
    val appGroupTypeFlow = storeFlow.asMutableStateFlow(
        getter = { it.appGroupType },
        setter = {
            storeFlow.value.copy(appGroupType = it)
        }
    )

    val editWhiteListModeFlow = MutableStateFlow(false)
    val blockAppListFlow = MutableStateFlow(blockMatchAppListFlow.value).also { stateFlow ->
        combine(blockMatchAppListFlow, editWhiteListModeFlow) { it }.launchCollect {
            if (!editWhiteListModeFlow.value) {
                stateFlow.value = blockMatchAppListFlow.value
            }
        }
    }

    val appFilter = useAppFilter(
        appGroupTypeFlow = appGroupTypeFlow,
        sortTypeFlow = sortTypeFlow,
        showBlockAppFlow = showBlockAppFlow,
        blockAppListFlow = blockAppListFlow,
    )
    val searchStrFlow = appFilter.searchStrFlow

    val showSearchBarFlow = MutableStateFlow(false).apply {
        launchCollect {
            if (!it) {
                searchStrFlow.value = ""
            }
        }
    }
    val appInfosFlow = appFilter.appListFlow

    val showToastInputDlgFlow = MutableStateFlow(false)
    val showNotifTextInputDlgFlow = MutableStateFlow(false)
    val showToastSettingsDlgFlow = MutableStateFlow(false)
    val showA11yBlockDlgFlow = MutableStateFlow(false)
}
