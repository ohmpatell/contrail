package com.contrail.contrail.feature.trips.importing

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contrail.contrail.core.common.Geo
import com.contrail.contrail.core.data.logbook.FlightLogImporter
import com.contrail.contrail.core.data.logbook.LogPreview
import com.contrail.contrail.core.data.repository.AirportRepository
import com.contrail.contrail.core.data.ticket.AirportInfo
import com.contrail.contrail.core.data.ticket.SharedTicketInbox
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LogStage { Intro, Reading, Preview, Saving, Done }

data class LogImportState(
    val stage: LogStage = LogStage.Intro,
    val preview: LogPreview? = null,
    val places: Map<String, AirportInfo> = emptyMap(),
    val distanceKm: Long = 0,
    val message: String? = null,
    val imported: Int = 0,
)

@HiltViewModel
class LogImportViewModel @Inject constructor(
    private val importer: FlightLogImporter,
    private val airports: AirportRepository,
    private val inbox: SharedTicketInbox,
) : ViewModel() {
    private val _state = MutableStateFlow(LogImportState())
    val state: StateFlow<LogImportState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // A CSV shared to Contrail from Files, Drive or email lands here.
            inbox.shared.collect { t ->
                if (t != null && isLog(t.mime, t.uris)) inbox.take()?.uris?.firstOrNull()?.let(::read)
            }
        }
    }

    fun read(uri: Uri) = load { importer.read(uri) }

    fun readText(text: String) = load { importer.read(text) }

    private fun load(block: suspend () -> LogPreview) {
        _state.update { it.copy(stage = LogStage.Reading, message = null) }
        viewModelScope.launch {
            val result = runCatching { block() }
            result.exceptionOrNull()?.let { android.util.Log.w("ContrailLog", "Log import failed", it) }
            val p = result.getOrNull()
            if (p == null || (p.flights.isEmpty() && p.duplicates == 0)) {
                _state.update {
                    it.copy(
                        stage = LogStage.Intro,
                        message = if (p == null) "Couldn't read that file. Pick the CSV export itself." else "No flights found in that file. Is it a flight log CSV?",
                    )
                }
                return@launch
            }
            val dir = airports.directory()
            val codes = p.flights.flatMap { listOf(it.origin, it.destination) }.toSet()
            val places = codes.mapNotNull { c -> dir.find(c)?.let { c to it } }.toMap()
            val km = p.flights.sumOf { s ->
                val a = places[s.origin]
                val b = places[s.destination]
                if (a != null && b != null) Geo.distanceM(a.lat, a.lon, b.lat, b.lon) / 1000 else 0.0
            }.toLong()
            _state.update { it.copy(stage = LogStage.Preview, preview = p, places = places, distanceKm = km) }
        }
    }

    fun save() {
        val p = _state.value.preview ?: return
        if (p.flights.isEmpty()) return
        _state.update { it.copy(stage = LogStage.Saving) }
        viewModelScope.launch {
            importer.save(p)
            _state.update { it.copy(stage = LogStage.Done, imported = p.flights.size) }
        }
    }

    fun reset() = _state.update { LogImportState() }

    companion object {
        fun isLog(mime: String?, uris: List<Uri>): Boolean =
            mime in setOf("text/csv", "text/comma-separated-values", "application/csv", "text/x-csv", "application/vnd.ms-excel") ||
                uris.any { it.lastPathSegment?.endsWith(".csv", ignoreCase = true) == true }
    }
}
