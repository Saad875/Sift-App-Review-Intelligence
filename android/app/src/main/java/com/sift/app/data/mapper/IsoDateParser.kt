package com.sift.app.data.mapper

import java.util.Calendar
import java.util.TimeZone

/**
 * Parses ISO 8601 timestamps such as "2024-05-02T10:15:00Z" or
 * "2024-05-02T10:15:00.123456+02:00" into epoch millis. java.time is not
 * available on API 24 without desugaring, so this uses java.util.Calendar.
 */
object IsoDateParser {
    private val pattern = Regex(
        """^(\d{4})-(\d{2})-(\d{2})(?:[T ](\d{2}):(\d{2})(?::(\d{2})(?:\.(\d+))?)?)?(Z|[+-]\d{2}:?\d{2})?$"""
    )

    fun parse(value: String?): Long? {
        val match = pattern.matchEntire(value?.trim().orEmpty()) ?: return null
        val groups = match.groupValues
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(groups[1].toInt(), groups[2].toInt() - 1, groups[3].toInt(),
                groups[4].toIntOrZero(), groups[5].toIntOrZero(), groups[6].toIntOrZero())
            set(Calendar.MILLISECOND, groups[7].padEnd(3, '0').take(3).toIntOrZero())
        }
        return calendar.timeInMillis - offsetMillis(groups[8])
    }

    private fun offsetMillis(zone: String): Long {
        if (zone.isEmpty() || zone == "Z") return 0
        val sign = if (zone.startsWith("-")) -1 else 1
        val digits = zone.drop(1).replace(":", "")
        val hours = digits.take(2).toInt()
        val minutes = digits.drop(2).toIntOrZero()
        return sign * (hours * 60L + minutes) * 60_000L
    }

    private fun String.toIntOrZero(): Int = toIntOrNull() ?: 0
}
