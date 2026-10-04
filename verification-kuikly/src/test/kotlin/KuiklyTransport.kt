package com.tencent.kuikly.core.module
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
class CallbackRef
class NativeResult(val callbackRef: CallbackRef)
/** 仅截获 SDK 传输；实际协程、超时、状态映射来自生产源码。 */
open class Module {
    lateinit var response: (JSONObject?) -> Unit
    val calls = mutableListOf<Pair<String, String>>()
    val cancelled = mutableListOf<JSONObject>()
    var removedCallbacks = 0
    open fun moduleName() = ""
    fun toNative(sync: Boolean, method: String, args: String,
                 callback: (JSONObject?) -> Unit, keepAlive: Boolean): NativeResult {
        calls += method to args
        response = callback
        return NativeResult(CallbackRef())
    }
    fun asyncToNativeMethod(method: String, args: JSONObject, callback: Any?) { cancelled += args }
    fun removeCallback(ref: CallbackRef) { removedCallbacks++ }
}
