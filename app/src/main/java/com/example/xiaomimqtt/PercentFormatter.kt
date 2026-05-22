package com.example.xiaomimqtt

import java.text.NumberFormat
import java.util.Locale

object PercentFormatter {
    private const val NBSP = "\u00A0"

    fun format(value: Double, decimals: Int = 1, locale: Locale = Locale.getDefault()): String {
        val number = NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = decimals
            maximumFractionDigits = decimals
        }.format(value)

        return formatNumber(number, locale)
    }

    fun format(value: Int, locale: Locale = Locale.getDefault()): String {
        val number = NumberFormat.getIntegerInstance(locale).format(value)
        return formatNumber(number, locale)
    }

    private fun formatNumber(number: String, locale: Locale): String {
        val language = locale.language.lowercase(Locale.ROOT)
        val country = locale.country.uppercase(Locale.ROOT)

        return when {
            language in prefixPercentLanguages -> "%$number"
            language in noSpacePercentLanguages -> "$number%"
            language == "es" && country in northAmericanSpanishCountries -> "$number%"
            language in spacedPercentLanguages -> "$number$NBSP%"
            else -> "$number%"
        }
    }

    private val prefixPercentLanguages = setOf(
        "tr", "az", "ba", "crh", "cv", "kk", "ky", "sah", "tk", "tt", "ug", "uz"
    )

    private val noSpacePercentLanguages = setOf(
        "ar", "en", "fa", "he"
    )

    private val spacedPercentLanguages = setOf(
        "cs", "de", "es", "fi", "fr", "hr", "nl", "ru", "sk", "sv"
    )

    private val northAmericanSpanishCountries = setOf("MX", "US")
}
