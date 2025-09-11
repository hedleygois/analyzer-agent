package net

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
import model.GetProductsParams
import model.GetProductsResult
import model.Product
import model.rpc.JSONRPCRequest
import model.rpc.JSONRPCResponse
import okhttp3.*
import okio.ByteString
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class WsClient(
    private val url: String,
    private val timeoutSeconds: Long
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val pending = ConcurrentHashMap<Int, CompletableFuture<JSONRPCResponse<JsonElement>>>()
    @Volatile private var nextId = 1

    fun connect(): CompletableFuture<Unit> {
        val future = CompletableFuture<Unit>()
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                future.complete(Unit)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val parsed = runCatching {
                    json.decodeFromString(JSONRPCResponse.serializer(JsonElement.serializer()), text)
                }.getOrNull() ?: return
                pending.remove(parsed.id)?.complete(parsed)
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
        val id = nextId++
        // For MCP tools server: tools/call with name=query_sqlite and SQL argument
        val sql = "SELECT * FROM item LIMIT 1000" // adjust as needed to fit your schema
        val params: Map<String, @Suppress("RemoveRedundantQualifierName") kotlinx.serialization.json.JsonElement> = mapOf(
            "name" to JsonPrimitive("query_sqlite"),
            "arguments" to JsonObject(mapOf("sql" to JsonPrimitive(sql)))
        )
        val req = JSONRPCRequest(method = "tools/call", params = params, id = id)
        val future = CompletableFuture<GetProductsResult>()
        val responseFuture = CompletableFuture<JSONRPCResponse<JsonElement>>()
        pending[id] = responseFuture
        responseFuture.whenComplete { resp, err ->
            if (err != null) {
                future.completeExceptionally(err)
            } else if (resp.error != null) {
                future.completeExceptionally(RuntimeException("RPC error ${resp.error.code}: ${resp.error.message}"))
            } else if (resp.result != null) {
                // Try to decode result directly as GetProductsResult
                val direct = runCatching { json.decodeFromJsonElement(GetProductsResult.serializer(), resp.result) }.getOrNull()
                if (direct != null) {
                    future.complete(direct)
                } else {
                    // Some MCP servers wrap tool output under result.content[].text as a JSON string
                    val parsed = tryParseProductsFromToolResult(resp.result)
                    if (parsed != null) {
                        future.complete(parsed)
                    } else {
                        System.err.println("[WsClient] Unexpected tool result format: ${resp.result}")
                        future.complete(GetProductsResult(products = emptyList(), count = 0))
                    }
                }
            } else {
                future.completeExceptionally(RuntimeException("Empty result"))
            }
        }
        val payload = json.encodeToString(JSONRPCRequest.serializer(MapSerializer(String.serializer(), JsonElement.serializer())), req)
        if (webSocket?.send(payload) != true) {
            future.completeExceptionally(IllegalStateException("WebSocket is not open"))
        }
        return future
    }

    private fun tryParseProductsFromToolResult(result: JsonElement): GetProductsResult? {
        // Attempt 1: result has { products: [...], count }
        runCatching { return json.decodeFromJsonElement(GetProductsResult.serializer(), result) }

        val obj = result as? JsonObject ?: return null

        // Attempt 2: tabular response with columns + rows (MCP SQLite tool)
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

            val products = rows.mapNotNull { rowEl ->
                val arr = rowEl as? JsonArray ?: return@mapNotNull null
                val idVal = idIdx?.let { arr.getOrNull(it) as? JsonPrimitive }?.content ?: return@mapNotNull null
                val priceVal = priceIdx?.let { arr.getOrNull(it) as? JsonPrimitive }?.content?.toDoubleOrNull() ?: return@mapNotNull null
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
                    currentPrice = priceVal,
                    currency = "USD",
                    priceHistory = emptyList(),
                    lastUpdated = null,
                    imageUrl = null,
                    description = null
                )
            }
            return GetProductsResult(products = products, count = products.size)
        }

        // Attempt 3: result.content[0].text contains JSON array of products or a wrapped object
        val content = (obj["content"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return null
        val text = (content["text"] as? JsonPrimitive)?.content ?: return null
        runCatching { return json.decodeFromString(GetProductsResult.serializer(), text) }
        val products = runCatching { json.decodeFromString(ListSerializer(Product.serializer()), text) }.getOrNull()
            ?: return null
        return GetProductsResult(products = products, count = products.size)
    }
}


