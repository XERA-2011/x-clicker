package com.xera.xclicker.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.xera.xclicker.util.appInfoMapFlow
import com.xera.xclicker.util.loadAppIcon

@Composable
fun AppIcon(
    modifier: Modifier = Modifier,
    appId: String,
) {
    val appInfo = appInfoMapFlow.collectAsState().value[appId]
    val icon = produceState<android.graphics.drawable.Drawable?>(null, appId, appInfo?.mtime) {
        value = withContext(Dispatchers.IO) { loadAppIcon(appId) }
    }.value
    val iconModifier = modifier.size(32.dp)
    if (icon != null) {
        Image(
            painter = rememberDrawablePainter(icon),
            contentDescription = null,
            modifier = iconModifier
        )
    } else {
        PerfIcon(
            imageVector = PerfIcon.Android,
            modifier = iconModifier
        )
    }
}
