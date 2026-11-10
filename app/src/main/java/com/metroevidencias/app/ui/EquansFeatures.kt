package com.metroevidencias.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.location.LocationManagerCompat
import androidx.work.*
import com.metroevidencias.app.domain.WorkRecord
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

fun api(base: String, path: String, body: JSONObject, token: String = ""): JSONObject {
    val url = URL(base.trimEnd('/') + path)
    require(url.protocol in listOf("http", "https"))
    val connection = url.openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 15000; connection.readTimeout = 30000
        connection.requestMethod = "POST"; connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        if (token.isNotEmpty()) connection.setRequestProperty("Authorization", "Bearer $token")
        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        check(connection.responseCode in 200..299) { "Servidor: ${connection.responseCode}. Comprueba acceso y conexión." }
        return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
    } finally { connection.disconnect() }
}

@Composable
fun LoginScreen(onLogin: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("session", 0) }
    var base by rememberSaveable { mutableStateOf(prefs.getString("base", "http://10.0.2.2:5080").orEmpty()) }
    var user by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        SectionTitle("FotoEvidenciaEquans", "Equans · Mantenimiento del sistema contraincendio")
        InfoCard("Inicia sesión", "El primer acceso requiere conexión al servidor. Después puedes registrar trabajos y capturar fotos sin conexión.")
        FormField("Servidor", base) { base = it }
        FormField("Usuario", user) { user = it }
        OutlinedTextField(password, { password = it }, label = { Text("Contraseña") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Button(enabled = !busy && user.isNotBlank() && password.isNotBlank(), onClick = {
            busy = true; status = "Conectando…"
            scope.launch {
                try {
                    val result = withContext(Dispatchers.IO) { api(base, "/login", JSONObject().put("user", user).put("password", password)) }
                    prefs.edit().putString("base", base.trimEnd('/')).putString("user", user)
                        .putString("role", result.getString("role")).putString("token", result.getString("token")).apply()
                    password = ""; scheduleSync(context); onLogin()
                } catch(e: Exception) { status = e.message ?: "No se pudo iniciar sesión" }
                finally { busy = false }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Iniciar sesión") }
        Text(status)
        Text("El supervisor crea las cuentas del equipo desde el panel de la computadora.")
    }
}

val fixedTags = listOf("Extintores", "Detectores", "Hidrantes", "Motobomba", "Electrobomba", "Rociadores", "Cortina de humo", "Escalera eléctrica")
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TagSelector(selected: String, onSelect: (String) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("tags",0) }
    var tags by remember { mutableStateOf((fixedTags + listOf("Gabinetes") + prefs.getStringSet("custom",emptySet()).orEmpty().sorted()).distinct()) }
    var custom by rememberSaveable { mutableStateOf("") }
    val chosen = selected.split("|").filter { it.isNotBlank() }
    Text("Etiquetas sobre la foto · Selección múltiple")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tags.forEach { tag -> FilterChip(selected = tag in chosen, onClick = {
            val next = if(tag in chosen) chosen - tag else chosen + tag
            if(next.isNotEmpty() && next.joinToString("|").length <= 500) onSelect(next.joinToString("|"))
        }, label = { Text("#" + tag.uppercase()) }) }
    }
    FormField("Agregar otra etiqueta",custom,max = 80) { custom = it.replace("|", " ").trimStart('#') }
    OutlinedButton(enabled = custom.isNotBlank(),onClick = {
        val tag = custom.trim(); val known = tags.find { it.equals(tag,true) } ?: tag
        if(known !in tags) { tags = tags + known; prefs.edit().putStringSet("custom",(tags-fixedTags.toSet()-"Gabinetes").toSet()).apply() }
        if(known !in chosen && (chosen+known).joinToString("|").length <= 500) onSelect((chosen+known).joinToString("|"))
        custom = ""
    }) { Text("Añadir etiqueta") }
}
@Composable
fun GpsCapture(value: String, onFix: (String) -> Unit) {
    val context = LocalContext.current
    var status by remember { mutableStateOf("") }
    val signal = remember { mutableStateOf<CancellationSignal?>(null) }
    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) { onDispose { signal.value?.cancel() } }
    fun capture() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) { status = "Permiso GPS no disponible"; return }
        signal.value?.cancel()
        try {
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) { status = "Activa ubicación en el teléfono. Puedes continuar manualmente."; return }
            val current = CancellationSignal(); signal.value = current; status = "Buscando señal GPS…"
            LocationManagerCompat.getCurrentLocation(manager, LocationManager.GPS_PROVIDER, current, ContextCompat.getMainExecutor(context)) { fix ->
                if (signal.value === current) {
                    signal.value = null
                    if (fix != null && System.currentTimeMillis() - fix.time < 120000) {
                        onFix("${fix.latitude}, ${fix.longitude} · precisión ${fix.accuracy.toInt()} m · ${java.time.Instant.ofEpochMilli(fix.time)}")
                        status = "Coordenadas registradas. Confirma también la zona manual."
                    } else status = "Sin señal reciente. Continúa con ubicación manual."
                }
            }
            scope.launch { delay(30000); if (signal.value === current) { current.cancel(); signal.value = null; status = "Sin señal GPS. Puedes continuar manualmente." } }
        } catch(e: Exception) { status = "No se pudo obtener GPS. Selecciona la ubicación manual." }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (it[Manifest.permission.ACCESS_FINE_LOCATION] == true) capture() else status = "GPS preciso no autorizado. Puedes continuar manualmente."
    }
    InfoCard("Ubicación GPS", value.ifBlank { "Sin coordenadas. En interiores o túneles puede no haber señal." })
    OutlinedButton(onClick = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) capture()
        else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }) { Text("Registrar ubicación GPS actual") }
    Text(status)
}

fun evidenceRoot(context: Context) = File(context.filesDir, "evidence").apply { mkdirs() }
fun scheduleSync(context: Context) {
    val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    WorkManager.getInstance(context).enqueueUniqueWork("evidence-send", ExistingWorkPolicy.REPLACE,
        OneTimeWorkRequestBuilder<EvidenceSyncWorker>().setConstraints(constraints).build())
    WorkManager.getInstance(context).enqueueUniquePeriodicWork("evidence-periodic", ExistingPeriodicWorkPolicy.KEEP,
        PeriodicWorkRequestBuilder<EvidenceSyncWorker>(15, java.util.concurrent.TimeUnit.MINUTES).setConstraints(constraints).build())
}
class EvidenceSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = applicationContext.getSharedPreferences("session",0)
        val token = prefs.getString("token","").orEmpty()
        if(token.isBlank()) return@withContext Result.success()
        var failed = false
        for(meta in evidenceRoot(applicationContext).listFiles()?.filter { it.extension=="json" }.orEmpty()) {
            val id = meta.nameWithoutExtension
            val sending = File(meta.parentFile,"$id.sending")
            val failure = File(meta.parentFile,"$id.error")
            try {
                if(File(meta.parentFile,"$id.sent").exists()) continue
                val json = JSONObject(meta.readText())
                if(json.optString("author")!=prefs.getString("user","")) continue
                if(json.optBoolean("requiresConfirmation",false) && !File(meta.parentFile,"$id.ready").exists()) continue
                sending.writeText("sending")
                val photo = File(meta.parentFile,"$id.jpg")
                val original = EvidenceStorage.original(applicationContext,id)
                require(photo.length() in 1..18L*1024*1024)
                if(original.exists()) {
                    require(original.length() in 1..18L*1024*1024)
                    json.put("originalImage",android.util.Base64.encodeToString(original.readBytes(),android.util.Base64.NO_WRAP))
                }
                json.put("image",android.util.Base64.encodeToString(photo.readBytes(),android.util.Base64.NO_WRAP))
                val reply = api(prefs.getString("base","").orEmpty(),"/evidence",json,token)
                check(reply.getString("id")==id && reply.getBoolean("stored"))
                EvidenceStorage.write(File(meta.parentFile,"$id.sent"),"confirmed".toByteArray())
                failure.delete()
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { failed = true; failure.writeText("Envío no confirmado. Reintenta con conexión y sesión válida.") }
            finally { sending.delete() }
        }
        if(failed) Result.retry() else Result.success()
    }
}
@Composable
fun EvidencePanel(record: WorkRecord) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val author = remember { context.getSharedPreferences("session",0).getString("user","").orEmpty() }
    var refresh by remember { mutableIntStateOf(0) }
    var camera by rememberSaveable { mutableStateOf(false) }
    var viewer by remember { mutableStateOf<File?>(null) }
    var status by remember { mutableStateOf("") }
    var metas by remember { mutableStateOf(emptyList<File>()) }
    LaunchedEffect(Unit) { while(true) { delay(4000); refresh++ } }
    LaunchedEffect(refresh,camera) { metas = withContext(Dispatchers.IO) { EvidenceStorage.recover(context); EvidenceStorage.records(context,record.id) } }
    Text("Fotografías: ${metas.size} · Toca para ampliar")
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(metas.size) { index ->
            val meta = metas[index]
            val photo = File(meta.parentFile,meta.nameWithoutExtension+".jpg")
            PhotoThumb(photo,Modifier.size(100.dp)) { viewer = photo }
        }
    }
    val states = metas.map { meta ->
        val id = meta.nameWithoutExtension
        when {
            File(meta.parentFile,"$id.sent").exists() -> "Sincronizadas"
            File(meta.parentFile,"$id.sending").let { it.exists() && System.currentTimeMillis()-it.lastModified()<120000 } -> "Enviando"
            File(meta.parentFile,"$id.error").exists() -> "Error / pendiente de reintento"
            File(meta.parentFile,"$id.ready").exists() -> "Pendiente de sincronización"
            runCatching { JSONObject(meta.readText()).optBoolean("requiresConfirmation",false) }.getOrDefault(false) -> "Por confirmar envío"
            else -> "Pendiente de sincronización"
        }
    }
    Text(states.groupingBy { it }.eachCount().entries.joinToString(" · ") { "${it.key}: ${it.value}" })
    OutlinedButton(onClick = { camera = true }) { Text("Abrir cámara · Varias fotos") }
    if(metas.isNotEmpty()) Button(onClick = {
        scope.launch {
            try {
                val count = withContext(Dispatchers.IO) { EvidenceStorage.confirm(context,record.id,author) }
                scheduleSync(context); refresh++; status = "$count fotos confirmadas para envío. Se conservarán también en el teléfono."
            } catch(e: Exception) { status = "No se pudo preparar el envío. Las fotos se conservan localmente." }
        }
    }) { Text("Enviar fotos pendientes") }
    Text(status)
    if(camera) CameraSession(record) { camera = false; refresh++ }
    viewer?.let { PhotoViewer(it) { viewer = null } }
}
@Composable
fun ConnectionScreen(onLogout: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("session", 0)
    Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionTitle("Almacenamiento y conexión", "Usuario: ${prefs.getString("user", "")}")
        InfoCard("Primero en el teléfono", "Las fotos y sus metadatos se conservan en el almacenamiento privado, con o sin Internet. No se borran al sincronizar.")
        Text("Servidor: ${prefs.getString("base", "")}")
        Text("El supervisor accede desde su navegador a la dirección del servidor. Los envíos se reintentan en segundo plano cuando hay red. Sin servidor accesible permanecen pendientes.")
        Text("Cerrar sesión detiene nuevos envíos de tu cuenta. Borrar los datos de la app o desinstalarla elimina las fotos locales.")
        Button(onClick = { scheduleSync(context) }) { Text("Reintentar sincronización") }
        OutlinedButton(onClick = { WorkManager.getInstance(context).cancelUniqueWork("evidence-send"); WorkManager.getInstance(context).cancelUniqueWork("evidence-periodic"); onLogout() }) { Text("Cerrar sesión") }
    }
}





