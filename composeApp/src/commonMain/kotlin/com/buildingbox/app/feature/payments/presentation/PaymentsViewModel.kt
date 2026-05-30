package com.buildingbox.app.feature.payments.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buildingbox.app.core.datetime.currentMonth
import com.buildingbox.app.core.datetime.shiftMonth
import com.buildingbox.app.core.money.DualAmount
import com.buildingbox.app.feature.payments.domain.ApartmentMonth
import com.buildingbox.app.feature.payments.domain.Due
import com.buildingbox.app.feature.payments.domain.DueInput
import com.buildingbox.app.feature.payments.domain.DuesRepository
import com.buildingbox.app.feature.payments.domain.PaymentStatus
import com.buildingbox.app.feature.payments.domain.aggregate
import com.buildingbox.app.feature.units.domain.Apartment
import com.buildingbox.app.feature.units.domain.ApartmentRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PaymentRow(val apartment: Apartment, val month: ApartmentMonth)

data class PaymentsUiState(
    val month: String = currentMonth(),
    val rows: List<PaymentRow> = emptyList(),
    val paidCount: Int = 0,
    val total: Int = 0,
    val collected: DualAmount = DualAmount.ZERO,
    val expected: DualAmount = DualAmount.ZERO,
    val missingBaseDues: Boolean = false,
)

class PaymentsViewModel(
    apartmentsRepo: ApartmentRepository,
    private val dues: DuesRepository,
) : ViewModel() {

    private val month = MutableStateFlow(currentMonth())

    val apartments: StateFlow<List<Apartment>> =
        apartmentsRepo.observeApartments()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<PaymentsUiState> =
        combine(
            apartments,
            month.flatMapLatest { dues.observeMonth(it) },
            month,
        ) { apts, monthDues, m ->
            val byApt = monthDues.groupBy { it.apartmentId }
            val rows = apts.map { a -> PaymentRow(a, aggregate(a.id, m, byApt[a.id] ?: emptyList())) }
            PaymentsUiState(
                month = m,
                rows = rows,
                paidCount = rows.count { it.month.status == PaymentStatus.PAID },
                total = rows.size,
                collected = rows.fold(DualAmount.ZERO) { acc, r -> acc + r.month.paid },
                expected = rows.fold(DualAmount.ZERO) { acc, r -> acc + r.month.total },
                missingBaseDues = rows.any { it.month.dues.none { d -> d.base } },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PaymentsUiState())

    fun prevMonth() { month.value = shiftMonth(month.value, -1) }
    fun nextMonth() { month.value = shiftMonth(month.value, 1) }

    fun setPaid(due: Due, paid: Boolean) = viewModelScope.launch { dues.setPaid(due, paid) }
    fun addDue(apartmentId: String, input: DueInput) = viewModelScope.launch { dues.addDue(apartmentId, month.value, input) }
    fun updateDue(due: Due, input: DueInput) = viewModelScope.launch { dues.updateDue(due, input) }
    fun removeDue(due: Due) = viewModelScope.launch { dues.removeDue(due) }

    fun generateBaseDues() = viewModelScope.launch {
        dues.generateBaseDues(month.value, apartments.value.associate { it.id to it.fee })
    }
}
