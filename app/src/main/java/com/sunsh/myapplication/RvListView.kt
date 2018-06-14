package com.sunsh.myapplication

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.ListView

class RvListView : ListView {

    private var downY: Float = 0.toFloat()

    private var parent: ViewGroup? = null

    constructor(context: Context) : super(context) {}

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {}

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {}

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return super.onInterceptTouchEvent(ev)
    }

    fun setParent(parent: ViewGroup) {
        this.parent = parent
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        parent!!.requestDisallowInterceptTouchEvent(true)
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> downY = ev.y
            MotionEvent.ACTION_MOVE ->
                if (canScroll2Up(this) && canScroll2Bottom(this)) {
                    parent!!.requestDisallowInterceptTouchEvent(true)
                } else {
                    val offset = ev.y - downY
                    if (offset > 0) {
                        // ev down TODO
                        if (canScroll2Up(this)) {
                            parent!!.requestDisallowInterceptTouchEvent(true)
                        } else {
                            Log.e("ev_down", "top")
                            parent!!.requestDisallowInterceptTouchEvent(false)
                        }
                    } else {
                        // ev up TODO
                        if (canScroll2Bottom(this)) {
                            parent!!.requestDisallowInterceptTouchEvent(true)
                        } else {
                            Log.e("ev_up", "bottom")
                            parent!!.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                    Log.e("move", offset.toString() + "")
                }
        }
        return super.onTouchEvent(ev)
    }


    fun canScroll2Up(absListView: ListView): Boolean {
        return if (Build.VERSION.SDK_INT < 14) {
            absListView.childCount > 0 && (absListView.firstVisiblePosition > 0 || absListView.getChildAt(0)
                    .top < absListView.paddingTop)
        } else {
            absListView.canScrollVertically(-1)
        }
    }


    fun canScroll2Bottom(absListView: ListView): Boolean {
        return if (Build.VERSION.SDK_INT < 14) {
            absListView.childCount > 0 && (absListView.lastVisiblePosition < absListView.childCount - 1 || absListView.getChildAt(absListView.childCount - 1).bottom > absListView.paddingBottom)
        } else {
            absListView.canScrollVertically(1)
        }
    }
}
