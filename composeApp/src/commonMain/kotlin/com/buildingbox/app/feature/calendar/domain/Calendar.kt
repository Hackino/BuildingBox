package com.buildingbox.app.feature.calendar.domain

import com.buildingbox.app.core.money.DualAmount
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

enum class ExpenseCategory(val key: String, val label: String) {
    MAINTENANCE("maintenance", "Maintenance"),
    UTILITIES("utilities", "Utilities"),
    CLEANING("cleaning", "Cleaning"),
    SECURITY("security", "Security"),
    REPAIRS("repairs", "Repairs"),
    OTHER("other", "Other");

    companion object {
        fun from(key: String?): ExpenseCategory = entries.firstOrNull { it.key == key } ?: OTHER
    }
}

data class Expense(
    val id: String,
    val month: String,
    val date: String,
    val label: String,
    val category: ExpenseCategory,
    val amount: DualAmount,
)

/** RTDB shape under /expenses/$month/$id. */
@Serializable
data class ExpenseDto(
    val date: String = "",
    val label: String = "",
    val category: String = "other",
    val usdCents: Long = 0,
    val lbp: Long = 0,
)

fun ExpenseDto.toDomain(id: String, month: String) = Expense(
    id = id,
    month = month,
    date = date,
    label = label,
    category = ExpenseCategory.from(category),
    amount = DualAmount(usdCents, lbp),
)

data class ExpenseInput(
    val label: String,
    val category: ExpenseCategory,
    val date: String,
    val usdCents: Long,
    val lbp: Long,
)

interface ExpensesRepository {
    fun observeMonth(month: String): Flow<List<Expense>>
    fun observeAll(): Flow<List<Expense>>
    suspend fun addExpense(month: String, input: ExpenseInput): Result<Unit>
}

/** A unified money-box movement for the calendar/ledger. */
enum class MovementKind { IN, OUT }

data class Movement(
    val id: String,
    val kind: MovementKind,
    val date: String,
    val label: String,
    val sublabel: String?,
    val amount: DualAmount,
)
