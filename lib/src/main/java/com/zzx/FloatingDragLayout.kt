package com.zzx

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.*
import android.widget.FrameLayout
import android.widget.Scroller
import androidx.core.view.NestedScrollingParent2
import androidx.core.view.NestedScrollingParentHelper
import androidx.core.view.ViewCompat
import com.zzx.floatingdraglayout.R
import java.lang.IllegalArgumentException
import kotlin.math.abs

class FloatingDragLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defaultStyleDef: Int = 0
): FrameLayout(context, attrs, defaultStyleDef), NestedScrollingParent2 {


    private var floatingLayout: View? = null

    private val touchSlop: Int

    private val scroller: Scroller

    private val nestedScrollParentHelper = NestedScrollingParentHelper(this)

    private var targetDyUnconsumed = 0
    private var targetHasFling = false

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.FloatingDragLayout)
        val layoutValue = TypedValue()
        val hasLayoutId = a.getValue(R.styleable.FloatingDragLayout_floating_layout, layoutValue)
        if (hasLayoutId) {
            floatingLayout = LayoutInflater.from(context).inflate(layoutValue.resourceId, this, false)
            addView(floatingLayout)
        }
        a.recycle()
        touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        scroller = Scroller(context)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        if (heightMode == MeasureSpec.AT_MOST) {
            throw IllegalArgumentException("you must use MATCH_PARENT mode in height")
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        floatingLayout?.apply {
            (layoutParams as LayoutParams).gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        }
        val hideView = if (floatingLayout == null) {
            getChildAt(1)
        } else {
            getChildAt(2)
        }
        hideView?.apply {
            (layoutParams as LayoutParams).topMargin = this@FloatingDragLayout.height
        }
        super.onLayout(changed, left, top, right, bottom)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        //将floatingLayout置于第二个子View的位置
        floatingLayout?.let {
            detachViewFromParent(it)
            attachViewToParent(it, 1, it.layoutParams)
            it.setOnTouchListener(FloatingLayoutTouchListener())
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        floatingLayout?.apply {
            setOnTouchListener(null)
        }
    }

    override fun onNestedPreScroll(target: View, dx: Int, dy: Int, consumed: IntArray, type: Int) {
        Log.e(TAG, "onNestedPreScroll -> dy=$dy  type=$type targetDyConsumed=$targetDyUnconsumed")
        if (type == ViewCompat.TYPE_TOUCH && abs(targetDyUnconsumed) > 0) {
            consumed[1] = dy
            scrollBy(0, dy)
        }
    }

    override fun onStopNestedScroll(target: View, type: Int) {
        Log.e(TAG, "onStopNestedScroll -> hasFling=$targetHasFling  type=$type")
        if (type == ViewCompat.TYPE_TOUCH && !targetHasFling) {
            onDragEnd(0.0f)
        }
        if (targetHasFling) {
            targetHasFling = false
        }
    }

    override fun onStartNestedScroll(child: View, target: View, axes: Int, type: Int): Boolean {
        Log.e(TAG, "onStartNestedScroll -> type=$type")
        targetDyUnconsumed = 0
        return (axes and ViewCompat.SCROLL_AXIS_VERTICAL != 0) && type == ViewCompat.TYPE_TOUCH
    }

    override fun onNestedScrollAccepted(child: View, target: View, axes: Int, type: Int) {
        nestedScrollParentHelper.onNestedScrollAccepted(child, target, axes, type)
    }

    private fun onDragEnd(velocityY: Float) {
        if (!scroller.isFinished) {
            scroller.forceFinished(true)
        }
        val floatingLayoutHeight = getFloatingLayoutHeight()
        if (scrollY >= (height - floatingLayoutHeight) / 2) {
            scroller.startScroll(0, scrollY, 0, (height - floatingLayoutHeight) - scrollY)
        } else {
            scroller.startScroll(0, scrollY, 0, -scrollY)
        }
        invalidate()
    }

    override fun onNestedScroll(
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        type: Int
    ) {
        targetDyUnconsumed = dyUnconsumed
    }

    override fun onNestedPreFling(target: View, velocityX: Float, velocityY: Float): Boolean {
        Log.e(TAG, "onNestedPreFling -> ")
        targetHasFling = true
        if (abs(targetDyUnconsumed) > 0) {
            onDragEnd(velocityY)
        }
        return false
    }

    private fun getFloatingLayoutHeight(): Int {
        return if (floatingLayout == null) {
            0
        } else {
            floatingLayout!!.height
        }
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollTo(scroller.currX, scroller.currY)
            invalidate()
        }
    }

    inner class FloatingLayoutTouchListener : OnTouchListener {

        private var touchDownY = 0
        private var lastMoveY = 0

        private val velocityTracker = VelocityTracker.obtain()

        /**
         * 这里必须用rawY，否则[FloatingDragLayout]scroll时，不好ACTION_MOVE滑动的距离
         */
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            velocityTracker.addMovement(event)
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownY = (event.rawY + 0.5f).toInt()
                    lastMoveY = (event.rawY + 0.5f).toInt()
                }
                MotionEvent.ACTION_MOVE -> {
                    val y = (event.rawY + 0.5f).toInt()
                    val dy = y - lastMoveY
                    if (abs(dy) >= touchSlop) {
                        lastMoveY = (event.rawY + 0.5f).toInt()
                        scrollBy(0, -dy)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    velocityTracker.computeCurrentVelocity(1000)
                    val velocityY = velocityTracker.yVelocity
                    val dy = (event.rawY + 0.5f).toInt() - touchDownY
                    onDragEnd(velocityY)
                    velocityTracker.clear()
                }
            }
            return true
        }

    }

    companion object {
        private const val TAG = "FloatingDragLayout"
    }
}