package github.leavesczy.wifip2p.utils

import android.net.wifi.p2p.WifiP2pDevice

/**
 * @Author: leavesCZY
 * @Desc:
 */
object WifiP2pUtils {

    fun getDeviceStatus(deviceStatus: Int): String {
        return when (deviceStatus) {
            WifiP2pDevice.AVAILABLE -> "사용 가능한"
            WifiP2pDevice.INVITED -> "초대중"
            WifiP2pDevice.CONNECTED -> "연결됨"
            WifiP2pDevice.FAILED -> "실패"
            WifiP2pDevice.UNAVAILABLE -> "사용할 수 없는"
            else -> "알 수 없는"
        }
    }

}