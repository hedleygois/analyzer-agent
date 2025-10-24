package config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import java.io.File
import java.time.Duration

@Serializable
data class Config(
    val openai: OpenAIConfig,
    val email: EmailConfig,
    // Prefer MCP if present; falls back to scraper for backward compat
    val mcp: McpConfig? = null,
    val scraper: ScraperConfig? = null,
    val analysis: AnalysisConfig,
    val websocket: WebSocketConfig
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun load(path: String): Config {
            val text = File(path).readText()
            val cfg = json.decodeFromString(serializer(), text)
            return cfg
        }
    }
}

@Serializable
data class OpenAIConfig(
    @SerialName("api_key") val apiKey: String,
    val model: String = "gpt-4o-mini"
)

@Serializable
data class EmailConfig(
    @SerialName("smtp_host") val smtpHost: String,
    @SerialName("smtp_port") val smtpPort: Int,
    val username: String,
    val password: String,
    @SerialName("from_email") val fromEmail: String,
    @SerialName("to_emails") val toEmails: List<String>,
    val subject: String = "Price Alert: Great Deal Found!"
)

@Serializable
data class McpConfig(
    @SerialName("websocket_url") val webSocketUrl: String,
    val timeout: String = "30s",
    @SerialName("retry_delay") val retryDelay: String = "5s",
    @SerialName("max_retries") val maxRetries: Int = 3,
    // RPC customization to adapt to different MCP server
    @SerialName("tool_call_envelope") val toolCallEnvelope: Boolean = false
) {
    fun timeoutDuration(): Duration = parseDuration(timeout)
    fun retryDelayDuration(): Duration = parseDuration(retryDelay)
}

// Backward-compatible alias for older configs
typealias ScraperConfig = McpConfig

@Serializable
data class AnalysisConfig(
    @SerialName("discount_threshold") val discountThreshold: Double = 0.1,
    @SerialName("analysis_period") val analysisPeriod: String = "720h",
    @SerialName("check_interval") val checkInterval: String = "1h"
) {
    fun analysisDuration(): Duration = parseDuration(analysisPeriod)
    fun checkIntervalDuration(): Duration = parseDuration(checkInterval)
}

@Serializable
data class WebSocketConfig(
    @SerialName("handshake_timeout") val handshakeTimeout: String = "10s",
    @SerialName("read_buffer_size") val readBufferSize: Int = 1024,
    @SerialName("write_buffer_size") val writeBufferSize: Int = 1024
) {
    fun handshakeDuration(): Duration = parseDuration(handshakeTimeout)
}

private fun parseDuration(spec: String): Duration {
    var total = Duration.ZERO
    var i = 0
    val n = spec.length
    fun readNumber(): Long {
        var start = i
        while (i < n && spec[i].isDigit()) i++
        if (start == i) throw IllegalArgumentException("Invalid duration: expected number in '$spec'")
        return spec.substring(start, i).toLong()
    }
    while (i < n) {
        val value = readNumber()
        if (i >= n) throw IllegalArgumentException("Invalid duration: missing unit after number in '$spec'")
        val unit = spec[i]
        i++
        total = total.plus(
            when (unit) {
                's' -> Duration.ofSeconds(value)
                'm' -> Duration.ofMinutes(value)
                'h' -> Duration.ofHours(value)
                'd' -> Duration.ofDays(value)
                else -> throw IllegalArgumentException("Invalid duration unit '$unit' in '$spec'")
            }
        )
    }
    return total
}


