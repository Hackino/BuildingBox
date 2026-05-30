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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class ReportsViewModel(
    apartmentsRepo: ApartmentRepository,
    dues: DuesRepository,
    expenses: ExpensesRepository,
    db: RealtimeDb,
) : ViewModel() {

    private val month = MutableStateFlow(currentMonth())

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

    fun prevMonth() { month.value = shiftMonth(month.value, -1) }
    fun nextMonth() { month.value = shiftMonth(month.value, 1) }
}
