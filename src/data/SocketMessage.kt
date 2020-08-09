package data


import com.google.gson.Gson

data class SocketMessage(val code: Int, val jsonData: String) {
    fun toJsonString(): String {
        return gson.toJson(this)
    }
}

fun Any.toSocketMessage(code: Int): SocketMessage {
    return SocketMessage(code, gson.toJson(this))
}


val gson = Gson()

class MessageHandler<T : Any>(private val classT: Class<T>, val handler: (T) -> Unit) {
    fun handle(jsonData: String) {
        val data = gson.fromJson(jsonData, classT) ?: return
        handler(data)
    }
}

object SocketMessageRouter {
    const val DEVICE_INFO_HANDLER = 2323
    const val DESKTOP_INFO_HANDLER = 18238
    val codeMap = hashMapOf<Int, (String) -> Unit>()

    inline fun <reified T> addHandler(code: Int, noinline handler: (T) -> Unit) {
        codeMap[code] = {
            val obj = gson.fromJson(it, T::class.java)
            handler(obj)
        }
    }

    fun handle(socketMessage: SocketMessage) {
        val handler = codeMap[socketMessage.code] ?: return
        handler(socketMessage.jsonData)
    }

}