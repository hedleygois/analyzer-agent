package analysis

import config.OpenAIConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import model.PriceAnalysis
import model.Product
import model.MarketTrends
import model.InsightResult
import com.aallam.openai.client.OpenAI
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.api.chat.ChatRole
import kotlinx.serialization.json.*
import java.time.Instant
import java.io.File
import java.nio.file.Paths

@Serializable
data class OpenAIResponseWrapper(val dummy: String = "")


class LLMAnalyzer(private val cfg: OpenAIConfig) {
	private val json = Json { ignoreUnknownKeys = true }
	private val client by lazy { OpenAI(token = cfg.apiKey) }

	fun analyze(products: List<Product>): List<PriceAnalysis> = runBlocking {
        if (products.isEmpty()) return@runBlocking emptyList()
        val prompt = buildPrompt(products)
        val req = ChatCompletionRequest(
            model = ModelId(cfg.model),
			temperature = 0.2,
            messages = listOf(
                ChatMessage(role = ChatRole.System, content = SYSTEM_PROMPT),
                ChatMessage(role = ChatRole.User, content = prompt),
            )
        )
        val resp = client.chatCompletion(req)
        val content = resp.choices.firstOrNull()?.message?.content.orEmpty()
        LLMEvaluator.parse(content, products)
    }

	private fun buildPrompt(products: List<Product>): String {
		val sb = StringBuilder()
		sb.append("Evaluate which products are good deals given their market trends. Use the following rules and provide insights on:\n" +
				"1. Price trends and ranges\n" +
				"2. Store availability and distribution\n" +
				"3. Product category distribution\n" +
				"4. Data quality assessment\n" +
				"5. Market observations\n" +
				"6. Potential opportunities or anomalies. " +
				"The return must be ONLY the valid JSON with fields: product_id, is_good_deal, reason, average, min, max, discount_percent. Always wrap key value within double quotes.\n")
		products.forEach { p ->
			sb.append("\nProduct: ").append(p.id).append(" | ").append(p.name)
			sb.append(" | price=").append(p.price).append(" ")
		}
		return sb.toString()
	}

	/**
	 * Generate insights about product data
	 */
	suspend fun generateInsights(products: List<Product>): InsightResult {
		if (products.isEmpty()) return InsightResult(error = "No products to analyze")
		
		val prompt = buildInsightsPrompt(products)
		val req = ChatCompletionRequest(
			model = ModelId(cfg.model),
			temperature = 0.3,
			maxTokens = 1000,
			messages = listOf(
				ChatMessage(role = ChatRole.User, content = prompt)
			)
		)
		
		return try {
			val response = client.chatCompletion(req)
			val content = response.choices.firstOrNull()?.message?.content.orEmpty()
			logResponse("generateInsights", response, null)
			
			println("🤖 AI Insights Response: $content")
			
			json.decodeFromString<InsightResult>(LLMEvaluator.stripCodeFences(content))
		} catch (error: Exception) {
			logResponse("generateInsights", null, error)
			println("❌ Failed to generate insights: ${error.message}")
			InsightResult(error = "Failed to generate insights")
		}
	}
	
	/**
	 * Analyze market trends from product data
	 */
	suspend fun analyzeMarketTrends(products: List<Product>): MarketTrends {
		if (products.isEmpty()) return MarketTrends(error = "No products to analyze")
		
		val prompt = createMarketTrendsPrompt(products)
		val req = ChatCompletionRequest(
			model = ModelId(cfg.model),
			temperature = 0.2,
			maxTokens = 32768,
			messages = listOf(
				ChatMessage(role = ChatRole.User, content = prompt)
			)
		)
		
		return try {
			val response = client.chatCompletion(req)
			val content = response.choices.firstOrNull()?.message?.content.orEmpty()
			logResponse("analyzeMarketTrends", response, null)
			
			println("🤖 Market Trends Analysis Response: $content")
			
			json.decodeFromString<MarketTrends>(LLMEvaluator.stripCodeFences(content))
		} catch (error: Exception) {
			logResponse("analyzeMarketTrends", null, error)
			println("❌ Failed to analyze market trends: ${error.message}")
			MarketTrends(error = "Failed to analyze market trends")
		}
	}
	
	/**
	 * Log market trends analysis to file
	 */
	fun logMarketTrends(marketTrends: MarketTrends) {
		val timestamp = Instant.now().toString()
		val logContent = formatMarketTrendsLog(timestamp, marketTrends)
		val marketTrendsLogPath = getMarketTrendsLogFilePath()
		
		try {
			File(marketTrendsLogPath).appendText(logContent)
		} catch (error: Exception) {
			println("❌ Failed to log market trends: ${error.message}")
		}
	}
	
	private fun buildInsightsPrompt(products: List<Product>): String {
		return """
			Analyze this product data and provide insights:
			
			Products: ${json.encodeToString(Product.serializer().list, products)}
			
			Provide insights on:
			1. Price trends and ranges
			2. Store availability and distribution
			3. Product category distribution
			4. Data quality assessment
			5. Market observations
			6. Potential opportunities or anomalies
			
			Return as JSON with structured insights. Always wrap key value within double quotes.
		""".trimIndent()
	}
	
	private fun createMarketTrendsPrompt(products: List<Product>): String {
		return """
			Analyze market trends from this product data:
			
			${json.encodeToString(Product.serializer().list, products)}
			
			Provide analysis on:
			1. Price trends and market positioning
			2. Brand distribution and popularity
			3. Store pricing strategies
			4. Product availability patterns
			5. Market opportunities
			6. Competitive insights
			7. Current prices for each product are below or above the average price (return one line for each product)
			
			Return as structured JSON analysis. Make sure to format string correctly. Always wrap key value within double quotes.
		""".trimIndent()
	}
	
	private fun logResponse(method: String, response: Any?, error: Exception?) {
		val timestamp = Instant.now().toString()
		val logContent = formatLogEntry(timestamp, method, response, error)
		val logFilePath = getLogFilePath()
		
		try {
			File(logFilePath).appendText(logContent)
		} catch (e: Exception) {
			println("❌ Failed to log response: ${e.message}")
		}
	}
	
	private fun formatLogEntry(timestamp: String, method: String, response: Any?, error: Exception?): String {
		val separator = "=".repeat(80)
		
		return buildString {
			appendLine(separator)
			appendLine("TIMESTAMP: $timestamp")
			appendLine("METHOD: $method")
			appendLine(separator)
			
			if (error != null) {
				appendLine("❌ ERROR:")
				appendLine("Error Message: ${error.message ?: "Unknown error"}")
				appendLine("Error Stack: ${error.stackTraceToString()}")
			} else {
				appendLine("✅ SUCCESS:")
				val responseContent = response?.toString() ?: "No content"
				appendLine("Response Content: $responseContent")
			}
			
			appendLine(separator)
			appendLine()
		}
	}
	
	private fun formatMarketTrendsLog(timestamp: String, marketTrends: MarketTrends): String {
		val separator = "=".repeat(80)
		
		return buildString {
			appendLine(separator)
			appendLine("MARKET TRENDS ANALYSIS")
			appendLine("TIMESTAMP: $timestamp")
			appendLine(separator)
			
			if (marketTrends.error != null) {
				appendLine("❌ ERROR: ${marketTrends.error}")
			} else {
				appendLine("📊 MARKET TRENDS DATA:")
				appendLine(json.encodeToString(MarketTrends.serializer(), marketTrends))
			}
			
			appendLine(separator)
			appendLine()
		}
	}
	
	private fun getLogFilePath(): String {
		return Paths.get(System.getProperty("user.dir"), "openai_responses.log").toString()
	}
	
	private fun getMarketTrendsLogFilePath(): String {
		return Paths.get(System.getProperty("user.dir"), "market_trends.log").toString()
	}

	companion object {
		private const val SYSTEM_PROMPT = "You are a price analysis assistant that flags good deals conservatively."
	}
}

object LLMEvaluator {
	private val json = Json { ignoreUnknownKeys = true }

	@Serializable
	data class Deal(
		@SerialName("product_id") val productId: String,
		@SerialName("is_good_deal") val isGoodDeal: Boolean,
		val reason: String? = null,
		val average: Double? = null,
		val min: Double? = null,
		val max: Double? = null,
		@SerialName("discount_percent") val discountPercent: Double? = null
	)

	// OpenAI responses are not consistent. Let's play safe here... and create a lot of crap
	fun parse(content: String, products: List<Product>): List<PriceAnalysis> {
		val sanitized = stripCodeFences(content).trim()
		val strictDeals = runCatching { json.decodeFromString<List<Deal>>(sanitized) }.getOrNull()
		if (strictDeals != null) return mapDeals(strictDeals, products)

		val root = runCatching { json.parseToJsonElement(sanitized) }.getOrNull() ?: return emptyList()
		val objects: List<JsonObject> = when (root) {
			is JsonArray -> root.mapNotNull { el: JsonElement -> el as? JsonObject }
			is JsonObject -> listOf(root)
			else -> return emptyList()
		}
		val byId = products.associateBy { it.id }
		return objects.mapNotNull { obj ->
			val productId = readString(obj, "product_id") ?: return@mapNotNull null
			val isGoodDeal = readBoolean(obj, "is_good_deal") ?: false
			val reason = readString(obj, "reason")
			val average = readDouble(obj, "average")
			val min = readDouble(obj, "min")
			val max = readDouble(obj, "max")
			val discountPercent = readDouble(obj, "discount_percent")
			val p = byId[productId] ?: return@mapNotNull null
			PriceAnalysis(
				product = p,
				averagePrice = average,
				minPrice = min,
				maxPrice = max,
				currentPrice = p.price,
				discountPercent = discountPercent,
				isGoodDeal = isGoodDeal,
				analyzedAt = Instant.now().toString(),
				reason = reason
			)
		}
	}

	private fun stripCodeFences(text: String): String {
		var t = text.trim()
		if (t.startsWith("```")) {
			// Remove opening fence and optional language tag
			t = t.removePrefix("```")
			val newlineIdx = t.indexOf('\n')
			if (newlineIdx >= 0) t = t.substring(newlineIdx + 1)
			// Remove closing fence if present
			val endIdx = t.lastIndexOf("```")
			if (endIdx >= 0) t = t.substring(0, endIdx)
		}
		return t
	}

	private fun mapDeals(deals: List<Deal>, products: List<Product>): List<PriceAnalysis> {
		val byId = products.associateBy { it.id }
		return deals.mapNotNull { d ->
			val p = byId[d.productId] ?: return@mapNotNull null
			PriceAnalysis(
				product = p,
				averagePrice = d.average,
				minPrice = d.min,
				maxPrice = d.max,
				currentPrice = p.price,
				discountPercent = d.discountPercent,
				isGoodDeal = d.isGoodDeal,
				analyzedAt = Instant.now().toString(),
				reason = d.reason
			)
		}
	}

	private fun readString(obj: JsonObject, key: String): String? {
		val el = obj[key] ?: return null
		return (el as? JsonPrimitive)?.content
	}

	private fun readBoolean(obj: JsonObject, key: String): Boolean? {
		val el = obj[key] as? JsonPrimitive ?: return null
		if (el.isString) {
			return when (el.content.trim().lowercase()) {
				"true", "yes", "y", "1" -> true
				"false", "no", "n", "0" -> false
				else -> null
			}
		}
		return el.booleanOrNull
	}

	private fun readDouble(obj: JsonObject, key: String): Double? {
		val el = obj[key] as? JsonPrimitive ?: return null
		el.doubleOrNull?.let { return it }
		val raw = el.content
		// Remove currency symbols, commas, percent signs, and whitespace
		val cleaned = raw.replace(Regex("[^0-9+\\-\\.eE]"), "")
		return cleaned.toDoubleOrNull()
	}
}

