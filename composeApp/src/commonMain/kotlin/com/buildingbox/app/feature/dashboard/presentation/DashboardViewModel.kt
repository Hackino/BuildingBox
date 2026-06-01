package com.buildingbox.app.feature.dashboard.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buildingbox.app.core.datetime.currentMonth
import com.buildingbox.app.core.datetime.dateOf
import com.buildingbox.app.core.datetime.monthOf
import com.buildingbox.app.core.datetime.shiftMonth
import com.buildingbox.app.core.money.DualAmount
import com.buildingbox.app.feature.calendar.domain.Expense
import com.buildingbox.app.feature.calendar.domain.ExpensesRepository
import com.buildingbox.app.feature.calendar.domain.Movement
import com.buildingbox.app.feature.calendar.domain.MovementKind
import com.buildingbox.app.feature.payments.domain.Due
import com.buildingbox.app.feature.payments.domain.DuesRepository
import com.buildingbox.app.feature.payments.domain.PaymentStatus
import com.buildingbox.app.feature.payments.domain.statusOf
import com.buildingbox.app.feature.units.domain.Apartment
import com.buildingbox.app.feature.units.domain.ApartmentRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class DashboardUiState(
    val month: String = currentMonth(),
    val balance: DualAmount = DualAmount.ZERO,
    val inThisMonth: DualAmount = DualAmount.ZERO,
    val outThisMonth: DualAmount = DualAmount.ZERO,
    val paid: Int = 0,
    val partial: Int = 0,
    val unpaid: Int = 0,
    val total: Int = 0,
    val recent: List<Movement> = emptyList(),
    val trendUsd: List<Float> = emptyList(),
    val trendLbp: List<Float> = emptyList(),
)

class DashboardViewModel(
    apartmentsRepo: ApartmentRepository,
    dues: DuesRepository,
    expenses: ExpensesRepository,
) : ViewModel() {

    val state: StateFlow<DashboardUiState> =
        combine(apartmentsRepo.observeApartments(), dues.observeAll(), expenses.observeAll()) { apts, allDues, allExp ->
            build(apts, allDues, allExp)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    private fun build(apts: List<Apartment>, allDues: List<Due>, allExp: List<Expense>): DashboardUiState {
        val month = currentMonth()
        val paidDues = allDues.filter { it.paid }

        val balance = paidDues.fold(DualAmount.ZERO) { a, d -> a + d.amount } -
            allExp.fold(DualAmount.ZERO) { a, e -> a + e.amount }

        val monthDues = allDues.filter { it.month == month }
        val inThis = monthDues.filter { it.paid }.fold(DualAmount.ZERO) { a, d -> a + d.amount }
        val outThis = allExp.filter { it.month == month }.fold(DualAmount.ZERO) { a, e -> a + e.amount }

        val statuses = apts.map { a -> statusOf(monthDues.filter { it.apartmentId == a.id }) }

        // Recent movements across the box.
        val nameById = apts.associateBy { it.id }
        val inMoves = paidDues.map { d ->
            val apt = nameById[d.apartmentId]
            Movement("in_${d.month}_${d.apartmentId}_${d.id}", MovementKind.IN, d.paidOn ?: dateOf(d.month, 1), "${apt?.name ?: "Unit"} — ${d.title}", apt?.ownerName, d.amount, apartmentId = d.apartmentId)
        }
        val outMoves = allExp.map { e -> Movement("out_${e.month}_${e.id}", MovementKind.OUT, e.date, e.label, e.category.label, e.amount) }
        val recent = (inMoves + outMoves).sortedByDescending { it.date }.take(5)

        // 5-month cumulative balance trend (oldest → newest).
        val trendUsd = mutableListOf<Float>()
        val trendLbp = mutableListOf<Float>()
        for (i in 4 downTo 0) {
            val upto = shiftMonth(month, -i)
            val pIn = paidDues.filter { monthOf(it.paidOn ?: dateOf(it.month, 1)) <= upto }.fold(DualAmount.ZERO) { a, d -> a + d.amount }
            val pOut = allExp.filter { monthOf(it.date) <= upto }.fold(DualAmount.ZERO) { a, e -> a + e.amount }
            trendUsd.add((pIn.usdCents - pOut.usdCents).toFloat())
            trendLbp.add((pIn.lbp - pOut.lbp).toFloat())
        }

        return DashboardUiState(
            month = month,
            balance = balance,
            inThisMonth = inThis,
            outThisMonth = outThis,
            paid = statuses.count { it == PaymentStatus.PAID },
            partial = statuses.count { it == PaymentStatus.PARTIAL },
            unpaid = statuses.count { it == PaymentStatus.UNPAID },
            total = apts.size,
            recent = recent,
            trendUsd = trendUsd,
            trendLbp = trendLbp,
        )
    }
}
