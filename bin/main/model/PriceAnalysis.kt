package model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class PriceAnalysis(
    val product: Product,
    @SerialName("average_price") val averagePrice: Double? = null,
    @SerialName("min_price") val minPrice: Double? = null,
    @SerialName("max_price") val maxPrice: Double? = null,
    @SerialName("current_price") val currentPrice: Double,
    @SerialName("discount_percent") val discountPercent: Double? = null,
    @SerialName("is_good_deal") val isGoodDeal: Boolean,
    @SerialName("analyzed_at") val analyzedAt: String = Instant.now().toString(),
    val reason: String? = null
)


