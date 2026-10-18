package com.nevonai.smartshape.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nevonai.smartshape.model.OcrResult
import com.nevonai.smartshape.model.TextMatch

@Composable
fun OcrPanel(
    ocr: OcrResult?,
    running: Boolean,
    query: String,
    matches: List<TextMatch>,
    activeMatch: Int,
    onQuery: (String) -> Unit,
    onStep: (Int) -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
    onRerun: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Search word, phrase or code") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (matches.isEmpty()) "0" else "${activeMatch + 1}/${matches.size}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        IconButton(onClick = { onStep(-1) }, enabled = matches.isNotEmpty()) { Icon(Icons.Rounded.KeyboardArrowUp, "Previous match") }
                        IconButton(onClick = { onStep(1) }, enabled = matches.isNotEmpty()) { Icon(Icons.Rounded.KeyboardArrowDown, "Next match") }
                        IconButton(onClick = { onQuery("") }) { Icon(Icons.Rounded.Close, "Clear search") }
                    }
                }
            },
        )

        if (running) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Recognising text…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = onCopy, enabled = !ocr?.text.isNullOrBlank(), label = { Text("Copy text") }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) })
            onRerun?.let { AssistChip(onClick = it, enabled = !running, label = { Text("Re-run OCR") }, leadingIcon = { Icon(Icons.Rounded.Refresh, null) }) }
        }

        val text = ocr?.text.orEmpty()
        if (!running && text.isBlank()) {
            Text("No text was recognised in this image.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (text.isNotBlank()) {
            SelectionContainer {
                Text(highlight(text, query), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun highlight(text: String, query: String) = buildAnnotatedString {
    val q = query.trim()
    if (q.isEmpty()) {
        append(text)
        return@buildAnnotatedString
    }
    var i = 0
    val lower = text.lowercase()
    val ql = q.lowercase()
    while (i < text.length) {
        val hit = lower.indexOf(ql, i)
        if (hit < 0) {
            append(text.substring(i))
            break
        }
        append(text.substring(i, hit))
        withStyle(SpanStyle(background = Color(0x66FFD54F))) { append(text.substring(hit, hit + q.length)) }
        i = hit + q.length
    }
}
