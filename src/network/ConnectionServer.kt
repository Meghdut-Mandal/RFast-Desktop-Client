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
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.concurrent.thread


class ConnectionServer : WebSocketListener() {
    val isConnected = JLiveData(false)
    val hashValue = JLiveData("")
    var tmpHash = ""
    val INFO_SUFFIX = "info_hash[="
    lateinit var socket: WebSocket

    val androidDevice = JLiveData(AndroidDevice("", listOf()))

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
                val properties = getSystemProps()
                properties.store(outputStream, "NONE")
                stringBuffer.append(String(outputStream.toByteArray()))
                println("network>ConnectionServer>  $stringBuffer ")
            }
            catch (e: IOException) {
                e.printStackTrace()
            }
            return stringBuffer.toString()
        }

    private fun getSystemProps(): Properties {
        val properties = System.getProperties()
        properties.remove("sun.boot.class.path")
        properties.remove("java.library.path")
        properties.remove("java.class.path")
        return properties
    }


    fun sendClientInfo(ws: WebSocket) {
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
                    tmpHash = response.body?.string() ?: return@use
                    println("network>ConnectionServer>startServer   $tmpHash ")
                    val sessionInfo = SessionInfo(Constants.deviceType, tmpHash)
                    // the socket is connected and we have received the hqsahKey
                    // send the key to the server it will be sen to the required room
                    ws.send(INFO_SUFFIX + gson.toJson(sessionInfo))
                    addHandlers()
                }
            }
        }
        catch (e: Exception) {
            e.printStackTrace()
            ws.close(23, "IO Error ${e.localizedMessage}")
        }
    }

    fun startServer() = thread {
        val request: Request =
                Request.Builder().url(Constants.socketUrl).build()
        client.newWebSocket(request, this)
    }


    private fun addHandlers() {
        SocketMessageRouter.addHandler(DEVICE_INFO_HANDLER) { device: AndroidDevice ->
            androidDevice.setValue(device)
            sendDesktopInfo()
        }
    }

    private fun sendDesktopInfo() {
        val systemProps = getSystemProps()
        val propsMaps = hashMapOf<String, String>()
        systemProps.toMap().forEach { (t, u) ->
            propsMaps[t.toString()] = u.toString()
        }
        val desktopClient = DesktopClient("Desktop", propsMaps)
        socket.send(gson.toJson(desktopClient.toSocketMessage(SocketMessageRouter.DESKTOP_INFO_HANDLER)))
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        socket = webSocket
        output("Websocket opened ")
        sendClientInfo(webSocket)
        hashValue.setValue(tmpHash)
        tmpHash = ""
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