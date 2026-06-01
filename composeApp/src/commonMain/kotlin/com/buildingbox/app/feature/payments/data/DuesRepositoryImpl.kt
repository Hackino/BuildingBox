package com.buildingbox.app.feature.payments.data

import com.buildingbox.app.core.datetime.today
import com.buildingbox.app.core.firebase.RealtimeDb
import com.buildingbox.app.core.money.DualAmount
import com.buildingbox.app.feature.payments.domain.Due
import com.buildingbox.app.feature.payments.domain.DueDto
import com.buildingbox.app.feature.payments.domain.DueInput
import com.buildingbox.app.feature.payments.domain.DuesRepository
import com.buildingbox.app.feature.payments.domain.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.serializer

private val MONTH_DUES = serializer<Map<String, Map<String, DueDto>>>()
private val ALL_DUES = serializer<Map<String, Map<String, Map<String, DueDto>>>>()

class DuesRepositoryImpl(private val db: RealtimeDb) : DuesRepository {

    override fun observeMonth(month: String): Flow<List<Due>> =
        db.observeValue("dues/$month", MONTH_DUES).map { byApt ->
            (byApt ?: emptyMap()).flatMap { (aptId, dues) ->
                dues.map { (dueId, dto) -> dto.toDomain(dueId, aptId, month) }
            }
        }

    override fun observeAll(): Flow<List<Due>> =
        db.observeValue("dues", ALL_DUES).map { byMonth ->
            (byMonth ?: emptyMap()).flatMap { (month, byApt) ->
                byApt.flatMap { (aptId, dues) -> dues.map { (id, dto) -> dto.toDomain(id, aptId, month) } }
            }
        }

    override suspend fun setPaid(due: Due, paid: Boolean): Result<Unit> = runCatching {
        val dto = DueDto(
            title = due.title,
            usdCents = due.amount.usdCents,
            lbp = due.amount.lbp,
            paid = paid,
            paidOn = if (paid) (due.paidOn ?: today()) else null,
            base = due.base,
        )
        db.setValue("dues/${due.month}/${due.apartmentId}/${due.id}", dto, DueDto.serializer())
    }

    override suspend fun addDue(apartmentId: String, month: String, input: DueInput): Result<Unit> = runCatching {
        db.push("dues/$month/$apartmentId", input.toDto(base = false), DueDto.serializer())
        Unit
    }

    override suspend fun updateDue(due: Due, input: DueInput): Result<Unit> = runCatching {
        db.setValue("dues/${due.month}/${due.apartmentId}/${due.id}", input.toDto(base = due.base), DueDto.serializer())
    }

    override suspend fun removeDue(due: Due): Result<Unit> = runCatching {
        db.update(mapOf("dues/${due.month}/${due.apartmentId}/${due.id}" to JsonNull))
    }

    override suspend fun removeApartmentDues(apartmentId: String): Result<Unit> = runCatching {
        // Find every month that has dues for this apartment, then null each path in one batch.
        val byMonth = db.getValue("dues", ALL_DUES) ?: emptyMap()
        val updates = byMonth
            .filterValues { byApt -> byApt.containsKey(apartmentId) }
            .keys
            .associate { month -> "dues/$month/$apartmentId" to (JsonNull as kotlinx.serialization.json.JsonElement) }
        if (updates.isNotEmpty()) db.update(updates)
    }

    override suspend fun generateBaseDues(
        month: String,
        feeByApartment: Map<String, DualAmount>,
    ): Result<Unit> = runCatching {
        val existing = db.getValue("dues/$month", MONTH_DUES) ?: emptyMap()
        feeByApartment.forEach { (aptId, fee) ->
            val hasBase = existing[aptId]?.containsKey("base") == true
            if (!hasBase) {
                val dto = DueDto(
                    title = "Monthly dues",
                    usdCents = fee.usdCents,
                    lbp = fee.lbp,
                    paid = false,
                    base = true,
                )
                db.setValue("dues/$month/$aptId/base", dto, DueDto.serializer())
            }
        }
    }
}

private fun DueInput.toDto(base: Boolean) = DueDto(
    title = title,
    usdCents = usdCents,
    lbp = lbp,
    paid = paid,
    // Stamp the pay date in the DB so it's not inferred/faked downstream.
    paidOn = if (paid) (paidOn ?: today()) else null,
    base = base,
)
