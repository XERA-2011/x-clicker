package com.xera.xclicker.ui

import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.xera.xclicker.db.DbSet
import com.xera.xclicker.ui.share.BaseViewModel

class ActivityLogVm : BaseViewModel() {
    val pagingDataFlow = Pager(PagingConfig(pageSize = 100)) { DbSet.activityLogDao.pagingSource() }
        .flow.cachedIn(viewModelScope)

    val logCountFlow =
        DbSet.activityLogDao.count().stateInit(0)
}