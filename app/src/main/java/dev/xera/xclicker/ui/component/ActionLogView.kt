package dev.xera.xclicker.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.xera.xclicker.service.ActionLog
import dev.xera.xclicker.service.ActionLogEntry

@Composable
fun ActionLogView(modifier: Modifier = Modifier) {
    val logs by ActionLog.entries.collectAsState()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "动作跟踪 (Action Log)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "清空",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .padding(4.dp)
                        .clickable { ActionLog.clear() }
                )
                Spacer(modifier = Modifier.width(8.dp))
                val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                val context = androidx.compose.ui.platform.LocalContext.current
                Text(
                    text = "复制",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(4.dp)
                        .clickable {
                            val logText = logs.joinToString("\n") { "[${it.time}] [${it.stage}] ${it.packageName}: ${it.detail}" }
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(logText))
                            android.widget.Toast.makeText(context, "日志已复制到剪贴板", android.widget.Toast.LENGTH_SHORT).show()
                        }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (logs.isEmpty()) {
                Text(
                    text = "等待引擎运行...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.height(200.dp) // 限制日志窗口高度
                ) {
                    items(logs) { entry ->
                        LogEntryItem(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogEntryItem(entry: ActionLogEntry) {
    val stageColor = when (entry.stage) {
        "EVENT" -> MaterialTheme.colorScheme.tertiary
        "MATCH" -> MaterialTheme.colorScheme.secondary
        "ACTION" -> MaterialTheme.colorScheme.primary
        "ERROR" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = entry.time,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.width(64.dp)
        )
        
        Spacer(modifier = Modifier.width(4.dp))
        
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "[${entry.stage}]",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = stageColor
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = entry.packageName,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            
            Text(
                text = entry.detail,
                fontSize = 12.sp,
                color = if (entry.success) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
