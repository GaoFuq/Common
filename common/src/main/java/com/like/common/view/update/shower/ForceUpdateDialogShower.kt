package com.like.common.view.update.shower

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.os.Bundle
import android.support.v4.app.FragmentManager
import android.view.KeyEvent
import android.view.View
import com.like.common.R
import com.like.common.application.BaseApplication
import com.like.common.databinding.DialogFragmentDownloadProgressBinding
import com.like.common.util.BaseDialogFragment
import com.like.common.util.show
import com.like.common.util.toDataStorageUnit
import com.like.common.view.update.TAG_CONTINUE
import com.like.common.view.update.TAG_PAUSE
import com.like.livedatabus.LiveDataBus
import com.like.retrofit.util.getCustomNetworkMessage

/**
 * 强制更新使用对话框显示进度条
 */
class ForceUpdateDialogShower(private val fragmentManager: FragmentManager) : Shower {
    private val downloadProgressDialog = DefaultDownloadProgressDialog()

    override fun onDownloadPending() {
        downloadProgressDialog.show(fragmentManager)
        downloadProgressDialog.setTitle("正在连接服务器...")
        downloadProgressDialog.setMessage("")// 避免中途网络断开，然后重新连接后点击继续时，错误信息还是存在
    }

    override fun onDownloadRunning(currentSize: Long, totalSize: Long) {
        downloadProgressDialog.setTitle("下载中，请稍后...")
        downloadProgressDialog.setProgress(currentSize, totalSize)
    }

    override fun onDownloadPaused(currentSize: Long, totalSize: Long) {
        downloadProgressDialog.setTitle("已经暂停下载")
        downloadProgressDialog.setProgress(currentSize, totalSize)
    }

    override fun onDownloadSuccessful(totalSize: Long) {
        downloadProgressDialog.dismissAllowingStateLoss()
        (downloadProgressDialog.context?.applicationContext as? BaseApplication)?.exitApp()
    }

    override fun onDownloadFailed(throwable: Throwable?) {
        downloadProgressDialog.setTitle("下载失败！")
        downloadProgressDialog.setMessage(throwable.getCustomNetworkMessage())
    }

    class DefaultDownloadProgressDialog : BaseDialogFragment<DialogFragmentDownloadProgressBinding>() {

        override fun getDialogFragmentLayoutResId(): Int = R.layout.dialog_fragment_download_progress

        override fun initData(arguments: Bundle?) {
            mBinding?.also {
                it.btnPause.setOnClickListener {
                    LiveDataBus.post(TAG_PAUSE)
                }
                it.btnContinue.setOnClickListener {
                    LiveDataBus.post(TAG_CONTINUE)
                }
                it.ivClose.setOnClickListener {
                    LiveDataBus.post(TAG_PAUSE)
                    dismissAllowingStateLoss()
                    (context?.applicationContext as? BaseApplication)?.exitApp()
                }
            }
            // 屏蔽返回键
            dialog.setOnKeyListener(DialogInterface.OnKeyListener { dialog, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    return@OnKeyListener true
                }
                false
            })
        }

        @SuppressLint("SetTextI18n")
        fun setProgress(currentSize: Long, totalSize: Long) {
            mBinding?.apply {
                val progress = Math.round(currentSize.toFloat() / totalSize.toFloat() * 100)
                pbProgress.progress = progress
                tvPercent.text = "$progress%"
                tvSize.text = "${currentSize.toDataStorageUnit()}/${totalSize.toDataStorageUnit()}"
            }
        }

        fun setTitle(title: String) {
            mBinding?.apply {
                tvTitle.text = title
            }
        }

        fun setMessage(msg: String) {
            mBinding?.apply {
                tvMessage.visibility = if (msg.isEmpty()) View.GONE else View.VISIBLE
                tvMessage.text = msg
            }
        }

    }
}