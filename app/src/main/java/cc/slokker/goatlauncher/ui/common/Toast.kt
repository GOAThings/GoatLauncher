package cc.slokker.goatlauncher.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import cc.slokker.goatlauncher.ui.theme.GoatShapes

/** Aviso curto no topo do ecrã. */
@Composable
fun ToastPill(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(top = 27.dp)
            .shadow(8.dp, GoatShapes.Control)
            .background(Color(0xE61C2333), GoatShapes.Control)
            .padding(horizontal = 22.dp, vertical = 10.dp),
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall)
    }
}
