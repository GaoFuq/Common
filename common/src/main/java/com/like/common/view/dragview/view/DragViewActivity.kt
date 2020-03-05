package com.like.common.view.dragview.view

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.like.common.R
import com.like.common.view.dragview.entity.DragInfo

class DragViewActivity : AppCompatActivity() {
    companion object {
        const val KEY_CUR_CLICK_POSITION = "key_cur_click_position"
        const val KEY_DATA_FOR_PREVIEW_IMAGE = "key_data_for_preview_image"
        const val KEY_DATA_FOR_PREVIEW_VIDEO = "key_data_for_preview_video"
    }

    private var view: BaseDragView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = ContextCompat.getColor(this, R.color.common_transparent)
        }

        try {
            if (intent.hasExtra(KEY_DATA_FOR_PREVIEW_IMAGE)) {
                val infos: List<DragInfo>? = intent.getParcelableArrayListExtra(KEY_DATA_FOR_PREVIEW_IMAGE)
                infos?.let {
                    val curClickPosition = intent.getIntExtra(KEY_CUR_CLICK_POSITION, 0)
                    view = DragPhotoView(this, it, curClickPosition)
                }
            } else if (intent.hasExtra(KEY_DATA_FOR_PREVIEW_VIDEO)) {
                val info: DragInfo? = intent.getParcelableExtra(KEY_DATA_FOR_PREVIEW_VIDEO)
                info?.let {
                    view = DragVideoView(this, it)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        view?.let {
            setContentView(it)
        }
    }

    override fun onBackPressed() {
        view?.disappear()
    }
}