package github.leavesczy.wifip2p.receiver

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import github.leavesczy.wifip2p.common.Constants
import github.leavesczy.wifip2p.common.FileTransferViewState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.ServerSocket
import android.util.Log
import java.io.FileOutputStream
import java.io.InputStream
import java.io.ObjectInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileReceiverViewModel(context: Application) : AndroidViewModel(context) {

    private val _fileTransferViewState = MutableSharedFlow<FileTransferViewState>()
    val fileTransferViewState: SharedFlow<FileTransferViewState> get() = _fileTransferViewState

    private val _log = MutableSharedFlow<String>()
    val log: SharedFlow<String> get() = _log

    // 수신된 메시지와 송신자 주소를 함께 전달하기 위한 데이터 클래스 정의
    data class ReceivedMessage(val message: String, val senderAddress: String)

    private val _receivedMessage = MutableSharedFlow<ReceivedMessage>()
    val receivedMessage: SharedFlow<ReceivedMessage> get() = _receivedMessage

    private var udpJob: Job? = null
    private var tcpJob: Job? = null
    private var datagramSocket: DatagramSocket? = null
    private var serverSocket: ServerSocket? = null

    // UDP Listener
    fun startListener() {
        Log.d("Mylog-ReceiverViewModel", "startListener 실행")
        if (udpJob != null) {
            Log.d("Mylog-ReceiverViewModel", "UDP Listener가 이미 실행 중입니다.")
            log("UDP Listener가 이미 실행 중입니다.")
            return
        }
        stopTCPListener() // TCP 리스너가 실행 중이라면 종료
        udpJob = viewModelScope.launch(context = Dispatchers.IO) {
            _fileTransferViewState.emit(FileTransferViewState.Idle)

            var buffer = ByteArray(1024 * 100)
            var byteArrayOutputStream: ByteArrayOutputStream? = null
            try {
                _fileTransferViewState.emit(FileTransferViewState.Connecting)
                log("UDP socket on")

                datagramSocket = DatagramSocket(null)
                datagramSocket?.reuseAddress = true
                datagramSocket?.bind(InetSocketAddress(Constants.PORT))

                datagramSocket?.soTimeout = 300000 // 5분으로 수정

                byteArrayOutputStream = ByteArrayOutputStream()

                while (true) {
                    val receivePacket = DatagramPacket(buffer, buffer.size)
                    datagramSocket?.receive(receivePacket)
                    val receivedData = receivePacket.data.copyOf(receivePacket.length)
                    val senderAddress = receivePacket.address.hostAddress

                    if (receivedData.isNotEmpty() && receivedData[0] == (-1).toByte()) {
                        val file = saveReceivedFile(byteArrayOutputStream.toByteArray())
                        _fileTransferViewState.emit(FileTransferViewState.Success(file = file))
                        log("파일 수신 완료")
                        break
                    } else if (receivedData.isNotEmpty()) {
                        val message = String(receivedData)
                        if (message.isNotBlank()) {
                            val truncatedMessage = message.take(10)
                            _receivedMessage.emit(ReceivedMessage(truncatedMessage, senderAddress))
                            log("수신된 메시지 from $senderAddress: $truncatedMessage")
                        } else {
                            byteArrayOutputStream.write(receivedData)
                            log("파일 전송 중，length : ${receivedData.size}")
                        }
                    }
                }
            } catch (e: Throwable) {
                log("UDP Listener 문제 발생: " + e.message)
                _fileTransferViewState.emit(FileTransferViewState.Failed(throwable = e))
            } finally {
                datagramSocket?.close()
                datagramSocket = null
                byteArrayOutputStream?.close()
            }
        }
        udpJob?.invokeOnCompletion {
            udpJob = null
        }
    }

    // TCP Listener
    fun startTCPListener() {
        Log.d("Mylog-ReceiverViewModel", "startTCPListener 실행")
        if (tcpJob != null) {
            Log.d("Mylog-ReceiverViewModel", "TCP Listener가 이미 실행 중입니다.")
            log("TCP Listener가 이미 실행 중입니다.")
            return
        }
        stopUDPListener() // UDP 리스너가 실행 중이라면 종료
        tcpJob = viewModelScope.launch(context = Dispatchers.IO) {
            _fileTransferViewState.emit(value = FileTransferViewState.Idle)

            var clientInputStream: InputStream? = null
            var objectInputStream: ObjectInputStream? = null
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val currentTime = sdf.format(Date())

                _fileTransferViewState.emit(value = FileTransferViewState.Connecting)
                log("$currentTime TCP socket on")

                serverSocket = ServerSocket()
                serverSocket?.reuseAddress = true
                serverSocket?.bind(InetSocketAddress(Constants.PORT))
                serverSocket?.soTimeout = 300000

                log("TCP socket accept，30초 내에 성공하지 않으면 연결이 끊깁니다")

                while (true) {
                    try {
                        log("클라이언트 연결 대기 중...")
                        val client = serverSocket?.accept()

                        _fileTransferViewState.emit(value = FileTransferViewState.Receiving)

                        clientInputStream = client?.getInputStream()
                        objectInputStream = ObjectInputStream(clientInputStream)
                        val transferObject = objectInputStream.readObject()

                        val receivedText = transferObject
                        log("연결 성공, 수신 대기: 텍스트 메시지")
                        log("수신된 텍스트: $receivedText")

                        val message = receivedText.toString()
                        val senderAddress = client?.inetAddress?.hostAddress ?: "알 수 없는 주소"
                        if (message.isNotBlank()) {
                            val truncatedMessage = message.take(10)
                            _receivedMessage.emit(ReceivedMessage(truncatedMessage, senderAddress))
                            _fileTransferViewState.emit(value = FileTransferViewState.MessageReceived)
                        }
                    } catch (e: Throwable) {
                        log("TCP Listener 문제 발생: " + e.message)
                        _fileTransferViewState.emit(value = FileTransferViewState.Failed(throwable = e))
                        break
                    }
                }
            } catch (e: Throwable) {
                log("TCP Listener 문제 발생: " + e.message)
                _fileTransferViewState.emit(value = FileTransferViewState.Failed(throwable = e))
            } finally {
                serverSocket?.close()
                serverSocket = null
                clientInputStream?.close()
                objectInputStream?.close()
            }
        }
        tcpJob?.invokeOnCompletion {
            tcpJob = null
        }
    }

    // UDP Listener 종료
    fun stopUDPListener() {
        udpJob?.cancel()
        udpJob = null
        datagramSocket?.close()
        datagramSocket = null
        Log.d("Mylog-ReceiverViewModel", "UDP Listener 종료")
        log("UDP Listener 종료")
    }

    // TCP Listener 종료
    fun stopTCPListener() {
        tcpJob?.cancel()
        tcpJob = null
        serverSocket?.close()
        serverSocket = null
        Log.d("Mylog-ReceiverViewModel", "TCP Listener 종료")
        log("TCP Listener 종료")
    }

    private fun saveReceivedFile(fileBytes: ByteArray): File {
        val context = getApplication<Application>().applicationContext
        val file = File(context.cacheDir, "received_file_" + System.currentTimeMillis())
        file.writeBytes(fileBytes)
        return file
    }

    private fun log(message: String) {
        viewModelScope.launch {
            _log.emit(message)
            Log.d("Mylog-ReceiverViewModel", message)
        }
    }
}
