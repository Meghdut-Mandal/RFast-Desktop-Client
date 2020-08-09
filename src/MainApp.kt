import adb.ADB
import adb.ADBParser
import adb.CommandLine
import com.meghdut.rfast.network.AndroidDevice
import data.onUI
import network.ConnectionServer
import java.util.concurrent.Executors
import java.util.logging.Level
import java.util.logging.Logger
import javax.swing.UIManager


object ADBHelper {
    private val executor = Executors.newFixedThreadPool(5)
    private val commandLine = CommandLine()
    private val adbParser = ADBParser()
    private val adb = ADB(commandLine, adbParser)


    fun startADb(onComplete: () -> Unit) = executor.submit {
        adb.enableTCPCommand()
        onComplete()
    }

    fun connectToDevice(ip: String, onComplete: (Boolean) -> Unit) = executor.submit {
        val connectDevice = adb.connectDevice(ip)
        onUI {
            onComplete(connectDevice)
        }
    }
}


fun main() {

    try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
    }
    catch (ex: Exception) {
        Logger.getLogger(QRProvider::class.java.name).log(Level.SEVERE, null, ex)
    }
    val server = ConnectionServer()
    val qrProvider = QRProvider()
    qrProvider.isVisible = true
    qrProvider.progressBar.isIndeterminate = true
    qrProvider.setLocationRelativeTo(null)
//    qrProvider.isAlwaysOnTop = true

    server.hashValue.observe(qrProvider.messageStatus) {
        if (it.isNotBlank()) {
            qrProvider.encodeText(it)
            qrProvider.setConnectionMessage("Connected to the server")
            qrProvider.setMessage("Scan the QR From the Android App")
            qrProvider.progressBar.isIndeterminate = false
            qrProvider.isAlwaysOnTop = false
        }
    }

    fun connectDevice(ip: String, androidDevice: AndroidDevice) {
        ADBHelper.connectToDevice(ip) {
            qrProvider.progressBar.isIndeterminate = false
            if (it) {
                qrProvider.setMessage("Connected to ${androidDevice.name}")
            } else {
                qrProvider.setMessage("Failed to connect ")
            }
        }
    }

    server.androidDevice.observe(qrProvider.messageStatus) { androidDevice ->
        val ip = androidDevice.ipList.find { it.startsWith("192") } ?: return@observe
        qrProvider.setConnectionMessage("Detected  ${androidDevice.name}")
        qrProvider.progressBar.isIndeterminate = true
        qrProvider.setMessage("Connecting to IP $ip")
        connectDevice(ip, androidDevice)
    }
    server.startServer()
    ADBHelper.startADb { }
}

