package dev.xera.xclicker.util

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils

object AccessibilityHelper {

    /**
     * 检测指定的无障碍服务是否已启用
     */
    fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<*>): Boolean {
        val expectedId = "${context.packageName}/${serviceClass.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals(expectedId, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    /**
     * 跳转到系统无障碍设置页面
     */
    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
