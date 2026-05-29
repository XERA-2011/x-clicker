package com.xera.xclicker.permission

import android.Manifest
import android.app.Activity
import android.app.AppOpsManager

import android.content.pm.PackageManager
import android.provider.Settings
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists
import com.hjq.permissions.permission.base.IPermission
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.updateAndGet
import com.xera.xclicker.MainActivity
import com.xera.xclicker.MainViewModel
import com.xera.xclicker.app
import com.xera.xclicker.appScope
import com.xera.xclicker.ui.AppOpsAllowRoute
import com.xera.xclicker.util.AndroidTarget
import com.xera.xclicker.util.toast
import com.xera.xclicker.util.updateAllAppInfo
import com.xera.xclicker.util.updateAppMutex

class PermissionState(
    val name: String,
    val check: () -> Boolean,
    val request: (suspend (context: MainActivity) -> PermissionResult)? = null,
    /**
     * show it when user doNotAskAgain
     */
    val reason: AuthReason? = null,
) {
    val stateFlow = MutableStateFlow(false)
    val value get() = stateFlow.value

    fun updateAndGet(): Boolean {
        return stateFlow.updateAndGet { check() }
    }

    fun updateChanged(): Boolean {
        return value != updateAndGet()
    }

    fun checkOrToast(): Boolean = if (!updateAndGet()) {
        val r = updateAndGet()
        if (!r) {
            reason?.text?.let { toast(it()) }
        }
        r
    } else {
        true
    }
}

private suspend fun asyncRequestPermission(
    context: Activity,
    permission: IPermission,
): PermissionResult {
    if (XXPermissions.isGrantedPermission(context, permission)) {
        return PermissionResult.Granted
    }
    val deferred = CompletableDeferred<PermissionResult>()
    XXPermissions.with(context)
        .unchecked()
        .permission(permission)
        .request { grantedList, _ ->
            if (grantedList.contains(permission)) {
                PermissionResult.Granted
            } else {
                PermissionResult.Denied(
                    XXPermissions.isDoNotAskAgainPermissions(
                        context,
                        arrayOf(permission)
                    )
                )
            }.let { deferred.complete(it) }
        }
    return deferred.await()
}

private fun checkAllowedOp(op: String): Boolean = app.appOpsManager.checkOpNoThrow(
    op,
    android.os.Process.myUid(),
    app.packageName
).let {
    it != AppOpsManager.MODE_IGNORED && it != AppOpsManager.MODE_ERRORED
}

// https://github.com/xclicker-kit/gkd/issues/954
// https://github.com/xclicker-kit/gkd/issues/887
val foregroundServiceSpecialUseState by lazy {
    PermissionState(
        name = "特殊用途的前台服务",
        check = {
            if (AndroidTarget.UPSIDE_DOWN_CAKE) {
                checkAllowedOp("android:foreground_service_special_use")
            } else {
                true
            }
        },
        reason = AuthReason(
            text = { "当前操作权限「特殊用途的前台服务」已被限制, 请先解除限制" },
            confirm = {
                MainViewModel.instance.navigatePage(AppOpsAllowRoute)
            },
        ),
    )
}

// https://github.com/orgs/xclicker-kit/discussions/1234
val accessA11yState by lazy {
    PermissionState(
        name = "访问无障碍",
        check = {
            if (AndroidTarget.Q) {
                checkAllowedOp("android:access_accessibility")
            } else {
                true
            }
        },
    )
}

val createA11yOverlayState by lazy {
    PermissionState(
        name = "创建无障碍悬浮窗",
        check = {
            true
        },
    )
}

const val Manifest_permission_GET_APP_OPS_STATS = "android.permission.GET_APP_OPS_STATS"

val getAppOpsStatsState by lazy {
    PermissionState(
        name = "获取应用权限状态",
        check = {
            app.checkGrantedPermission(Manifest_permission_GET_APP_OPS_STATS)
        },
    )
}

private var canRestrictsRead = true
val accessRestrictedSettingsState by lazy {
    PermissionState(
        name = "访问受限设置",
        check = {
            if (canRestrictsRead && AndroidTarget.UPSIDE_DOWN_CAKE && getAppOpsStatsState.updateAndGet()) {
                try {
                    // https://cs.android.com/android/platform/superproject/+/android-14.0.0_r55:frameworks/base/services/core/java/com/android/server/appop/AppOpsService.java;l=4237
                    checkAllowedOp("android:access_restricted_settings")
                } catch (_: SecurityException) {
                    // https://cs.android.com/android/platform/superproject/+/android-14.0.0_r54:frameworks/base/services/core/java/com/android/server/appop/AppOpsService.java;l=4227
                    canRestrictsRead = false
                    true
                }
            } else {
                true
            }
        },
    )
}

val appOpsRestrictStateList by lazy {
    arrayOf(
        accessA11yState,
        createA11yOverlayState,
        accessRestrictedSettingsState,
        foregroundServiceSpecialUseState,
    )
}

val appOpsRestrictedFlow by lazy {
    combine(
        *appOpsRestrictStateList.map { it.stateFlow }.toTypedArray(),
    ) { list ->
        list.any { !it }
    }.stateIn(appScope, SharingStarted.Eagerly, false)
}

val notificationState by lazy {
    val permission = PermissionLists.getNotificationServicePermission()
    PermissionState(
        name = "通知权限",
        check = {
            XXPermissions.isGrantedPermission(app, permission)
        },
        request = { asyncRequestPermission(it, permission) },
        reason = AuthReason(
            text = { "当前操作需要「通知权限」\n请先前往权限页面授权" },
            confirm = {
                XXPermissions.startPermissionActivity(app, permission)
            }
        ),
    )
}

val canQueryPkgState by lazy {
    val permission = PermissionLists.getGetInstalledAppsPermission()
    val supported by lazy { permission.isSupportRequestPermission(app) }
    PermissionState(
        name = "读取应用列表权限",
        check = {
            if (supported) {
                // 此框架内部有两个 printStackTrace 导致每次检测都会打印日志污染控制台
                XXPermissions.isGrantedPermission(app, permission)
            } else {
                true
            }
        },
        request = {
            asyncRequestPermission(it, permission)
        },
        reason = AuthReason(
            text = { "当前操作需要「读取应用列表权限」\n请先前往权限页面授权" },
            confirm = {
                XXPermissions.startPermissionActivity(app, permission)
            }
        ),
    )
}

val canDrawOverlaysState by lazy {
    PermissionState(
        name = "悬浮窗权限",
        check = {
            // https://developer.android.com/security/fraud-prevention/activities?hl=zh-cn#hide_overlay_windows
            Settings.canDrawOverlays(app)
        },
        reason = AuthReason(
            text = {
                "当前操作需要「悬浮窗权限」\n请先前往权限页面授权"
            },
            confirm = {
                XXPermissions.startPermissionActivity(
                    app,
                    PermissionLists.getSystemAlertWindowPermission()
                )
            }
        ),
    )
}

val canWriteExternalStorage by lazy {
    PermissionState(
        name = "写入外部存储权限",
        check = {
            if (AndroidTarget.Q) {
                true
            } else {
                app.checkGrantedPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        },
        request = {
            if (AndroidTarget.Q) {
                PermissionResult.Granted
            } else {
                asyncRequestPermission(it, PermissionLists.getWriteExternalStoragePermission())
            }
        },
        reason = AuthReason(
            text = { "当前操作需要「写入外部存储权限」\n请先前往权限页面授权" },
            confirm = {
                XXPermissions.startPermissionActivity(
                    app,
                    PermissionLists.getWriteExternalStoragePermission()
                )
            }
        ),
    )
}

val ignoreBatteryOptimizationsState by lazy {
    val permission = PermissionLists.getRequestIgnoreBatteryOptimizationsPermission()
    PermissionState(
        name = "忽略电池优化权限",
        check = {
            app.powerManager.isIgnoringBatteryOptimizations(app.packageName)
        },
        request = {
            asyncRequestPermission(it, permission)
        },
        reason = AuthReason(
            text = { "当前操作需要「忽略电池优化权限」\n请先前往权限页面授权" },
            confirm = {
                XXPermissions.startPermissionActivity(
                    app,
                    permission
                )
            }
        ),
    )
}

val writeSecureSettingsState by lazy {
    PermissionState(
        name = "写入安全设置权限",
        check = { app.checkGrantedPermission(Manifest.permission.WRITE_SECURE_SETTINGS) },
    )
}



val allPermissionStates by lazy {
    listOf(
        notificationState,
        foregroundServiceSpecialUseState,
        accessA11yState,
        createA11yOverlayState,
        getAppOpsStatsState,
        accessRestrictedSettingsState,
        canDrawOverlaysState,
        canWriteExternalStorage,
        ignoreBatteryOptimizationsState,
        writeSecureSettingsState,
        canQueryPkgState,
    )
}

fun updatePermissionState() {
    allPermissionStates.forEach {
        if (it === canQueryPkgState && !updateAppMutex.mutex.isLocked) {
            if (canQueryPkgState.updateChanged()) {
                updateAllAppInfo()
            }
        } else {
            it.updateAndGet()
        }
    }
}