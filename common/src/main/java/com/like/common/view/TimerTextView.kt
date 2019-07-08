package com.like.common.view

import android.arch.lifecycle.Lifecycle
import android.arch.lifecycle.LifecycleObserver
import android.arch.lifecycle.LifecycleOwner
import android.arch.lifecycle.OnLifecycleEvent
import android.content.Context
import android.support.v7.widget.AppCompatTextView
import android.util.AttributeSet
import com.like.common.util.DelegateSharedPreferences
import java.util.*

/**
 * 显示倒计时的AppCompatTextView
 *
 * 倒计时不会因为关闭app而停止。重新打开后会继续倒计时。
 *
 * 在xml布局文件中直接使用。然后调用start()方法启动倒计时
 */
class TimerTextView(context: Context, attrs: AttributeSet?) : AppCompatTextView(context, attrs) {
    /**
     * 倒计时总时长(毫秒)
     */
    private var totalTime: Long by DelegateSharedPreferences(
            context,
            "${context.packageName}${this::class.java.simpleName}",
            "totalTime",
            0L
    )
    /**
     * 开始倒计时的时间（毫秒），用于退出界面后，重新计时时候的计算。
     */
    private var startTime: Long by DelegateSharedPreferences(
            context,
            "${context.packageName}${this::class.java.simpleName}",
            "startTime",
            0L
    )
    /**
     * 倒计时的步长（毫秒）
     */
    private var step: Long by DelegateSharedPreferences(
            context,
            "${context.packageName}${this::class.java.simpleName}",
            "step",
            1000L
    )
    private var remainingTime: Long = 0L// 剩余时长(毫秒)
    private var timer: Timer? = null
    private var timerTask: TimerTask? = null
    private var tickListener: OnTickListener? = null

    init {
        if (context is LifecycleOwner) {
            (context as LifecycleOwner).lifecycle.addObserver(object : LifecycleObserver {
                @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
                fun onDestroy() {
                    destroy()
                }
            })
        }
    }

    /**
     * 开始倒计时
     *
     * @param length 倒计时总时长，毫秒
     * @param stepTime 倒计时的步长，毫秒
     */
    fun start(length: Long, stepTime: Long = 1000L) {
        if (length <= 0 || stepTime <= 0 || length < stepTime) return

        timer = Timer()
        timerTask = object : TimerTask() {
            override fun run() {
                post {
                    when {
                        remainingTime == totalTime -> {
                            this@TimerTextView.isEnabled = false
                            tickListener?.onStart(remainingTime)
                        }
                        remainingTime < step -> {
                            this@TimerTextView.isEnabled = true
                            tickListener?.onEnd()
                            destroy()
                        }
                        else -> {
                            this@TimerTextView.isEnabled = false
                            tickListener?.onTick(remainingTime)
                        }
                    }
                    remainingTime -= step
                }
            }
        }

        val passTime = System.currentTimeMillis() - startTime// 上次开始倒计时到现在的时间间隔
        remainingTime = totalTime - passTime// 上次剩余的时长
        if (remainingTime < step) {// 上次时间已经走完了
            remainingTime = length
            totalTime = length
            step = stepTime
            startTime = System.currentTimeMillis()
        }
        timer?.schedule(timerTask, 0, step)
    }

    fun destroy() {
        timerTask?.cancel()
        timerTask = null
        timer?.cancel()
        timer = null
    }

    /**
     * @param tickListener 倒计时回调
     */
    fun setOnTickListener(tickListener: OnTickListener) {
        this.tickListener = tickListener
    }

    interface OnTickListener {
        fun onStart(time: Long)
        fun onTick(time: Long)
        fun onEnd()
    }

}