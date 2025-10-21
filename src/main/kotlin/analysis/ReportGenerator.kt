package analysis

import model.PriceAnalysis
import model.MarketTrends
import model.InsightResult

object ReportGenerator {
	
	fun generateTabularReport(
		goodDeals: List<PriceAnalysis>,
		insights: InsightResult,
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
			marketTrends.insights?.let { sb.appendLine("📊 Insights: $it") }
			marketTrends.recommendations?.let { 
				sb.appendLine("💡 Recommendations:")
				it.forEach { rec -> sb.appendLine("  • $rec") }
			}
			marketTrends.priceAnalysis?.let { price ->
				sb.appendLine("💰 Price Analysis:")
				price.averageGPUPrice?.let { sb.appendLine("  • Average GPU Price: $it") }
				price.averageCPUPrice?.let { sb.appendLine("  • Average CPU Price: $it") }
				price.priceRange?.let { sb.appendLine("  • Price Range: ${it.min} - ${it.max}") }
			}
			marketTrends.storeAnalysis?.let { store ->
				sb.appendLine("🏪 Store Analysis:")
				store.mostExpensive?.let { sb.appendLine("  • Most Expensive: $it") }
				store.mostAffordable?.let { sb.appendLine("  • Most Affordable: $it") }
			}
		}
		
		sb.appendLine()
		
		sb.appendLine("GENERAL INSIGHTS:")
		sb.appendLine("-".repeat(40))
		if (insights.error != null) {
			sb.appendLine("❌ Error: ${insights.error}")
		} else {
			insights.insights?.let { sb.appendLine("📈 Insights: $it") }
			insights.recommendations?.let { sb.appendLine("💡 Recommendations: $it") }
		}
		
		sb.appendLine()
		sb.appendLine("=".repeat(120))
		
		return sb.toString()
	}
}
