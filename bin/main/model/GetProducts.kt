package model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GetProductsParams(
    @SerialName("days_back") val daysBack: Int
)

@Serializable
data class GetProductsResult(
    val products: List<Product>,
    val count: Int
)


