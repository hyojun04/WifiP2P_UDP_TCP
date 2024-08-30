package github.leavesczy.wifip2p.sender

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.core.net.toFile
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import github.leavesczy.wifip2p.common.Constants
import github.leavesczy.wifip2p.common.FileTransfer
import github.leavesczy.wifip2p.common.FileTransferViewState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.random.Random
import android.util.Log
import java.io.ObjectOutputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

class FileSenderViewModel(context: Application) : AndroidViewModel(context) {

    private val _fileTransferViewState = MutableSharedFlow<FileTransferViewState>()
    val fileTransferViewState: SharedFlow<FileTransferViewState> get() = _fileTransferViewState

    private val _log = MutableSharedFlow<String>()
    val log: SharedFlow<String> get() = _log

    private var job: Job? = null

    fun send(ipAddress: String, fileUri: Uri) {
        Log.d("Mylog-SenderViewModel", "send 실행")
        if (job != null) {
            return
        }
        job = viewModelScope.launch {
            withContext(context = Dispatchers.IO) {
                _fileTransferViewState.emit(FileTransferViewState.Idle)

                var socket: DatagramSocket? = null
                var fileInputStream: FileInputStream? = null
                try {
                    val cacheFile = saveFileToCacheDir(context = getApplication(), fileUri = fileUri)
                    val fileTransfer = FileTransfer(fileName = cacheFile.name)

                    _fileTransferViewState.emit(FileTransferViewState.Connecting)
                    _log.emit("전송 대기 중인 파일: $fileTransfer")
                    _log.emit("socket on")

                    socket = DatagramSocket()
                    val address = InetAddress.getByName(ipAddress)

                    _log.emit("socket connect，30초 내에 성공하지 않으면 연결이 끊깁니다")

                    _fileTransferViewState.emit(FileTransferViewState.Receiving)
                    _log.emit("연결 성공, 파일 전송 시작")

                    val buffer = ByteArray(1024 * 100)
                    fileInputStream = FileInputStream(cacheFile)
                    var length: Int
                    while (true) {
                        length = fileInputStream.read(buffer)
                        if (length > 0) {
                            val packet = DatagramPacket(buffer, length, address, Constants.PORT)
                            socket.send(packet)
                        } else {
                            // 파일 전송 완료 신호 전송
                            val endSignal = byteArrayOf(-1) // 종료 신호
                            val endPacket = DatagramPacket(endSignal, endSignal.size, address, Constants.PORT)
                            socket.send(endPacket)
                            break
                        }
                        _log.emit("파일 전송 중，length : $length")
                    }
                    _log.emit("파일 전송 완료")
                    Log.d("Mylog-SenderViewModel", "파일 전송 완료")
                    _fileTransferViewState.emit(FileTransferViewState.Success(file = cacheFile))
                } catch (e: Throwable) {
                    e.printStackTrace()
                    _log.emit("오류: " + e.message)
                    _fileTransferViewState.emit(FileTransferViewState.Failed(throwable = e))
                } finally {
                    fileInputStream?.close()
                    socket?.close()
                }
            }
        }
        job?.invokeOnCompletion {
            job = null
        }
    }

    private suspend fun saveFileToCacheDir(context: Context, fileUri: Uri): File {
        return withContext(context = Dispatchers.IO) {
            val documentFile = DocumentFile.fromSingleUri(context, fileUri)
                ?: throw NullPointerException("fileName for given input Uri is null")
            val fileName = documentFile.name
            val outputFile =
                File(context.cacheDir, Random.nextInt(1, 200).toString() + "_" + fileName)
            if (outputFile.exists()) {
                outputFile.delete()
            }
            outputFile.createNewFile()
            val outputFileUri = Uri.fromFile(outputFile)
            copyFile(context, fileUri, outputFileUri)
            return@withContext outputFile
        }
    }

    private suspend fun copyFile(context: Context, inputUri: Uri, outputUri: Uri) {
        withContext(context = Dispatchers.IO) {
            val inputStream = context.contentResolver.openInputStream(inputUri)
                ?: throw NullPointerException("InputStream for given input Uri is null")
            val outputStream = FileOutputStream(outputUri.toFile())
            val buffer = ByteArray(1024)
            var length: Int
            while (true) {
                length = inputStream.read(buffer)
                if (length > 0) {
                    outputStream.write(buffer, 0, length)
                } else {
                    break
                }
            }
            inputStream.close()
            outputStream.close()
        }
    }

    fun sendMessage(ipAddress: String, message: String) {
        Log.d("Mylog-SenderViewModel", "sendMessage 실행")
        if (job != null) {
            return
        }
        job = viewModelScope.launch {
            withContext(context = Dispatchers.IO) {
                _fileTransferViewState.emit(FileTransferViewState.Idle)

                var socket: DatagramSocket? = null
                try {
                    _fileTransferViewState.emit(FileTransferViewState.Connecting)
                    //_log.emit("메시지 전송 대기 중: $message")
                    _log.emit("socket on")

                    socket = DatagramSocket()
                    val address = InetAddress.getByName(ipAddress)

                    _log.emit("socket connect，30초 내에 성공하지 않으면 연결이 끊깁니다")

                    _fileTransferViewState.emit(FileTransferViewState.Receiving)
                    _log.emit("연결 성공, 메시지 전송 시작")

                    val buffer = message.toByteArray()
                    val packet = DatagramPacket(buffer, buffer.size, address, Constants.PORT)
                    socket.send(packet)

                    _log.emit("메시지 전송 완료")
                    Log.d("Mylog-SenderViewModel", "메시지 전송 완료")
                    _fileTransferViewState.emit(FileTransferViewState.Success(file = File(""))) // 성공 상태 전송
                } catch (e: Throwable) {
                    e.printStackTrace()
                    _log.emit("오류: " + e.message)
                    _fileTransferViewState.emit(FileTransferViewState.Failed(throwable = e))
                } finally {
                    socket?.close()
                }
            }
        }
        job?.invokeOnCompletion {
            job = null
        }
    }

    // 메시지를 전송하는 TCP 메소드 추가
    fun sendMessage_TCP(ipAddress: String, message: String) {
        Log.d("Mylog-SenderViewModel", "sendMessage 실행")
        if (job != null) {
        }
        job = viewModelScope.launch {
            withContext(context = Dispatchers.IO) {
                _fileTransferViewState.emit(value = FileTransferViewState.Idle)

                var socket: Socket? = null
                var outputStream: OutputStream? = null
                var objectOutputStream: ObjectOutputStream? = null
                try {
                    _fileTransferViewState.emit(value = FileTransferViewState.Connecting)
                    //_log.emit(value = "메시지 전송 대기 중: $message")
                    _log.emit(value = "socket on")

                    socket = Socket()
                    socket.bind(null)
                    Log.d("Mylog-SenderViewModel", "socket on")

                    _log.emit(value = "socket connect，30초 내에 성공하지 않으면 연결이 끊깁니다")

                    socket.connect(InetSocketAddress(ipAddress, Constants.PORT), 30000)

                    _fileTransferViewState.emit(value = FileTransferViewState.Receiving)
                    _log.emit(value = "연결 성공, 메시지 전송 시작")

                    outputStream = socket.getOutputStream()
                    objectOutputStream = ObjectOutputStream(outputStream)
                    objectOutputStream.writeObject(message)

                    _log.emit(value = "메시지 전송 완료")
                    Log.d("Mylog-SenderViewModel", "메시지 전송 완료")
                    _fileTransferViewState.emit(value = FileTransferViewState.Success(file = File(""))) // 성공 상태 전송
                } catch (e: Throwable) {
                    e.printStackTrace()
                    _log.emit(value = "오류: " + e.message)
                    _fileTransferViewState.emit(value = FileTransferViewState.Failed(throwable = e))
                } finally {
                    outputStream?.close()
                    objectOutputStream?.close()
                    socket?.close()
                }
            }
        }
        job?.invokeOnCompletion {
            job = null
        }
    }
}
