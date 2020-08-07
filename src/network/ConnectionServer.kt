package network

import com.google.gson.Gson
import com.meghdut.rfast.network.AndroidDevice
import data.*
import data.SocketMessageRouter.DEVICE_INFO_HANDLER
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.concurrent.thread


class ConnectionServer : WebSocketListener() {
    val isConnected = JLiveData(false)
    val hashValue = JLiveData("")
    var tmpHash = ""
     val INFO_SUFFIX = "info_hash[="

    val androidDevice = JLiveData(AndroidDevice("", listOf()))
    val socket: Future<WebSocket> = newSocket()

    private fun newSocket() =
            Executors.newSingleThreadExecutor().submit(Callable {
                val request: Request =
                        Request.Builder().url(Constants.socketUrl).build()
                client.newWebSocket(request, this)
            })

    private val client: OkHttpClient =
            OkHttpClient.Builder().build()
    var error = JLiveData<Exception>(null)
    val gson = Gson()
    private val propString: String
        get() {
            val stringBuffer = StringBuffer()
            val outputStream = ByteArrayOutputStream()
            try {
                val properties = System.getProperties()
                properties.remove("sun.boot.class.path")
                properties.remove("java.library.path")
                properties.remove("java.class.path")
                properties.store(outputStream, "NONE")
                stringBuffer.append(String(outputStream.toByteArray()))
                println("network>ConnectionServer>  $stringBuffer ")
            }
            catch (e: IOException) {
                e.printStackTrace()
            }
            return stringBuffer.toString()
        }


    fun startServer(retry: Int = 0) {
        thread {
            if (retry > 5)
                return@thread

            try {
                val textType = ("text/html; charset=UTF-8").toMediaType()
                val body: RequestBody = propString.toRequestBody(textType)
                val request =
                        Request.Builder().url(Constants.newClientUrl).post(body).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        println("network>>startServer  Failed to Connect to server ")
                        error.setValue(Exception("Failed to connect " + response.body!!.string()))
                    } else {
                        val string = response.body?.string() ?: return@use
                        println("network>ConnectionServer>startServer   $string ")
                        connectSocket(string)
                    }
                }
            }
            catch (e: Exception) {
                e.printStackTrace()
                return@thread startServer(retry + 1)
            }

        }
    }


    fun connectSocket(hashID: String) {
        tmpHash = hashID
        val sessionInfo = SessionInfo(Constants.deviceType, hashID)
        val request: Request =
                Request.Builder().url(Constants.socketUrl).header("Info", gson.toJson(sessionInfo)).build()
        client.newWebSocket(request, this)
        addHandlers()
    }

    private fun addHandlers() {
        SocketMessageRouter.addHandler(DEVICE_INFO_HANDLER) { device: AndroidDevice ->
            androidDevice.setValue(device)
        }
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        hashValue.setValue(tmpHash)
        tmpHash = ""
        output("Websocket opened ")
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        output("Receiving : $text")
        if (text.contains("jsonData")) {
            val msg = text.split("|").lastOrNull() ?: return
            val socketMessage = gson.fromJson(msg, SocketMessage::class.java)
            SocketMessageRouter.handle(socketMessage)
        }
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        output("Receiving bytes : " + bytes.hex())

    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        webSocket.close(NORMAL_CLOSURE_STATUS, null)
        output("Closing : $code / $reason")
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        output("Error : " + t.message)
    }

    private fun output(s: String) {
        println("network>>output   $s")
    }

    companion object {
        private const val NORMAL_CLOSURE_STATUS = 1000
    }

}