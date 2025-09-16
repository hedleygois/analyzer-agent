package net

import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import config.OpenAIConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import model.GetProductsResult
import model.Product
import okhttp3.*
import okio.ByteString
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class WsClient(
    private val url: String,
    private val timeoutSeconds: Long,
    private val openAIConfig: OpenAIConfig? = null
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .build()
    private val openAI: OpenAI? = openAIConfig?.let { OpenAI(token = it.apiKey) }

    private var webSocket: WebSocket? = null
    private val pending = ConcurrentHashMap<Int, CompletableFuture<JsonObject>>()

    @Volatile
    private var nextId = 1

    fun connect(): CompletableFuture<Unit> {
        val future = CompletableFuture<Unit>()
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                future.complete(Unit)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return
                val idEl = root["id"] as? JsonPrimitive ?: return
                val id = idEl.content.toIntOrNull() ?: return
                pending.remove(id)?.complete(root)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                onMessage(webSocket, bytes.utf8())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                pending.values.forEach { it.completeExceptionally(t) }
                pending.clear()
                if (!future.isDone) future.completeExceptionally(t)
            }
        })
        return future
    }

    fun disconnect() {
        webSocket?.close(1000, null)
        webSocket = null
    }

    fun getProducts(daysBack: Int): CompletableFuture<GetProductsResult> {
        val resultFuture = CompletableFuture<GetProductsResult>()

        call("initialize", JsonObject(emptyMap()))
            .exceptionally { null }
            .thenCompose { call("tools/list", JsonNull) }
            .thenCompose { tools ->
                val queryTool = chooseQueryToolName(tools) ?: "query"
                toolsCall("get_schema", "")
                    .thenApply { schema -> Pair(queryTool, schema) }
            }
            .thenCompose { (queryTool, schema) ->
                val sql = synthesizeSql(schema, daysBack)
                if (sql == null) CompletableFuture.failedFuture(IllegalStateException("Failed to synthesize SQL"))
                else toolsCall(queryTool,sql)
            }
            .whenComplete { respEl, err ->
                if (err != null) {
                    resultFuture.completeExceptionally(err)
                } else if (respEl == null) {
                    resultFuture.completeExceptionally(IllegalStateException("Null response"))
                } else {
                    val parsed = tryParseProductsFromToolResult(respEl)
                    if (parsed != null) resultFuture.complete(parsed)
                    else {
                        System.err.println("[WsClient] Unexpected tool result format: ${respEl}")
                        resultFuture.complete(GetProductsResult(products = emptyList(), count = 0))
                    }
                }
            }

        return resultFuture
    }

    private fun chooseQueryToolName(toolsResult: JsonElement?): String? {
        val obj = toolsResult as? JsonObject ?: return null
        val tools = (obj["tools"] as? JsonArray) ?: return null
        val names = tools.mapNotNull { (it as? JsonObject)?.get("name") as? JsonPrimitive }.map { it.content }
        return when {
            names.contains("query") -> "query"
            names.contains("query_sqlite") -> "query_sqlite"
            else -> null
        }
    }

    private fun synthesizeSql(schemaEl: JsonElement?, daysBack: Int): String? {
        val schemaText = when (schemaEl) {
            is JsonObject -> schemaEl["schema"]?.jsonPrimitive?.content ?: schemaEl.toString()
            is JsonPrimitive -> schemaEl.content
            else -> schemaEl?.toString() ?: ""
        }
        val system = "You are a SQL expert. Given a database schema and a goal, output a single SQLite SQL query string only, no explanation. Ensure the query returns product fields and a scraped_at timestamp."
        val user = buildString {
            appendLine("Schema:\n${schemaText}")
            appendLine()
            appendLine("Goal: Return recent products with their latest scrape timestamps within the last ${daysBack} days. Include columns: id, name, url, store_id, item_type, price, scraped_at (ISO-8601). Order by scraped_at desc and limit 100.")
            appendLine("Rules: Return ONLY the SQL text. Use proper joins. If a latest timestamp is available, use MAX(timestamp) as scraped_at.")
        }
        val client = openAI ?: return defaultSql(daysBack)
        return try {
            val req = ChatCompletionRequest(
                model = ModelId(openAIConfig!!.model),
                temperature = 0.1,
                messages = listOf(
                    ChatMessage(role = ChatRole.System, content = system),
                    ChatMessage(role = ChatRole.User, content = user)
                )
            )
            val resp = runBlocking { client.chatCompletion(req) }
            val raw = resp.choices.firstOrNull()?.message?.content?.trim().orEmpty()
            stripCodeFences(raw).trim().ifEmpty { defaultSql(daysBack) }
        } catch (t: Throwable) {
            defaultSql(daysBack)
        }
    }

    private fun defaultSql(daysBack: Int): String {
        return """
            SELECT item.id, item.name, item.url, item.store_id, item.item_type, item.price,
                   MAX(scrape.timestamp) AS scraped_at
            FROM item
            JOIN scrape_item ON item.id = scrape_item.item_id
            JOIN scrape ON scrape.id = scrape_item.scrape_id
            WHERE scrape.timestamp >= datetime('now', '-' || $daysBack || ' days')
            GROUP BY item.id, item.name, item.url, item.store_id, item.item_type, item.price
            ORDER BY scraped_at DESC
            LIMIT 1000
        """.trimIndent()
    }

    private fun stripCodeFences(text: String): String {
        var t = text.trim()
        if (t.startsWith("```") ) {
            t = t.removePrefix("```")
            val nl = t.indexOf('\n')
            if (nl >= 0) t = t.substring(nl + 1)
            val end = t.lastIndexOf("```")
            if (end >= 0) t = t.substring(0, end)
        }
        return t
    }

    private fun toolsCall(name: String, arguments: String): CompletableFuture<JsonElement?> {
        val params = JsonObject(mapOf(
            "name" to JsonPrimitive(name),
            "arguments" to JsonPrimitive(arguments)
        ))
        return call("tools/call", params)
    }

    private fun call(method: String, params: JsonElement): CompletableFuture<JsonElement?> {
        val id = nextId++
        val request = JsonObject(mapOf(
            "jsonrpc" to JsonPrimitive("2.0"),
            "method" to JsonPrimitive(method),
            "params" to params,
            "id" to JsonPrimitive(id)
        ))
        val responseFuture = CompletableFuture<JsonObject>()
        pending[id] = responseFuture
        val payload = json.encodeToString(request)
        val outer = CompletableFuture<JsonElement?>()
        if (webSocket?.send(payload) != true) {
            outer.completeExceptionally(IllegalStateException("WebSocket is not open"))
            return outer
        }
        responseFuture.whenComplete { root, err ->
            if (err != null) {
                outer.completeExceptionally(err)
            } else if (root == null) {
                outer.completeExceptionally(IllegalStateException("Empty response"))
            } else {
                val error = root["error"]
                if (error != null && error !is JsonNull) {
                    val msg = (error as? JsonObject)?.get("message")?.jsonPrimitive?.content
                    val code = (error as? JsonObject)?.get("code")?.jsonPrimitive?.content?.toIntOrNull()
                    outer.completeExceptionally(RuntimeException("RPC error ${code ?: ""}: ${msg ?: "unknown"}"))
                } else {
                    outer.complete(root["result"])
                }
            }
        }
        return outer
    }

    private fun tryParseProductsFromToolResult(result: JsonElement): GetProductsResult? {
        val obj = result as? JsonObject ?: return null

        val columns = obj["columns"] as? JsonArray
        val rows = obj["rows"] as? JsonArray
        if (columns != null && rows != null) {
            val colIndex = columns.mapIndexedNotNull { idx, el ->
                (el as? JsonPrimitive)?.content?.let { it to idx }
            }.toMap()

            fun idx(name: String): Int? = colIndex[name]
            val idIdx = idx("id")
            val priceIdx = idx("price")
            val nameIdx = idx("name")
            val urlIdx = idx("url")
            val storeIdIdx = idx("store_id")
            val typeIdx = idx("item_type")
            val scrapedAtIdx = idx("scraped_at") ?: idx("timestamp")

            val products = rows.mapNotNull { rowEl ->
                val arr = rowEl as? JsonArray ?: return@mapNotNull null
                val idVal = idIdx?.let { arr.getOrNull(it) as? JsonPrimitive }?.content ?: return@mapNotNull null
                val priceVal = priceIdx?.let { arr.getOrNull(it) as? JsonPrimitive }?.content?.toDoubleOrNull()
                    ?: return@mapNotNull null
                val nameVal = nameIdx?.let { arr.getOrNull(it) as? JsonPrimitive }?.content ?: ""
                val urlVal = urlIdx?.let { arr.getOrNull(it) as? JsonPrimitive }?.content
                val storeIdVal = storeIdIdx?.let { arr.getOrNull(it) as? JsonPrimitive }?.content
                val typeVal = typeIdx?.let { arr.getOrNull(it) as? JsonPrimitive }?.content

                Product(
                    id = idVal,
                    name = nameVal,
                    url = urlVal,
                    store = storeIdVal?.let { "store-$it" },
                    category = typeVal,
                    price = priceVal,
                    scrapedAt = scrapedAtIdx?.let { (arr.getOrNull(it) as? JsonPrimitive)?.content }
                )
            }
            return GetProductsResult(products = products, count = products.size)
        }

        val content = (obj["content"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return null
        val text = (content["text"] as? JsonPrimitive)?.content ?: return null
        val arr = runCatching { json.parseToJsonElement(text) as? JsonArray }.getOrNull() ?: return null
        val products = arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val id = o["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val name = o["name"]?.jsonPrimitive?.content ?: ""
            val url = (o["url"] as? JsonPrimitive)?.content
            val store = (o["store"] as? JsonPrimitive)?.content
            val category = (o["category"] as? JsonPrimitive)?.content
            val price = o["price"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return@mapNotNull null
            val scrapedAt = (o["scraped_at"] as? JsonPrimitive)?.content
                ?: (o["timestamp"] as? JsonPrimitive)?.content
            Product(
                id = id,
                name = name,
                url = url,
                store = store,
                category = category,
                price = price,
                scrapedAt = scrapedAt
            )
        }
        return GetProductsResult(products = products, count = products.size)
    }
}


