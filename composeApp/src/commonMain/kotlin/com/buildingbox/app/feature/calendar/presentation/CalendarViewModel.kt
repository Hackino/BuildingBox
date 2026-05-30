package com.buildingbox.app.feature.calendar.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buildingbox.app.core.datetime.currentMonth
import com.buildingbox.app.core.datetime.dateOf
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CalendarUiState(
    val month: String = currentMonth(),
    val movements: List<Movement> = emptyList(),
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

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<CalendarUiState> =
        month.flatMapLatest { m ->
            combine(apartmentsRepo.observeApartments(), dues.observeMonth(m), expenses.observeMonth(m)) { apts, monthDues, monthExp ->
                build(m, apts, monthDues, monthExp)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    fun prevMonth() { month.value = shiftMonth(month.value, -1) }
    fun nextMonth() { month.value = shiftMonth(month.value, 1) }
    fun addExpense(input: ExpenseInput) { viewModelScope.launch { expenses.addExpense(month.value, input) } }

    private fun build(m: String, apts: List<Apartment>, dues: List<Due>, exp: List<com.buildingbox.app.feature.calendar.domain.Expense>): CalendarUiState {
        val nameById = apts.associate { it.id to it }
        val inMoves = dues.filter { it.paid }.map { d ->
            val apt = nameById[d.apartmentId]
            Movement(
                id = "in_${d.apartmentId}_${d.id}",
                kind = MovementKind.IN,
                date = d.paidOn ?: dateOf(m, 1),
                label = "${apt?.name ?: "Unit"} — ${d.title}",
                sublabel = apt?.ownerName,
                amount = d.amount,
            )
        }
        val outMoves = exp.map { e ->
            Movement("out_${e.id}", MovementKind.OUT, e.date, e.label, e.category.label, e.amount)
        }
        val movements = (inMoves + outMoves).sortedByDescending { it.date }
        val inTotal = inMoves.fold(DualAmount.ZERO) { a, mv -> a + mv.amount }
        val outTotal = outMoves.fold(DualAmount.ZERO) { a, mv -> a + mv.amount }
        fun dayOf(date: String) = date.split("-").getOrNull(2)?.toIntOrNull() ?: 0
        return CalendarUiState(
            month = m,
            movements = movements,
            inTotal = inTotal,
            outTotal = outTotal,
            daysIn = inMoves.map { dayOf(it.date) }.toSet(),
            daysOut = outMoves.map { dayOf(it.date) }.toSet(),
        )
    }
}
