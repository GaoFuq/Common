package com.like.common.view.banner.indicator

import android.content.Context
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.viewpager.widget.ViewPager

/**
 * 小圆点指示器
 *
 * @param mContext
 * @param mCount                    指示器的数量
 * @param mIndicatorContainer       小圆点的容器
 * @param mIndicatorPadding         小圆点之间的间隔
 * @param mNormalIndicatorResId     正常状态的小圆点图片资源id
 * @param mSelectedIndicatorResIds  选中状态的小圆点图片资源id，可以为多个，比如每个选中状态对应一种颜色。
 */
class DotIndicator(
        private val mContext: Context,
        private val mCount: Int,
        private val mIndicatorContainer: LinearLayout,
        private val mIndicatorPadding: Int,
        @DrawableRes private val mNormalIndicatorResId: Int,
        private val mSelectedIndicatorResIds: List<Int>
) : ViewPager.OnPageChangeListener {
    private var mPreSelectedPosition = 0

    init {
        require(mIndicatorPadding > 0) { "mIndicatorPadding 必须大于0" }
        require(mCount > 0) { "mCount 必须大于0" }
        require(mSelectedIndicatorResIds.isNotEmpty()) { "mSelectedIndicatorResIds 不能为空" }
        mSelectedIndicatorResIds.forEach {
            require(it > 0) { "mSelectedIndicatorResIds 中的小圆点图片资源 id 无效" }
        }

        mIndicatorContainer.removeAllViews()
        for (i in 0 until mCount) {
            // 加载指示器图片
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)// 设置指示器宽高
            val iv = ImageView(mContext)
            if (i == 0) {
                iv.setBackgroundResource(mSelectedIndicatorResIds[0])
                params.setMargins(0, 0, 0, 0)// 设置指示器边距
            } else {
                iv.setBackgroundResource(mNormalIndicatorResId)
                params.setMargins(mIndicatorPadding, 0, 0, 0)// 设置指示器边距
            }
            iv.layoutParams = params
            mIndicatorContainer.addView(iv)
        }
    }

    override fun onPageSelected(position: Int) {
        mIndicatorContainer.getChildAt(mPreSelectedPosition).setBackgroundResource(mNormalIndicatorResId)
        val selectResId = if (position >= mSelectedIndicatorResIds.size) {
            mSelectedIndicatorResIds[mSelectedIndicatorResIds.size - 1]
        } else {
            mSelectedIndicatorResIds[position]
        }
        mIndicatorContainer.getChildAt(position).setBackgroundResource(selectResId)
        mPreSelectedPosition = position
    }

    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
    }

    override fun onPageScrollStateChanged(state: Int) {
    }

}