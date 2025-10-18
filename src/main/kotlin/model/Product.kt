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
    val price: Double,
    @SerialName("scraped_at") val scrapedAt: String? = null,
)


