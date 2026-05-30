package com.buildingbox.app.feature.payments.domain

import com.buildingbox.app.core.money.DualAmount
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/** A single charge line for an apartment in a month (carries its RTDB key as [id]). */
data class Due(
    val id: String,
    val apartmentId: String,
    val month: String,
    val title: String,
    val amount: DualAmount,
    val paid: Boolean,
    val paidOn: String?,
    val base: Boolean,
)

/** RTDB shape under /dues/$month/$aptId/$dueId. */
@Serializable
data class DueDto(
    val title: String = "",
    val usdCents: Long = 0,
    val lbp: Long = 0,
    val paid: Boolean = false,
    val paidOn: String? = null,
    val base: Boolean = false,
)

fun DueDto.toDomain(id: String, apartmentId: String, month: String) = Due(
    id = id,
    apartmentId = apartmentId,
    month = month,
    title = title,
    amount = DualAmount(usdCents, lbp),
    paid = paid,
    paidOn = paidOn,
    base = base,
)

data class DueInput(
    val title: String,
    val usdCents: Long,
    val lbp: Long,
    val paid: Boolean,
    val paidOn: String?,
)

enum class PaymentStatus { PAID, PARTIAL, UNPAID }

/** Aggregated view of one apartment's dues for a month. */
data class ApartmentMonth(
    val apartmentId: String,
    val month: String,
    val dues: List<Due>,
    val total: DualAmount,
    val paid: DualAmount,
    val remaining: DualAmount,
    val status: PaymentStatus,
)

fun statusOf(dues: List<Due>): PaymentStatus = when {
    dues.isEmpty() -> PaymentStatus.PAID
    dues.all { it.paid } -> PaymentStatus.PAID
    dues.none { it.paid } -> PaymentStatus.UNPAID
    else -> PaymentStatus.PARTIAL
}

fun aggregate(apartmentId: String, month: String, dues: List<Due>): ApartmentMonth {
    val ordered = dues.sortedByDescending { it.base }
    val total = ordered.fold(DualAmount.ZERO) { a, d -> a + d.amount }
    val paid = ordered.filter { it.paid }.fold(DualAmount.ZERO) { a, d -> a + d.amount }
    return ApartmentMonth(apartmentId, month, ordered, total, paid, total - paid, statusOf(ordered))
}

interface DuesRepository {
    /** All dues for a month across apartments. */
    fun observeMonth(month: String): Flow<List<Due>>
    /** Every due across all months (box balance until precomputed aggregates land). */
    fun observeAll(): Flow<List<Due>>
    suspend fun setPaid(due: Due, paid: Boolean): Result<Unit>
    suspend fun addDue(apartmentId: String, month: String, input: DueInput): Result<Unit>
    suspend fun updateDue(due: Due, input: DueInput): Result<Unit>
    suspend fun removeDue(due: Due): Result<Unit>
    /** Create the base monthly due for any apartment in [feeByApartment] missing one this month. */
    suspend fun generateBaseDues(month: String, feeByApartment: Map<String, DualAmount>): Result<Unit>
}
