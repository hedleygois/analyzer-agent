package model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PriceRange(
    val min: Double,
    val max: Double
)

@Serializable
data class PriceAnalysisData(
    @SerialName("average_gpu_price") val averageGPUPrice: Double? = null,
    @SerialName("average_cpu_price") val averageCPUPrice: Double? = null,
    @SerialName("price_range") val priceRange: PriceRange? = null
)

@Serializable
data class StoreAnalysis(
    @SerialName("most_expensive") val mostExpensive: String? = null,
    @SerialName("most_affordable") val mostAffordable: String? = null
)

@Serializable
data class MarketTrends(
    @SerialName("price_analysis") val priceAnalysis: PriceAnalysisData? = null,
    @SerialName("store_analysis") val storeAnalysis: StoreAnalysis? = null,
    val recommendations: List<String>? = null,
    val insights: String? = null,
    val error: String? = null
)

@Serializable
data class InsightResult(
    val insights: String? = null,
    val recommendations: String? = null,
    val error: String? = null
)