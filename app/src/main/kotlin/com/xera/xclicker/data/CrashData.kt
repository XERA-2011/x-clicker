package com.xera.xclicker.data

import kotlinx.serialization.Serializable
import com.xera.xclicker.util.crashFolder
import com.xera.xclicker.util.crashTempFolder
import com.xera.xclicker.util.format
import com.xera.xclicker.util.json

@Serializable
data class CrashData(
    val id: Long,
    val mtime: Long,
    val device: String,
    val androidVersionCode: Int,
    val androidVersionName: String,
    val versionCode: Int,
    val versionName: String,
    val name: String,
    val message: String?,
    val thread: String,
    val stackTrace: String,
) {
    val filename get() = "xclicker_crash-" + mtime.format("yyyyMMdd_HHmmss") + ".json"
    fun save() {
        val text = json.encodeToString(this)
        crashFolder.resolve(filename).writeText(text)
        crashTempFolder.resolve(filename).writeText(text)
    }

}
