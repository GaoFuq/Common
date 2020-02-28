package com.like.common.view.toolbar.custom

import android.content.Context
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.appcompat.widget.ActionMenuView
import androidx.appcompat.widget.Toolbar
import androidx.databinding.DataBindingUtil
import com.like.common.R
import com.like.common.databinding.ToolbarCustomViewBinding

/**
 * [Toolbar]中的自定义视图管理类
 */
class CustomViewManager(context: Context, binding: ToolbarCustomViewBinding? = null) {
    private val mBinding: ToolbarCustomViewBinding by lazy {
        binding ?: DataBindingUtil.inflate<ToolbarCustomViewBinding>(
                LayoutInflater.from(context),
                R.layout.toolbar_custom_view,
                null, false
        )
    }

    /**
     * 获取自定义视图
     */
    fun getRootView(): View = mBinding.root

    /**
     * 获取自定义视图的第一层孩子根目录，用于配合[com.like.common.view.badgeview.BadgeView]来显示消息并调整其位置
     */
    fun getContentView(): View = mBinding.cl

    /**
     * 设置自定义视图的内容的 padding
     *
     * 自定义视图的root为第一层，那么真正的内容在第二层显示，
     * 这里其实是设置第二层的margin，用于配合[com.like.common.view.badgeview.BadgeView]来显示消息并调整其位置
     */
    fun setContentPadding(left: Int = 0, top: Int = 0, right: Int = 0, bottom: Int = 0) {
        mBinding.cl.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT)
                .apply {
                    leftMargin = left
                    topMargin = top
                    rightMargin = right
                    bottomMargin = bottom
                }
    }

    /**
     * 设置自定义视图的 margin
     */
    fun setMargin(left: Int = 0, top: Int = 0, right: Int = 0, bottom: Int = 0) {
        val layoutParams = mBinding.root.layoutParams
        if (layoutParams is Toolbar.LayoutParams) {// 如果是NavigationView：Toolbar.LayoutParams
            mBinding.root.layoutParams = Toolbar.LayoutParams(Toolbar.LayoutParams.WRAP_CONTENT, Toolbar.LayoutParams.MATCH_PARENT)
                    .apply {
                        leftMargin = left
                        topMargin = top
                        rightMargin = right
                        bottomMargin = bottom
                    }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && layoutParams is ActionMenuView.LayoutParams) {// 如果是Menu：ActionMenuView.LayoutParams
            mBinding.root.layoutParams = ActionMenuView.LayoutParams(ActionMenuView.LayoutParams.WRAP_CONTENT, ActionMenuView.LayoutParams.MATCH_PARENT)
                    .apply {
                        leftMargin = left
                        topMargin = top
                        rightMargin = right
                        bottomMargin = bottom
                    }
        }
    }

    /**
     * 设置自定义视图点击监听
     *
     * @param clickListener     点击监听。默认为null，表示取消监听。
     */
    fun setOnClickListener(clickListener: View.OnClickListener? = null) {
        if (clickListener == null) {
            mBinding.root.setOnClickListener(null)
        } else {
            mBinding.root.setOnClickListener { view -> clickListener.onClick(view) }
        }
    }

    /**
     * 设置自定义视图的文本
     *
     * @param title             文本
     * @param textColor         文本颜色。默认为null，表示不设置，保持原样。
     * @param textSize          文本字体大小。默认为null，表示不设置，保持原样。
     */
    fun setTitle(title: String, @ColorInt textColor: Int? = null, textSize: Float? = null) {
        if (title.isEmpty()) {
            mBinding.tvTitle.visibility = View.GONE
            mBinding.tvTitle.text = ""
        } else {
            mBinding.tvTitle.visibility = View.VISIBLE
            mBinding.tvTitle.text = title
            if (textColor != null) {
                mBinding.tvTitle.setTextColor(textColor)
            }
            if (textSize != null) {
                mBinding.tvTitle.textSize = textSize
            }
        }
    }

    fun getTitle(): String {
        return mBinding.tvTitle.text.toString()
    }

    /**
     * 设置自定义视图的文本
     *
     * @param iconResId         图标资源id。如果设置为0，表示去掉图标。
     */
    fun setIcon(@DrawableRes iconResId: Int) {
        if (iconResId == 0) {
            mBinding.iv.visibility = View.GONE
            mBinding.iv.setImageDrawable(null)
        } else {
            mBinding.iv.visibility = View.VISIBLE
            mBinding.iv.setImageResource(iconResId)
        }
    }
} 