package com.metroevidencias.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.metroevidencias.app.domain.*
import com.metroevidencias.app.ui.theme.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun InfoCard(title: String, text: String) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}
@Composable
fun SectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Muted, style = MaterialTheme.typography.bodyMedium)
    }
}
@Composable
fun FormField(label: String, value: String, max: Int = 200, lines: Int = 1, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = { onChange(it.take(max)) },
        label = { Text(label) }, modifier = Modifier.fillMaxWidth(), minLines = lines,
        singleLine = lines == 1, shape = RoundedCornerShape(12.dp))
}
@Composable
fun <T> ChoiceMenu(label: String, values: List<T>, selected: T?, name: (T) -> String, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = Muted)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(selected?.let(name) ?: "Seleccionar", modifier = Modifier.weight(1f), color = Ink)
                    Text("⌄", color = Teal)
                }
            }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                values.forEach { value -> DropdownMenuItem(text = { Text(name(value)) },
                    onClick = { onSelect(value); expanded = false }) }
            }
        }
    }
}
@Composable
fun SummaryRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Muted)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
@Composable
fun RecordCard(record: WorkRecord) {
    val d = record.draft
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(d.workType.label, color = Teal, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.background(Color(0xFFE1F3EF), RoundedCornerShape(6.dp)).padding(7.dp))
                if (d.system != SystemType.NONE) Text(d.system.label, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.background(Color(0xFFE8EEF1), RoundedCornerShape(6.dp)).padding(7.dp))
            }
            Text(d.locationLabel(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(if (d.zone == Zone.OTHER) d.customZone else d.zone.label, color = Muted,
                style = MaterialTheme.typography.bodySmall)
            Text(d.description)
            if (d.reference.isNotBlank()) Text(d.reference, color = Muted)
            if (d.zone == Zone.OTHER) Text("Trabajo: ${d.customWork}", color = Muted)
            HorizontalDivider(color = Color(0xFFE8EEF1))
            Text(displayDate(record.createdAt), style = MaterialTheme.typography.labelMedium, color = Muted)
            Text("${d.workDate} · ${d.shift.ifBlank { "Turno sin definir" }}")
            Text("#" + d.activity.uppercase() + " #" + d.tag.replace("|", " #"))
            Text(d.gps.ifBlank { "GPS no registrado" })
            EvidencePanel(record)
        }
    }
}
fun displayDate(value: String): String = DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm")
    .withZone(ZoneId.of("America/Lima")).format(Instant.parse(value))


