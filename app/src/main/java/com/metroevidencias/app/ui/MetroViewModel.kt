package com.metroevidencias.app.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metroevidencias.app.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

data class MetroState(
    val records: List<WorkRecord> = emptyList(), val loading: Boolean = true,
    val storageReady: Boolean = false, val saving: Boolean = false,
    val error: String? = null, val notice: String? = null,
    val screen: String = "home", val step: Int = 0, val draft: RecordDraft = RecordDraft()
)
class MetroViewModel(private val repository: RecordRepository, private val saved: SavedStateHandle) : ViewModel() {
    private val mutable = MutableStateFlow(MetroState(
        screen = saved["screen"] ?: "home", step = saved["step"] ?: 0,
        draft = RecordDraft(
            workType = WorkType.valueOf(saved["work"] ?: WorkType.MAINTENANCE.name),
            system = SystemType.valueOf(saved["system"] ?: SystemType.NONE.name),
            description = saved["description"] ?: "", zone = Zone.valueOf(saved["zone"] ?: Zone.STATION.name),
            siteId = saved["site"] ?: "", reference = saved["reference"] ?: "",
            customZone = saved["customZone"] ?: "", customLocation = saved["customLocation"] ?: "",
            customWork = saved["customWork"] ?: "", locationConfirmed = saved["confirmed"] ?: false, tag = saved["tag"] ?: "Extintores", gps = saved["gps"] ?: "", author = saved["author"] ?: "", shift = saved["shift"] ?: "", workDate = saved["workDate"] ?: "", activity = saved["activity"] ?: "Inspección")))
    val state = mutable.asStateFlow()
    init { reload() }
    fun reload() {
        if (mutable.value.saving) return
        mutable.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try { val records = repository.read(); mutable.update { it.copy(records = records, loading = false, storageReady = true) } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(loading = false, storageReady = false,
                error = "No se pudieron leer los registros. Se ha bloqueado el guardado para proteger los datos. Puedes reintentar.") } }
        }
    }
    fun navigate(screen: String) {
        if (mutable.value.saving) return
        saved["screen"] = screen
        mutable.update { it.copy(screen = screen, error = if (it.storageReady) null else it.error) }
    }
    fun start() {
        if (!mutable.value.storageReady) return
        edit { RecordDraft(shift = WorkCalendar.shift(java.time.Instant.now()), workDate = WorkCalendar.journeyDate(java.time.Instant.now()).toString()) }; setStep(0); saved["recordId"] = UUID.randomUUID().toString()
        navigate("form"); mutable.update { it.copy(notice = null) }
    }
    fun edit(change: (RecordDraft) -> RecordDraft) {
        if (mutable.value.saving) return
        val d = change(mutable.value.draft)
        saved["shift"] = d.shift; saved["workDate"] = d.workDate; saved["activity"] = d.activity; saved["tag"] = d.tag; saved["gps"] = d.gps; saved["author"] = d.author; saved["work"] = d.workType.name; saved["system"] = d.system.name
        saved["description"] = d.description; saved["zone"] = d.zone.name
        saved["site"] = d.siteId; saved["reference"] = d.reference
        saved["customZone"] = d.customZone; saved["customLocation"] = d.customLocation
        saved["customWork"] = d.customWork; saved["confirmed"] = d.locationConfirmed
        mutable.update { it.copy(draft = d, error = null) }
    }
    fun selectZone(zone: Zone) = edit { it.copy(zone = zone,
        siteId = if (zone == Zone.TUNNEL) "TUNNEL" else "", reference = "", customZone = "",
        customLocation = "", customWork = "", locationConfirmed = false) }
    private fun setStep(step: Int) { saved["step"] = step; mutable.update { it.copy(step = step, error = null) } }
    fun previous() { if (!mutable.value.saving && mutable.value.step > 0) setStep(mutable.value.step - 1) }
    fun next() {
        val s = mutable.value
        val error = if (s.step == 0) s.draft.workError() else s.draft.locationError()
        if (error != null) mutable.update { it.copy(error = error) } else setStep((s.step + 1).coerceAtMost(2))
    }
    fun save() {
        val s = mutable.value
        if (s.saving || !s.storageReady) return
        val error = s.draft.error()
        if (error != null) { mutable.update { it.copy(error = error) }; return }
        mutable.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                val id: String = saved["recordId"] ?: UUID.randomUUID().toString().also { saved["recordId"] = it }
                val record = WorkRecord(id, Instant.now().toString(), s.draft.copy(description = s.draft.description.trim()))
                val records = repository.add(record)
                saved["screen"] = "home"
                mutable.update { it.copy(records = records, saving = false, screen = "home",
                    notice = "Registro guardado en este dispositivo.") }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(saving = false,
                error = "No se pudo guardar. Tu formulario sigue disponible; vuelve a intentarlo.") } }
        }
    }
}


