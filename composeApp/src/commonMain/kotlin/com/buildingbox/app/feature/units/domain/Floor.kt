package com.buildingbox.app.feature.units.domain

/**
 * Human label for a floor number.
 *   0  → "Ground floor"
 *   N>0 → "Floor N"
 *   N<0 → "Basement |N|"  (underground levels)
 */
fun floorLabel(floor: Int): String = when {
    floor == 0 -> "Ground floor"
    floor > 0 -> "Floor $floor"
    else -> "Basement ${-floor}"
}

/** Short label used in list section headers (ground floor also notes shops). */
fun floorSectionLabel(floor: Int): String = when {
    floor == 0 -> "Ground floor · shops"
    floor > 0 -> "Floor $floor"
    else -> "Basement ${-floor}"
}
