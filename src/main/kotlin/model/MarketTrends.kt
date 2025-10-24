package model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable



@Serializable
data class MarketTrends(
    val insights: Insights? = null,
    val error: String? = null
)

@Serializable
data class PriceTrendsAndRanges(
    @SerialName("lowest_price") val lowestPrice: Double? = null,
    @SerialName("highest_price") val highestPrice: Double? = null,
    @SerialName("average_price") val averagePrice: Double? = null,
    @SerialName("price_range") val priceRange: String? = null,
    @SerialName("notable_price_points") val notablePricePoints: NotablePricePoints? = null
)

@Serializable
data class NotablePricePoints(
    @SerialName("entry_level") val entryLevel: Double? = null,
    @SerialName("mid_range") val midRange: Double? = null,
    @SerialName("high_end") val highEnd: Double? = null
)

@Serializable
data class StoreAvailabilityAndDistribution(
    val store: String? = null,
    @SerialName("total_products") val totalProducts: Int? = null,
    @SerialName("product_distribution") val productDistribution: ProductDistribution? = null
)

@Serializable
data class ProductDistribution(
    @SerialName("available_products") val availableProducts: Int? = null,
    @SerialName("out_of_stock") val outOfStock: Int? = null
)

@Serializable
data class ProductCategoryDistribution(
    val category: String? = null,
    @SerialName("total_products_in_category") val totalProductsInCategory: Int? = null,
    @SerialName("category_percentage") val categoryPercentage: String? = null
)

@Serializable
data class DataQualityAssessment(
    @SerialName("data_freshness") val dataFreshness: String? = null,
    @SerialName("url_validity") val urlValidity: String? = null,
    @SerialName("product_ids") val productIds: String? = null,
    @SerialName("price_format") val priceFormat: String? = null
)

@Serializable
data class MarketObservations(
    @SerialName("high_demand") val highDemand: String? = null,
    @SerialName("premium_products") val premiumProducts: String? = null,
    @SerialName("entry_level_options") val entryLevelOptions: String? = null
)

@Serializable
data class PotentialOpportunitiesOrAnomalies(
    val opportunities: Opportunities? = null,
    val anomalies: Anomalies? = null
)

@Serializable
data class Opportunities(
    @SerialName("entry_level_gpu") val entryLevelGpu: String? = null,
    @SerialName("bundled_offers") val bundledOffers: String? = null
)

@Serializable
data class Anomalies(
    @SerialName("price_discrepancy") val priceDiscrepancy: String? = null
)

@Serializable
data class Insights(
    @SerialName("price_trends_and_ranges") val priceTrendsAndRanges: PriceTrendsAndRanges? = null,
    @SerialName("store_availability_and_distribution") val storeAvailabilityAndDistribution: StoreAvailabilityAndDistribution? = null,
    @SerialName("product_category_distribution") val productCategoryDistribution: ProductCategoryDistribution? = null,
    @SerialName("data_quality_assessment") val dataQualityAssessment: DataQualityAssessment? = null,
    @SerialName("market_observations") val marketObservations: MarketObservations? = null,
    @SerialName("potential_opportunities_or_anomalies") val potentialOpportunitiesOrAnomalies: PotentialOpportunitiesOrAnomalies? = null
)

@Serializable
data class InsightResult(
    val insights: Insights? = null,
    val error: String? = null
)
