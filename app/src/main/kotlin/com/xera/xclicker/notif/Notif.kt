package com.xera.xclicker.notif

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.net.toUri
import kotlinx.atomicfu.atomic
import com.xera.xclicker.META
import com.xera.xclicker.MainActivity
import com.xera.xclicker.R
import com.xera.xclicker.app
import com.xera.xclicker.permission.foregroundServiceSpecialUseState
import com.xera.xclicker.permission.notificationState

import com.xera.xclicker.util.AndroidTarget
import com.xera.xclicker.util.componentName
import kotlin.reflect.KClass

// 相同的 request code 会导致后续 PendingIntent 失效
private val pendingIntentReqId = atomic(0)

data class Notif(
    val channel: NotifChannel = NotifChannel.Default,
    val id: Int,
    val smallIcon: Int = R.drawable.ic_status,
    val title: String,
    val text: String? = null,
    val ongoing: Boolean = true,
    val autoCancel: Boolean = false,
    val uri: String? = null,
    val stopService: KClass<out Service>? = null,
) {
    private fun toNotification(): Notification {
        val contextIntent = PendingIntent.getActivity(
            app,
            pendingIntentReqId.incrementAndGet(),
            Intent().apply {
                component = MainActivity::class.componentName
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                data = uri?.toUri()
            },
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(app, channel.id)
            .setSmallIcon(smallIcon)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contextIntent)
            .setOngoing(ongoing)
            .setAutoCancel(autoCancel)
        if (stopService != null) {
            val deleteIntent = PendingIntent.getBroadcast(
                app,
                pendingIntentReqId.incrementAndGet(),
                StopServiceReceiver.getIntent(stopService),
                PendingIntent.FLAG_IMMUTABLE
            )
            notification
                .setDeleteIntent(deleteIntent)
                .addAction(0, "停止", deleteIntent)
        }
        return notification.build()
    }

    fun notifySelf() {
        if (!notificationState.updateAndGet()) return
        if (!foregroundServiceSpecialUseState.updateAndGet()) return
        @SuppressLint("MissingPermission")
        NotificationManagerCompat.from(app).notify(id, toNotification())
    }

    context(service: Service)
    fun notifyService() {
        if (!notificationState.updateAndGet()) return
        if (!foregroundServiceSpecialUseState.updateAndGet()) return
        ServiceCompat.startForeground(
            service,
            id,
            toNotification(),
            if (AndroidTarget.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST else -1
        )
    }
}

val abNotif by lazy {
    Notif(
        id = 100,
        title = META.appName,
        text = "无障碍正在运行",
    )
}


val exposeNotif = Notif(
    id = 104,
    title = "运行外部调用任务中",
    text = "任务完成后自动关闭",
)

val snapshotNotif = Notif(
    channel = NotifChannel.Snapshot,
    id = 105,
    title = "快照已保存",
    ongoing = false,
    autoCancel = true,
    uri = "gkd://page/2",
)

