package com.msop.player.core.network

import java.io.IOException

/**
 * 远程请求失败的统一类型。
 *
 * 特意继承 [IOException]：流播解析发生在 `ResolvingDataSource.Resolver` 里，
 * Media3 只把 IOException 当可恢复的播放错误，抛别的异常会直接崩播放器。
 */
sealed class OpenListException(message: String, cause: Throwable? = null) :
    IOException(message, cause) {

    class NotConfigured : OpenListException("还没有配置 OpenList 服务器")

    class Network(cause: Throwable) : OpenListException("无法连接服务器：${cause.message}", cause)

    class Http(val status: Int) : OpenListException("服务器返回 HTTP $status")

    class Auth : OpenListException("账号或密码错误，或登录已过期")

    class Api(val code: Int, val serverMessage: String) :
        OpenListException("服务器返回错误 $code：$serverMessage")

    class EmptyStreamUrl : OpenListException("这首歌没有拿到可用的播放直链")

    class BadResponse : OpenListException("服务器返回的内容无法解析")
}

/** 给 UI 直接显示的中文提示。 */
fun OpenListException.userMessage(): String = when (this) {
    is OpenListException.NotConfigured -> "请先在设置里填写 OpenList 服务器地址与账号"
    is OpenListException.Network -> "连不上服务器。检查网络、HTTPS 证书与反代地址：${cause?.message.orEmpty()}"
    is OpenListException.Auth -> "账号或密码不对，请检查后重试"
    is OpenListException.Http -> when (status) {
        404 -> "接口不存在（404）。这个部署可能没启用媒体接口，请核对地址"
        403 -> "没有访问权限（403）"
        else -> "服务器返回 HTTP $status"
    }
    is OpenListException.Api -> "服务器返回错误：$serverMessage"
    is OpenListException.EmptyStreamUrl -> "这首歌取不到播放直链，可能已被移动或删除"
    is OpenListException.BadResponse -> "服务器返回的不是预期数据，确认填的是 OpenList 地址"
}
