package model

import kotlinx.serialization.Serializable

@Serializable
data class PricePoint(
    val price: Double,
    val timestamp: String
)


