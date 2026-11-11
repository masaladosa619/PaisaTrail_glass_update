package com.paisatrail.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.paisatrail.domain.model.Category

fun Category.color(): Color = when (this) {
    Category.FOOD -> Color(0xFFFF6B6B)
    Category.SHOPPING -> Color(0xFF4D96FF)
    Category.TRAVEL -> Color(0xFF22C1C3)
    Category.GROCERY -> Color(0xFF34D17A)
    Category.RENT -> Color(0xFFFF9F43)
    Category.INVESTMENTS -> Color(0xFFF5C542)
    Category.HEALTH -> Color(0xFFE056FD)
    Category.EMI_BILL -> Color(0xFFFF7AA2)
    Category.SUBSCRIPTIONS -> Color(0xFF7C6CF6)
    Category.OTHERS -> Color(0xFF8A8F98)
}

fun Category.icon(): ImageVector = when (this) {
    Category.FOOD -> Icons.Filled.Restaurant
    Category.SHOPPING -> Icons.Filled.ShoppingBag
    Category.TRAVEL -> Icons.Filled.Flight
    Category.GROCERY -> Icons.Filled.LocalGroceryStore
    Category.RENT -> Icons.Filled.Home
    Category.INVESTMENTS -> Icons.Filled.TrendingUp
    Category.HEALTH -> Icons.Filled.LocalHospital
    Category.EMI_BILL -> Icons.Filled.Receipt
    Category.SUBSCRIPTIONS -> Icons.Filled.Subscriptions
    Category.OTHERS -> Icons.Filled.Category
}

/** Distinct colours for people in split charts. */
val PersonPalette: List<Color> = listOf(
    Color(0xFF4D96FF), Color(0xFFFF6B6B), Color(0xFF22C1C3), Color(0xFFF5C542),
    Color(0xFF7C6CF6), Color(0xFFFF9F43), Color(0xFFE056FD), Color(0xFF34D17A)
)
