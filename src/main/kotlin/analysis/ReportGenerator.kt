package analysis

import model.PriceAnalysis
import model.MarketTrends
import model.InsightResult

object ReportGenerator {
	
	fun generateTabularReport(
		goodDeals: List<PriceAnalysis>,
		marketTrends: MarketTrends
	): String {
		val sb = StringBuilder()
		
		sb.appendLine("=".repeat(120))
		sb.appendLine("GOOD DEALS ANALYSIS REPORT")
		sb.appendLine("=".repeat(120))
		sb.appendLine()
		
		sb.appendLine("| Product Name | Current Price | Avg Price | Min Price | Max Price | Discount % | Reason | Store |")
		sb.appendLine("|" + "-".repeat(118) + "|")
		
		goodDeals.forEach { analysis ->
			val productName = analysis.product.name.take(20) // Truncate long names
			val currentPrice = String.format("%.2f", analysis.currentPrice)
			val avgPrice = analysis.averagePrice?.let { String.format("%.2f", it) } ?: "N/A"
			val minPrice = analysis.minPrice?.let { String.format("%.2f", it) } ?: "N/A"
			val maxPrice = analysis.maxPrice?.let { String.format("%.2f", it) } ?: "N/A"
			val discount = analysis.discountPercent?.let { String.format("%.1f%%", it * 100) } ?: "N/A"
			val reason = analysis.reason?.take(30) ?: "N/A" // Truncate long reasons
			val store = analysis.product.store ?: "N/A"
			
			sb.appendLine("| $productName | $currentPrice | $avgPrice | $minPrice | $maxPrice | $discount | $reason | $store |")
		}
		
		sb.appendLine()
		
		sb.appendLine("MARKET TRENDS SUMMARY:")
		sb.appendLine("-".repeat(40))
		if (marketTrends.error != null) {
			sb.appendLine("❌ Error: ${marketTrends.error}")
		} else {
			marketTrends.insights?.let { insights ->

				insights.priceTrendsAndRanges?.let { priceTrends ->
					sb.appendLine("💰 Price Trends and Ranges:")
					priceTrends.lowestPrice?.let { sb.appendLine("  • Lowest Price: $it") }
					priceTrends.highestPrice?.let { sb.appendLine("  • Highest Price: $it") }
					priceTrends.averagePrice?.let { sb.appendLine("  • Average Price: $it") }
					priceTrends.priceRange?.let { sb.appendLine("  • Price Range: $it") }
					priceTrends.notablePricePoints?.let { points ->
						sb.appendLine("  • Notable Price Points:")
						points.entryLevel?.let { sb.appendLine("    - Entry Level: $it") }
						points.midRange?.let { sb.appendLine("    - Mid Range: $it") }
						points.highEnd?.let { sb.appendLine("    - High End: $it") }
					}
				}
				
				insights.storeAvailabilityAndDistribution?.let { storeDist ->
					sb.appendLine("🏪 Store Availability and Distribution:")
					storeDist.store?.let { sb.appendLine("  • Store: $it") }
					storeDist.totalProducts?.let { sb.appendLine("  • Total Products: $it") }
					storeDist.productDistribution?.let { dist ->
						dist.availableProducts?.let { sb.appendLine("  • Available Products: $it") }
						dist.outOfStock?.let { sb.appendLine("  • Out of Stock: $it") }
					}
				}
				
				insights.productCategoryDistribution?.let { categoryDist ->
					sb.appendLine("📦 Product Category Distribution:")
					categoryDist.category?.let { sb.appendLine("  • Category: $it") }
					categoryDist.totalProductsInCategory?.let { sb.appendLine("  • Total Products in Category: $it") }
					categoryDist.categoryPercentage?.let { sb.appendLine("  • Category Percentage: $it") }
				}
		
				insights.dataQualityAssessment?.let { dataQuality ->
					sb.appendLine("🔍 Data Quality Assessment:")
					dataQuality.dataFreshness?.let { sb.appendLine("  • Data Freshness: $it") }
					dataQuality.urlValidity?.let { sb.appendLine("  • URL Validity: $it") }
					dataQuality.productIds?.let { sb.appendLine("  • Product IDs: $it") }
					dataQuality.priceFormat?.let { sb.appendLine("  • Price Format: $it") }
				}
				
				insights.marketObservations?.let { observations ->
					sb.appendLine("📊 Market Observations:")
					observations.highDemand?.let { sb.appendLine("  • High Demand: $it") }
					observations.premiumProducts?.let { sb.appendLine("  • Premium Products: $it") }
					observations.entryLevelOptions?.let { sb.appendLine("  • Entry Level Options: $it") }
				}
				
				insights.potentialOpportunitiesOrAnomalies?.let { opportunities ->
					sb.appendLine("💡 Potential Opportunities or Anomalies:")
					opportunities.opportunities?.let { opps ->
						opps.entryLevelGpu?.let { sb.appendLine("  • Entry Level GPU: $it") }
						opps.bundledOffers?.let { sb.appendLine("  • Bundled Offers: $it") }
					}
					opportunities.anomalies?.let { anomalies ->
						anomalies.priceDiscrepancy?.let { sb.appendLine("  • Price Discrepancy: $it") }
					}
				}
			}
		}
		
		sb.appendLine()
		
		return sb.toString()
	}
}
