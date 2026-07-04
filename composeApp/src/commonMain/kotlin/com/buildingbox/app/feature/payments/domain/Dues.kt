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
    /** How much of [amount] the owner has actually paid against this due. */
    val paidAmount: DualAmount,
    /** True iff [paidAmount] fully covers [amount]. Derived — legacy field kept for existing call sites. */
    val paid: Boolean,
    val paidOn: String?,
    val base: Boolean,
) {
    val remaining: DualAmount get() = amount - paidAmount
    val isFullyPaid: Boolean get() = fullyCovers(paidAmount, amount)
    val isUntouched: Boolean get() = paidAmount.usdCents == 0L && paidAmount.lbp == 0L
}

/**
 * `paid` covers `total` when neither currency is short. Used as the single source
 * of truth for the derived `paid` flag on both the domain and the DTO.
 */
fun fullyCovers(paid: DualAmount, total: DualAmount): Boolean =
    paid.usdCents >= total.usdCents && paid.lbp >= total.lbp

/**
 * RTDB shape under /dues/$month/$aptId/$dueId.
 *
 * [paidUsdCents]/[paidLbp] were added when per-due partial payments were introduced.
 * Older records only have [paid]+[usdCents]+[lbp]; [DueDto.toDomain] translates them
 * so no batch migration is required.
 */
@Serializable
data class DueDto(
    val title: String = "",
    val usdCents: Long = 0,
    val lbp: Long = 0,
    val paidUsdCents: Long = 0,
    val paidLbp: Long = 0,
    val paid: Boolean = false,
    val paidOn: String? = null,
    val base: Boolean = false,
)

fun DueDto.toDomain(id: String, apartmentId: String, month: String): Due {
    val amount = DualAmount(usdCents, lbp)
    // New-shape record if any paid* field is populated; else fall back to legacy
    // boolean (paid=true → fully paid, paid=false → untouched).
    val paidAmount = when {
        paidUsdCents > 0L || paidLbp > 0L -> DualAmount(paidUsdCents, paidLbp)
        paid -> amount
        else -> DualAmount.ZERO
    }
    return Due(
        id = id,
        apartmentId = apartmentId,
        month = month,
        title = title,
        amount = amount,
        paidAmount = paidAmount,
        paid = fullyCovers(paidAmount, amount),
        paidOn = paidOn,
        base = base,
    )
}

data class DueInput(
    val title: String,
    val usdCents: Long,
    val lbp: Long,
    val paidUsdCents: Long,
    val paidLbp: Long,
    val paidOn: String?,
)

enum class PaymentStatus { NONE, PAID, PARTIAL, UNPAID }

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
    dues.isEmpty() -> PaymentStatus.NONE
    dues.all { it.isFullyPaid } -> PaymentStatus.PAID
    dues.all { it.isUntouched } -> PaymentStatus.UNPAID
    else -> PaymentStatus.PARTIAL
}

fun aggregate(apartmentId: String, month: String, dues: List<Due>): ApartmentMonth {
    val ordered = dues.sortedByDescending { it.base }
    val total = ordered.fold(DualAmount.ZERO) { a, d -> a + d.amount }
    // Sum actual paid amount per due (partial or full); no longer boolean-filtered.
    val paid = ordered.fold(DualAmount.ZERO) { a, d -> a + d.paidAmount }
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
    /** Delete every due for an apartment across all months (used when deleting the apartment). */
    suspend fun removeApartmentDues(apartmentId: String): Result<Unit>
    /** Create the base monthly due for any apartment in [feeByApartment] missing one this month. */
    suspend fun generateBaseDues(month: String, feeByApartment: Map<String, DualAmount>): Result<Unit>
}
