package dev.xera.xclicker.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 动作日志 —— 记录引擎在每个阶段的行为，方便排查问题。
 */
data class ActionLogEntry(
    val time: String,
    val packageName: String,
    val stage: String,      // EVENT / MATCH / CLICK / RESULT
    val detail: String,
    val success: Boolean
)

object ActionLog {
    private const val MAX_ENTRIES = 50
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    private val _entries = MutableStateFlow<List<ActionLogEntry>>(emptyList())
    val entries: StateFlow<List<ActionLogEntry>> = _entries.asStateFlow()

    fun log(packageName: String, stage: String, detail: String, success: Boolean = true) {
        val entry = ActionLogEntry(
            time = dateFormat.format(Date()),
            packageName = packageName.substringAfterLast('.'), // 只显示最后一段包名
            stage = stage,
            detail = detail,
            success = success
        )
        _entries.value = (listOf(entry) + _entries.value).take(MAX_ENTRIES)
    }

    fun clear() {
        _entries.value = emptyList()
    }
}
