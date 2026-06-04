package com.buildingbox.app.feature.reports.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buildingbox.app.core.datetime.currentMonth
import com.buildingbox.app.core.datetime.shiftMonth
import com.buildingbox.app.core.firebase.RealtimeDb
import com.buildingbox.app.feature.calendar.domain.ExpensesRepository
import com.buildingbox.app.feature.payments.domain.DuesRepository
import com.buildingbox.app.feature.reports.domain.BuildingDto
import com.buildingbox.app.feature.reports.domain.ReportData
import com.buildingbox.app.feature.reports.domain.buildReport
import com.buildingbox.app.feature.units.domain.ApartmentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReportsViewModel(
    apartmentsRepo: ApartmentRepository,
    dues: DuesRepository,
    expenses: ExpensesRepository,
    db: RealtimeDb,
) : ViewModel() {

    private val month = MutableStateFlow(currentMonth())

    /** The currently-selected report month (for the multi-export range default). */
    val selectedMonth: StateFlow<String> = month

    /** True while switching months; cleared when the rebuilt report emits. */
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    // Cache the latest raw inputs so we can rebuild ANY month's report on demand
    // (e.g. for the multi-month PDF export) without re-querying.
    private var latestApts: List<com.buildingbox.app.feature.units.domain.Apartment> = emptyList()
    private var latestDues: List<com.buildingbox.app.feature.payments.domain.Due> = emptyList()
    private var latestExp: List<com.buildingbox.app.feature.calendar.domain.Expense> = emptyList()
    private var latestBuilding: BuildingDto? = null

    val state: StateFlow<ReportData?> =
        combine(
            apartmentsRepo.observeApartments(),
            dues.observeAll(),
            expenses.observeAll(),
            db.observeValue("building", BuildingDto.serializer()),
            month,
        ) { apts, allDues, allExp, building, m ->
            latestApts = apts; latestDues = allDues; latestExp = allExp; latestBuilding = building
            buildReport(apts, allDues, allExp, building, m)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Build reports for an inclusive [from]..[to] month range (chronological order). */
    fun reportsForRange(from: String, to: String): List<ReportData> {
        val (lo, hi) = if (from <= to) from to to else to to from
        val months = buildList {
            var m = lo
            while (m <= hi) { add(m); m = shiftMonth(m, 1) }
        }
        return months.map { buildReport(latestApts, latestDues, latestExp, latestBuilding, it) }
    }

    // The report is rebuilt locally from already-loaded flows, so a month switch has
    // ~no fetch latency. Show the loader for a brief minimum so it's actually visible.
    private fun switchMonth(delta: Int) {
        viewModelScope.launch {
            _loading.value = true
            month.value = shiftMonth(month.value, delta)
            delay(300)
            _loading.value = false
        }
    }

    fun prevMonth() = switchMonth(-1)
    fun nextMonth() = switchMonth(1)
}
