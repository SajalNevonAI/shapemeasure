package com.nevonai.smartshape.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanRepositoryTest {

    @Test
    fun ftsQueryUsesPrefixTokensAndDropsOperators() {
        assertEquals("blue* label*", ScanRepository.ftsQuery("Blue  label"))
        assertEquals("inv* 12345*", ScanRepository.ftsQuery("INV-12345"))
        assertEquals("a* or* b*", ScanRepository.ftsQuery("\"a\" OR b*"))
        assertNull(ScanRepository.ftsQuery("  -- "))
    }

    @Test
    fun tagsAreNormalised() {
        assertEquals("packaging client-a", ScanRepository.normalizeTags(listOf(" Packaging", "client a", "PACKAGING", "")))
    }
}
