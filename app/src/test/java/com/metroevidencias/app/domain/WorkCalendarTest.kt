package com.metroevidencias.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class WorkCalendarTest {
    private fun local(value: String) = LocalDateTime.parse(value).atZone(WorkCalendar.zone).toInstant()
    @Test fun nightKeepsPreviousDateBeforeEight() {
        val instant=local("2026-11-11T00:01:00")
        assertEquals("Nocturno",WorkCalendar.shift(instant))
        assertEquals(LocalDate.parse("2026-11-10"),WorkCalendar.journeyDate(instant))
        assertEquals(LocalDate.parse("2026-11-10"),WorkCalendar.journeyDate(local("2026-11-11T07:59:59")))
    }
    @Test fun exactShiftBoundaries() {
        assertEquals("Diurno",WorkCalendar.shift(local("2026-11-11T08:00:00")))
        assertEquals("Diurno",WorkCalendar.shift(local("2026-11-11T19:59:59")))
        assertEquals("Nocturno",WorkCalendar.shift(local("2026-11-11T20:00:00")))
        assertEquals(LocalDate.parse("2026-11-11"),WorkCalendar.journeyDate(local("2026-11-11T20:00:00")))
    }
    @Test fun stampUsesCaptureTimeRatherThanJourneyDate() {
        val d=RecordDraft(siteId="E20",shift="Nocturno",workDate="2026-11-10",tag="Extintores|Detectores")
        val lines=WorkCalendar.stampLines(d,local("2026-11-11T01:02:03"))
        assertEquals("#11/11/2026 01:02:03",lines.first())
        assertTrue(lines.all { it.startsWith("#") })
        assertFalse(lines.contains("#CUARTO DE BOMBAS"))
        assertTrue(lines.contains("#EXTINTORES")); assertTrue(lines.contains("#DETECTORES"))
    }
}
