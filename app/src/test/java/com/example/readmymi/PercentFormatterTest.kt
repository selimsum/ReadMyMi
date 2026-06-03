package com.example.readmymi

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class PercentFormatterTest {

    private val NBSP = "\u00A0"

    @Test
    fun testFormatDouble_defaultDecimals() {
        val localeEn = Locale("en")
        assertEquals("45.5%", PercentFormatter.format(45.5, locale = localeEn))

        val localeFr = Locale("fr")
        // In French, decimals use a comma, so 45.5 becomes 45,5
        assertEquals("45,5$NBSP%", PercentFormatter.format(45.5, locale = localeFr))
    }

    @Test
    fun testFormatDouble_customDecimals() {
        val localeEn = Locale("en")
        assertEquals("45.50%", PercentFormatter.format(45.5, decimals = 2, locale = localeEn))
        assertEquals("45%", PercentFormatter.format(45.0, decimals = 0, locale = localeEn))
        assertEquals("45.56%", PercentFormatter.format(45.556, decimals = 2, locale = localeEn))
    }

    @Test
    fun testFormatInt() {
        val localeEn = Locale("en")
        assertEquals("45%", PercentFormatter.format(45, locale = localeEn))

        val localeFr = Locale("fr")
        assertEquals("45$NBSP%", PercentFormatter.format(45, locale = localeFr))
    }

    @Test
    fun testFormat_PrefixLanguage() {
        // Turkish
        val localeTr = Locale("tr")
        assertEquals("%45,5", PercentFormatter.format(45.5, locale = localeTr))
        assertEquals("%45", PercentFormatter.format(45, locale = localeTr))
    }

    @Test
    fun testFormat_NoSpaceLanguage() {
        // English
        val localeEn = Locale("en")
        assertEquals("45.5%", PercentFormatter.format(45.5, locale = localeEn))
        assertEquals("45%", PercentFormatter.format(45, locale = localeEn))
    }

    @Test
    fun testFormat_SpacedLanguage() {
        // French
        val localeFr = Locale("fr")
        assertEquals("45,5$NBSP%", PercentFormatter.format(45.5, locale = localeFr))
        assertEquals("45$NBSP%", PercentFormatter.format(45, locale = localeFr))

        // Spanish (Spain) - Spaced
        val localeEs = Locale("es", "ES")
        assertEquals("45,5$NBSP%", PercentFormatter.format(45.5, locale = localeEs))
        assertEquals("45$NBSP%", PercentFormatter.format(45, locale = localeEs))
    }

    @Test
    fun testFormat_NorthAmericanSpanish() {
        // Spanish (Mexico) - No Space
        val localeEsMx = Locale("es", "MX")
        assertEquals("45.5%", PercentFormatter.format(45.5, locale = localeEsMx))
        assertEquals("45%", PercentFormatter.format(45, locale = localeEsMx))

        // Spanish (US) - No Space
        val localeEsUs = Locale("es", "US")
        assertEquals("45.5%", PercentFormatter.format(45.5, locale = localeEsUs))
        assertEquals("45%", PercentFormatter.format(45, locale = localeEsUs))
    }

    @Test
    fun testFormat_Fallback() {
        // Default to no-space with "%" at the end for unlisted languages
        val localeIt = Locale("it")
        // Italian uses comma for decimal separator
        assertEquals("45,5%", PercentFormatter.format(45.5, locale = localeIt))
        assertEquals("45%", PercentFormatter.format(45, locale = localeIt))
    }
}
