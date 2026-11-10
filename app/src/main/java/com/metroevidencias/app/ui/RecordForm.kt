package com.metroevidencias.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.metroevidencias.app.domain.*
import com.metroevidencias.app.ui.theme.*

@Composable
fun RecordForm(s: MetroState, model: MetroViewModel, onCancel: () -> Unit) {
    val d = s.draft
    val scroll = key(s.step) { rememberScrollState() }
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("NUEVO REGISTRO", style = MaterialTheme.typography.labelLarge, color = Teal)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Trabajo", "Ubicación", "Revisión").forEachIndexed { index, name ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    LinearProgressIndicator(progress = { if (index <= s.step) 1f else 0f }, modifier = Modifier.fillMaxWidth())
                    Text("${index + 1}. $name", style = MaterialTheme.typography.labelMedium,
                        color = if (index == s.step) Teal else Muted)
                }
            }
        }
        // During writes, prevent changing fields beneath the captured record snapshot.
        if (s.saving) {
            InfoCard("Guardando registro", "Espera un momento mientras se guardan los datos.")
            CircularProgressIndicator()
        } else when (s.step) {
            0 -> {
                SectionTitle("¿Qué trabajo realizaste?", "Selecciona la actividad y añade una descripción breve.")
                ShiftFields(d) { change -> model.edit { change } }
                Text("Tipo de trabajo", style = MaterialTheme.typography.labelLarge, color = Muted)
                WorkType.entries.forEach { type ->
                    Card(onClick = { model.edit { it.copy(workType = type) } }, modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = if (d.workType == type) MaterialTheme.colorScheme.primaryContainer else Color.White)) {
                        Row(Modifier.padding(12.dp)) {
                            RadioButton(selected = d.workType == type, onClick = { model.edit { it.copy(workType = type) } })
                            Column(Modifier.padding(top = 9.dp)) {
                                Text(type.label, fontWeight = FontWeight.SemiBold)
                                Text(if (type == WorkType.MAINTENANCE) "Inspección, revisión o prueba" else "Atención de una incidencia",
                                    style = MaterialTheme.typography.bodySmall, color = Muted)
                            }
                        }
                    }
                }
                ChoiceMenu("Sistema (opcional)", SystemType.entries.toList(), d.system, { it.label }) { v -> model.edit { it.copy(system = v) } }
                Text("ACI: contra incendios · DACI: detección y alarma", color = Muted, style = MaterialTheme.typography.bodySmall)
                FormField("Etiqueta o descripción *", d.description, max = 2000, lines = 3) { v -> model.edit { it.copy(description = v) } }
                ChoiceMenu("Actividad", listOf("Inspección", "Limpieza", "Prueba", "Reparación", "Reemplazo"), d.activity, { it }) { v -> model.edit { it.copy(activity = v) } }
                TagSelector(d.tag) { v -> model.edit { it.copy(tag = v) } }
                Text("Las etiquetas no crean carpetas.", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            1 -> {
                SectionTitle("Ubica el trabajo", "Elige el lugar y confirma que corresponde a tu actividad.")
                GpsCapture(d.gps) { v -> model.edit { it.copy(gps = v) } }
                ChoiceMenu("Zona de trabajo *", Zone.entries.toList(), d.zone, { it.label }, model::selectZone)
                if (d.zone == Zone.OTHER) {
                    FormField("Zona de trabajo *", d.customZone) { v -> model.edit { it.copy(customZone = v, locationConfirmed = false) } }
                    FormField("Ubicación o descripción *", d.customLocation) { v -> model.edit { it.copy(customLocation = v, locationConfirmed = false) } }
                    FormField("Tipo de trabajo en Otros *", d.customWork) { v -> model.edit { it.copy(customWork = v) } }
                } else if (d.zone == Zone.TUNNEL) {
                    InfoCard("Túnel", "Registro general del túnel. No necesitas seleccionar un tramo.")
                } else {
                    val sites = Sites.inZone(d.zone)
                    ChoiceMenu("Ubicación específica *", sites, sites.find { it.id == d.siteId }, { it.label }) { v ->
                        model.edit { it.copy(siteId = v.id, locationConfirmed = false) }
                    }
                }
                FormField("Referencia adicional (opcional)", d.reference) { v -> model.edit { it.copy(reference = v) } }
                Row {
                    Checkbox(checked = d.locationConfirmed, onCheckedChange = { v -> model.edit { it.copy(locationConfirmed = v) } })
                    Text("Confirmo que esta ubicación corresponde al trabajo realizado.", modifier = Modifier.padding(top = 12.dp).weight(1f))
                }
            }
            else -> {
                SectionTitle("Revisa tu registro", "Verifica la información antes de guardarla.")
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        SummaryRow("Jornada y turno", "${d.workDate} · ${d.shift}")
                        SummaryRow("Etiquetas", "#" + d.activity.uppercase() + " #" + d.tag.replace("|", " #"))
                        SummaryRow("Tipo de trabajo", d.workType.label)
                        SummaryRow("Sistema", d.system.label)
                        SummaryRow("Descripción", d.description)
                        HorizontalDivider()
                        SummaryRow("Zona", if (d.zone == Zone.OTHER) d.customZone else d.zone.label)
                        SummaryRow("Ubicación", d.locationLabel())
                        if (d.reference.isNotBlank()) SummaryRow("Referencia", d.reference)
                        if (d.zone == Zone.OTHER) SummaryRow("Trabajo en Otros", d.customWork)
                    }
                }
                InfoCard("Guardado local", "Esta etapa guarda la información del trabajo. Después de guardar, adjunta fotografías desde Registros.")
            }
        }
        Button(onClick = { if (s.step == 2) model.save() else model.next() },
            enabled = !s.saving && s.storageReady,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(if (s.saving) "Guardando…" else if (s.step == 2) "Guardar registro" else "Continuar")
        }
        if (s.step > 0) OutlinedButton(onClick = model::previous, enabled = !s.saving,
            modifier = Modifier.fillMaxWidth()) { Text("Volver al paso anterior") }
        TextButton(onClick = onCancel, enabled = !s.saving, modifier = Modifier.fillMaxWidth()) { Text("Cancelar registro") }
        Text("* Campos obligatorios", color = Muted, style = MaterialTheme.typography.labelSmall)
    }
}


