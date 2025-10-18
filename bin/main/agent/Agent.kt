package agent

import analysis.LLMAnalyzer
import config.Config
import email.EmailService
import net.WsClient
import java.util.*

class Agent(private val cfg: Config) {
    private val conn = cfg.mcp ?: cfg.scraper ?: error("Missing MCP/scraper configuration")
    private val ws = WsClient(
        url = conn.webSocketUrl,
        timeoutSeconds = conn.timeoutDuration().seconds,
        openAIConfig = cfg.openai
    )
    private val llm = LLMAnalyzer(cfg.openai)
    private val email = EmailService(cfg.email)
    private var timer: Timer? = null

    fun start() {
        ws.connect().join()
        schedule()
    }

    fun stop() {
        timer?.cancel()
        ws.disconnect()
    }

    private fun schedule() {
        val interval = cfg.analysis.checkIntervalDuration()
        timer = Timer(true)
        timer!!.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                runAnalysis()
            }
        }, 0L, interval.toMillis())
    }

    private fun runAnalysis() {
        val days = (cfg.analysis.analysisDuration().toHours() / 24).toInt().coerceAtLeast(90)
        val products = ws.getProducts(days).join().products
        val analyses = llm.analyze(products)
        val goodDeals = analyses.filter { it.isGoodDeal }
        if (goodDeals.isNotEmpty()) {
            val html = buildReport(goodDeals)
            email.send(cfg.email.subject, html)
        }
    }

    // ufly report, but that's it
    private fun buildReport(deals: List<model.PriceAnalysis>): String {
        val sb = StringBuilder()
        sb.append("<h2>Good Deals Found: ").append(deals.size).append("</h2>")
        deals.forEach { a ->
            sb.append("<div><strong>").append(a.product.name).append("</strong><br/>")
            sb.append("Store: ").append(a.product.store ?: "-").append("<br/>")
            sb.append("Current: ").append(a.currentPrice).append(" ").append("<br/>")
            a.averagePrice?.let { sb.append("Average: ").append(it).append("<br/>") }
            a.discountPercent?.let { sb.append("Discount: ").append(String.format("%.1f%%", it * 100)).append("<br/>") }
            a.reason?.let { sb.append("Reason: ").append(it).append("<br/>") }
            a.product.url?.let { sb.append("<a href=\"").append(it).append("\">Link</a>") }
            sb.append("</div><hr/>")
        }
        return sb.toString()
    }
}


