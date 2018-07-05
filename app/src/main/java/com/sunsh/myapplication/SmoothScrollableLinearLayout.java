package com.sunsh.myapplication;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v4.util.ArraySet;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.OverScroller;

import java.util.Set;

/**
 * Created by sunsh on 2018/6/7.
 */


public class SmoothScrollableLinearLayout extends LinearLayout {
    private final OverScroller mOverScroller;

    public SmoothScrollableLinearLayout(Context context) {
        this(context, null);
    }

    public SmoothScrollableLinearLayout(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SmoothScrollableLinearLayout(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mOverScroller = new OverScroller(context);
    }

    /**
     * Smoothly scroll this view to a position relative to its old position.
     *
     * @param deltaX   The amount of pixels to scroll by horizontally.
     *                 Positive numbers will scroll the view to the right.
     * @param deltaY   The amount of pixels to scroll by vertically.
     *                 Positive numbers will scroll the view down.
     * @param duration duration of the scroll in milliseconds.
     */
    public void smoothScrollBy(int deltaX, int deltaY, int duration) {
        if (deltaX != 0 || deltaY != 0) {
            mOverScroller.startScroll(getScrollX(), getScrollY(), -deltaX, -deltaY, duration);
            invalidate();
        }
    }

    /**
     * Smoothly scroll this view to a position.
     *
     * @param desX     The x position to scroll to in pixels.
     * @param desY     The y position to scroll to in pixels.
     * @param duration duration of the scroll in milliseconds.
     */
    public void smoothScrollTo(int desX, int desY, int duration) {
        final int scrollX = getScrollX();
        final int scrollY = getScrollY();

        final boolean finished = mOverScroller.isFinished();
        if (finished && (-scrollX != desX || -scrollY != desY) ||
                !finished && (mOverScroller.getFinalX() != desX || mOverScroller.getFinalY() != desY)) {

            final int deltaX = scrollX + desX;
            final int deltaY = scrollY + desY;
            smoothScrollBy(deltaX, deltaY, duration);
        }
    }

    @Override
    public void computeScroll() {
        // 重写computeScroll()方法，并在其内部完成平滑滚动的逻辑
        if (mOverScroller.computeScrollOffset()) {
            scrollTo(mOverScroller.getCurrX(), mOverScroller.getCurrY());
            invalidate();
        }
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        if (mOnScrollChangeListeners != null)
            for (OnScrollChangeListener listener : mOnScrollChangeListeners)
                listener.onScrollChange(this, l, t, oldl, oldt);
    }

    private Set<OnScrollChangeListener> mOnScrollChangeListeners;

    public void addOnScrollChangeListener(OnScrollChangeListener listener) {
        if (mOnScrollChangeListeners == null)
            mOnScrollChangeListeners = new ArraySet<>();
        mOnScrollChangeListeners.add(listener);
    }

    public void removeOnScrollChangeListener(OnScrollChangeListener listener) {
        if (mOnScrollChangeListeners != null)
            mOnScrollChangeListeners.remove(listener);
    }

    public void clearOnScrollChangeListeners(OnScrollChangeListener listener) {
        if (mOnScrollChangeListeners != null)
            mOnScrollChangeListeners.clear();
    }

    /**
     * Interface definition for a callback to be invoked when the scroll
     * X or Y positions of a view change.
     * <p>
     * <b>Note:</b> Some views handle scrolling independently from View and may
     * have their own separate listeners for scroll-type events. For example,
     * {@link android.widget.ListView ListView} allows clients to register an
     * {@link android.widget.ListView#setOnScrollListener(android.widget.AbsListView.OnScrollListener)}
     * to listen for changes in list scroll position.
     *
     * @see #addOnScrollChangeListener(OnScrollChangeListener)
     */
    public interface OnScrollChangeListener {
        /**
         * Called when the scroll position of a view changes.
         *
         * @param v          The view whose scroll position has changed.
         * @param scrollX    Current horizontal scroll origin.
         * @param scrollY    Current vertical scroll origin.
         * @param oldScrollX Previous horizontal scroll origin.
         * @param oldScrollY Previous vertical scroll origin.
         */
        void onScrollChange(View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY);
    }
}