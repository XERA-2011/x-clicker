package com.xera.xclicker.ui

import com.xera.xclicker.db.DbSet
import com.xera.xclicker.ui.share.BaseViewModel

class SnapshotVm : BaseViewModel() {
    val snapshotsState = DbSet.snapshotDao.query().attachLoad()
        .stateInit(emptyList())
}