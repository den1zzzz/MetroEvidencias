package com.metroevidencias.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metroevidencias.app.domain.*
import com.metroevidencias.app.ui.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun MetroApp(model: MetroViewModel) {
    val rawState by model.state.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val session = remember { context.getSharedPreferences("session", 0) }
    var logged by remember { mutableStateOf(session.contains("token")) }
    if (!logged) { LoginScreen { model.navigate("home"); logged = true }; return }
    val state = rawState.copy(records = rawState.records.filter { session.getString("role", "") == "supervisor" || it.draft.author == session.getString("user", "") })
    var discard by rememberSaveable { mutableStateOf(false) }
    fun cancel() {
        if (state.draft != RecordDraft()) discard = true else model.navigate("home")
    }
    BackHandler(enabled = state.screen != "home") {
        if (!state.saving) {
            if (state.screen == "form" && state.step > 0) model.previous()
            else if (state.screen == "form") cancel() else model.navigate("home")
        }
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(color = Ink) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(40.dp).background(Teal, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        Text("M", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("FotoEvidenciaEquans", color = Color.White, style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                        Text("EQUANS · SISTEMA CONTRAINCENDIO", color = Color(0xFFB7CED5), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }, bottomBar = {
            if (state.screen != "form") NavigationBar(containerColor = Color.White) {
                NavigationBarItem(selected = state.screen == "home", onClick = { model.navigate("home") },
                    icon = { Text("◫") }, label = { Text("Inicio") })
                NavigationBarItem(selected = state.screen == "records", onClick = { model.navigate("records") },
                    icon = { Text("≡") }, label = { Text("Registros") })
                NavigationBarItem(selected = state.screen == "progress", onClick = { model.navigate("progress") },
                    icon = { Text("↗") }, label = { Text("Conexión") })
            }
        }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { error ->
                Surface(color = MaterialTheme.colorScheme.errorContainer) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(error, color = MaterialTheme.colorScheme.onErrorContainer)
                        if (!state.storageReady && !state.loading) TextButton(onClick = model::reload) { Text("Reintentar lectura") }
                    }
                }
            }
            when (state.screen) {
                "form" -> RecordForm(state, model, ::cancel)
                "records" -> RecordsScreen(state)
                "progress" -> ConnectionScreen { session.edit().clear().apply(); logged = false }
                else -> HomeScreen(state, { model.start(); model.edit { it.copy(author = session.getString("user", "").orEmpty()) } }) { model.navigate("records") }
            }
        }
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false },
        title = { Text("¿Descartar este formulario?") }, text = { Text("Los datos que aún no guardaste se perderán.") },
        confirmButton = { TextButton(onClick = { discard = false; model.navigate("home") }) { Text("Descartar") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("Seguir editando") } })
}

@Composable
private fun HomeScreen(s: MetroState, onNew: () -> Unit, onRecords: () -> Unit) {
    val today = LocalDate.now(ZoneId.of("America/Lima"))
    val todayCount = s.records.count { Instant.parse(it.createdAt).atZone(ZoneId.of("America/Lima")).toLocalDate() == today }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { SectionTitle("Tu jornada, organizada.", "Registra cada trabajo y conserva su información en un solo lugar.") }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Ink)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("REGISTRO DE TRABAJO", color = Color(0xFF80DAC7), style = MaterialTheme.typography.labelMedium)
                    Text("Cada trabajo comienza\ncon un registro.", color = Color.White,
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Mantenimiento o avería, sistema y ubicación.", color = Color(0xFFB7CED5))
                    Button(onClick = onNew, enabled = s.storageReady && !s.loading,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text("+  Nuevo registro") }
                }
            }
        }
        s.notice?.let { item { InfoCard("Guardado", it) } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("${s.records.size}", "Registros locales", Modifier.weight(1f))
                StatCard("$todayCount", "Registros de hoy", Modifier.weight(1f))
            }
        }
        item { InfoCard("Fotos por ubicación y turno", "Toma varias fotos, revisa sus etiquetas y envíalas juntas. Se conservarán en el teléfono hasta recibir confirmación.") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Actividad reciente", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = onRecords) { Text("Ver todo") }
            }
        }
        if (s.records.isEmpty()) item { EmptyRecords() }
        items(s.records.take(3), key = { it.id }) { RecordCard(it) }
    }
}
@Composable
private fun StatCard(value: String, label: String, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Teal)
            Text(label, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}
@Composable
private fun EmptyRecords() {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Tu primer registro empieza aquí", style = MaterialTheme.typography.titleMedium)
            Text("Los trabajos que guardes aparecerán en este espacio.", color = Muted)
        }
    }
}
@Composable
private fun RecordsScreen(s: MetroState) {
    var filter by rememberSaveable { mutableStateOf("Todos") }
    val filtered = s.records.filter { filter == "Todos" || it.draft.workType.label == filter }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SectionTitle("Registros", "Consulta los datos guardados en este dispositivo.") }
        item { ChoiceMenu("Tipo de trabajo", listOf("Todos", "Mantenimiento", "Avería"), filter, { it }) { filter = it } }
        item { Text("${filtered.size} ${if (filtered.size == 1) "registro" else "registros"} · Hora de Lima", color = Muted, style = MaterialTheme.typography.labelLarge) }
        if (filtered.isEmpty()) item { EmptyRecords() }
        items(filtered, key = { it.id }) { RecordCard(it) }
    }
}
