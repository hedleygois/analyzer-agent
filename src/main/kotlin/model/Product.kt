package model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Product(
    val id: String,
    val name: String,
    val url: String? = null,
    val store: String? = null,
    val category: String? = null,
    @SerialName("current_price") val currentPrice: Double,
    val currency: String = "USD",
    @SerialName("price_history") val priceHistory: List<PricePoint> = emptyList(),
    @SerialName("last_updated") val lastUpdated: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    val description: String? = null
)


