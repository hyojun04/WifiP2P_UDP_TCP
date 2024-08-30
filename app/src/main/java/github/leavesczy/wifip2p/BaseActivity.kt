package github.leavesczy.wifip2p

import android.app.Activity
import android.app.ProgressDialog
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * @Author: leavesCZY
 * @Desc:
 */
open class BaseActivity : AppCompatActivity() {

    private var loadingDialog: ProgressDialog? = null

    protected fun showLoadingDialog(message: String = "", cancelable: Boolean = true) {
        Log.d("Mylog-BaseActivity","showLoadingDialog시작")
        loadingDialog?.dismiss()
        loadingDialog = ProgressDialog(this).apply {
            setMessage(message)
            setCancelable(cancelable)
            setCanceledOnTouchOutside(false)
            show()
        }
    }

    protected fun dismissLoadingDialog() {
        Log.d("Mylog-BaseActivity","dismissLoadingDialog시작")
        loadingDialog?.dismiss()
        loadingDialog = null
    }

    protected fun showToast(message: String) {
        Log.d("Mylog-BaseActivity","권한 확인 누름")
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    protected fun <T : Activity> startActivity(clazz: Class<T>) {
        Log.d("Mylog-BaseActivity","startActivity시작")
        startActivity(Intent(this, clazz))
    }

}

