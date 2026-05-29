package com.xera.xclicker.ui

import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import kotlinx.coroutines.flow.MutableStateFlow
import com.xera.xclicker.data.A11yEventLog
import com.xera.xclicker.db.DbSet
import com.xera.xclicker.ui.share.BaseViewModel

class A11yEventLogVm : BaseViewModel() {
    val pagingDataFlow =
        Pager(PagingConfig(pageSize = 100)) { DbSet.a11yEventLogDao.pagingSource() }
            .flow.cachedIn(viewModelScope)

    val logCountFlow = DbSet.a11yEventLogDao.count().stateInit(0)
    val resetKey = mutableIntStateOf(0)
    val showEventLogFlow = MutableStateFlow<A11yEventLog?>(null)
}