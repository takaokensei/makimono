package zechs.drive.stream.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import zechs.drive.stream.R
import zechs.drive.stream.ui.model.AnimeItem

@Composable
fun ShelfItem(
    item: AnimeItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clickable { onClick() }
            .padding(all = getDimension(R.dimen.spacing_2))
            .size(180.dp),
        shape = MaterialTheme.shapes.medium,
        elevation = 2.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = androidx.compose.layout.Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.layout.Arrangement.Top
        ) {
            // Imagem do pôster
            androidx.compose.foundation.image.rememberAsyncImagePainter(
                model = item.posterUrl,
                placeholder = androidx.compose.foundation.image painterResource(id = R.drawable.ic_movie_placeholder)
            ) { painter ->
                androidx.compose.foundation.image.Image(
                    painter = painter,
                    contentDescription = item.title,
                    contentScale = androidx.compose.foundation.layout.ContentScale.Crop,
                    modifier = Modifier
                        .size(120.dp)
                        .clip(MaterialTheme.shapes.small)
                )
            }

            // Título
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = getDimension(R.dimen.spacing_1))
                    .width(120.dp)
                    .wrapContentWidth(align = androidx.layout.Alignment.Start)
            )
        }
    }
}

private fun getDimension(resId: Int): Dp =
    androidx.compose.ui.res.dimenValueResource(resId)