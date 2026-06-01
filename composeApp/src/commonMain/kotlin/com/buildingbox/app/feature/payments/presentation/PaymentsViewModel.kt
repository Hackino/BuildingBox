package com.buildingbox.app.feature.payments.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buildingbox.app.core.datetime.currentMonth
import com.buildingbox.app.core.datetime.monthOf
import com.buildingbox.app.core.datetime.shiftMonth
import com.buildingbox.app.core.money.DualAmount
import com.buildingbox.app.feature.calendar.domain.Expense
import com.buildingbox.app.feature.calendar.domain.ExpenseInput
import com.buildingbox.app.feature.calendar.domain.ExpensesRepository
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PaymentRow(val apartment: Apartment, val month: ApartmentMonth)

data class PaymentsUiState(
    val month: String = currentMonth(),
    val rows: List<PaymentRow> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val paidCount: Int = 0,
    val total: Int = 0,
    val collected: DualAmount = DualAmount.ZERO,
    val expected: DualAmount = DualAmount.ZERO,
    val missingBaseDues: Boolean = false,
)

class PaymentsViewModel(
    apartmentsRepo: ApartmentRepository,
    private val dues: DuesRepository,
    private val expensesRepo: ExpensesRepository,
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
            month.flatMapLatest { expensesRepo.observeMonth(it) },
            month,
        ) { apts, monthDues, monthExp, m ->
            val byApt = monthDues.groupBy { it.apartmentId }
            val rows = apts.map { a -> PaymentRow(a, aggregate(a.id, m, byApt[a.id] ?: emptyList())) }
            PaymentsUiState(
                month = m,
                rows = rows,
                expenses = monthExp.sortedByDescending { it.date },
                paidCount = rows.count { it.month.status == PaymentStatus.PAID },
                total = rows.size,
                collected = rows.fold(DualAmount.ZERO) { acc, r -> acc + r.month.paid },
                expected = rows.fold(DualAmount.ZERO) { acc, r -> acc + r.month.total },
                missingBaseDues = rows.any { it.month.dues.none { d -> d.base } },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PaymentsUiState())

    /** Drives the payments screen's loading overlay for pay/add/generate actions. */
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private fun action(block: suspend () -> Unit) = viewModelScope.launch {
        _loading.value = true
        block()
        _loading.value = false
    }

    // Month rows recompute from cached flows, so show the loader for a brief minimum.
    private fun switchMonth(delta: Int) = viewModelScope.launch {
        _loading.value = true
        month.value = shiftMonth(month.value, delta)
        delay(300)
        _loading.value = false
    }

    fun prevMonth() = switchMonth(-1)
    fun nextMonth() = switchMonth(1)

    fun setPaid(due: Due, paid: Boolean) = action { dues.setPaid(due, paid) }
    fun addDue(apartmentId: String, input: DueInput) = action { dues.addDue(apartmentId, month.value, input) }
    fun updateDue(due: Due, input: DueInput) = action { dues.updateDue(due, input) }
    fun removeDue(due: Due) = action { dues.removeDue(due) }

    fun generateBaseDues() = action {
        dues.generateBaseDues(month.value, apartments.value.associate { it.id to it.fee })
    }

    fun updateExpense(oldMonth: String, id: String, input: ExpenseInput) = action {
        val newMonth = monthOf(input.date)
        if (newMonth == oldMonth) {
            expensesRepo.updateExpense(oldMonth, id, input)
        } else {
            expensesRepo.removeExpense(oldMonth, id)
            expensesRepo.addExpense(newMonth, input)
        }
    }

    fun deleteExpense(month: String, id: String) = action { expensesRepo.removeExpense(month, id) }
}
