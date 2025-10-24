package model.rpc

import kotlinx.serialization.Serializable

@Serializable
data class JSONRPCRequest<T>(
    val jsonrpc: String = "2.0",
    val method: String,
    val params: T? = null,
    val id: Int
)

@Serializable
data class JSONRPCResponse<R>(
    val jsonrpc: String = "2.0",
    val result: R? = null,
    val error: JSONRPCError? = null,
    val id: Int
)

@Serializable
data class JSONRPCError(
    val code: Int,
    val message: String,
    val data: String? = null
)


