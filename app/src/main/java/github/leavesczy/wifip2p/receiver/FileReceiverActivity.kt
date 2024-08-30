package github.leavesczy.wifip2p.receiver

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import coil.load
import github.leavesczy.wifip2p.BaseActivity
import github.leavesczy.wifip2p.DirectActionListener
import github.leavesczy.wifip2p.DirectBroadcastReceiver
import github.leavesczy.wifip2p.R
import github.leavesczy.wifip2p.common.FileTransferViewState
import github.leavesczy.wifip2p.sender.FileSenderActivity
import github.leavesczy.wifip2p.sender.FileSenderViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

/**
 * @Author: leavesCZY
 * @Desc:
 */
class FileReceiverActivity : BaseActivity() {

    /* 파일 수신 기능 잠시 비활성화
    private val ivImage by lazy {
        findViewById<ImageView>(R.id.ivImage)
    }*/

    private val tvLog by lazy {
        findViewById<TextView>(R.id.tvLog)
    }

    private val btnCreateGroup by lazy {
        findViewById<Button>(R.id.btnCreateGroup)
    }

    private val btnRemoveGroup by lazy {
        findViewById<Button>(R.id.btnRemoveGroup)
    }

    private val btnStartReceive by lazy {
        findViewById<Button>(R.id.btnStartReceive)
    }

    // 메시지 보내기 버튼 추가
    private val btnSendMessage by lazy {
        findViewById<Button>(R.id.btnSendMessage)
    }
    //메시지 입력 창 레이아웃
    private val etMessageInput by lazy {
        findViewById<EditText>(R.id.etMessageInput)
    }
    

    //메시지 송수신 로그 창 추가
    private val ReceiveMessageLog by lazy {
        findViewById<TextView>(R.id.tvReceiveMessageLog)
    }

    private val SendMessageLog by lazy {
        findViewById<TextView>(R.id.tvSendMessageLog)
    }

    private var sentMessageCount = 0 // 보낸 메시지 개수 추적
    private var receivedMessageCount = 0 // 받은 메시지 개수 추적

    private val fileReceiverViewModel by viewModels<FileReceiverViewModel>()
    private val fileSenderViewModel by viewModels<FileSenderViewModel>() // 수신자쪽에서도 메세지를 보낼 수 있도록 SenderViewModel추가

    private lateinit var wifiP2pManager: WifiP2pManager

    private lateinit var wifiP2pChannel: WifiP2pManager.Channel

    private var connectionInfoAvailable = false

    private var broadcastReceiver: BroadcastReceiver? = null

    //메시지 보내기를 위한 정의
    private var wifiP2pInfo: WifiP2pInfo? = null // 추가: WiFi P2P 정보 저장
    private var devicename: String? = null // 기기 이름을 저장하기 위한 변수 추가

    //Hz 채널을 위한 정의
    private var wifiP2pGroup: WifiP2pManager.GroupInfoListener? = null



    //메시지 보내기 기능을 위한 메소드
    private fun getBroadcastAddress(ipAddress: String): String {
        // IP 주소를 .으로 분리
        Log.d("Mylog-ReceiverActivity", "getBroadcastAddress 실행")
        val parts = ipAddress.split(".")
        if (parts.size != 4) {
            throw IllegalArgumentException("Invalid IP address format")
        }
        // 마지막 옥텟을 255로 설정
        return "${parts[0]}.${parts[1]}.${parts[2]}.255"
    }

    private fun sendMessage(message: String) {
        Log.d("Mylog-ReceiverActivity", "sendMessage 실행")
        val ipAddress2 = wifiP2pInfo?.groupOwnerAddress?.hostAddress ?: ""

        val ipAddress = getBroadcastAddress(ipAddress2)
        if (ipAddress.isNotBlank()) {
            Log.d("Mylog-ReceiverActivity", "sendMessage 성공")
            fileSenderViewModel.sendMessage(ipAddress = ipAddress, message = message)

            // 메시지 전송 후 송신 로그 업데이트
            sentMessageCount += 1
            val truncatedMessage = message.take(10) // 메시지의 앞 10글자만 추출
            val timestamp = System.currentTimeMillis() // 현재 시간 저장
            val formattedMessage = "[$sentMessageCount] $truncatedMessage $timestamp"
            SendMessageLog.append("$formattedMessage\n\n")
        } else {
            Log.d("Mylog-ReceiverActivity", "sendMessage 실패")
            showToast("IP 주소를 찾을 수 없습니다.")
        }
    }


    private val directActionListener = object : DirectActionListener {
        override fun wifiP2pEnabled(enabled: Boolean) {
            log("wifiP2pEnabled: $enabled")
            Log.d("Mylog-ReceiverViewModel", "wifiP2pEnabled 실행")
        }

        @SuppressLint("MissingPermission")
        override fun onConnectionInfoAvailable(wifiP2pInfo: WifiP2pInfo) {
            Log.d("Mylog-ReceiverViewModel", "onConnectionInfoAvailable 실행")
            this@FileReceiverActivity.wifiP2pInfo = wifiP2pInfo // WiFi P2P 정보 저장
            log("onConnectionInfoAvailable")
            log("isGroupOwner：" + wifiP2pInfo.isGroupOwner)
            log("groupFormed：" + wifiP2pInfo.groupFormed)
            if (wifiP2pInfo.groupFormed && wifiP2pInfo.isGroupOwner) {
                connectionInfoAvailable = true
            }

            // 추가: 그룹의 주파수 정보를 요청
            wifiP2pManager.requestGroupInfo(wifiP2pChannel) { group ->
                if (group != null) {
                    val channel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        group.frequency
                    } else {
                        TODO("VERSION.SDK_INT < Q")
                    }
                    Log.d("Mylog-ReceiverActivity", "그룹 채널: $channel Hz") //Hz로 수정함
                } else {
                    Log.d("Mylog-ReceiverActivity", "그룹 정보가 없습니다.")
                }
            }

        }

        override fun onDisconnection() {
            connectionInfoAvailable = false
            Log.d("Mylog-ReceiverViewModel", "onDisconnection 실행")
            log("onDisconnection")
        }

        override fun onSelfDeviceAvailable(wifiP2pDevice: WifiP2pDevice) {
            Log.d("Mylog-ReceiverViewModel", "onSelfDeviceAvailable 실행")
            log("onSelfDeviceAvailable: \n$wifiP2pDevice")
            devicename = wifiP2pDevice.deviceName // 기기 이름 저장
        }

        override fun onPeersAvailable(wifiP2pDeviceList: Collection<WifiP2pDevice>) {
            Log.d("Mylog-ReceiverViewModel", "onPeersAvailable 실행")
            log("onPeersAvailable , size:" + wifiP2pDeviceList.size)
            for (wifiP2pDevice in wifiP2pDeviceList) {
                log("wifiP2pDevice: $wifiP2pDevice")
            }
        }

        override fun onChannelDisconnected() {
            Log.d("Mylog-ReceiverViewModel", "onChannelDisconnected 실행")
            log("onChannelDisconnected")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("Mylog-ReceiverActivity", "onCreate 시작")
        setContentView(R.layout.activity_file_receiver)
        initView()
        initDevice()
        initEvent()
    }

    private fun initView() {
        supportActionBar?.title = "수신자"
        Log.d("Mylog-ReceiverActivity", "initView 실행")
        btnCreateGroup.setOnClickListener {
            createGroup()
            Log.d("Mylog-ReceiverActivity", "그룹생성 버튼 눌림")
        }
        btnRemoveGroup.setOnClickListener {
            removeGroup()
            Log.d("Mylog-ReceiverActivity", "그룹제거 버튼 눌림")
        }
        btnStartReceive.setOnClickListener {
            fileReceiverViewModel.startListener()
            Log.d("Mylog-ReceiverActivity", "수신대기 버튼 눌림")
        }
        btnSendMessage.setOnClickListener { // 새로운 "메시지 보내기" 버튼을 리스너와 연결
            Log.d("Mylog-ReceiverActivity", "btnSendMessage 실행")
            val message = etMessageInput.text.toString() // EditText에 입력된 메시지를 가져옴 //앞에 기기정보를 추가해야함
            if (message.isNotBlank()) { // 메시지가 비어있지 않은 경우에만 전송
                sendMessage("$devicename:  " + message)
                etMessageInput.text.clear() // 메시지 전송 후 입력 필드를 초기화
            } else {
                showToast("메시지를 입력하세요")
            }
        }
    }

    private fun initDevice() {
        val mWifiP2pManager = getSystemService(WIFI_P2P_SERVICE) as? WifiP2pManager
        Log.d("Mylog-ReceiverActivity", "initDevice 실행")
        if (mWifiP2pManager == null) {
            finish()
            return
        }
        wifiP2pManager = mWifiP2pManager
        wifiP2pChannel = wifiP2pManager.initialize(this, mainLooper, directActionListener)
        broadcastReceiver = DirectBroadcastReceiver(
            wifiP2pManager = wifiP2pManager,
            wifiP2pChannel = wifiP2pChannel,
            directActionListener = directActionListener
        )
        ContextCompat.registerReceiver(
            this,
            broadcastReceiver,
            DirectBroadcastReceiver.getIntentFilter(),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun initEvent() {
        lifecycleScope.launch {
            Log.d("Mylog-ReceiverActivity", "initEvent 실행")
            launch {
                fileReceiverViewModel.fileTransferViewState.collect {
                    when (it) {
                        FileTransferViewState.Idle -> {
                            Log.d("Mylog-ReceiverActivity", "FileTransferViewState.Idle")
                            clearLog()
                            dismissLoadingDialog()
                        }

                        FileTransferViewState.Connecting -> {
                            Log.d("Mylog-ReceiverActivity", "FileTransferViewState.Connecting")
                            showLoadingDialog(message = "")
                        }

                        is FileTransferViewState.Receiving -> {
                            Log.d("Mylog-ReceiverActivity", "FileTransferViewState.Receiving")
                            showLoadingDialog(message = "")
                        }

                        is FileTransferViewState.Failed -> {
                            Log.d("Mylog-ReceiverActivity", "FileTransferViewState.Failed")
                            dismissLoadingDialog()
                        }

                        else -> {}
                    }
                }
            }
            launch {
                fileReceiverViewModel.log.collect {
                    log(it)
                }
            }
            // 새로운 메시지 수신 이벤트 처리
            launch {
                fileReceiverViewModel.receivedMessage.collect { (message, senderAddress) ->
                    Log.d("Mylog-ReceiverActivity", "메시지 수신 성공")

                    // 수신된 메시지를 화면에 추가
                    receivedMessageCount += 1 // 수신된 메시지 개수도 함께 추적
                    val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()) //시간 스탬프 추가
                    val formattedMessage = "[$receivedMessageCount] from $senderAddress: $message at $timestamp"
                    ReceiveMessageLog.append("$formattedMessage\n\n")
                }
            }

        }
    }
    override fun onDestroy() {
        super.onDestroy()
        Log.d("Mylog-ReceiverActivity", "onDestroy 실행")
        if (broadcastReceiver != null) {
            unregisterReceiver(broadcastReceiver)
        }
        removeGroup()
    }

    @SuppressLint("MissingPermission")
    private fun createGroup() {
        lifecycleScope.launch {
            Log.d("Mylog-ReceiverActivity", "createGroup 실행")
            removeGroupIfNeed()
            wifiP2pManager.createGroup(wifiP2pChannel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d("Mylog-ReceiverActivity", "createGroup onSuccess")
                    val log = "createGroup onSuccess"
                    log(log = log)
                    showToast(message = log)
                }

                override fun onFailure(reason: Int) {
                    Log.d("Mylog-ReceiverActivity", "createGroup onFailure")
                    val log = "createGroup onFailure: $reason"
                    log(log = log)
                    showToast(message = log)
                }
            })
        }
    }

    private fun removeGroup() {
        lifecycleScope.launch {
            Log.d("Mylog-ReceiverActivity", "removeGroup 실행")
            removeGroupIfNeed()
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun removeGroupIfNeed() {
        Log.d("Mylog-ReceiverActivity", "removeGroupIfNeed 함수실행")
        return suspendCancellableCoroutine { continuation ->
            wifiP2pManager.requestGroupInfo(wifiP2pChannel) { group ->
                if (group == null) {
                    continuation.resume(value = Unit)
                } else {
                    wifiP2pManager.removeGroup(wifiP2pChannel,
                        object : WifiP2pManager.ActionListener {
                            override fun onSuccess() {
                                Log.d("Mylog-ReceiverActivity", "removeGroup onSuccess 실행")
                                val log = "removeGroup onSuccess"
                                log(log = log)
                                showToast(message = log)
                                continuation.resume(value = Unit)
                            }

                            override fun onFailure(reason: Int) {
                                Log.d("Mylog-ReceiverActivity", "removeGroup onFailure 실행")
                                val log = "removeGroup onFailure: $reason"
                                log(log = log)
                                showToast(message = log)
                                continuation.resume(value = Unit)
                            }
                        })
                }
            }
        }
    }

    private fun log(log: String) {
        tvLog.append(log)
        tvLog.append("\n\n")
    }

    private fun clearLog() {
        tvLog.text = ""
    }
}
