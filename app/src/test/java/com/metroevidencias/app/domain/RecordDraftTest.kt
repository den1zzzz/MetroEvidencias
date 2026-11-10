package com.metroevidencias.app.domain

import org.junit.Assert.*
import org.junit.Test

class RecordDraftTest {
    private val valid = RecordDraft(description = "Inspección", siteId = "E20", locationConfirmed = true)
    @Test fun stationCannotBeUsedAsPtsaBuilding() {
        assertNotNull(valid.copy(zone = Zone.PTSA).locationError())
    }
    @Test fun tunnelDoesNotRequireAnInventedSubdivision() {
        assertNull(valid.copy(zone = Zone.TUNNEL, siteId = "TUNNEL").error())
    }
    @Test fun manualLocationNeedsWorkerConfirmation() {
        assertNotNull(valid.copy(locationConfirmed = false).error())
    }
    @Test fun othersRequireAllThreeManualFields() {
        val draft = valid.copy(zone = Zone.OTHER, siteId = "", customZone = "Otra contrata",
            customLocation = "Sala técnica", customWork = "Prueba de alarma")
        assertNull(draft.error())
        assertNotNull(draft.copy(customZone = " ").error())
        assertNotNull(draft.copy(customLocation = " ").error())
        assertNotNull(draft.copy(customWork = " ").error())
    }
    @Test fun whitespaceIsNotADescription() { assertNotNull(valid.copy(description = "  ").error()) }
    @Test fun catalogContainsOnlySuppliedSites() {
        assertEquals(listOf("E20", "E21", "E22", "E23", "E24"), Sites.inZone(Zone.STATION).map { it.id })
        assertEquals(listOf("TSAM", "TSAA", "TSAO", "TSAR", "SEAT", "TSAP", "TSAX"), Sites.inZone(Zone.PTSA).map { it.id })
        assertEquals((19..24).map { "POZO-$it" }, Sites.inZone(Zone.SHAFT).map { it.id })
        assertEquals("E20 – Evitamiento", Sites.inZone(Zone.STATION).first().label)
        assertEquals(1, Sites.inZone(Zone.TUNNEL).size)
        assertTrue(Sites.inZone(Zone.OTHER).isEmpty())
    }
}
