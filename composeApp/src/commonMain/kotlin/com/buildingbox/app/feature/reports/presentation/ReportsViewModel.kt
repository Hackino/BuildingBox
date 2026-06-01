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

    /** True while switching months; cleared when the rebuilt report emits. */
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    val state: StateFlow<ReportData?> =
        combine(
            apartmentsRepo.observeApartments(),
            dues.observeAll(),
            expenses.observeAll(),
            db.observeValue("building", BuildingDto.serializer()),
            month,
        ) { apts, allDues, allExp, building, m ->
            buildReport(apts, allDues, allExp, building, m)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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
