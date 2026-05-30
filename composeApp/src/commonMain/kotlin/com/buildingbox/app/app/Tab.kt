package com.buildingbox.app.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Payments
import androidx.compose.ui.graphics.vector.ImageVector

enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.GridView),
    PAYMENTS("Payments", Icons.Filled.Payments),
    CALENDAR("Calendar", Icons.Filled.CalendarMonth),
    UNITS("Units", Icons.Filled.Apartment),
    REPORTS("Reports", Icons.Filled.Description),
}
