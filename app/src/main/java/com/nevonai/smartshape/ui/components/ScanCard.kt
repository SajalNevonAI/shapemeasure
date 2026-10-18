package com.nevonai.smartshape.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nevonai.smartshape.data.Scan
import com.nevonai.smartshape.model.LengthUnit
import com.nevonai.smartshape.ui.theme.overlayColor
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
fun ScanCard(scan: Scan, unit: LengthUnit, onClick: () -> Unit, modifier: Modifier = Modifier, onFavorite: (() -> Unit)? = null) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(MaterialTheme.shapes.medium)) {
            AsyncImage(model = File(scan.thumbnailPath), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Row(Modifier.align(Alignment.BottomStart).padding(8.dp)) {
                scan.shapes.map { it.type }.distinct().take(4).forEach {
                    Box(Modifier.padding(end = 4.dp).size(10.dp).background(it.overlayColor(), CircleShape))
                }
            }
            onFavorite?.let {
                IconButton(onClick = it, modifier = Modifier.align(Alignment.TopEnd)) {
                    Icon(
                        if (scan.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        if (scan.favorite) "Remove from favourites" else "Add to favourites",
                        tint = if (scan.favorite) Color(0xFFFF6B8B) else Color.White,
                    )
                }
            }
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(scan.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.padding(top = 2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val primary = scan.measurements.firstOrNull()
                Text(
                    primary?.headline(unit) ?: DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(scan.createdAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (!scan.ocr?.text.isNullOrBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.TextFields, "Contains text", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
