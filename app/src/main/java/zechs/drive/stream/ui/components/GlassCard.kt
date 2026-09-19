package zechs.drive.stream.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.shapable
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import zechs.drive.stream.R

@Composable
fun GlassCard(
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    elevation: Int = 2
) {
    Card(
        modifier = modifier
            .padding(horizontal = getDimension(R.dimen.horizontal_padding))
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        elevation = elevation.dp,
        backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.border.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
            shape = MaterialTheme.shapes.medium
        )
    ) {
        content()
    }
}

private fun getDimension(resId: Int): Dp =
    androidx.compose.ui.res.dimenValueResource(resId)