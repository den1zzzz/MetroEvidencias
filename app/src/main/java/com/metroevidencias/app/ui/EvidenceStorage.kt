package com.metroevidencias.app.ui

import android.content.Context
import android.graphics.*
import android.util.AtomicFile
import androidx.exifinterface.media.ExifInterface
import com.metroevidencias.app.domain.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

object EvidenceStorage {
    private val active = ConcurrentHashMap.newKeySet<String>()
    fun original(context: Context, id: String) = File(evidenceRoot(context), "$id-original.jpg")
    fun write(file: File, data: ByteArray) {
        val atomic = AtomicFile(file); val stream = atomic.startWrite()
        try { stream.write(data); atomic.finishWrite(stream) } catch(e: Exception) { atomic.failWrite(stream); throw e }
    }
    fun prepare(context: Context, record: WorkRecord, author: String, id: String, instant: Instant) {
        val d = record.draft
        val shift = d.shift.ifBlank { "Sin definir" }
        val workDate = d.workDate.ifBlank { instant.atZone(WorkCalendar.zone).toLocalDate().toString() }
        val json = JSONObject().put("id", id).put("recordId", record.id).put("author", author)
            .put("type", d.workType.label).put("location", d.locationLabel()).put("tag", d.tag)
            .put("gps", d.gps).put("description", d.description).put("createdAt", instant.toString())
            .put("shift", shift).put("workDate", workDate)
            .put("siteCode", placeCode(d)).put("activity", d.activity).put("requiresConfirmation", true)
            .put("stampLines", JSONArray(WorkCalendar.stampLines(d, instant)))
        active.add(id)
        try { write(File(evidenceRoot(context), "$id.capture"), json.toString().toByteArray()) }
        catch(e: Exception) { active.remove(id); throw e }
    }
    fun release(id: String) { active.remove(id) }
    @Synchronized
    fun finish(context: Context, id: String) {
        val root = evidenceRoot(context)
        if(File(root,"$id.json").exists()) return
        val capture = File(root,"$id.capture")
        val json = JSONObject(capture.readText())
        val array = json.getJSONArray("stampLines")
        val bitmap = decoded(original(context,id), 2048)
        try {
            val canvas = Canvas(bitmap)
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE; typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                textSize = bitmap.width * .032f; setShadowLayer(3f, 1f, 2f, Color.BLACK)
            }
            val margin = bitmap.width * .018f
            fun wrapped(): List<String> = (0 until array.length()).flatMap { i ->
                val source = array.getString(i)
                val result = mutableListOf<String>(); var line = ""
                source.split(" ").forEach { word ->
                    val candidate = if(line.isEmpty()) word else "$line $word"
                    if(text.measureText(candidate) > bitmap.width - margin*2 && line.isNotEmpty()) { result.add(line); line = "#" + word.trimStart('#') }
                    else line = candidate
                }
                if(line.isNotEmpty()) result.add(line)
                result
            }
            var lines = wrapped()
            while((lines.size * text.textSize * 1.2f > bitmap.height * .48f || lines.any { text.measureText(it) > bitmap.width - margin*2 }) && text.textSize > 10f) { text.textSize *= .9f; lines = wrapped() }
            val lineHeight = text.textSize * 1.2f
            val top = bitmap.height - margin - lineHeight*lines.size
            val widest = lines.maxOfOrNull { text.measureText(it) } ?: 0f
            canvas.drawRoundRect(bitmap.width-margin-widest-10, top-8, bitmap.width-margin+6, bitmap.height-margin+6, 8f,8f,
                Paint().apply { color = Color.argb(70,0,0,0) })
            text.textAlign = Paint.Align.RIGHT
            lines.forEachIndexed { index,line -> canvas.drawText(line,bitmap.width-margin,top+(index+1)*lineHeight-text.descent(),text) }
            val atomic = AtomicFile(File(root,"$id.jpg")); val stream = atomic.startWrite()
            try { check(bitmap.compress(Bitmap.CompressFormat.JPEG,92,stream)); atomic.finishWrite(stream) }
            catch(e: Exception) { atomic.failWrite(stream); throw e }
            write(File(root,"$id.json"), json.toString().toByteArray())
            capture.delete() // Only the completed internal capture journal, never the original.
        } finally { bitmap.recycle() }
    }
    fun recover(context: Context) {
        evidenceRoot(context).listFiles()?.filter { it.extension == "capture" && it.nameWithoutExtension !in active }?.forEach {
            val id = it.nameWithoutExtension
            if(original(context,id).length() > 0) runCatching { finish(context,id) }
        }
    }
    fun records(context: Context, recordId: String): List<File> = evidenceRoot(context).listFiles()?.filter {
        it.extension == "json" && runCatching { JSONObject(it.readText()).getString("recordId") == recordId }.getOrDefault(false)
    }?.sortedBy { it.lastModified() }.orEmpty()
    fun confirm(context: Context, recordId: String, author: String): Int {
        var count = 0
        records(context,recordId).forEach { file ->
            val json = JSONObject(file.readText()); val id = json.getString("id")
            if(json.getString("author") == author && !File(file.parentFile,"$id.sent").exists()) {
                write(File(file.parentFile,"$id.ready"), byteArrayOf(1)); count++
            }
        }
        return count
    }
    fun decoded(file: File, maximum: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path,bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Imagen incompleta" }
        var sample = 1
        while(maxOf(bounds.outWidth,bounds.outHeight)/sample > maximum) sample *= 2
        val bitmap = requireNotNull(BitmapFactory.decodeFile(file.path,BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }))
        val exif = ExifInterface(file)
        val matrix = Matrix().apply { postRotate(exif.rotationDegrees.toFloat()); if(exif.isFlipped) postScale(-1f,1f) }
        val oriented = Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true)
        if(oriented !== bitmap) bitmap.recycle()
        val mutable = oriented.copy(Bitmap.Config.ARGB_8888,true)
        oriented.recycle(); return mutable
    }
}

