package com.metroevidencias.app.domain

enum class WorkType(val label: String) { MAINTENANCE("Mantenimiento"), FAILURE("Avería") }
enum class SystemType(val label: String) { NONE("Sin especificar"), ACI("ACI"), DACI("DACI") }
enum class Zone(val label: String) {
    STATION("Estaciones"), PTSA("Patio Taller Santa Anita"), TUNNEL("Túnel"),
    SHAFT("Pozos de ventilación"), OTHER("Otros")
}
data class Site(val id: String, val label: String, val zone: Zone)

/** Only site names supplied by the project owner. No invented coordinates. */
object Sites {
    val all = (20..24).map { Site("E$it", if (it == 20) "E20 – Evitamiento" else "E$it", Zone.STATION) } +
        listOf("TSAM", "TSAA", "TSAO", "TSAR", "SEAT", "TSAP", "TSAX")
            .map { Site(it, it, Zone.PTSA) } +
        Site("TUNNEL", "Túnel", Zone.TUNNEL) +
        (19..24).map { Site("POZO-$it", "Pozo $it", Zone.SHAFT) }
    fun inZone(zone: Zone) = all.filter { it.zone == zone }
}
data class RecordDraft(
    val workType: WorkType = WorkType.MAINTENANCE,
    val system: SystemType = SystemType.NONE,
    val description: String = "",
    val zone: Zone = Zone.STATION,
    val siteId: String = "",
    val reference: String = "",
    val customZone: String = "",
    val customLocation: String = "",
    val customWork: String = "",
    val locationConfirmed: Boolean = false, val tag: String = "Extintores", val gps: String = "", val author: String = "", val shift: String = "", val workDate: String = "", val activity: String = "Inspección"
) {
    fun locationLabel(): String = if (zone == Zone.OTHER) customLocation.trim()
        else Sites.all.find { it.id == siteId && it.zone == zone }?.label.orEmpty()
    fun workError(): String? = when { description.isBlank() -> "Escribe una descripción del trabajo."; shift.isNotEmpty() && shift !in listOf("Diurno", "Nocturno") -> "Selecciona un turno válido."; workDate.isNotEmpty() && runCatching { java.time.LocalDate.parse(workDate) }.isFailure -> "Fecha de jornada inválida."; else -> null }
    fun locationError(): String? = when {
        zone == Zone.OTHER && customZone.isBlank() -> "Escribe la zona de trabajo."
        zone == Zone.OTHER && customLocation.isBlank() -> "Escribe la ubicación o descripción del lugar."
        zone == Zone.OTHER && customWork.isBlank() -> "Escribe el tipo de trabajo para Otros."
        zone != Zone.OTHER && locationLabel().isEmpty() -> "Selecciona una ubicación."
        !locationConfirmed -> "Confirma que la ubicación es correcta."
        else -> null
    }
    fun error(): String? = workError() ?: locationError()
}

/** Work records have an independent lifecycle from their photographs and upload state. */
data class WorkRecord(val id: String, val createdAt: String, val draft: RecordDraft)
interface RecordRepository {
    suspend fun read(): List<WorkRecord>
    suspend fun add(record: WorkRecord): List<WorkRecord>
}


