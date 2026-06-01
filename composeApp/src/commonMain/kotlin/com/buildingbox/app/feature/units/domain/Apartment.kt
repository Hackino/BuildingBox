package com.buildingbox.app.feature.units.domain

import com.buildingbox.app.core.money.DualAmount
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/** Domain apartment (carries its RTDB key as [id]). */
data class Apartment(
    val id: String,
    val name: String,
    val ownerName: String,
    val floor: Int,
    val fee: DualAmount,
    val phone: String,
    val active: Boolean = true,
)

/** RTDB shape under /apartments/$id (no id — that's the key). */
@Serializable
data class ApartmentDto(
    val name: String = "",
    val ownerName: String = "",
    val floor: Int = 0,
    val feeUsdCents: Long = 0,
    val feeLbp: Long = 0,
    val phone: String = "",
    val active: Boolean = true,
    val createdAt: Long = 0,
)

fun ApartmentDto.toDomain(id: String) = Apartment(
    id = id,
    name = name,
    ownerName = ownerName,
    floor = floor,
    fee = DualAmount(feeUsdCents, feeLbp),
    phone = phone,
    active = active,
)

/** Form input for creating/updating an apartment. */
data class ApartmentInput(
    val name: String,
    val ownerName: String,
    val floor: Int,
    val feeUsdCents: Long,
    val feeLbp: Long,
    val phone: String,
)

interface ApartmentRepository {
    fun observeApartments(): Flow<List<Apartment>>
    suspend fun addApartment(input: ApartmentInput): Result<Unit>
    suspend fun updateApartment(id: String, input: ApartmentInput): Result<Unit>
    suspend fun deleteApartment(id: String): Result<Unit>
}
