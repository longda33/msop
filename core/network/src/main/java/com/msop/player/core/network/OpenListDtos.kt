package com.msop.player.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * OpenList v4.2.9 原生 API 的请求/响应结构。字段以 2026-11-11 实测响应为准，
 * 一律 `ignoreUnknownKeys`，服务端加字段不会让客户端炸。
 *
 * 注意 `code` 是**业务码**（HTTP 200 也可能返回 401/500），两层都要判。
 */
@Serializable
data class ApiEnvelope<T>(
    val code: Int,
    val message: String = "",
    val data: T? = null,
)

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class LoginData(val token: String = "")

@Serializable
data class FsListRequest(
    val path: String,
    val page: Int = 1,
    @SerialName("per_page") val perPage: Int = 100,
    val password: String = "",
    val refresh: Boolean = false,
)

@Serializable
data class FsGetRequest(val path: String, val password: String = "")

@Serializable
data class FsItem(
    val name: String,
    val size: Long = 0L,
    @SerialName("is_dir") val isDir: Boolean = false,
    val modified: String? = null,
    val created: String? = null,
    val sign: String? = null,
    val thumb: String? = null,
    val type: Int = 0,
)

@Serializable
data class FsListData(
    val content: List<FsItem>? = null,
    val total: Int = 0,
    val provider: String? = null,
    val write: Boolean = false,
)

@Serializable
data class FsGetData(
    val name: String,
    val size: Long = 0L,
    @SerialName("raw_url") val rawUrl: String = "",
    val sign: String? = null,
    val provider: String? = null,
)
