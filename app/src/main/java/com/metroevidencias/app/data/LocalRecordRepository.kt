package com.metroevidencias.app.data

import android.content.Context
import android.util.AtomicFile
import com.metroevidencias.app.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.time.Instant

/** Atomic record storage. Read failures propagate to prevent overwriting unreadable data. */
class LocalRecordRepository(context: Context) : RecordRepository {
    private val file = AtomicFile(File(context.filesDir, "registros.json"))
    private val mutex = Mutex()
    override suspend fun read() = withContext(Dispatchers.IO) { mutex.withLock { readFile() } }
    override suspend fun add(record: WorkRecord) = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(record.draft.error() == null)
            val previous = readFile()
            if (previous.any { it.id == record.id }) return@withLock previous
            val updated = listOf(record) + previous
            val array = JSONArray()
            updated.forEach { r -> array.put(encode(r)) }
            val json = JSONObject().put("schemaVersion", 2).put("records", array)
            val stream = file.startWrite()
            try {
                stream.write(json.toString().toByteArray(Charsets.UTF_8)); file.finishWrite(stream)
            } catch (e: Exception) { file.failWrite(stream); throw e }
            updated
        }
    }
    private fun readFile(): List<WorkRecord> {
        val raw = try { file.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() } }
            catch (e: java.io.FileNotFoundException) { return emptyList() }
        val root = JSONTokener(raw).nextValue()
        val legacy = root is JSONArray
        val array = if (legacy) root as JSONArray else (root as JSONObject).let {
            check(it.getInt("schemaVersion") == 2) { "Unsupported schema" }
            it.getJSONArray("records")
        }
        return (0 until array.length()).map { index ->
            val r = array.getJSONObject(index)
            val record = if (legacy) decodeLegacy(r) else decode(r)
            Instant.parse(record.createdAt)
            record
        }
    }
    private fun encode(r: WorkRecord) = JSONObject().apply {
        put("id", r.id); put("createdAt", r.createdAt)
        with(r.draft) {
            put("workType", workType.name); put("system", system.name)
            put("description", description); put("zone", zone.name); put("siteId", siteId)
            put("reference", reference); put("customZone", customZone)
            put("customLocation", customLocation); put("customWork", customWork)
            put("locationConfirmed", locationConfirmed); put("tag", tag); put("gps", gps); put("author", author); put("shift", shift); put("workDate", workDate); put("activity", activity)
        }
    }
    private fun decode(r: JSONObject) = WorkRecord(r.getString("id"), r.getString("createdAt"), RecordDraft(
        WorkType.valueOf(r.getString("workType")), SystemType.valueOf(r.getString("system")),
        r.getString("description"), Zone.valueOf(r.getString("zone")), r.getString("siteId"),
        r.getString("reference"), r.getString("customZone"), r.getString("customLocation"),
        r.getString("customWork"), r.getBoolean("locationConfirmed"), r.optString("tag", "Extintores"), r.optString("gps"), r.optString("author"), r.optString("shift"), r.optString("workDate"), r.optString("activity", "Inspección")))

    // Accept the original JSON format during upgrades.
    private fun decodeLegacy(r: JSONObject): WorkRecord {
        val zone = when (r.getString("zona")) {
            "Estación" -> Zone.STATION; "PTSA" -> Zone.PTSA; "Túnel" -> Zone.TUNNEL
            "Pozo" -> Zone.SHAFT; "Otros" -> Zone.OTHER
            else -> error("Unknown legacy zone")
        }
        val place = r.getString("ubicacion")
        val site = when (zone) {
            Zone.TUNNEL -> "TUNNEL"; Zone.SHAFT -> place.replace("Pozo ", "POZO-")
            Zone.OTHER -> ""; else -> place
        }
        return WorkRecord(r.getString("id"), r.getString("fecha"), RecordDraft(
            workType = if (r.getString("tipo") == "Avería") WorkType.FAILURE else WorkType.MAINTENANCE,
            description = r.getString("descripcion"), zone = zone, siteId = site,
            reference = r.getString("detalle"), customZone = if (zone == Zone.OTHER) "Otros" else "",
            customLocation = if (zone == Zone.OTHER) r.getString("detalle") else "",
            customWork = if (zone == Zone.OTHER) r.getString("tipo") else "", locationConfirmed = true))
    }
}


