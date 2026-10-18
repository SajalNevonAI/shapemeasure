package com.nevonai.smartshape.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TextSearchTest {

    private fun result(vararg lines: String): OcrResult {
        var y = 0f
        val ocrLines = lines.map { line ->
            var x = 0f
            val words = line.split(" ").map { w ->
                OcrWord(w, BoxF(x, y, x + w.length * 10, y + 20)).also { x += w.length * 10 + 10 }
            }
            OcrLine(line, BoxF(0f, y, x, y + 20), words).also { y += 30 }
        }
        return OcrResult(lines.joinToString("\n"), listOf(OcrBlock("", BoxF(0f, 0f, 100f, y), ocrLines)), 1000, 1000)
    }

    @Test
    fun caseInsensitiveWordWithPunctuation() {
        val r = result("Total: 42.00", "TOTAL due, total paid.")
        assertEquals(3, TextSearch.find(r, "total").size)
    }

    @Test
    fun phraseAcrossLineBreak() {
        val r = result("Made in", "India for export")
        val m = TextSearch.find(r, "made in india")
        assertEquals(1, m.size)
        assertEquals(3, m[0].boxes.size)
    }

    @Test
    fun codeSubstring() {
        val r = result("Batch INV-12345 exp 2027")
        assertEquals(1, TextSearch.find(r, "123").size)
        assertEquals(0, TextSearch.find(r, "999").size)
    }

    @Test
    fun blankQueryHasNoMatches() {
        assertEquals(0, TextSearch.find(result("anything"), "   ").size)
    }
}
