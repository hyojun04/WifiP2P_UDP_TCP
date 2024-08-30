package github.leavesczy.wifip2p

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import github.leavesczy.wifip2p.receiver.FileReceiverActivity
import github.leavesczy.wifip2p.sender.FileSenderActivity

/**
 * @Author: CZY
 * @Date: 2022/9/28 14:24
 * @Desc:
 */
class MainActivity : BaseActivity() {

    private val requestedPermissions = buildList {
        Log.d("Mylog-MainActivity","requestPermisons시작")
        add(Manifest.permission.ACCESS_NETWORK_STATE)
        add(Manifest.permission.CHANGE_NETWORK_STATE)
        add(Manifest.permission.ACCESS_WIFI_STATE)
        add(Manifest.permission.CHANGE_WIFI_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }.toTypedArray()

    private val requestPermissionLaunch = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { it ->
        if (it.all { it.value }) {
            showToast("모든 권한 획득")
        } else {
            onPermissionDenied()
        }
        Log.d("Mylog-MainActivity","requestPermisionLanch끝")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("Mylog-MainActivity","oncreate시작")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<View>(R.id.btnCheckPermission).setOnClickListener {
            requestPermissionLaunch.launch(requestedPermissions)
        }
        findViewById<View>(R.id.btnSender).setOnClickListener {
            Log.d("Mylog-MainActivity","파일 송신기 클릭")
            if (allPermissionGranted()) {
                startActivity(FileSenderActivity::class.java)
            } else {
                onPermissionDenied()
            }
        }
        findViewById<View>(R.id.btnReceiver).setOnClickListener {
            Log.d("Mylog-MainActivity","파일 수신기 클릭")
            if (allPermissionGranted()) {
                startActivity(FileReceiverActivity::class.java)
            } else {
                onPermissionDenied()
            }
        }
    }

    private fun onPermissionDenied() {
        Log.d("Mylog-MainActivity","onPermissionDenied 권한 부족 알림")
        showToast("권한이 부족합니다. 먼저 권한을 부여해 주세요.")
    }

    private fun allPermissionGranted(): Boolean {
        Log.d("Mylog-MainActivity","allPermisiionGranted 권환 획득")
        requestedPermissions.forEach {
            if (ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

}

