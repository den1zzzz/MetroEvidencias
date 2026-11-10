package com.metroevidencias.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.metroevidencias.app.domain.*
import kotlinx.coroutines.*
import java.io.File
import java.time.Instant
import java.util.UUID

@Composable
fun PhotoThumb(file: File, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    var image by remember(file.path,file.lastModified()) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(file.path,file.lastModified()) { image = withContext(Dispatchers.IO) { runCatching { EvidenceStorage.decoded(file,600) }.getOrNull() } }
    image?.let { Image(it.asImageBitmap(),"Abrir vista previa de fotografía",modifier.clickable(onClick = onClick)) }
}

@Composable
fun PhotoViewer(file: File, onDismiss: () -> Unit) {
    var original by remember { mutableStateOf(false) }
    val originalFile = File(file.parentFile,file.nameWithoutExtension+"-original.jpg")
    val selected = if(original && originalFile.exists()) originalFile else file
    var image by remember(selected.path) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(selected.path) { image = withContext(Dispatchers.IO) { runCatching { EvidenceStorage.decoded(selected,2048) }.getOrNull() } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.systemBarsPadding().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 36.dp),verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Vista previa · ${if(original) "Original" else "Foto con etiquetas"}",color = Color.White)
                Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment = Alignment.Center) {
                    image?.let { Image(it.asImageBitmap(),"Fotografía ampliada",Modifier.fillMaxSize()) } ?: Text("Cargando fotografía…",color = Color.White)
                }
                if(originalFile.exists()) OutlinedButton(colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White), onClick = { original = !original },modifier = Modifier.fillMaxWidth()) { Text(if(original) "Ver etiquetas" else "Ver original sin marcas") }
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Volver a las fotos") }
            }
        }
    }
}

@Composable
fun CameraSession(record: WorkRecord, onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val author = remember { context.getSharedPreferences("session",0).getString("user","").orEmpty() }
    var permitted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permitted = it }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val preview = remember { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(ResolutionStrategy(android.util.Size(2048,1536),ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)).build()).build() }
    var ready by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var photos by remember { mutableStateOf(emptyList<File>()) }
    var viewer by remember { mutableStateOf<File?>(null) }
    var clock by remember { mutableStateOf(Instant.now()) }
    var revision by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while(true) { clock = Instant.now(); delay(1000) } }
    LaunchedEffect(revision) { photos = withContext(Dispatchers.IO) { EvidenceStorage.recover(context); EvidenceStorage.records(context,record.id).map { File(it.parentFile,it.nameWithoutExtension+".jpg") } } }
    DisposableEffect(permitted,lifecycle) {
        var disposed = false
        if(permitted) {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                if(!disposed) try {
                    val provider = future.get(); cameraProvider = provider
                    val cameraPreview = Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
                    provider.bindToLifecycle(lifecycle,CameraSelector.DEFAULT_BACK_CAMERA,cameraPreview,capture); ready = true
                } catch(e: Exception) { message = "No se pudo abrir la cámara: ${e.message}" }
            },ContextCompat.getMainExecutor(context))
        }
        onDispose { disposed = true; cameraProvider?.unbind(capture); cameraProvider?.unbindAll(); ready = false }
    }
    Dialog(onDismissRequest = { if(!busy) onClose() },properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(),color = Color.Black) {
            Column(Modifier.systemBarsPadding().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 36.dp),verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${record.draft.workType.label} · ${record.draft.locationLabel()}",color = Color.White)
                Text("${record.draft.workDate} · ${record.draft.shift} · ${record.draft.locationLabel()}",color = Color.LightGray)
                Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                    if(permitted) AndroidView(factory = { preview },modifier = Modifier.fillMaxSize().clipToBounds())
                    else Column(Modifier.align(Alignment.Center)) {
                        Text("Autoriza la cámara para tomar varias fotos sin salir del trabajo.",color = Color.White)
                        Button(onClick = { permission.launch(Manifest.permission.CAMERA) }) { Text("Permitir cámara") }
                    }
                    if(permitted) Text(WorkCalendar.stampLines(record.draft,clock).joinToString("\n"),color = Color.White,
                        modifier = Modifier.align(Alignment.BottomEnd).background(Color(0x66000000)).padding(8.dp),style = MaterialTheme.typography.labelMedium)
                }
                Text("Fotos del registro: ${photos.size} · Toca una miniatura para ampliarla",color = Color.White)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(photos,key = { it.path }) { file -> PhotoThumb(file,Modifier.size(82.dp)) { viewer = file } } }
                Text(message,color = Color.White,style = MaterialTheme.typography.bodySmall)
                Button(colors = ButtonDefaults.buttonColors(disabledContainerColor = Color(0xFF333C40), disabledContentColor = Color.LightGray), enabled = ready && !busy,modifier = Modifier.fillMaxWidth(),onClick = {
                    busy = true; val id = UUID.randomUUID().toString(); val instant = Instant.now()
                    capture.targetRotation = preview.display?.rotation ?: android.view.Surface.ROTATION_0
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { EvidenceStorage.prepare(context,record,author,id,instant) }
                            capture.takePicture(ImageCapture.OutputFileOptions.Builder(EvidenceStorage.original(context,id)).build(),ContextCompat.getMainExecutor(context),object:ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                    EvidenceStorage.release(id)
                                    scope.launch {
                                        try { withContext(Dispatchers.IO) { EvidenceStorage.finish(context,id) }; revision++; message = "Guardada con fecha, hora y etiquetas. Puedes tomar otra." }
                                        catch(e: Exception) { message = "Original conservado. La copia marcada se recuperará al abrir el registro." }
                                        finally { busy = false }
                                    }
                                }
                                override fun onError(exception: ImageCaptureException) { EvidenceStorage.release(id); busy = false; message = "No se completó la captura. Vuelve a intentarlo." }
                            })
                        } catch(e: Exception) { EvidenceStorage.release(id); busy = false; message = "No se pudo guardar la foto. Comprueba el espacio disponible." }
                    }
                }) { Text(if(busy) "Guardando fotografía…" else "●  Tomar otra foto") }
                Button(colors = ButtonDefaults.buttonColors(disabledContainerColor = Color(0xFF333C40), disabledContentColor = Color.LightGray), enabled = !busy && photos.isNotEmpty(),modifier = Modifier.fillMaxWidth(),onClick = {
                    busy = true; scope.launch {
                        try { val count = withContext(Dispatchers.IO) { EvidenceStorage.confirm(context,record.id,author) }; scheduleSync(context); message = "$count fotos preparadas para envío"; onClose() }
                        catch(e: Exception) { message = "Las fotos se conservan en el teléfono. Reintenta desde el registro." }
                        finally { busy = false }
                    }
                }) { Text("Guardar y enviar fotos") }
                TextButton(enabled = !busy,onClick = onClose,modifier = Modifier.fillMaxWidth()) { Text("Guardar pendientes y volver") }
            }
        }
    }
    viewer?.let { PhotoViewer(it) { viewer = null } }
}


