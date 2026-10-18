package com.nevonai.smartshape.model

import kotlinx.serialization.Serializable

@Serializable
data class OcrWord(val text: String, val box: BoxF)

@Serializable
data class OcrLine(val text: String, val box: BoxF, val words: List<OcrWord>)

@Serializable
data class OcrBlock(val text: String, val box: BoxF, val lines: List<OcrLine>)

@Serializable
data class OcrResult(val text: String, val blocks: List<OcrBlock>, val imageWidth: Int, val imageHeight: Int) {
    val words: List<OcrWord> get() = blocks.flatMap { b -> b.lines.flatMap { it.words } }

    companion object {
        val Empty = OcrResult("", emptyList(), 0, 0)
    }
}

data class TextMatch(val wordIndices: IntRange, val boxes: List<BoxF>)

/** Case-insensitive word / phrase / code search over OCR output, in reading order. */
object TextSearch {

    fun normalize(s: String) = s.lowercase().trim { !it.isLetterOrDigit() }

    fun find(result: OcrResult, query: String): List<TextMatch> {
        val tokens = query.split(Regex("\\s+")).map(::normalize).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()
        val words = result.words
        val norm = words.map { normalize(it.text) }
        val matches = mutableListOf<TextMatch>()
        var i = 0
        while (i <= norm.size - tokens.size) {
            if (matchesAt(norm, i, tokens)) {
                val range = i until i + tokens.size
                matches += TextMatch(range, range.map { words[it].box })
                i += tokens.size
            } else {
                i++
            }
        }
        return matches
    }

    /**
     * Single-token queries match inside words (so "123" finds "INV-12345"); phrases match whole
     * inner words and allow the first/last word to be a suffix/prefix.
     */
    private fun matchesAt(words: List<String>, start: Int, tokens: List<String>): Boolean {
        if (tokens.size == 1) return words[start].contains(tokens[0])
        for (k in tokens.indices) {
            val w = words[start + k]
            val t = tokens[k]
            val ok = when (k) {
                0 -> w.endsWith(t)
                tokens.lastIndex -> w.startsWith(t)
                else -> w == t
            }
            if (!ok) return false
        }
        return true
    }
}
