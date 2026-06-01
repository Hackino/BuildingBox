package com.buildingbox.app.feature.units.data

import com.buildingbox.app.core.firebase.RealtimeDb
import com.buildingbox.app.feature.units.domain.Apartment
import com.buildingbox.app.feature.units.domain.ApartmentDto
import com.buildingbox.app.feature.units.domain.ApartmentInput
import com.buildingbox.app.feature.units.domain.ApartmentRepository
import com.buildingbox.app.feature.units.domain.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.serializer

private val APARTMENTS_MAP = serializer<Map<String, ApartmentDto>>()

private fun ApartmentInput.toDto(createdAt: Long = 0) = ApartmentDto(
    name = name,
    ownerName = ownerName,
    floor = floor,
    feeUsdCents = feeUsdCents,
    feeLbp = feeLbp,
    phone = phone,
    active = true,
    createdAt = createdAt,
)

class ApartmentRepositoryImpl(private val db: RealtimeDb) : ApartmentRepository {

    override fun observeApartments(): Flow<List<Apartment>> =
        db.observeValue("apartments", APARTMENTS_MAP).map { map ->
            (map ?: emptyMap())
                .map { (id, dto) -> dto.toDomain(id) }
                .sortedWith(compareByDescending<Apartment> { it.floor }.thenBy { it.name })
        }

    override suspend fun addApartment(input: ApartmentInput): Result<Unit> = runCatching {
        db.push("apartments", input.toDto(), ApartmentDto.serializer())
        Unit
    }

    override suspend fun updateApartment(id: String, input: ApartmentInput): Result<Unit> = runCatching {
        db.setValue("apartments/$id", input.toDto(), ApartmentDto.serializer())
    }

    override suspend fun deleteApartment(id: String): Result<Unit> = runCatching {
        db.update(mapOf("apartments/$id" to JsonNull))
    }
}
