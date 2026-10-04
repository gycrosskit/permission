package com.tencent.kuikly.core.nvi.serialization.json
/** 输入/回执对象替身；不承诺覆盖 SDK JSON 序列化。 */
class JSONObject {
    private val values = mutableMapOf<String, Any>()
    fun put(key: String, value: Any) { values[key] = value }
    fun optString(key: String) = values[key] as? String ?: ""
    fun optDouble(key: String, fallback: Double) = (values[key] as? Number)?.toDouble() ?: fallback
    fun optLong(key: String, fallback: Long) = (values[key] as? Number)?.toLong() ?: fallback
    override fun toString() = values.toString()
}
