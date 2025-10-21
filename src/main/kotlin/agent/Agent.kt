package agent

import analysis.LLMAnalyzer
import analysis.ReportGenerator
import config.Config
import email.EmailService
import net.WsClient
import java.util.*
import java.util.logging.Logger
import java.util.logging.Level

class Agent(private val cfg: Config) {
    private val logger = Logger.getLogger(Agent::class.java.name)
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
        logger.log(Level.FINE, "Products: ${products.size} - ${products.joinToString { it.name }}")
        val analyses = llm.analyze(products)
        logger.log(Level.FINE, "Analyses: ${analyses.size} - ${analyses.joinToString { it.product.name }}")
        val goodDeals = analyses.filter { it.isGoodDeal }
        logger.log(Level.FINE, "Good Deals: ${goodDeals.size} - ${goodDeals.joinToString { it.product.name }}")
        val marketTrends = llm.analyzeMarketTrends(products)
        logger.log(Level.FINE, "Market Trends: ${if (marketTrends.error != null) "Error: ${marketTrends.error}" else "Success"}")
        
        val insights = llm.generateInsights(products)
        logger.log(Level.FINE, "Insights: ${if (insights.error != null) "Error: ${insights.error}" else "Success"}")
        
        if (goodDeals.isNotEmpty()) {
            val tabularReport = ReportGenerator.generateTabularReport(goodDeals, insights, marketTrends)
            // logger.log(Level.FINE, "📊 TABULAR REPORT:\n$tabularReport")
            println(tabularReport)
            
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


