package com.buildingbox.app.feature.units.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buildingbox.app.core.datetime.currentMonth
import com.buildingbox.app.feature.payments.domain.DuesRepository
import com.buildingbox.app.feature.payments.domain.PaymentStatus
import com.buildingbox.app.feature.payments.domain.statusOf
import com.buildingbox.app.feature.units.domain.Apartment
import com.buildingbox.app.feature.units.domain.ApartmentInput
import com.buildingbox.app.feature.units.domain.ApartmentRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class UnitsViewModel(
    private val repo: ApartmentRepository,
    dues: DuesRepository,
) : ViewModel() {

    val apartments: StateFlow<List<Apartment>> =
        repo.observeApartments()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Current-month payment status per apartment (for the list's pills). */
    val statusByApartment: StateFlow<Map<String, PaymentStatus>> =
        combine(apartments, dues.observeMonth(currentMonth())) { apts, monthDues ->
            apts.associate { a -> a.id to statusOf(monthDues.filter { it.apartmentId == a.id }) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun add(input: ApartmentInput) {
        viewModelScope.launch { repo.addApartment(input) }
    }

    fun update(id: String, input: ApartmentInput) {
        viewModelScope.launch { repo.updateApartment(id, input) }
    }
}
