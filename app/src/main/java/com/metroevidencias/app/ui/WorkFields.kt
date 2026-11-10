package com.metroevidencias.app.ui

import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalContext
import com.metroevidencias.app.domain.*
import java.time.LocalDate

@Composable
fun ShiftFields(d: RecordDraft, update: (RecordDraft) -> Unit) {
    val context = LocalContext.current
    ChoiceMenu("Turno de trabajo", listOf("Diurno", "Nocturno"), d.shift.ifBlank { null },
        { if (it == "Diurno") "Diurno · 08:00–20:00" else "Nocturno · 20:00–08:00" }) { value ->
        update(d.copy(shift = value, workDate = WorkCalendar.journeyDate(java.time.Instant.now(), value).toString()))
    }
    Text("Jornada: ${d.workDate.ifBlank { "Seleccionar fecha" }} · La noche conserva la fecha en que inicia.")
    OutlinedButton(onClick = {
        val date = runCatching { LocalDate.parse(d.workDate) }.getOrDefault(LocalDate.now(WorkCalendar.zone))
        android.app.DatePickerDialog(context, { _, year, month, day ->
            update(d.copy(workDate = LocalDate.of(year, month + 1, day).toString()))
        }, date.year, date.monthValue - 1, date.dayOfMonth).show()
    }) { Text("Cambiar fecha de jornada") }
}

fun placeCode(d: RecordDraft) = if(d.zone == Zone.OTHER) "OTHER:${d.customZone.trim()}" else "${d.zone.name}:${d.siteId}"
