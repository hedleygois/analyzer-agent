package analysis

import config.OpenAIConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import model.PriceAnalysis
import model.Product
import com.aallam.openai.client.OpenAI
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.api.chat.ChatRole
import java.time.Instant

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
            messages = listOf(
                ChatMessage(role = ChatRole.System, content = SYSTEM_PROMPT),
                ChatMessage(role = ChatRole.User, content = prompt)
            )
        )
        val resp = client.chatCompletion(req)
        val content = resp.choices.firstOrNull()?.message?.content.orEmpty()
        LLMEvaluator.parse(content, products)
    }

	private fun buildPrompt(products: List<Product>): String {
		val sb = StringBuilder()
		sb.append("Evaluate which products are good deals given their 30-day price history. Return JSON with fields: product_id, is_good_deal, reason, average, min, max, discount_percent.\n")
		products.forEach { p ->
			sb.append("\nProduct: ").append(p.id).append(" | ").append(p.name)
			sb.append(" | current=").append(p.currentPrice).append(" ").append(p.currency)
			sb.append("\nHistory: ")
			p.priceHistory.takeLast(60).forEach { point ->
				sb.append("(").append(point.price).append(",").append(point.timestamp).append(") ")
			}
			sb.append("\n")
		}
		return sb.toString()
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

	fun parse(content: String, products: List<Product>): List<PriceAnalysis> {
		val deals = runCatching { json.decodeFromString(ListSerializer(Deal.serializer()), content) }.getOrNull()
			?: return emptyList()
		val byId = products.associateBy { it.id }
		return deals.mapNotNull { d ->
			val p = byId[d.productId] ?: return@mapNotNull null
			PriceAnalysis(
				product = p,
				averagePrice = d.average,
				minPrice = d.min,
				maxPrice = d.max,
				currentPrice = p.currentPrice,
				discountPercent = d.discountPercent,
				isGoodDeal = d.isGoodDeal,
				analyzedAt = Instant.now().toString(),
				reason = d.reason
			)
		}
	}
}

