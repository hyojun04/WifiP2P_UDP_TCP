package github.leavesczy.wifip2p.common

import java.io.Serializable

/**
 * @Author: leavesCZY
 * @Date: 2024/4/1 11:18
 * @Desc:
 */
data class FileTransfer(val fileName: String) : Serializable

sealed class FileTransferViewState {
    object Idle : FileTransferViewState()
    object Connecting : FileTransferViewState()
    object Receiving : FileTransferViewState()
    data class Success(val file: Any) : FileTransferViewState()
    data class Failed(val throwable: Throwable) : FileTransferViewState()
    object MessageReceived : FileTransferViewState() // 메시지 수신 상태 추가
}
