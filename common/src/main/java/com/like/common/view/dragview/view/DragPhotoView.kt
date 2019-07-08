package com.like.common.view.dragview.view

import android.R
import android.content.Context
import android.graphics.drawable.Drawable
import android.support.v4.view.PagerAdapter
import android.support.v4.view.ViewPager
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.Toast
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.github.chrisbanes.photoview.PhotoView
import com.like.common.util.onGlobalLayoutListener
import com.like.common.view.dragview.entity.DragInfo

class DragPhotoView(context: Context, val infos: List<DragInfo>, var curClickPosition: Int) : BaseDragView(context, infos[curClickPosition]) {
    private val TAG = DragPhotoView::class.java.simpleName
    private val mPagerViews = mutableListOf<PagerView>()
    private var isFirstMove = true

    init {
        if (curClickPosition != -1) {
            infos.forEach {
                mPagerViews.add(PagerView(context))
            }

            addView(DragViewPager(context).apply {
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
                    addRule(CENTER_IN_PARENT)
                }
                adapter = MyPagerAdapter(mPagerViews)
                addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
                    override fun onPageSelected(position: Int) {
                        curClickPosition = position
                        mConfig.setData(infos[curClickPosition])
                        showImage(curClickPosition)
                    }
                })
                currentItem = curClickPosition
            })

            onGlobalLayoutListener {
                mConfig.setData(infos[curClickPosition])
                showImage(curClickPosition)
                enter()
            }
        }
    }

    private fun showImage(index: Int) {
        val info = infos[index]
        val pagerView = mPagerViews[index]
        mGlideUtils.hasCached(info.imageUrl, hasCached = { url, isCached ->
            if (isCached) {// 如果有原图缓存，就直接显示原图，不显示缩略图了。
                pagerView.removeProgressBar()
                pagerView.removeThumbnailImageView()
                pagerView.addPhotoView()
                mGlideUtils.display(info.imageUrl, pagerView.photoView)
                Log.v(TAG, "从缓存中获取了图片：${info.imageUrl}")
            } else {// 如果没有原图缓存
                if (info.thumbImageUrl.isNotEmpty()) {
                    pagerView.addThumbnailImageView()
                    pagerView.addProgressBar()
                    mGlideUtils.display(info.thumbImageUrl, pagerView.thumbnailImageView, object : RequestListener<Drawable> {
                        override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
                            delay1000Millis {
                                pagerView.removeProgressBar()
                                pagerView.removeThumbnailImageView()
                                Toast.makeText(context, "获取缩略图失败！", Toast.LENGTH_SHORT).show()
                            }
                            return false
                        }

                        override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                            showOriginImage(info, pagerView)
                            return false
                        }
                    })
                } else {
                    pagerView.addProgressBar()
                    showOriginImage(info, pagerView)
                }
            }
        })
    }

    private fun showOriginImage(info: DragInfo, pagerView: PagerView) {
        if (info.imageUrl.isEmpty()) {
            delay1000Millis {
                pagerView.removeProgressBar()
                Toast.makeText(context, "图片地址为空！", Toast.LENGTH_SHORT).show()
            }
            return
        }
        delay1000Millis {
            pagerView.addPhotoView()
            mGlideUtils.display(info.imageUrl, pagerView.photoView, object : RequestListener<Drawable> {
                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
                    pagerView.removeProgressBar()
                    pagerView.removePhotoView()
                    Toast.makeText(context, "获取图片失败！", Toast.LENGTH_SHORT).show()
                    return false
                }

                override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                    delay100Millis {
                        // 防闪烁
                        pagerView.removeProgressBar()
                        pagerView.removeThumbnailImageView()
                    }
                    Log.v(TAG, "从网络获取了图片：${info.imageUrl}")
                    return false
                }
            })
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        // 当scale == 1时才能drag
        if (scaleX == 1f && scaleY == 1f) {
            when (event?.action) {
                MotionEvent.ACTION_DOWN -> {
                    onActionDown(event)
                    isFirstMove = true
                }
                MotionEvent.ACTION_MOVE -> {
                    // ViewPager的事件
                    if (isFirstMove && event.y - mDownY <= 0 && mConfig.curCanvasTranslationY == 0f && mConfig.curCanvasTranslationX != 0f) {
                        return super.dispatchTouchEvent(event)
                    }

                    // 单手指按下
                    if (event.pointerCount == 1) {
                        if (isFirstMove && event.y - mDownY <= 0) {
                            mConfig.updateCanvasTranslationY(0f)
                        } else {
                            mConfig.updateCanvasTranslationY(event.y - mDownY)
                            isFirstMove = false
                        }
                        mConfig.updateCanvasTranslationX(event.x - mDownX)
                        mConfig.updateCanvasScale()
                        mConfig.updateCanvasBgAlpha()
                        invalidate()
                        return true
                    }

                    // 防止下拉的时候双手缩放
                    if (mConfig.curCanvasTranslationY >= 0f && mConfig.curCanvasScale < 0.95f) {
                        return true
                    }
                }
                MotionEvent.ACTION_UP -> {
                    onActionUp(event)
                    isFirstMove = true
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onDestroy() {
        mPagerViews.clear()
    }

    class PagerView(context: Context) : RelativeLayout(context) {
        val thumbnailImageView: ImageView by lazy {
            ImageView(context).apply {
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            }
        }
        val progressBar: ProgressBar by lazy {
            ProgressBar(context, null, R.attr.progressBarStyleInverse).apply {
                layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    addRule(CENTER_IN_PARENT)
                }
            }
        }
        val photoView: PhotoView by lazy {
            PhotoView(context).apply {
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            }
        }

        private fun containsThumbnailImageView() = (0 until childCount).any { getChildAt(it) == thumbnailImageView }

        private fun containsProgressBar() = (0 until childCount).any { getChildAt(it) == progressBar }

        private fun containsPhotoView() = (0 until childCount).any { getChildAt(it) == photoView }

        fun addThumbnailImageView() {
            if (!containsThumbnailImageView()) {
                addView(thumbnailImageView)
            }
        }

        fun addProgressBar() {
            if (!containsProgressBar()) {
                addView(progressBar)
            }
        }

        fun addPhotoView() {
            if (!containsPhotoView()) {
                addView(photoView)
            }
        }

        fun removeThumbnailImageView() {
            removeView(thumbnailImageView)
        }

        fun removeProgressBar() {
            removeView(progressBar)
        }

        fun removePhotoView() {
            removeView(photoView)
        }
    }

    class MyPagerAdapter(private val pagerViews: List<PagerView>) : PagerAdapter() {
        override fun isViewFromObject(p0: View, p1: Any): Boolean = p0 == p1

        override fun getCount(): Int = pagerViews.size

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            container.addView(pagerViews[position])
            return pagerViews[position]
        }

        override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
            container.removeView(pagerViews[position])
        }
    }

}
