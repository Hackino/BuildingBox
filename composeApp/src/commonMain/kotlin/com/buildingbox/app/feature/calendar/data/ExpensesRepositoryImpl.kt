package com.buildingbox.app.feature.calendar.data

import com.buildingbox.app.core.firebase.RealtimeDb
import com.buildingbox.app.feature.calendar.domain.Expense
import com.buildingbox.app.feature.calendar.domain.ExpenseDto
import com.buildingbox.app.feature.calendar.domain.ExpenseInput
import com.buildingbox.app.feature.calendar.domain.ExpensesRepository
import com.buildingbox.app.feature.calendar.domain.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.serializer

private val MONTH_EXPENSES = serializer<Map<String, ExpenseDto>>()
private val ALL_EXPENSES = serializer<Map<String, Map<String, ExpenseDto>>>()

class ExpensesRepositoryImpl(private val db: RealtimeDb) : ExpensesRepository {

    override fun observeMonth(month: String): Flow<List<Expense>> =
        db.observeValue("expenses/$month", MONTH_EXPENSES).map { map ->
            (map ?: emptyMap()).map { (id, dto) -> dto.toDomain(id, month) }
        }

    override fun observeAll(): Flow<List<Expense>> =
        db.observeValue("expenses", ALL_EXPENSES).map { byMonth ->
            (byMonth ?: emptyMap()).flatMap { (month, ex) -> ex.map { (id, dto) -> dto.toDomain(id, month) } }
        }

    override suspend fun addExpense(month: String, input: ExpenseInput): Result<Unit> = runCatching {
        db.push(
            "expenses/$month",
            ExpenseDto(
                date = input.date,
                label = input.label,
                category = input.category.key,
                usdCents = input.usdCents,
                lbp = input.lbp,
            ),
            ExpenseDto.serializer(),
        )
        Unit
    }

    override suspend fun updateExpense(month: String, id: String, input: ExpenseInput): Result<Unit> = runCatching {
        db.setValue(
            "expenses/$month/$id",
            ExpenseDto(
                date = input.date,
                label = input.label,
                category = input.category.key,
                usdCents = input.usdCents,
                lbp = input.lbp,
            ),
            ExpenseDto.serializer(),
        )
    }

    override suspend fun removeExpense(month: String, id: String): Result<Unit> = runCatching {
        db.update(mapOf("expenses/$month/$id" to JsonNull))
    }
}
