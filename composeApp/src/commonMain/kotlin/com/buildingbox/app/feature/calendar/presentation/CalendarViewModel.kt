package com.buildingbox.app.feature.calendar.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buildingbox.app.core.datetime.currentMonth
import com.buildingbox.app.core.datetime.dateOf
import com.buildingbox.app.core.datetime.monthOf
import com.buildingbox.app.core.datetime.shiftMonth
import com.buildingbox.app.core.money.DualAmount
import com.buildingbox.app.feature.calendar.domain.ExpenseInput
import com.buildingbox.app.feature.calendar.domain.ExpensesRepository
import com.buildingbox.app.feature.calendar.domain.Movement
import com.buildingbox.app.feature.calendar.domain.MovementKind
import com.buildingbox.app.feature.payments.domain.Due
import com.buildingbox.app.feature.payments.domain.DuesRepository
import com.buildingbox.app.feature.units.domain.Apartment
import com.buildingbox.app.feature.units.domain.ApartmentRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CalendarUiState(
    val month: String = currentMonth(),
    val movements: List<Movement> = emptyList(),
    val expenses: List<com.buildingbox.app.feature.calendar.domain.Expense> = emptyList(),
    val inTotal: DualAmount = DualAmount.ZERO,
    val outTotal: DualAmount = DualAmount.ZERO,
    val daysIn: Set<Int> = emptySet(),
    val daysOut: Set<Int> = emptySet(),
)

class CalendarViewModel(
    apartmentsRepo: ApartmentRepository,
    dues: DuesRepository,
    private val expenses: ExpensesRepository,
) : ViewModel() {

    private val month = MutableStateFlow(currentMonth())

    /** True while a month switch / add is in flight; cleared when fresh data arrives. */
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<CalendarUiState> =
        month.flatMapLatest { m ->
            combine(apartmentsRepo.observeApartments(), dues.observeMonth(m), expenses.observeMonth(m)) { apts, monthDues, monthExp ->
                build(m, apts, monthDues, monthExp)
            }
        }.onEach { _loading.value = false }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    fun prevMonth() { _loading.value = true; month.value = shiftMonth(month.value, -1) }
    fun nextMonth() { _loading.value = true; month.value = shiftMonth(month.value, 1) }
    fun addExpense(input: ExpenseInput) {
        // Shard by the expense's OWN date-month, not the month being viewed — otherwise a
        // June expense entered while viewing May lands in the May shard with a June date.
        viewModelScope.launch { _loading.value = true; expenses.addExpense(monthOf(input.date), input); _loading.value = false }
    }

    fun deleteExpense(month: String, id: String) {
        viewModelScope.launch { _loading.value = true; expenses.removeExpense(month, id); _loading.value = false }
    }

    /** Update an existing expense. If its date moved to another month, relocate the
     *  record to the correct month shard (delete the old, create in the new). */
    fun updateExpense(oldMonth: String, id: String, input: ExpenseInput) {
        viewModelScope.launch {
            _loading.value = true
            val newMonth = monthOf(input.date)
            if (newMonth == oldMonth) {
                expenses.updateExpense(oldMonth, id, input)
            } else {
                expenses.removeExpense(oldMonth, id)
                expenses.addExpense(newMonth, input)
            }
            _loading.value = false
        }
    }

    private fun build(m: String, apts: List<Apartment>, dues: List<Due>, exp: List<com.buildingbox.app.feature.calendar.domain.Expense>): CalendarUiState {
        val nameById = apts.associate { it.id to it }
        // Cash-flow: include any due that has received money (fully or partial),
        // and count the actual paidAmount rather than the total. Under the old
        // all-or-nothing model this was `filter { it.paid }.amount`; a partial
        // due had no `paidOn` and never contributed. Now a $200 payment against
        // a $500 due shows as +$200 on the day it was paid.
        val inMoves = dues.filter { !it.isUntouched }.map { d ->
            val apt = nameById[d.apartmentId]
            Movement(
                id = "in_${d.apartmentId}_${d.id}",
                kind = MovementKind.IN,
                date = d.paidOn ?: dateOf(m, 1),
                label = "${apt?.name ?: "Unit"} — ${d.title}",
                sublabel = apt?.ownerName,
                amount = d.paidAmount,
                apartmentId = d.apartmentId,
            )
        }
        val outMoves = exp.map { e ->
            Movement(
                id = "out_${e.id}",
                kind = MovementKind.OUT,
                date = e.date,
                label = e.label,
                sublabel = e.category.label,
                amount = e.amount,
                expenseMonth = e.month,
                expenseId = e.id,
            )
        }
        val movements = (inMoves + outMoves).sortedByDescending { it.date }
        val inTotal = inMoves.fold(DualAmount.ZERO) { a, mv -> a + mv.amount }
        val outTotal = outMoves.fold(DualAmount.ZERO) { a, mv -> a + mv.amount }
        fun dayOf(date: String) = date.split("-").getOrNull(2)?.toIntOrNull() ?: 0
        return CalendarUiState(
            month = m,
            movements = movements,
            expenses = exp,
            inTotal = inTotal,
            outTotal = outTotal,
            daysIn = inMoves.map { dayOf(it.date) }.toSet(),
            daysOut = outMoves.map { dayOf(it.date) }.toSet(),
        )
    }
}
