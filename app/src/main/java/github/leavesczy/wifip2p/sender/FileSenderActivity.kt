package github.leavesczy.wifip2p.sender

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.pm.PackageManager
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import github.leavesczy.wifip2p.BaseActivity
import github.leavesczy.wifip2p.DeviceAdapter
import github.leavesczy.wifip2p.DirectActionListener
import github.leavesczy.wifip2p.DirectBroadcastReceiver
import github.leavesczy.wifip2p.OnItemClickListener
import github.leavesczy.wifip2p.R
import github.leavesczy.wifip2p.common.FileTransferViewState
import github.leavesczy.wifip2p.utils.WifiP2pUtils
import github.leavesczy.wifip2p.receiver.FileReceiverViewModel
import kotlinx.coroutines.launch
import java.util.Locale
import android.util.Log
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.time.delay
import java.net.InetAddress
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.io.IOException
import android.os.Handler
import android.os.Looper
import java.io.BufferedReader
import java.io.InputStreamReader





/**
 * @Author: leavesCZY
 * @Desc:
 */
@SuppressLint("NotifyDataSetChanged")
class FileSenderActivity : BaseActivity() { // BaseActivity를 상속받음

    private val tvDeviceState by lazy { // lazy를 사용하여 필요할 때 초기화. 즉, 나중에 tvDeviceState가 호출되면 아래 함수 실행
        findViewById<TextView>(R.id.tvDeviceState) //  findViewById 메소드는 리소스 id를 통해서 레이아웃에 있는 뷰 객체들 중 일치하는 뷰를 가져오는 메소드
    }

    private val tvConnectionStatus by lazy {
        findViewById<TextView>(R.id.tvConnectionStatus)
    }

    private val btnDisconnect by lazy {
        findViewById<Button>(R.id.btnDisconnect)
    }


    private val btnChooseFile by lazy {
        findViewById<Button>(R.id.btnChooseFile)
    }

    private val rvDeviceList by lazy {
        findViewById<RecyclerView>(R.id.rvDeviceList)
    }

    private val tvLog by lazy {
        findViewById<TextView>(R.id.tvLog)
    }

    private val btnDirectDiscover by lazy {
        findViewById<Button>(R.id.btnDirectDiscover)
    }

    // 새로운 버튼 초기화 코드
    private val btnSendMessage by lazy {
        findViewById<Button>(R.id.btnSendMessage)

    }

    private val etMessageInput by lazy {
        findViewById<EditText>(R.id.etMessageInput)
    }

    // 메시지를 표시할 TextView 추가
    private val tvReceiveMessageLog by lazy {
        findViewById<TextView>(R.id.tvReceiveMessageLog)
    }
    // 메시지를 표시할 TextView 추가
    private val tvSendMessageLog by lazy {
        findViewById<TextView>(R.id.tvSendMessageLog)
    }
    //Unicast Button 추가
    /*private val btnSendUnicastMessage by lazy {
        findViewById<Button>(R.id.btnSendUnicastMessage)
    }*/
    //TimeInterval 추가
    private val etTargetTimeInterval by lazy {
        findViewById<EditText>(R.id.etTargetTimeInterval)
    }
    //TimeLasting 입력창 추가
    private val etTargetTimeRange by lazy {
        findViewById<EditText>(R.id.etTargetTimeRange)
    }
    //수신된 메시지 지우기 버튼
    private val btneraseReceiveMessageLog by lazy {
        findViewById<Button>(R.id.btneraseReceiveMessageLog)

    }
    //송신된 메시지 지우기 버튼
    private val btneraseSendMessageLog by lazy {
        findViewById<Button>(R.id.btneraseSendMessageLog)

    }

    //Tcp 전송 버튼
    private val btnSendTcpMessage by lazy {
        findViewById<Button>(R.id.btnSendTcpMessage)
    }

    //핑 보내기
    private val btnSendPing by lazy {
        findViewById<Button>(R.id.btnSendPing)

    }

    private  var devicename: String? = null

    private val fileSenderViewModel by viewModels<FileSenderViewModel>() // fileSenderViewModel 초기화, ViewModel은 UI데이터를 관리
    private val fileReceiverViewModel by viewModels<FileReceiverViewModel>() // 수신 기능을 위한 ViewModel 추가

    //Unicast를 위한 IP 입력창
    private val etTargetIpAddress by lazy {
        findViewById<EditText>(R.id.etTargetIpAddress)
    }
    private var sentMessageCount = 0 // 보낸 메시지 개수 추적
    private var receiveMessageCount = 0 // 받은 메시지 개수 추적

    //메시지 프로그램을 위한 번호 지정
    private var messageNumber:Int = 0
    //udp 수신 대기 버튼
    private val btnStartReceive by lazy {
        findViewById<Button>(R.id.btnStartReceive)
    }
    //tcp 수신 대기 버튼 추가
    private val  btnStartTCPReceive by lazy {
        findViewById<Button>(R.id. btnStartTCPReceive)
    }
    private var isUDPListening = false  // UDP 수신대기 상태를 추적
    private var isTCPListening = false  // TCP 수신대기 상태를 추적

    //Unicast로 메시지 프로그램화 하여 보내는 버튼
    private val btnSendUnicastProgMessage by lazy {
        findViewById<Button>(R.id.btnSendUnicastProgMessage)
    }






    private val imagePickerLauncher = // 나중에 파일 선택 버튼을 누르면 호출됨. 이미지 파일을 선택하기 위한 ActivityResultLauncher를 초기화, 선택한 이미지 파일의 URI를 받아서 파일 송신 작업을 시작
        registerForActivityResult(ActivityResultContracts.GetContent()) { imageUri ->
            if (imageUri != null) {
                val ipAddress = wifiP2pInfo?.groupOwnerAddress?.hostAddress
                log("getContentLaunch $imageUri $ipAddress")
                if (!ipAddress.isNullOrBlank()) {
                    fileSenderViewModel.send(ipAddress = ipAddress, fileUri = imageUri)
                }
            }
        }

    //WiFi P2P 관련 변수들을 초기화. 연결 가능한 디바이스 목록, 어댑터, 브로드캐스트 리시버, WiFi P2P 매니저와 채널, 연결 정보 등

    private val wifiP2pDeviceList = mutableListOf<WifiP2pDevice>()  // 연결 가능한 디바이스들 리스트

    private val deviceAdapter = DeviceAdapter(wifiP2pDeviceList)

    private var broadcastReceiver: BroadcastReceiver? = null

    private lateinit var wifiP2pManager: WifiP2pManager

    private lateinit var wifiP2pChannel: WifiP2pManager.Channel // 지연 초기화

    private var wifiP2pInfo: WifiP2pInfo? = null

    private var wifiP2pEnabled = false

    //ping의 핸들러
    private val handler = Handler(Looper.getMainLooper())
    private var isPinging = false
    private var ttl = 1  // TTL 값을 1로 시작
    private val maxHops = 30  // 최대 홉 수를 30으로 설정

    private val pingRunnable: Runnable = object : Runnable {
        override fun run() {
            if (isPinging && ttl <= maxHops) {
                Thread {
                    sendPing(etTargetIpAddress.text.toString())  // 핑을 보낼 IP 주소를 설정합니다.
                }.start()
                handler.postDelayed(this, 2000)  // 1초 후에 다시 실행
            }
        }
    }

    private fun getBroadcastAddress(ipAddress: String): String {
        // IP 주소를 .으로 분리
        Log.d("Mylog-SenderActivity", "getBroadcastAddress 실행")
        val parts = ipAddress.split(".")
        if (parts.size != 4) {
            throw IllegalArgumentException("Invalid IP address format")
        }
        // 마지막 옥텟을 255로 설정
        //임시로 192.168.49.~로 고정
        return "${parts[0]}.${parts[1]}.${parts[2]}.255"
    }
    //Unicast로 메시지 보내는 메소드
    private fun sendUnicastMessage(message: String, targetIpAddress: String) {
        Log.d("Mylog-SenderActivity", "sendUnicastMessage 실행")

        if (targetIpAddress.isNotBlank()) {
            fileSenderViewModel.sendMessage(ipAddress = targetIpAddress, message = message)

            // 송신된 메시지를 화면에 추가 (20글자까지만 표시)
            val truncatedMessage = if (message.length > 20) message.take(20) else message
            sentMessageCount += 1
            val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val formattedMessage = "Unicast [$sentMessageCount] $truncatedMessage at $timestamp"
            tvSendMessageLog.append("$formattedMessage\n\n")
        } else {
            Log.d("Mylog-SenderActivity", "sendUnicastMessage 실패")
            showToast("유효한 IP 주소를 입력하세요.")
        }
    }
    //tcp로 메시지 보내는 메소드
    private fun sendTcpMessage(message: String, targetIpAddress: String) {
        Log.d("Mylog-SenderActivity", "sendTcpMessage 실행")

        if (targetIpAddress.isNotBlank()) {
            fileSenderViewModel.sendMessage_TCP(ipAddress = targetIpAddress, message = message)

            // 송신된 메시지를 화면에 추가 (20글자까지만 표시)
            val truncatedMessage = if (message.length > 20) message.take(20) else message
            sentMessageCount += 1
            val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val formattedMessage = "TCP [$sentMessageCount] $truncatedMessage at $timestamp"
            tvSendMessageLog.append("$formattedMessage\n\n")
        } else {
            Log.d("Mylog-SenderActivity", "sendTcpMessage 실패")
            showToast("유효한 IP 주소를 입력하세요.")
        }
    }

    // 새로운 메시지 보내는 메소드 추가
    private fun sendMessage(message: String) {
        Log.d("Mylog-SenderActivity", "sendMessage 실행")
        //val ipAddress = wifiP2pInfo?.groupOwnerAddress?.hostAddress //Broadcast or Unicast를 하고 싶으면 UDP통신으로 변경해야함
        var ipAddress2 = wifiP2pInfo?.groupOwnerAddress?.hostAddress ?: ""
        //기기가 host라면 null이 입력됨
        Log.d("Mylog-SenderActivity", "if 전 $ipAddress2 입니다.")
        if (ipAddress2 == null || ipAddress2.isEmpty() || ipAddress2 == "") {
            ipAddress2 = getLocalIpAddress().toString()
        }
        Log.d("Mylog-SenderActivity", "if 후 $ipAddress2 입니다.")
        val ipAddress =  "192.168.49.255"//임시 수정 getBroadcastAddress(ipAddress2)
        Log.d("Mylog-SenderActivity", "broadcastAddress :$ipAddress 입니다.")
        if (!ipAddress.isNullOrBlank()) {

            Log.d("Mylog-SenderActivity", "sendMessage 성공")
            fileSenderViewModel.sendMessage(ipAddress = ipAddress, message = message)
            // 송신된 메시지를 화면에 추가 (20글자까지만 표시)
            val truncatedMessage = if (message.length > 20) message.take(20) else message
            sentMessageCount += 1
            val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val formattedMessage = "[$sentMessageCount] $truncatedMessage at $timestamp"
            tvSendMessageLog.append("$formattedMessage\n\n")
        } else {
            Log.d("Mylog-SenderActivity", "sendMessage 실패")
            showToast("IP 주소를 찾을 수 없습니다.")
        }
    }
    // 메시지를 N ms마다 N KB씩 N초 동안 전송하는 함수, Broadcast용
    private fun sendPeriodicMessages(message: String) {
        lifecycleScope.launch {
            val TargetTimeRange = etTargetTimeRange.text.toString() // EditText 객체로 반환 후 String으로 변환 후 Long으로 변환
            val TargetTimeInterval = etTargetTimeInterval.text.toString() // EditText 객체로 반환 후 String으로 변환 후 Long으로 변환
            if (TargetTimeRange.isBlank() || TargetTimeInterval.isBlank())
                sendMessage("$messageNumber: "+message)
            else{
                val totalDurationMs = TargetTimeRange.toLong()
                val intervalMs = TargetTimeInterval.toLong()
                val times = totalDurationMs / intervalMs // 100번 전송
                repeat(times.toInt()) {
                    ++messageNumber
                    sendMessage("$messageNumber: "+message)
                    delay(intervalMs)
                }
            }
        }
    }
    // 메시지를 N ms마다 N KB씩 N초 동안 전송하는 함수, UDP의 Unicast + TCP 용
    private fun sendPeriodicMessages(message: String, ipAddress: String, type: Int) {
        lifecycleScope.launch {
            val totalDurationMs = etTargetTimeRange.text.toString().toLong() // EditText 객체로 반환 후 String으로 변환 후 Long으로 변환
            val intervalMs = etTargetTimeInterval.text.toString().toLong() // EditText 객체로 반환 후 String으로 변환 후 Long으로 변환
            val times = totalDurationMs / intervalMs // 반복 횟수 계산

            repeat(times.toInt()) {
                ++messageNumber
                if (type == 0)
                    sendUnicastMessage("$messageNumber: " + message, ipAddress)
                else if (type ==1)
                    sendTcpMessage( "$messageNumber: " + message,ipAddress) // 새로운 메서드를 만들어서 송신쪽 메시지 창에도 추가되게 바꾸기
                delay(intervalMs)
            }
        }
    }


    // 60KB 크기의 문자열 생성 함수
    private fun generateLargeString(sizeInKB: Int): String {
        val sizeInBytes = sizeInKB * 1024 // KB를 바이트로 변환
        return "A".repeat(sizeInBytes)
    }

    // DirectActionListener 인터페이스를 구현하여 WiFi P2P 관련 이벤트를 처리
    private val directActionListener = object : DirectActionListener {

        // WiFi P2P 기능이 활성화되었는지 여부를 수신하여, wifiP2pEnabled 변수에 상태를 저장
        override fun wifiP2pEnabled(enabled: Boolean) {
            Log.d("Mylog-SenderActivity", "wifiP2pEnabled 함수실행")
            wifiP2pEnabled = enabled
        }

        //DirectActionListener 인터페이스에서 상속받아 재정의한 메서드.WiFi Direct 연결 정보가 사용 가능할 때 호출
        @SuppressLint("MissingPermission")
        override fun onConnectionInfoAvailable(wifiP2pInfo: WifiP2pInfo) {
            Log.d("Mylog-SenderActivity", "onConnectionInfoAvailable 실행")
            dismissLoadingDialog()
            wifiP2pDeviceList.clear()
            deviceAdapter.notifyDataSetChanged() // 어댑터에게 변경을 알림
            btnDisconnect.isEnabled = true // 연결 해제 버튼 활성화
            btnChooseFile.isEnabled = true // 파일 선택 버튼 활성화
            log("onConnectionInfoAvailable")
            log("onConnectionInfoAvailable groupFormed: " + wifiP2pInfo.groupFormed)
            log("onConnectionInfoAvailable isGroupOwner: " + wifiP2pInfo.isGroupOwner)
            log("onConnectionInfoAvailable getHostAddress: " + wifiP2pInfo.groupOwnerAddress.hostAddress) // 그룹 오너의 주소
            val localIpAddress = getLocalIpAddress()
            log("Local IP Address: $localIpAddress")



            // 추가: 그룹의 채널 정보를 요청
            wifiP2pManager.requestGroupInfo(wifiP2pChannel) { group ->
                if (group != null) {
                    val channel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        group.frequency
                        // 자신의 MAC 주소 가져오기
                        val selfDevice = group.clientList.firstOrNull { it.deviceAddress == group.owner.deviceAddress }
                            ?: group.owner
                        val selfMacAddress = selfDevice.deviceAddress ?: "MAC 주소를 가져올 수 없습니다."
                        // SSID (네트워크 이름) 가져오기
                        val groupSSID = group.networkName


                        val stringBuilder = StringBuilder()
                        stringBuilder.append("\n")
                        stringBuilder.append("그룹 주인인가요?：")
                        stringBuilder.append(if (wifiP2pInfo.isGroupOwner) "맞습니다" else "아닙니다")
                        stringBuilder.append("\n")
                        stringBuilder.append("그룹 주인의 IP 주소：")
                        stringBuilder.append(wifiP2pInfo.groupOwnerAddress.hostAddress)
                        stringBuilder.append("\n")


                        val localIpAddresses = getLocalIpAddresses()//Ip주소 리스트 받아오기
                        log("Local IP Addresses: $localIpAddresses")
                        // 모든 IP 주소를 출력
                        stringBuilder.append("내 디바이스의 IP 주소：")
                        for (ipAddress in localIpAddresses) {
                            stringBuilder.append("\n- ")
                            stringBuilder.append(ipAddress)
                        }


                        stringBuilder.append("\n")
                        stringBuilder.append("연결된 SSID：")
                        stringBuilder.append(groupSSID)  // SSID 표시
                        stringBuilder.append("\n")
                        stringBuilder.append("MAC 주소：")
                        stringBuilder.append(selfMacAddress)  // MAC 주소 표시
                        tvConnectionStatus.text = stringBuilder

                    } else {
                        TODO("VERSION.SDK_INT < Q")
                    }
                    Log.d("Mylog-SenderActivity", "그룹 채널: $channel Hz")
                } else {
                    Log.d("Mylog-SenderActivity", "그룹 정보가 없습니다.")
                }
            }





            // 그룹이 형성되었고 그룹 소유자가 아닌 경우에만 wifiP2pInfo를 저장
            if (wifiP2pInfo.groupFormed && !wifiP2pInfo.isGroupOwner) {
                this@FileSenderActivity.wifiP2pInfo = wifiP2pInfo
            }


        }
        //모든 로컬 IP 주소를 가져오는 함수
        fun getLocalIpAddresses(): List<String> {
            val ipAddresses = mutableListOf<String>()

            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                for (networkInterface in interfaces) {
                    val addresses = networkInterface.inetAddresses
                    for (address in addresses) {
                        if (!address.isLoopbackAddress && address is InetAddress) {
                            val hostAddress = address.hostAddress
                            if (hostAddress != null && !hostAddress.contains(":")) { // IPv4 주소만 필터링
                                ipAddresses.add(hostAddress)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            return ipAddresses
        }
        override fun onDisconnection() { // 연결 해제
            Log.d("Mylog-SenderActivity", "onDisconnection 실행")
            log("onDisconnection")

            btnDisconnect.isEnabled = false
            btnChooseFile.isEnabled = false
            wifiP2pDeviceList.clear()
            deviceAdapter.notifyDataSetChanged()
            tvConnectionStatus.text = null
            wifiP2pInfo = null
            showToast("연결 해제")
        }

        override fun onSelfDeviceAvailable(wifiP2pDevice: WifiP2pDevice) {
            Log.d("Mylog-SenderActivity", "onSelfDeviceAvailable 실행")
            log("onSelfDeviceAvailable")
            log("DeviceName: " + wifiP2pDevice.deviceName)
            log("Device BSSID: " + wifiP2pDevice.deviceAddress) //BSSID와 동일함
            log("Status: " + wifiP2pDevice.status)
            val log = "deviceName：" + wifiP2pDevice.deviceName + "\n" +
                    "deviceAddress：" + wifiP2pDevice.deviceAddress + "\n" +
                    "deviceStatus：" + WifiP2pUtils.getDeviceStatus(wifiP2pDevice.status)
            devicename = wifiP2pDevice.deviceName
            tvDeviceState.text = log
        }

        override fun onPeersAvailable(wifiP2pDeviceList: Collection<WifiP2pDevice>) { // 주변 p2p 장치가 사용 가능할 때
            Log.d("Mylog-SenderActivity", "onPeersAvailable 실행")
            log("onPeersAvailable :" + wifiP2pDeviceList.size) // 연결 가능한 장치 수
            this@FileSenderActivity.wifiP2pDeviceList.clear() // 디바이스 리스트 클리어
            this@FileSenderActivity.wifiP2pDeviceList.addAll(wifiP2pDeviceList) // 인자로 전달된 디바이스를 모두 리스트에 추가
            deviceAdapter.notifyDataSetChanged() // 어댑터에 변경 사항을 알림
            dismissLoadingDialog() // 로그 닫기
        }

        override fun onChannelDisconnected() {
            Log.d("Mylog-SenderActivity", "onChannelDisconnected 실행")
            log("onChannelDisconnected")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) { // onCreate 메서드는 activity가 생성될 때 호출
        super.onCreate(savedInstanceState) // 부모 클래스인 Activity의 onCreate 메서드를 호출
        Log.d("Mylog-SenderActivity", "onCreate 실행")
        setContentView(R.layout.activity_file_sender) // 송신자 화면을 띄움
        initView() // UI 초기화
        initDevice() // 디바이스 초기화
        initEvent() // 이벤트 초기화

    }
    // 버튼 상태를 업데이트하는 메소드
    private fun updateButtonStates() {
        if (isUDPListening) {
            btnStartReceive.setBackgroundColor(ContextCompat.getColor(this, R.color.blue))  // UDP 버튼을 파란색으로 설정
            btnStartTCPReceive.setBackgroundColor(ContextCompat.getColor(this, R.color.gray))  // TCP 버튼을 회색으로 설정
        } else if (isTCPListening) {
            btnStartTCPReceive.setBackgroundColor(ContextCompat.getColor(this, R.color.blue))  // TCP 버튼을 파란색으로 설정
            btnStartReceive.setBackgroundColor(ContextCompat.getColor(this, R.color.gray))  // UDP 버튼을 회색으로 설정
        } else {

            btnStartReceive.setBackgroundColor(ContextCompat.getColor(this, R.color.gray))  // UDP 버튼을 회색으로 설정
            btnStartTCPReceive.setBackgroundColor(ContextCompat.getColor(this, R.color.gray))  // TCP 버튼을 회색으로 설정
        }
    }

    @SuppressLint("MissingPermission")
    private fun initView() { // UI요소들 설정
        Log.d("Mylog-SenderActivity", "initView 실행")
        supportActionBar?.title = "송신자"
        btnDisconnect.setOnClickListener { // 연결 끊기 버튼을 리스너와 연결

            //버튼을 누른 시간 출력
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val currentTime = sdf.format(Date())

            log("$currentTime 연결 끊기 버튼 눌림")

            disconnect()
        }
        // 초기화 시 버튼 상태 설정
        updateButtonStates()

        // UDP 수신대기 버튼 클릭 리스너 설정
        btnStartReceive.setOnClickListener {
            if (!isUDPListening) {
                fileReceiverViewModel.startListener()  // UDP 수신 대기 시작
                isUDPListening = true
                isTCPListening = false
                Log.d("Mylog-SenderActivity", "UDP 수신대기 시작")
            } else {
                // 추가적으로 UDP 수신을 중단하는 로직이 필요할 수 있음
                fileReceiverViewModel.stopUDPListener()
                isUDPListening = false
                Log.d("Mylog-SenderActivity", "UDP 수신대기 중지")
            }
            updateButtonStates()  // 버튼 상태 업데이트
        }

        // TCP 수신대기 버튼 클릭 리스너 설정
        btnStartTCPReceive.setOnClickListener {
            if (!isTCPListening) {
                fileReceiverViewModel.startTCPListener()  // TCP 수신 대기 시작
                isTCPListening = true
                isUDPListening = false
                Log.d("Mylog-SenderActivity", "TCP 수신대기 시작")
            } else {
                // 추가적으로 TCP 수신을 중단하는 로직이 필요할 수 있음
                fileReceiverViewModel.stopTCPListener()
                isTCPListening = false
                Log.d("Mylog-SenderActivity", "TCP 수신대기 중지")
            }
            updateButtonStates()  // 버튼 상태 업데이트
        }


        btnChooseFile.setOnClickListener { // 파일 선택 버튼을 리스너와 연결

            //버튼을 누른 시간 출력
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val currentTime = sdf.format(Date())

            log("$currentTime 파일 선택 버튼 눌림")
            Log.d("Mylog-SenderActivity", "파일 선택 버튼 눌림")

            imagePickerLauncher.launch("image/*")
        }
        btnDirectDiscover.setOnClickListener { // 장치 검색 버튼을 리스너와 연결

            //버튼을 누른 시간 출력
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val currentTime = sdf.format(Date())

            log("$currentTime 장치 검색 버튼 눌림")
            Log.d("Mylog-SenderActivity", "장치 검색 버튼 눌림")
            //Wifi가 켜져있어도 인식을 못하고 계속 메시지가 발생하여 주석처리함
            /*if (!wifiP2pEnabled) {
                showToast("먼저 wifi를 켜야합니다")
                return@setOnClickListener
            }*/
            showLoadingDialog(message = "주변 장치 검색 중")
            wifiP2pDeviceList.clear() // 디바이스 리스트 클리어
            deviceAdapter.notifyDataSetChanged()
            wifiP2pManager.discoverPeers(wifiP2pChannel, object : WifiP2pManager.ActionListener { // 연결 가능한 디바이스 찾기
                override fun onSuccess() { // 검색 프로세스가 성공한 경우. 발견한 디바이스들에 대한 어떠한 정보도 제공하지 않음
                    Log.d("Mylog-SenderActivity", "장치 검색(discoverPeers) 성공")
                    showToast("discoverPeers Success")
                    dismissLoadingDialog()
                }

                override fun onFailure(reasonCode: Int) {
                    Log.d("Mylog-SenderActivity", "장치 검색(discoverPeers) 실패")
                    showToast("discoverPeers Failure：$reasonCode")
                    dismissLoadingDialog()
                }
            })
        }

        /*btnSendUnicastMessage.setOnClickListener {
            Log.d("Mylog-SenderActivity", "btnSendUnicastMessage 실행")
            val message = etMessageInput.text.toString()
            val targetIpAddress = etTargetIpAddress.text.toString()
            //각 메시지 버튼을 누를때마다 송수신 카운트 초기화
            receiveMessageCount = 0
            sentMessageCount = 0
            if (message.isNotBlank() && targetIpAddress.isNotBlank()) {
                sendUnicastMessage(message, targetIpAddress)
            } else {
                showToast("메시지와 IP 주소를 입력하세요")
            }
        }*/
        //TCP 메시지 전송 버튼
        btnSendTcpMessage.setOnClickListener{
            val largeMessage = generateLargeString(60)
            val targetIpAddress = etTargetIpAddress.text.toString()
            if (etTargetTimeInterval.text.toString().isBlank() || etTargetTimeRange.text.toString().isBlank())
                sendTcpMessage("$messageNumber: "+largeMessage,targetIpAddress)
            else
                sendPeriodicMessages(largeMessage, targetIpAddress, 1)

        }

        btnSendUnicastProgMessage.setOnClickListener{
            Log.d("Mylog-SenderActivity", "btnSendUnicastProgMessage 실행")
            val message = generateLargeString(60) // 60KB 크기의 메시지 생성
            val targetIpAddress = etTargetIpAddress.text.toString()
            val targettimeinterval = etTargetTimeInterval.text.toString()
            val targettimerange = etTargetTimeRange.text.toString()
            //각 메시지 버튼을 누를때마다 송수신 카운트 초기화
            receiveMessageCount = 0
            sentMessageCount = 0
            if (targetIpAddress.isNotBlank()) {
                // 메시지 간격이나 메시지 전송 지속시간이 비어있으면 한번만 전송
                if (targettimeinterval.isBlank() || targettimerange.isBlank())
                    sendUnicastMessage(message, targetIpAddress)
                else
                    sendPeriodicMessages(message, targetIpAddress,0) // 주기적으로 Unicast 메시지 전송
            } else {
                showToast("메시지와 유효한 IP 주소를 입력하세요.")
            }

        }
        deviceAdapter.onItemClickListener = object : OnItemClickListener { //검색된 장치 중 하나를 선택하면 연결
            override fun onItemClick(position: Int) {
                val wifiP2pDevice = wifiP2pDeviceList.getOrNull(position)

                //버튼을 누른 시간 출력
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val currentTime = sdf.format(Date())



                if (wifiP2pDevice != null) {
                    Log.d("Mylog-SenderActivity", "장치 선택")
                    log("$currentTime 디바이스 선택: " + wifiP2pDevice.deviceName)
                    connect(wifiP2pDevice = wifiP2pDevice) //연결 메서드. 아래에 있음
                }
            }
        }
        //레이아웃 매니저를 사용하여 아이템 배치 방향과 스크롤 가능 여부를 설정하는 역할
        rvDeviceList.adapter = deviceAdapter
        // RecyclerView의 레이아웃 매니저를 설정. LinearLayoutManager는 세로 방향으로 아이템을 배치하는 레이아웃 매니저
        rvDeviceList.layoutManager = object : LinearLayoutManager(this) {
            override fun canScrollVertically(): Boolean { // 스크롤 관련 메서드
                return false
            }
        }
        btnSendMessage.setOnClickListener {
            //각 메시지 버튼을 누를때마다 송수신 카운트 초기화
            receiveMessageCount = 0
            sentMessageCount = 0
            val largeMessage = generateLargeString(60)
            sendPeriodicMessages(largeMessage)

        }
        btneraseReceiveMessageLog.setOnClickListener {
            tvReceiveMessageLog.text = ""
            receiveMessageCount = 0
            Log.d("Mylog-SenderActivity", "수신된 메시지 지우기 버튼 눌림")
        }


        btneraseSendMessageLog.setOnClickListener {
            tvSendMessageLog.text = ""
            sentMessageCount = 0
            Log.d("Mylog-SenderActivity", "송신된 메시지 지우기 버튼 눌림")
        }

        //핑을 보내는 버튼 리스너
        btnSendPing.setOnClickListener {
            if (isPinging) {
                // 핑 중지
                isPinging = false
                handler.removeCallbacks(pingRunnable)
                btnSendPing.setBackgroundColor(ContextCompat.getColor(this, R.color.gray))
                Log.d("Mylog-SenderActivity", "Pinging stopped.")
            } else {
                // 핑 시작
                isPinging = true
                handler.post(pingRunnable)
                btnSendPing.setBackgroundColor(ContextCompat.getColor(this, R.color.blue))
                Log.d("Mylog-SenderActivity", "Pinging started.")
            }
        }




    }

    private fun initDevice() {
        val mWifiP2pManager = getSystemService(WIFI_P2P_SERVICE) as? WifiP2pManager // 시스템에서 WiFi P2P 관리자를 가져옴
        Log.d("Mylog-SenderActivity", "initDevice 실행")
        if (mWifiP2pManager == null) {
            finish() //WiFi P2P 관리자가 null인 경우, 액티비티를 종료
            return
        }
        wifiP2pManager = mWifiP2pManager // 가져온 WiFi P2P 관리자를 인스턴스 변수에 할당
        wifiP2pChannel = mWifiP2pManager.initialize(this, mainLooper, directActionListener)
        broadcastReceiver = // WiFi Direct 이벤트를 처리할 브로드캐스트 리시버를 생성
            DirectBroadcastReceiver(mWifiP2pManager, wifiP2pChannel, directActionListener)
        ContextCompat.registerReceiver( // 브로드캐스트 리시버 등록
            this,
            broadcastReceiver,
            DirectBroadcastReceiver.getIntentFilter(),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun initEvent() {
        lifecycleScope.launch {
            Log.d("Mylog-SenderActivity", "initEvent 실행")
            launch {
                fileSenderViewModel.fileTransferViewState.collect {
                    when (it) {
                        FileTransferViewState.Idle -> {
                            clearLog()
                            dismissLoadingDialog()
                        }

                        FileTransferViewState.Connecting -> {
                            showLoadingDialog(message = "")
                        }

                        is FileTransferViewState.Receiving -> {
                            showLoadingDialog(message = "")
                        }

                        is FileTransferViewState.Success -> {
                            dismissLoadingDialog()
                        }

                        is FileTransferViewState.Failed -> {
                            dismissLoadingDialog()
                        }

                        // 메시지 전송 성공 시 처리
                        is FileTransferViewState.MessageReceived -> {
                            Log.d("Mylog-SenderActivity", "FileTransferViewState.MessageReceived")
                            dismissLoadingDialog()
                        }
                    }
                }
            }

            launch {
                fileSenderViewModel.log.collect {
                    log(it)
                }
            }

            launch {
                fileReceiverViewModel.receivedMessage.collect { (message, senderAddress) ->
                    Log.d("Mylog-SenderActivity", "메시지 수신 성공")

                    // 수신된 메시지를 화면에 추가
                    receiveMessageCount += 1
                    val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    val formattedMessage = "[$receiveMessageCount] $message from $senderAddress  ($timestamp)"
                    tvReceiveMessageLog.append("$formattedMessage\n\n")
                }
            }
        }
    }


    override fun onDestroy() { // activity가 종료될 때 실행
        super.onDestroy()
        Log.d("Mylog-SenderActivity", "onDestroy 실행")
        if (broadcastReceiver != null) {
            //unregisterReceiver() 메서드를 호출하여 액티비티에 등록된 브로드캐스트 리시버를 등록 해제. 이는 메모리 누수를 방지하고, 리소스 사용을 최적화하는 데 도움
            unregisterReceiver(broadcastReceiver)
        }
        //tcp,udp 수신대기 중지
        fileReceiverViewModel.stopTCPListener()
        fileReceiverViewModel.stopUDPListener()
        // Activity가 종료될 때 핑 작업을 취소합니다.
        handler.removeCallbacks(pingRunnable)
    }

    @SuppressLint("MissingPermission")
    private fun connect(wifiP2pDevice: WifiP2pDevice) { // WifiP2pDevice 객체를 매개변수로 받아 WiFi P2P 연결을 시도
        Log.d("Mylog-SenderActivity", "connect 실행")
        val wifiP2pConfig = WifiP2pConfig() // connect() 메서드 호출은 연결하는 디바이스의 정보를 포함하는 WifiPwpConfig 객체가 필요
        wifiP2pConfig.deviceAddress = wifiP2pDevice.deviceAddress // 연결하려는 디바이스의 주소(MAC)를 설정
        wifiP2pConfig.wps.setup = WpsInfo.PBC // WpsInfo 객체의 setup 속성을 PBC로 설정. 아마 버튼으로 연결 수락하는 설정인 것으로 추정
        showLoadingDialog(message = "연결중，deviceName: " + wifiP2pDevice.deviceName) // 연결 시도 중임을 알리는 로딩 다이얼로그를 표시
        showToast("연결중，deviceName: " + wifiP2pDevice.deviceName) // 연결 시도 중임을 알리는 토스트 메시지를 표시
        wifiP2pManager.connect( //wifiP2pManager를 사용하여 WiFi P2P 연결을 시도. connect 메서드는 wifiP2pChannel, wifiP2pConfig, 그리고 ActionListener를 매개변수로 받음
            wifiP2pChannel,
            wifiP2pConfig,
            object : WifiP2pManager.ActionListener {
                override fun onSuccess() { // ActionListener 인터페이스의 onSuccess 메서드는 연결 시도에 성공했을 때 호출
                    Log.d("Mylog-SenderActivity", "connect 성공")
                    log("connect onSuccess")
                }

                override fun onFailure(reason: Int) { // ActionListener 인터페이스의 onFailure 메서드는 연결 시도에 실패했을 때 호출
                    Log.d("Mylog-SenderActivity", "connect 실패")
                    showToast("연결 실패 $reason")
                    dismissLoadingDialog()
                }
            }
        )
    }

    private fun disconnect() {
        //wifiP2pManager를 사용하여 현재 진행 중인 연결 시도를 취소. cancelConnect 메서드는 wifiP2pChannel과 ActionListener를 매개변수로 받음.
        Log.d("Mylog-SenderActivity", "disconnect 실행")
        wifiP2pManager.cancelConnect(wifiP2pChannel, object : WifiP2pManager.ActionListener {
            override fun onFailure(reasonCode: Int) { // ActionListener 인터페이스의 onFailure 메서드는 연결 취소 시도가 실패했을 때 호출
                Log.d("Mylog-SenderActivity", "disconnect 활성")
                log("cancelConnect onFailure:$reasonCode")
            }

            override fun onSuccess() { //ActionListener 인터페이스의 onSuccess 메서드는 연결 취소 시도가 성공했을 때 호출
                Log.d("Mylog-SenderActivity", "disconnect 성공")
                log("cancelConnect onSuccess")
                tvConnectionStatus.text = null // 연결 상태를 표시하는 TextView의 텍스트를 지워 연결 상태가 초기화되었음을 나타냄
                btnDisconnect.isEnabled = false // 연결 끊기 버튼 비활성화
                btnChooseFile.isEnabled = false // 파일 선택 버튼 비활성화
            }
        })
        wifiP2pManager.removeGroup(wifiP2pChannel, null) // 그룹을 제거하는 메서드
        //하지만 실제 동작에서는 연결 끊기를 눌러도 수신자의 그룹이 사리지지는 않음
    }

    private fun log(log: String) {
        tvLog.append(log)
        tvLog.append("\n\n")
    }

    private fun clearLog() {
        Log.d("Mylog-SenderActivity", "clearLog 실행")
        tvLog.text = ""
    }
    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (networkInterface in interfaces) {
                val addresses = networkInterface.inetAddresses
                for (address in addresses) {
                    if (!address.isLoopbackAddress && address is InetAddress) {
                        val hostAddress = address.hostAddress
                        if (hostAddress != null && hostAddress.contains(":").not()) { // 필터링하여 IPv4 주소만 얻음
                            return hostAddress
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }


    //핑을 보내고 받아서 시간 간격을 측정함
    private fun sendPing(ipAddress: String) {
        val traceStartTime = System.currentTimeMillis()  // Traceroute 시작 시간 기록

        try {
            val runtime = Runtime.getRuntime()
            val cmd = "ping -t $ttl -c 1 -w 1 $ipAddress"
            val startTime = System.currentTimeMillis()  // 핑 전송 시작 시간 기록
            val process = runtime.exec(cmd)

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            var hopIp: String? = null
            var trSuccess = true

            while (reader.readLine().also { line = it } != null) {
                when {
                    line!!.contains("Time to live exceeded") -> {
                        val hopIpArray = line!!.split(" ")
                        hopIp = hopIpArray[1]
                        Log.d("DEBUG", "HOP $ttl : $hopIp")
                    }
                    line!!.contains("icmp_seq") -> {
                        val endTime = System.currentTimeMillis()  // 핑 응답 수신 시간 기록
                        val duration = endTime - startTime  // 핑 전송 및 응답 시간 계산
                        Log.d("DEBUG", "END $ttl : $ipAddress, Time: ${duration}ms")
                        trSuccess = false
                        break
                    }
                    line!!.contains("1 packets transmitted, 0 received") -> {
                        Log.d("DEBUG", "$ttl Hop : NULL")
                        hopIp = "NULL"
                    }
                }
            }

            val exitCode = process.waitFor()
            if (exitCode == 0) {
                if (trSuccess) {
                    ttl++
                } else {
                    val traceEndTime = System.currentTimeMillis()  // Traceroute 종료 시간 기록
                    val totalTraceTime = traceEndTime - traceStartTime  // 전체 Traceroute 시간 계산
                    Log.d("Mylog-SenderActivity", "Traceroute completed to $ipAddress, Total Time: ${totalTraceTime}ms")
                }
            } else {
                Log.e("Mylog-SenderActivity", "Ping failed with exit code $exitCode")
            }

        } catch (e: IOException) {
            Log.e("Mylog-SenderActivity", "Ping failed: ${e.message}")
        } catch (e: InterruptedException) {
            Log.e("Mylog-SenderActivity", "Ping was interrupted: ${e.message}")
        }
    }



}
