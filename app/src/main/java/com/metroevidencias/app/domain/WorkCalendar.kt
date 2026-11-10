package com.metroevidencias.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object WorkCalendar {
    val zone: ZoneId = ZoneId.of("America/Lima")
    fun shift(instant: Instant): String = if (instant.atZone(zone).hour in 8..19) "Diurno" else "Nocturno"
    fun journeyDate(instant: Instant, shift: String = shift(instant)): LocalDate {
        val local = instant.atZone(zone)
        return if (shift == "Nocturno" && local.hour < 8) local.toLocalDate().minusDays(1) else local.toLocalDate()
    }
    fun stampLines(draft: RecordDraft, instant: Instant): List<String> {
        val tags = listOf(draft.locationLabel(), draft.shift.takeIf { it.isNotBlank() }?.let { "Turno $it" }.orEmpty(),
            draft.workType.label, draft.activity) + draft.tag.split("|")
        return listOf("#" + DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(zone).format(instant)) +
            tags.filter { it.isNotBlank() }.map { "#" + it.trim().trimStart('#').uppercase(java.util.Locale.ROOT) }.distinct()
    }
}
