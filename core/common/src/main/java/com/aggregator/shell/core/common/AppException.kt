package com.aggregator.shell.core.common

sealed class AppException(
    val errorCode: Int,
    override val message: String,
    override val cause: Throwable? = null
) : RuntimeException(message, cause) {

    class NetworkException(cause: Throwable) : AppException(1000, "网络请求失败", cause)
    class SourceInvalidException(sourceId: String) : AppException(2000, "源已失效：$sourceId")
    class RuleParseException(rule: String, detail: String) :
        AppException(2100, "规则解析失败 [$rule]：$detail")
    class JsExecutionTimeoutException(script: String) :
        AppException(2200, "JS 脚本执行超时：$script")
    class PlayUrlInvalidException(url: String) :
        AppException(3000, "播放地址不合法：$url")
    class DatabaseException(cause: Throwable) : AppException(4000, "本地数据错误", cause)
    class SubscriptionUpdateException(url: String, cause: Throwable) :
        AppException(5000, "订阅更新失败：$url", cause)
    class AiException(message: String, cause: Throwable? = null) :
        AppException(6000, "AI 助手异常：$message", cause)
}
