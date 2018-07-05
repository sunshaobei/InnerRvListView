package com.sunsh.myapplication;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.util.ArraySet;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.LinearLayout;


import com.sunsh.myapplication.listener.OverFlyingDetector;

import java.lang.reflect.Field;
import java.util.Set;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.JELLY_BEAN_MR1;
import static android.support.v4.widget.ViewDragHelper.INVALID_POINTER;
import static com.sunsh.myapplication.listener.OverFlyingDetector.getOverFlyingMinimumDuration;

/**
 * Created by sunsh on 2018/6/7.
 */

public class SwipeMenuRecyclerView extends RecyclerView implements OverScrollView,
        Animator.AnimatorListener, ValueAnimator.AnimatorUpdateListener,
        OverFlyingDetector.OnOverFlyingListener {
    // @formatter:off
    private static final String TAG = "SwipeMenuRecyclerView";
    private static final boolean DEBUG = false;

    private int mPaddingStart;
    private int mPaddingTop;
    private int mPaddingEnd;
    private int mPaddingBottom;

    public static final int NO_ORIENTATION = -1;

    /** dp */
    protected final float mDp;
    protected final int mTouchSlop;

    private int mViewFlags;

    /** 标志手指在滑动itemView */
    private static final int VIEW_FLAG_ITEM_TOUCHED_SCROLL = 1;

    /** 标志itemView已完全被滑开 */
    private static final int VIEW_FLAG_ITEM_FULL_SCROLLED = 1 << 1;

    /** 标志在收到{@link MotionEvent#ACTION_DOWN}事件时，itemView已完全被滑开 */
    private static final int VIEW_FLAG_ITEM_FULL_SCROLLED_ON_ACTION_DOWN = 1 << 2;

    /**
     * 标志itemView可以滚动
     * @see #setItemScrollingEnabled(boolean)
     * */
    private static final int VIEW_FLAG_ITEM_SCROLLING_ENABLED = 1 << 3;

    /**
     * 标志itemView可以向水平结束端滚动且松手后有自动回弹的效果
     * @see #setItemSpringBackEnabled(boolean)
     */
    private static final int VIEW_FLAG_ITEM_SPRING_BACK_ENABLED = 1 << 4;

    /**
     * 标志列表可以过度滚动
     * @see #setOverScrollEnabled(boolean)
     */
    private static final int VIEW_FLAG_OVERSCROLL_ENABLED = 1 << 5;

    private int mActivePointerId = INVALID_POINTER;

    private int mDownX;
    private int mDownY;

    private final int[] mTouchX = new int[2];
    private final int[] mTouchY = new int[2];

    private VelocityTracker mVelocityTracker;

    /** 当前滚动的itemView */
    private SmoothScrollableLinearLayout mItemView;
    /** 上次滚动的itemView */
    private SmoothScrollableLinearLayout mLastItemView;

    /** 当前所触摸itemView的边界范围（相对于当前view） */
    private final Rect mTouchedItemBounds = new Rect();
    /** 当前所触摸itemView的菜单的边界范围（相对于当前view） */
    private final Rect mTouchedItemMenuBounds = new Rect();

    /** itemView向水平开始端滚动时的最大滚动距离 */
    private int mItemMaxScrollDistFromEndToStart;
    /** itemView向水平结束端滚动时的最大滚动距离 */
    private int mItemMaxScrollDistFromStartToEnd; // 10 dp

    /** 松手后使itemView自动滚动的最小手势速度 */
    private final float mAutoScrollItemMinVelocityX; // 200 dp/s

    /** itemView从完全隐藏到完全滚动开的时间 */
    private static final int DURATION_ITEM_MENU_AUTO_SCROLL_OUT = 500; // ms

    /**
     * itemView发生一次回弹的时间 <br>
     * <b>注：itemView隐藏时，每次往水平结束端滑动触发两次不同方向的回弹</b>
     */
    private static final int DURATION_ITEM_SPRING_BACK = 150; // ms

    private final Runnable mItemSpringBackRunnable = new Runnable() {
        @Override
        public void run() {
            releaseItemViewInternal(mItemView, DURATION_ITEM_SPRING_BACK);
        }
    };

    @OverScrollEdge
    private int mOverScrollEdge = OVERSCROLL_EDGE_UNSPECIFIED;

    @OverScrollState
    private int mOverScrollState = OVERSCROLL_STATE_IDLE;

    private int mOverScrollDist;

    private OverFlyingDetector mOverflyingDetector;

    /** 列表发生过度滚动后回弹时的时间 */
    private static final int DURATION_SPRING_BACK = 250;

    private ValueAnimator mOverScrollAnim;
    private final Interpolator mInterpolator = new DecelerateInterpolator();

    private int mAnimFlags;
    private static final int ANIM_FLAG_HEADER_ANIM_RUNNING = 1;
    private static final int ANIM_FLAG_FOOTER_ANIM_RUNNING = 1 << 1;
    // @formatter:on

    public boolean isDraggingItemView() {
        return (mViewFlags & VIEW_FLAG_ITEM_TOUCHED_SCROLL) != 0;
    }

    public boolean isItemFullScrolled() {
        return (mViewFlags & VIEW_FLAG_ITEM_FULL_SCROLLED) != 0;
    }

    public boolean isItemScrollingEnabled() {
        return (mViewFlags & VIEW_FLAG_ITEM_SCROLLING_ENABLED) != 0;
    }

    /**
     * Only works on vertical layout
     */
    public void setItemScrollingEnabled(boolean enabled) {
        if (enabled)
            mViewFlags |= VIEW_FLAG_ITEM_SCROLLING_ENABLED;
        else
            mViewFlags &= ~VIEW_FLAG_ITEM_SCROLLING_ENABLED;
    }

    public boolean isItemSpringBackEnabled() {
        return (mViewFlags & VIEW_FLAG_ITEM_SPRING_BACK_ENABLED) != 0;
    }

    /**
     * Only works on vertical layout and that scrolling itemView is enabled
     */
    public void setItemSpringBackEnabled(boolean enabled) {
        if (enabled) {
            mViewFlags |= VIEW_FLAG_ITEM_SPRING_BACK_ENABLED;
            mItemMaxScrollDistFromStartToEnd = (int) (10f * mDp + 0.5f);
        } else {
            mViewFlags &= ~VIEW_FLAG_ITEM_SPRING_BACK_ENABLED;
            mItemMaxScrollDistFromStartToEnd = 0;
        }
    }

    public boolean isOverScrollEnabled() {
        return (mViewFlags & VIEW_FLAG_OVERSCROLL_ENABLED) != 0;
    }

    public void setOverScrollEnabled(boolean enabled) {
        if (enabled) {
            mViewFlags |= VIEW_FLAG_OVERSCROLL_ENABLED;
            // 禁用列表拉到两端时发荧光的效果
            setOverScrollMode(OVER_SCROLL_NEVER);
            // 将滚动条设置在padding区域外并且覆盖在view上，如果滚动条比padding大可能会遮挡内容
            setScrollBarStyle(SCROLLBARS_OUTSIDE_OVERLAY);
        } else {
            mViewFlags &= ~VIEW_FLAG_OVERSCROLL_ENABLED;
            setOverScrollMode(OVER_SCROLL_ALWAYS); // default
            // 将滚动条设置在padding区域内并且覆盖在在内容上面，会遮挡些内容
            setScrollBarStyle(SCROLLBARS_INSIDE_OVERLAY); // default
        }
    }

    public boolean isOverScrolling() {
        return mOverScrollState != OVERSCROLL_STATE_IDLE;
    }

    @OverScrollEdge
    public int getOverScrollEdge() {
        return mOverScrollEdge;
    }

    @OverScrollState
    public int getOverScrollState() {
        return mOverScrollState;
    }

    public int getOverScrollDistance() {
        return mOverScrollDist;
    }

    public SwipeMenuRecyclerView(Context context) {
        this(context, null);
    }

    public SwipeMenuRecyclerView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SwipeMenuRecyclerView(Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        post(new Runnable() {
            @Override
            public void run() {
                mOverflyingDetector = new OverFlyingDetector(getHandler());
                mPaddingStart = SDK_INT >= JELLY_BEAN_MR1 ? getPaddingStart() : getPaddingLeft();
                mPaddingTop = getPaddingTop();
                mPaddingEnd = SDK_INT >= JELLY_BEAN_MR1 ? getPaddingEnd() : getPaddingRight();
                mPaddingBottom = getPaddingBottom();
            }
        });
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mDp = context.getResources().getDisplayMetrics().density;
        mAutoScrollItemMinVelocityX = mDp * 200f;

        if (DEBUG)
            addOnOverScrollListener(new OnOverScrollListener() {
                @Override
                public void onOverScrollStart(OverScrollView view, int edge) {
                    Log.d(TAG, "onOverScrollStart   edge=" + edge);
                }

                @Override
                public void onOverScrollEnd(OverScrollView view, int edge) {
                    Log.d(TAG, "onOverScrollEnd   edge=" + edge);
                }

                @Override
                public void onOverScrollDistanceChange(OverScrollView view, float distance) {
                    Log.d(TAG, "onOverScrollDistanceChange   distance=" + distance);
                }

                @Override
                public void onOverScrollStateChange(OverScrollView view, int state) {
                    Log.d(TAG, "onOverScrollStateChange   state=" + state);
                }
            });
    }

    @Override
    public void setOverScrollMode(int overScrollMode) {
        if (isOverScrollEnabled() && getOverScrollMode() == OVER_SCROLL_NEVER)
            return;
        super.setOverScrollMode(overScrollMode);
    }

    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        final boolean rtl = isLayoutRtl();
        mPaddingStart = rtl ? right : left;
        mPaddingTop = top;
        mPaddingEnd = rtl ? left : right;
        mPaddingBottom = bottom;
        super.setPadding(left, top, right, bottom);
    }

    @Override
    public void setPaddingRelative(int start, int top, int end, int bottom) {
        if (!isOverScrolling()) {
            mPaddingStart = start;
            mPaddingTop = top;
            mPaddingEnd = end;
            mPaddingBottom = bottom;
        }
        super.setPaddingRelative(start, top, end, bottom);
    }

    protected boolean isLayoutRtl() {
        return SDK_INT >= JELLY_BEAN_MR1 && getLayoutDirection() == LAYOUT_DIRECTION_RTL;
    }

    /**
     * Returns the current orientation of the layout.
     *
     * @return Current orientation,  either {@link #HORIZONTAL} or {@link #VERTICAL}
     * @see LinearLayoutManager#setOrientation(int)
     * @see StaggeredGridLayoutManager#setOrientation(int)
     */
    public int getLayoutOrientation() {
        if (getLayoutManager() instanceof LinearLayoutManager)
            return (((LinearLayoutManager) getLayoutManager()).getOrientation());
        else if (getLayoutManager() instanceof StaggeredGridLayoutManager)
            return (((StaggeredGridLayoutManager) getLayoutManager()).getOrientation());
        return NO_ORIENTATION;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        switch (ev.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                if (isItemFullScrolled())
                    mViewFlags |= VIEW_FLAG_ITEM_FULL_SCROLLED_ON_ACTION_DOWN;
                else
                    mViewFlags &= ~VIEW_FLAG_ITEM_FULL_SCROLLED_ON_ACTION_DOWN;
            case MotionEvent.ACTION_POINTER_DOWN:
                final int actionIndex = ev.getActionIndex();
                mActivePointerId = ev.getPointerId(actionIndex);
                mDownX = (int) (ev.getX(actionIndex) + 0.5f);
                mDownY = (int) (ev.getY(actionIndex) + 0.5f);
                markCurrTouchPoint(ev);
                break;
            case MotionEvent.ACTION_MOVE:
                final int pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex < 0) {
                    Log.e(TAG, "Error processing scroll; pointer index for id "
                            + mActivePointerId + " not found. Did any MotionEvents get skipped?");
                    return false;
                }
                markCurrTouchPoint(ev);
                break;
            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mActivePointerId = INVALID_POINTER;
                break;
        }
        // overflying
        if (isOverScrollEnabled() && getChildCount() > 0 && !isDraggingItemView()
                && (mViewFlags & VIEW_FLAG_ITEM_FULL_SCROLLED_ON_ACTION_DOWN) == 0) {
            mOverflyingDetector.onTouchEvent(ev);
        }
        return super.dispatchTouchEvent(ev);
    }

    private void onSecondaryPointerUp(MotionEvent ev) {
        final int pointerIndex = ev.getActionIndex();
        final int pointerId = ev.getPointerId(pointerIndex);
        if (pointerId == mActivePointerId) {
            // This was our active pointer going up.
            // Choose a new active pointer and adjust accordingly.
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            mActivePointerId = ev.getPointerId(newPointerIndex);
            mDownX = (int) (ev.getX(newPointerIndex) + 0.5f);
            mDownY = (int) (ev.getY(newPointerIndex) + 0.5f);
            markCurrTouchPoint(ev);
        }
    }

    private void markCurrTouchPoint(MotionEvent ev) {
        final int actionIndex = ev.findPointerIndex(mActivePointerId);
        System.arraycopy(mTouchX, 1, mTouchX, 0, mTouchX.length - 1);
        mTouchX[mTouchX.length - 1] = (int) (ev.getX(actionIndex) + 0.5f);
        System.arraycopy(mTouchY, 1, mTouchY, 0, mTouchY.length - 1);
        mTouchY[mTouchY.length - 1] = (int) (ev.getY(actionIndex) + 0.5f);
    }

    /**
     * 拦截touch事件
     *
     * @return 返回true时，ViewGroup的事件有效，执行onTouchEvent事件；
     * 返回false时，事件向下传递，onTouchEvent无效。
     */
    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        boolean intercept = false;
        switch (e.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                for (int i = getChildCount() - 1; i >= 0; i--) {
                    View child = getChildAt(i);
                    if (child.getVisibility() != VISIBLE) continue;

                    child.getHitRect(mTouchedItemBounds);
                    if (!mTouchedItemBounds.contains(mDownX, mDownY)) continue;

                    if (!(child instanceof SmoothScrollableLinearLayout)) continue;
                    mItemView = (SmoothScrollableLinearLayout) child;
                    mItemMaxScrollDistFromEndToStart = mItemView.getChildCount() <= 1 ? 0 :
                            mItemView.getChildAt(mItemView.getChildCount() - 1).getMeasuredWidth();
                    break;
                }

                // 如果存在itemView被滑开
                if ((mViewFlags & VIEW_FLAG_ITEM_FULL_SCROLLED_ON_ACTION_DOWN) != 0) {
                    if (mLastItemView == mItemView) {
                        final int left = isLayoutRtl() ? 0 :
                                mItemView.getRight() - mItemMaxScrollDistFromEndToStart;
                        final int right = left + mItemMaxScrollDistFromEndToStart;
                        mTouchedItemMenuBounds.set(left, mTouchedItemBounds.top,
                                right, mTouchedItemBounds.bottom);
                        // 点击的是itemView被滑开后显示的菜单时，不拦截touch事件
                        if (mTouchedItemMenuBounds.contains(mDownX, mDownY))
                            break;
                            // 点击的不是itemView被滑开后显示的菜单但是itemView所在区域时，拦截touch事件
                        else if (mTouchedItemBounds.contains(mDownX, mDownY)) {
                            return true;
                            // 点击的是列表中除items以外的区域，隐藏已滑开的itemView且不拦截touch事件
                        } else {
                            releaseItemViewInternal(mItemView, DURATION_ITEM_MENU_AUTO_SCROLL_OUT);
                            break;
                        }
                    }
                    // 之前滑开的和现在点击的不是同一个，隐藏之前滑开的itemView且拦截touch事件
                    releaseItemViewInternal(mLastItemView, DURATION_ITEM_MENU_AUTO_SCROLL_OUT);
                    return true;
                }
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                // 如果第一根手指点击的是itemView被滑开后显示的菜单，
                // 在第二根手指按下时隐藏已滑开itemView且拦截touch事件
                if ((mViewFlags & VIEW_FLAG_ITEM_FULL_SCROLLED_ON_ACTION_DOWN) != 0
                        && mTouchedItemMenuBounds.contains(
                        mTouchX[mTouchX.length - 2], mTouchY[mTouchY.length - 2])) {
                    releaseItemViewInternal(mItemView, DURATION_ITEM_MENU_AUTO_SCROLL_OUT);
                    return true;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                intercept = tryHandleItemScrollingEvent();
                // 点击的是itemView被滑开后显示的菜单时，不让父类拦截touch事件
                // 只在itemView有发生左右滑动的趋势时，才拦截touch事件
                if ((mViewFlags & VIEW_FLAG_ITEM_FULL_SCROLLED_ON_ACTION_DOWN) != 0
                        && mTouchedItemMenuBounds.contains(mDownX, mDownY)) {
                    return intercept;
                }
                break;
            case MotionEvent.ACTION_UP:
                // 如果点击的是itemView被滑开后显示的菜单且没有滑动它，在手指抬起时使其隐藏
                releaseItemViewInternal(mItemView, DURATION_ITEM_MENU_AUTO_SCROLL_OUT);
                break;
        }
        return intercept || super.onInterceptTouchEvent(e);
    }

    private boolean tryHandleItemScrollingEvent() {
        if (!(isItemScrollingEnabled() && getChildCount() > 0 && getLayoutOrientation() == VERTICAL))
            return false;
        // 不存在itemView被触摸
        if (mItemView == null) return false;

        final int absDX = Math.abs(mTouchX[mTouchX.length - 1] - mDownX);
        final int absDY = Math.abs(mTouchY[mTouchY.length - 1] - mDownY);
        if (absDX > absDY && absDX >= mTouchSlop) {
            mViewFlags |= VIEW_FLAG_ITEM_TOUCHED_SCROLL;
            getParent().requestDisallowInterceptTouchEvent(true);

            // itemView滚动时不显示竖直滚动条
            setVerticalScrollBarEnabled(false);
            return true;
        }
        return false;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        final boolean consume = handleItemScrollingEvent(e) || handleOverScroll(e);
        switch (e.getAction()) {
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                recycleVelocityTracker();
                break;
        }
        return consume || super.onTouchEvent(e);
    }

    private void initVelocityTracker() {
        if (mVelocityTracker == null)
            mVelocityTracker = VelocityTracker.obtain();
    }

    private void recycleVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    protected boolean handleItemScrollingEvent(MotionEvent e) {
        if (isOverScrolling())
            return false;

        if (mItemView == null) // set mItemView = null after postDelayed(mItemSpringBackRunnable, 150)
            return (mViewFlags & VIEW_FLAG_ITEM_FULL_SCROLLED_ON_ACTION_DOWN) != 0;
        switch (e.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_MOVE:
                // 如果在滑动itemView
                if (isDraggingItemView()) {
                    initVelocityTracker();
                    mVelocityTracker.addMovement(e);

                    final boolean rtl = isLayoutRtl();
                    int dx = mTouchX[mTouchX.length - 2] - mTouchX[mTouchX.length - 1];
                    if (rtl) dx = -dx; // 向水平开始端滑动为正
                    final int scrollX = rtl ? -mItemView.getScrollX() : mItemView.getScrollX(); // 向水平开始端滚动为正
                    // 向水平开始端滑动至最大可滑动距离
                    if (dx + scrollX > mItemMaxScrollDistFromEndToStart) {
                        dx = mItemMaxScrollDistFromEndToStart - scrollX;
                        // 向水平结束端滑动至最大可滑动距离
                    } else if (scrollX + dx < -mItemMaxScrollDistFromStartToEnd) {
                        dx = -mItemMaxScrollDistFromStartToEnd - scrollX;
                    }
                    mItemView.smoothScrollBy(rtl ? dx : -dx, 0, 0);
                    // 消费当前touch事件且不调用父类onTouchEvent(e) --> 列表无法上下滚动
                    return true;
                } else {
                    // 如果在手指按下时存在itemView被滑开，始终消费touch事件
                    // 只有在itemView有发生左右滑动的趋势时，才处理touch事件
                    return (mViewFlags & VIEW_FLAG_ITEM_FULL_SCROLLED_ON_ACTION_DOWN) != 0
                            | tryHandleItemScrollingEvent();
                }
            case MotionEvent.ACTION_UP:
                if (isDraggingItemView()) {
                    mViewFlags &= ~VIEW_FLAG_ITEM_TOUCHED_SCROLL;
                    mLastItemView = mItemView;

                    final boolean rtl = isLayoutRtl();
                    final int scrollX = rtl ? -mItemView.getScrollX() : mItemView.getScrollX(); // 向水平开始端滚动为正

                    // 向水平结束端滑时
                    if (scrollX < 0) {
                        mViewFlags |= VIEW_FLAG_ITEM_FULL_SCROLLED;
                        // ltr: 从(10dp, 0)处向左平滑滚动20dp到(-10dp, 0)
                        mItemView.smoothScrollTo(rtl ? mItemMaxScrollDistFromStartToEnd :
                                -mItemMaxScrollDistFromStartToEnd, 0, DURATION_ITEM_SPRING_BACK);
                        // ltr: 从(-10dp, 0)处向右平滑滚动10dp回到原点
                        postDelayed(mItemSpringBackRunnable, DURATION_ITEM_SPRING_BACK);

                        // 滑至itemView的菜单完全隐藏
                    } else if (scrollX == 0f) {
                        mViewFlags &= ~VIEW_FLAG_ITEM_FULL_SCROLLED;

                        // 完全滑开
                    } else if (scrollX == mItemMaxScrollDistFromEndToStart) {
                        mViewFlags |= VIEW_FLAG_ITEM_FULL_SCROLLED;

                        // scrollX > 0f && scrollX < mItemMaxScrollDistFromEndToStart
                    } else {
                        final int dx = rtl ? mTouchX[mTouchX.length - 1] - mTouchX[mTouchX.length - 2]
                                : mTouchX[mTouchX.length - 2] - mTouchX[mTouchX.length - 1]; // 向水平开始端滑动为正

                        mVelocityTracker.computeCurrentVelocity(1000);
                        final float velocityX = mVelocityTracker.getXVelocity(mActivePointerId);
                        // 向水平开始端滑时 手指抬起时的速度 >= 200dp/s --> itemView自动滚动以显示菜单
                        if (dx > 0 && Math.abs(velocityX) >= mAutoScrollItemMinVelocityX) {
                            mViewFlags |= VIEW_FLAG_ITEM_FULL_SCROLLED;
                            final int duration = (int) (DURATION_ITEM_MENU_AUTO_SCROLL_OUT *
                                    (float) (mItemMaxScrollDistFromEndToStart - scrollX)
                                    / mItemMaxScrollDistFromEndToStart + 0.5f);
                            mItemView.smoothScrollTo(rtl ? mItemMaxScrollDistFromEndToStart :
                                    -mItemMaxScrollDistFromEndToStart, 0, duration);

                            // 向水平结束端滑时 手指抬起时的速度 >= 200dp/s --> itemView自动滚动以隐藏菜单
                        } else if (dx < 0 && Math.abs(velocityX) >= mAutoScrollItemMinVelocityX) {
                            final int duration = (int) (DURATION_ITEM_MENU_AUTO_SCROLL_OUT *
                                    (float) scrollX / mItemMaxScrollDistFromEndToStart + 0.5f);
                            releaseItemViewInternal(mItemView, duration);

                        } else {
                            final int middle = mItemMaxScrollDistFromEndToStart / 2;
                            // 滑动距离 < 最大可滑动距离的一半 --> itemView自动滚动以隐藏菜单
                            if (scrollX < middle) {
                                final int duration = (int) (DURATION_ITEM_MENU_AUTO_SCROLL_OUT / 2f *
                                        scrollX / middle + 0.5f);
                                releaseItemViewInternal(mItemView, duration);

                                // 滑动距离 >= 最大可滑动距离的一半 --> itemView自动滚动以显示菜单
                            } else {
                                mViewFlags |= VIEW_FLAG_ITEM_FULL_SCROLLED;
                                final int duration = (int) (DURATION_ITEM_MENU_AUTO_SCROLL_OUT / 2f *
                                        (mItemMaxScrollDistFromEndToStart - scrollX) / middle + 0.5f);
                                mItemView.smoothScrollTo(rtl ? mItemMaxScrollDistFromEndToStart :
                                        -mItemMaxScrollDistFromEndToStart, 0, duration);
                            }
                        }
                    }
                    return true;
                }
                // 之前滑开的和现在点击的是同一个，且没有滑动该itemView，手指抬起时使其隐藏
                // 之前滑开的和现在点击的不是同一个，且没有滑动现在的itemView，设置mLastItemView = mItemView = null;
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_CANCEL:
                mViewFlags &= ~VIEW_FLAG_ITEM_TOUCHED_SCROLL;
                mLastItemView = mItemView;
                releaseItemViewInternal(mItemView, DURATION_ITEM_MENU_AUTO_SCROLL_OUT);
                break;
        }
        return false;
    }

    /**
     * 使当前滚开的itemView回到初始位置
     */
    public void releaseItemView() {
        releaseItemViewInternal(mItemView, DURATION_ITEM_MENU_AUTO_SCROLL_OUT);
    }

    private void releaseItemViewInternal(SmoothScrollableLinearLayout itemView, int duration) {
        if (itemView != null) {
            itemView.smoothScrollTo(0, 0, duration);
            mViewFlags &= ~VIEW_FLAG_ITEM_FULL_SCROLLED;

            if (itemView == mItemView) mItemView = null;
            if (itemView == mLastItemView) mLastItemView = null;
        }
    }

    private boolean tryHandleOverScroll() {
        if (!(isOverScrollEnabled() && getChildCount() > 0))
            return false;

        final int absDX = Math.abs(mTouchX[mTouchX.length - 1] - mDownX);
        final int absDY = Math.abs(mTouchY[mTouchY.length - 1] - mDownY);
        final boolean canScrollHorizontally = getLayoutManager().canScrollHorizontally();
        final boolean canScrollVertically = getLayoutManager().canScrollVertically();
        final boolean handle = canScrollVertically && absDY > absDX && absDY >= mTouchSlop
                || canScrollHorizontally && absDX > absDY && absDX >= mTouchSlop;
        if (handle)
            setVerticalScrollBarEnabled(true);
        return handle;
    }

    @SuppressLint("SwitchIntDef")
    @Override
    public boolean handleOverScroll(MotionEvent ev) {
        if (isDraggingItemView() || (mViewFlags & VIEW_FLAG_ITEM_FULL_SCROLLED_ON_ACTION_DOWN) != 0)
            return false;

        switch (ev.getAction()) {
            case MotionEvent.ACTION_MOVE:
                switch (mOverScrollState) {
                    case OVERSCROLL_STATE_IDLE:
                        if (!tryHandleOverScroll()) break;
                        if (getLayoutManager().canScrollVertically()) {
                            final int dy = mTouchY[mTouchY.length - 1] - mTouchY[mTouchY.length - 2];
                            final boolean atTop = isAtHead();
                            final boolean atBottom = isAtTail();
                            // itemView较少时，列表不能上下滚动 --> 不限制下拉和上拉
                            if (atTop && atBottom) {
                                mOverScrollEdge = OVERSCROLL_EDGE_TOP_OR_BOTTOM;
                                // 下拉
                            } else if (atTop && dy > 0)
                                mOverScrollEdge = OVERSCROLL_EDGE_TOP;
                                // 上拉
                            else if (atBottom && dy < 0)
                                mOverScrollEdge = OVERSCROLL_EDGE_BOTTOM;
                            else break;
                        } else if (getLayoutManager().canScrollHorizontally()) {
                            final int dx = isLayoutRtl() ?
                                    -(mTouchX[mTouchX.length - 1] - mTouchX[mTouchX.length - 2])
                                    : mTouchX[mTouchX.length - 1] - mTouchX[mTouchX.length - 2]; // 向水平结束端滑动为正
                            final boolean atStart = isAtHead();
                            final boolean atEnd = isAtTail();
                            // itemView较少时，列表不能左右滚动 --> 不限制左右拉
                            if (atStart && atEnd) {
                                mOverScrollEdge = OVERSCROLL_EDGE_START_OR_END;
                                // 向水平结束端拉
                            } else if (atStart && dx > 0)
                                mOverScrollEdge = OVERSCROLL_EDGE_START;
                                // 向水平开始端拉
                            else if (atEnd && dx < 0)
                                mOverScrollEdge = OVERSCROLL_EDGE_END;
                            else break;
                        } else break;
                        deliverOverScrollStartEventIfNeeded(mOverScrollEdge);
                        deliverOverScrollStateChangeIfNeeded(OVERSCROLL_STATE_TOUCH_SCROLL);
                        return true;
                    case OVERSCROLL_STATE_TOUCH_SCROLL:
                        initVelocityTracker();
                        mVelocityTracker.addMovement(ev);
                        switch (mOverScrollEdge) {
                            case OVERSCROLL_EDGE_TOP: {
                                final int deltaY = computeOverScrollDeltaY();
                                if (deltaY == 0)
                                    return true;

                                final int oldPt = getPaddingTop();
                                int paddingTop = oldPt + deltaY;
                                if (paddingTop < mPaddingTop)
                                    paddingTop = mPaddingTop;
                                setPaddingRelative(mPaddingStart, paddingTop, mPaddingEnd, mPaddingBottom);
                                deliverOverScrollDistanceChangeIfNeeded(paddingTop);

                                if (paddingTop < oldPt) {
                                    invalidateParentCachedTouchPos();
                                    if (paddingTop == mPaddingTop)
                                        endOverScroll();
                                    return true;
                                }
                                // Not consume this event when user scroll this view down,
                                // to enable nested scrolling.
                                break;
                            }
                            case OVERSCROLL_EDGE_BOTTOM: {
                                final int deltaY = computeOverScrollDeltaY();
                                if (deltaY == 0)
                                    return true;

                                final int oldPb = getPaddingBottom();
                                int paddingBottom = oldPb - deltaY;
                                if (paddingBottom < mPaddingBottom)
                                    paddingBottom = mPaddingBottom;
                                setPaddingRelative(mPaddingStart, mPaddingTop, mPaddingEnd, paddingBottom);
                                deliverOverScrollDistanceChangeIfNeeded(paddingBottom);

                                if (paddingBottom < oldPb) {
                                    invalidateParentCachedTouchPos();
                                    if (paddingBottom == mPaddingBottom)
                                        endOverScroll();
                                    return true;
                                }
                                break;
                            }
                            case OVERSCROLL_EDGE_TOP_OR_BOTTOM: {
                                final int deltaY = computeOverScrollDeltaY();
                                if (deltaY == 0)
                                    return true;

                                final int oldPt = getPaddingTop();
                                final int paddingTop = oldPt + deltaY;
                                setPaddingRelative(mPaddingStart, paddingTop, mPaddingEnd, mPaddingBottom);
                                deliverOverScrollDistanceChangeIfNeeded(paddingTop);

                                if (paddingTop > mPaddingTop && paddingTop < oldPt
                                        || paddingTop < mPaddingTop && paddingTop > oldPt) {
                                    invalidateParentCachedTouchPos();
                                    return true;
                                }
                                break;
                            }
                            case OVERSCROLL_EDGE_START: {
                                final int deltaX = computeOverScrollDeltaX();
                                if (deltaX == 0)
                                    return true;

                                final int oldPs = SDK_INT >= JELLY_BEAN_MR1 ? getPaddingStart() : getPaddingLeft();
                                int paddingStart = oldPs + deltaX;
                                if (paddingStart < mPaddingStart)
                                    paddingStart = mPaddingStart;
                                setPaddingRelative(paddingStart, mPaddingTop, mPaddingEnd, mPaddingBottom);
                                deliverOverScrollDistanceChangeIfNeeded(paddingStart);

                                if (paddingStart < oldPs) {
                                    invalidateParentCachedTouchPos();
                                    if (paddingStart == mPaddingStart)
                                        endOverScroll();
                                    return true;
                                }
                                break;
                            }
                            case OVERSCROLL_EDGE_END: {
                                final int deltaX = computeOverScrollDeltaX();
                                if (deltaX == 0)
                                    return true;

                                final int oldPe = SDK_INT >= JELLY_BEAN_MR1 ? getPaddingEnd() : getPaddingRight();
                                int paddingEnd = oldPe - deltaX;
                                if (paddingEnd < mPaddingEnd)
                                    paddingEnd = mPaddingEnd;
                                setPaddingRelative(mPaddingStart, mPaddingTop, paddingEnd, mPaddingBottom);
                                scrollToTail();
                                deliverOverScrollDistanceChangeIfNeeded(paddingEnd);

                                if (paddingEnd < oldPe) {
                                    invalidateParentCachedTouchPos();
                                    if (paddingEnd == mPaddingEnd)
                                        endOverScroll();
                                    return true;
                                }
                                break;
                            }
                            case OVERSCROLL_EDGE_START_OR_END: {
                                final int deltaX = computeOverScrollDeltaX();
                                if (deltaX == 0)
                                    return true;

                                final int oldPs = SDK_INT >= JELLY_BEAN_MR1 ? getPaddingStart() : getPaddingLeft();
                                final int paddingStart = oldPs + deltaX;
                                setPaddingRelative(paddingStart, mPaddingTop, mPaddingEnd, mPaddingBottom);
                                deliverOverScrollDistanceChangeIfNeeded(paddingStart);

                                if (paddingStart > mPaddingStart && paddingStart < oldPs
                                        || paddingStart < mPaddingStart && paddingStart > oldPs) {
                                    invalidateParentCachedTouchPos();
                                    return true;
                                }
                                break;
                            }
                        }
                        break;
                }
                break;
            case MotionEvent.ACTION_UP:
                if (mVelocityTracker == null) break;
                mVelocityTracker.computeCurrentVelocity(1000);
                if (getLayoutManager().canScrollVertically()) {
                    final float velocityY = mVelocityTracker.getYVelocity(mActivePointerId);
                    if (Math.abs(velocityY) >= mOverflyingDetector.getOverFlyingMinimumVelocity())
                        return false;
                } else if (getLayoutManager().canScrollHorizontally()) {
                    final float velocityX = mVelocityTracker.getXVelocity(mActivePointerId);
                    if (Math.abs(velocityX) >= mOverflyingDetector.getOverFlyingMinimumVelocity())
                        return false;
                }
            case MotionEvent.ACTION_CANCEL:
                if (mOverScrollState == OVERSCROLL_STATE_TOUCH_SCROLL)
                    smoothSpringBack();
                break;
        }
        return false;
    }

    private void endOverScroll() {
        if ((mAnimFlags & (ANIM_FLAG_HEADER_ANIM_RUNNING | ANIM_FLAG_FOOTER_ANIM_RUNNING)) == 0) {
            deliverOverScrollEndEventIfNeeded(mOverScrollEdge);
            deliverOverScrollStateChangeIfNeeded(OVERSCROLL_STATE_IDLE);
            mOverScrollEdge = OVERSCROLL_EDGE_UNSPECIFIED;
        }
    }

    @SuppressLint("SwitchIntDef")
    private int computeOverScrollDeltaY() {
        switch (mOverScrollEdge) {
            case OVERSCROLL_EDGE_TOP:
            case OVERSCROLL_EDGE_BOTTOM:
            case OVERSCROLL_EDGE_TOP_OR_BOTTOM:
                final int deltaY = mTouchY[mTouchY.length - 1] - mTouchY[mTouchY.length - 2];// 向下滑动为正
                if (isPushingBack())
                    return deltaY;
                else {
                    final float ratio = (Math.abs(getPaddingTop() - mPaddingTop)
                            + getPaddingBottom() - mPaddingBottom) /
                            ((getHeight() - mPaddingTop - mPaddingBottom) * 0.95f);
                    return (int) (1d / (2d + Math.tan(Math.PI / 2d * ratio)) * deltaY);
                }
        }
        return 0;
    }

    @SuppressLint("SwitchIntDef")
    private int computeOverScrollDeltaX() {
        switch (mOverScrollEdge) {
            case OVERSCROLL_EDGE_START:
            case OVERSCROLL_EDGE_END:
            case OVERSCROLL_EDGE_START_OR_END:
                final int deltaX = isLayoutRtl() ? -(mTouchX[mTouchX.length - 1] - mTouchX[mTouchX.length - 2])
                        : mTouchX[mTouchX.length - 1] - mTouchX[mTouchX.length - 2];// 向结束端滑动为正
                if (isPushingBack())
                    return deltaX;
                else {
                    final float ratio = (
                            Math.abs((SDK_INT >= JELLY_BEAN_MR1 ? getPaddingStart() : getPaddingLeft()) - mPaddingStart)
                                    + (SDK_INT >= JELLY_BEAN_MR1 ? getPaddingEnd() : getPaddingRight()) - mPaddingEnd) /
                            ((getWidth() - mPaddingStart - mPaddingEnd) * 0.95f);
                    return (int) (1d / (2d + Math.tan(Math.PI / 2d * ratio)) * deltaX);
                }
        }
        return 0;
    }

    @SuppressLint("SwitchIntDef")
    private boolean isPushingBack() {
        final int deltaY = mTouchY[mTouchY.length - 1] - mTouchY[mTouchY.length - 2];// 向下滑动为正
        final int deltaX = isLayoutRtl() ? -(mTouchX[mTouchX.length - 1] - mTouchX[mTouchX.length - 2])
                : mTouchX[mTouchX.length - 1] - mTouchX[mTouchX.length - 2];// 向右滑动为正
        switch (mOverScrollEdge) {
            case OVERSCROLL_EDGE_TOP:
                // 向下拉时手指向上滑动
                return deltaY < 0 && getPaddingTop() > mPaddingTop;
            case OVERSCROLL_EDGE_BOTTOM:
                // 向上拉时手指向下滑动
                return deltaY > 0 && getPaddingBottom() > mPaddingBottom;
            case OVERSCROLL_EDGE_TOP_OR_BOTTOM:
                // 向下拉时手指向上滑动
                return deltaY < 0 && getPaddingTop() > mPaddingTop ||
                        // 向上拉时手指向下滑动
                        deltaY > 0 && getPaddingTop() < mPaddingTop;
            case OVERSCROLL_EDGE_START:
                // 向水平结束端拉时手指向水平开始端滑动
                return deltaX < 0 && (SDK_INT >= JELLY_BEAN_MR1 ?
                        getPaddingStart() : getPaddingLeft()) > mPaddingStart;
            case OVERSCROLL_EDGE_END:
                // 向水平开始端拉时手指向水平结束端滑动
                return deltaX > 0 && (SDK_INT >= JELLY_BEAN_MR1 ?
                        getPaddingEnd() : getPaddingRight()) > mPaddingEnd;
            case OVERSCROLL_EDGE_START_OR_END:
                final int ps = (SDK_INT >= JELLY_BEAN_MR1 ?
                        getPaddingStart() : getPaddingLeft());
                // 向水平结束端拉时手指向水平开始端滑动
                return deltaX < 0 && ps > mPaddingStart ||
                        // 向水平开始端拉时手指向水平结束端滑动
                        deltaX > 0 && ps < mPaddingStart;
        }
        return false;
    }

    @SuppressWarnings("deprecation")
    public boolean isAtHead() {
        if (getLayoutManager().getItemCount() == 0) return true;

        try {
            if (getLayoutManager().canScrollVertically())
                return !ViewCompat.canScrollVertically(this, -1);
            else if (getLayoutManager().canScrollHorizontally())
                return !ViewCompat.canScrollHorizontally(this, isLayoutRtl() ? 1 : -1);
        } catch (NullPointerException e) {
            // It causes this exception on invoking #notifyDataSetChanged().
        }
        return false;
    }

    @SuppressWarnings("deprecation")
    public boolean isAtTail() {
        final int lastItemPosition = getLayoutManager().getItemCount() - 1;
        if (lastItemPosition < 0) return true;

        try {
            if (getLayoutManager().canScrollVertically())
                return !ViewCompat.canScrollVertically(this, 1);
            else if (getLayoutManager().canScrollHorizontally())
                return !ViewCompat.canScrollHorizontally(this, isLayoutRtl() ? -1 : 1);
        } catch (NullPointerException e) {
            //
        }
        return false;
    }

    public void scrollToHead() {
        if (getLayoutManager() instanceof LinearLayoutManager) {
            ((LinearLayoutManager) getLayoutManager())
                    .scrollToPositionWithOffset(0, 0);
        } else if (getLayoutManager() instanceof StaggeredGridLayoutManager) {
            ((StaggeredGridLayoutManager) getLayoutManager())
                    .scrollToPositionWithOffset(0, 0);
        }
    }

    public void scrollToTail() {
        final int lastItemPosition = getLayoutManager().getItemCount() - 1;
        if (lastItemPosition < 0) return;

        // 先粗略地滚动列表到最后一个itemView的位置
        scrollToPosition(lastItemPosition);
        View lastChild = getChildAt(getChildCount() - 1);

        if (getLayoutManager().canScrollVertically()) {
            final int dy = lastChild.getMeasuredHeight() -
                    (getHeight() - getPaddingBottom());
            final int offsetY = -(dy < 0 ? 0 : dy);
            if (getLayoutManager() instanceof LinearLayoutManager) {
                ((LinearLayoutManager) getLayoutManager())
                        .scrollToPositionWithOffset(lastItemPosition, offsetY);
            } else if (getLayoutManager() instanceof StaggeredGridLayoutManager) {
                ((StaggeredGridLayoutManager) getLayoutManager())
                        .scrollToPositionWithOffset(lastItemPosition, offsetY);
            }
        } else if (getLayoutManager().canScrollHorizontally()) {
            // FIXME:scroll this view to the end.Note that scrollToPositionWithOffset may not work.
            final int dx = lastChild.getMeasuredWidth() -
                    (getWidth() - (SDK_INT >= JELLY_BEAN_MR1 ? getPaddingEnd() : getPaddingRight()));
            final int offsetX = -(dx < 0 ? 0 : dx);
            if (getLayoutManager() instanceof LinearLayoutManager) {
                ((LinearLayoutManager) getLayoutManager())
                        .scrollToPositionWithOffset(lastItemPosition, offsetX);
            } else if (getLayoutManager() instanceof StaggeredGridLayoutManager) {
                ((StaggeredGridLayoutManager) getLayoutManager())
                        .scrollToPositionWithOffset(lastItemPosition, offsetX);
            }
        }
    }

    public void smoothSpringBack() {
        if (getPaddingTop() != mPaddingTop) {
            animateHeadOverScroll(getPaddingTop(), mPaddingTop, DURATION_SPRING_BACK);
        } else if (getPaddingBottom() != mPaddingBottom) {
            animateTailOverScroll(getPaddingBottom(), mPaddingBottom, DURATION_SPRING_BACK);
        } else {
            final int ps = SDK_INT >= JELLY_BEAN_MR1 ? getPaddingStart() : getPaddingLeft();
            final int pe = SDK_INT >= JELLY_BEAN_MR1 ? getPaddingEnd() : getPaddingRight();
            if (ps != mPaddingStart) {
                animateHeadOverScroll(ps, mPaddingStart, DURATION_SPRING_BACK);
            } else if (pe != mPaddingEnd) {
                animateTailOverScroll(pe, mPaddingEnd, DURATION_SPRING_BACK);
            } else {
                mOverScrollAnim = null;
                endOverScroll();
            }
        }
    }

    /**
     * @param from     current padding of top or start
     * @param to       the padding of top or start that the view will be set to.
     * @param duration the time this animation will last for.
     */
    public void animateHeadOverScroll(int from, int to, int duration) {
        if (from != to) {
            if (getLayoutManager().canScrollVertically())
                mOverScrollEdge = OVERSCROLL_EDGE_TOP;
            else if (getLayoutManager().canScrollHorizontally())
                mOverScrollEdge = OVERSCROLL_EDGE_START;
            resetAnim(from, to, duration);
            mAnimFlags |= ANIM_FLAG_HEADER_ANIM_RUNNING;
        }
    }

    /**
     * @param from     current padding of bottom or end
     * @param to       the padding of bottom or end that the view will be set to.
     * @param duration the time this animation will last for.
     */
    public void animateTailOverScroll(int from, int to, int duration) {
        if (from != to) {
            if (getLayoutManager().canScrollVertically())
                mOverScrollEdge = OVERSCROLL_EDGE_BOTTOM;
            else if (getLayoutManager().canScrollHorizontally())
                mOverScrollEdge = OVERSCROLL_EDGE_END;
            resetAnim(from, to, duration);
            mAnimFlags |= ANIM_FLAG_FOOTER_ANIM_RUNNING;
        }
    }

    private void resetAnim(int from, int to, int duration) {
        if ((mAnimFlags & (ANIM_FLAG_HEADER_ANIM_RUNNING | ANIM_FLAG_FOOTER_ANIM_RUNNING)) != 0) {
            mOverScrollAnim.removeListener(this);
            mOverScrollAnim.removeUpdateListener(this);
            mOverScrollAnim.cancel();
            clearAnimFlag();
        }
        mOverScrollAnim = ValueAnimator.ofInt(from, to);
        mOverScrollAnim.addListener(this);
        mOverScrollAnim.addUpdateListener(this);
        mOverScrollAnim.setInterpolator(mInterpolator);
        mOverScrollAnim.setDuration(duration).start();
    }

    @Override
    public void onAnimationStart(Animator animation) {
        deliverOverScrollStartEventIfNeeded(mOverScrollEdge);
        deliverOverScrollStateChangeIfNeeded(OVERSCROLL_STATE_AUTO_SCROLL);
    }

    @Override
    public void onAnimationEnd(Animator animation) {
        clearAnimFlag();
        smoothSpringBack();
    }

    private void clearAnimFlag() {
        if ((mAnimFlags & ANIM_FLAG_HEADER_ANIM_RUNNING) != 0)
            mAnimFlags &= ~ANIM_FLAG_HEADER_ANIM_RUNNING;
        else if ((mAnimFlags & ANIM_FLAG_FOOTER_ANIM_RUNNING) != 0)
            mAnimFlags &= ~ANIM_FLAG_FOOTER_ANIM_RUNNING;
    }

    @Override
    public void onAnimationCancel(Animator animation) {
    }

    @Override
    public void onAnimationRepeat(Animator animation) {
    }

    @Override
    public void onAnimationUpdate(ValueAnimator animation) {
        final int padding = (int) animation.getAnimatedValue();
        if ((mAnimFlags & ANIM_FLAG_HEADER_ANIM_RUNNING) != 0) {
            if (getLayoutManager().canScrollVertically()) {
                final int pt = getPaddingTop();
                setPaddingRelative(mPaddingStart, padding, mPaddingEnd, mPaddingBottom);
                // 在顶部回弹时，使view正常显示
                if (padding > pt)
                    scrollToHead();
                deliverOverScrollDistanceChangeIfNeeded(padding);

            } else if (getLayoutManager().canScrollHorizontally()) {
                final int ps = SDK_INT >= JELLY_BEAN_MR1 ? getPaddingStart() : getPaddingLeft();
                setPaddingRelative(padding, mPaddingTop, mPaddingEnd, mPaddingBottom);
                // 在水平开始端回弹时，使view正常显示
                if (padding > ps)
                    scrollToHead();
                deliverOverScrollDistanceChangeIfNeeded(padding);
            }
        } else if ((mAnimFlags & ANIM_FLAG_FOOTER_ANIM_RUNNING) != 0) {
            if (getLayoutManager().canScrollVertically()) {
                final int pb = getPaddingBottom();
                setPaddingRelative(mPaddingStart, mPaddingTop, mPaddingEnd, padding);
                // 在底部回弹时，使view正常显示
                if (padding > pb)
                    scrollToTail();
                deliverOverScrollDistanceChangeIfNeeded(padding);

            } else if (getLayoutManager().canScrollHorizontally()) {
                final int pe = SDK_INT >= JELLY_BEAN_MR1 ? getPaddingEnd() : getPaddingRight();
                setPaddingRelative(mPaddingStart, mPaddingTop, padding, mPaddingBottom);
                // 在水平结束端回弹时，使view正常显示
                if (padding > pe)
                    scrollToTail();
                deliverOverScrollDistanceChangeIfNeeded(padding);
            }
        }
    }

    @Override
    public void onTopEdgeOverFling(float overHeight, int duration) {
        final int pt = getPaddingTop();
        final float toPt = mPaddingTop + overHeight > pt ?
                mPaddingTop + overHeight : pt + mOverflyingDetector.getOverFlyingMinimumDistance();
        duration = (int) ((toPt - pt) / toPt * duration + 0.5f);
        animateHeadOverScroll(pt, (int) (toPt + 0.5f), duration < getOverFlyingMinimumDuration() ?
                getOverFlyingMinimumDuration() : duration);
    }

    @Override
    public void onBottomEdgeOverFling(float overHeight, int duration) {
        final int pb = getPaddingBottom();
        final float toPb = mPaddingBottom + overHeight > pb ?
                mPaddingBottom + overHeight : pb + mOverflyingDetector.getOverFlyingMinimumDistance();
        duration = (int) ((toPb - pb) / toPb * duration + 0.5f);
        animateTailOverScroll(pb, (int) (toPb + 0.5f), duration < getOverFlyingMinimumDuration() ?
                getOverFlyingMinimumDuration() : duration);
    }

    @Override
    public void onStartEdgeOverFling(float overWidth, int duration) {
        final int ps = SDK_INT >= JELLY_BEAN_MR1 ? getPaddingStart() : getPaddingLeft();
        final float toPs = mPaddingStart + overWidth > ps ?
                mPaddingStart + overWidth : ps + mOverflyingDetector.getOverFlyingMinimumDistance();
        duration = (int) ((toPs - ps) / toPs * duration + 0.5f);
        animateHeadOverScroll(ps, (int) (toPs + 0.5f), duration < getOverFlyingMinimumDuration() ?
                getOverFlyingMinimumDuration() : duration);
    }

    @Override
    public void onEndEdgeOverFling(float overWidth, int duration) {
        final int pe = SDK_INT >= JELLY_BEAN_MR1 ? getPaddingEnd() : getPaddingRight();
        final float toPe = mPaddingEnd + overWidth > pe ?
                mPaddingEnd + overWidth : pe + mOverflyingDetector.getOverFlyingMinimumDistance();
        duration = (int) ((toPe - pe) / toPe * duration + 0.5f);
        animateTailOverScroll(pe, (int) (toPe + 0.5f), duration < getOverFlyingMinimumDuration() ?
                getOverFlyingMinimumDuration() : duration);
    }

    protected class OverFlyingDetector extends com.sunsh.myapplication.listener.OverFlyingDetector {
        public OverFlyingDetector(@Nullable Handler handler) {
            super(SwipeMenuRecyclerView.this, SwipeMenuRecyclerView.this, handler);
        }

        @Override
        public boolean isViewAtTop() {
            return getLayoutManager().canScrollVertically() && isAtHead();
        }

        @Override
        public boolean isViewAtBottom() {
            return getLayoutManager().canScrollVertically() && isAtTail();
        }

        @Override
        public boolean isViewAtStart() {
            return getLayoutManager().canScrollHorizontally() && isAtHead();
        }

        @Override
        public boolean isViewAtEnd() {
            return getLayoutManager().canScrollHorizontally() && isAtTail();
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // OverScroll Listener
    ///////////////////////////////////////////////////////////////////////////

    private void deliverOverScrollStartEventIfNeeded(int edge) {
        if (!isOverScrolling() && mOnOverScrollListeners != null)
            for (OnOverScrollListener listener : mOnOverScrollListeners)
                listener.onOverScrollStart(this, edge);
    }

    private void deliverOverScrollStateChangeIfNeeded(int state) {
        if (mOverScrollState != state) {
            mOverScrollState = state;
            if (mOnOverScrollListeners != null)
                for (OnOverScrollListener listener : mOnOverScrollListeners)
                    listener.onOverScrollStateChange(this, mOverScrollState);
        }
    }

    private void deliverOverScrollDistanceChangeIfNeeded(int dist) {
        if (mOverScrollDist != dist) {
            mOverScrollDist = dist;
            if (mOnOverScrollListeners != null)
                for (OnOverScrollListener listener : mOnOverScrollListeners)
                    listener.onOverScrollDistanceChange(this, mOverScrollDist);
        }
    }

    private void deliverOverScrollEndEventIfNeeded(int edge) {
        if (isOverScrolling() && mOnOverScrollListeners != null)
            for (OnOverScrollListener listener : mOnOverScrollListeners)
                listener.onOverScrollEnd(this, edge);
    }

    private Set<OnOverScrollListener> mOnOverScrollListeners;

    public void addOnOverScrollListener(OnOverScrollListener listener) {
        if (mOnOverScrollListeners == null)
            mOnOverScrollListeners = new ArraySet<>();
        mOnOverScrollListeners.add(listener);
    }

    public void removeOnOverScrollListener(OnOverScrollListener listener) {
        if (mOnOverScrollListeners != null)
            mOnOverScrollListeners.add(listener);
    }

    public void clearOnOverScrollListeners() {
        if (mOnOverScrollListeners != null)
            mOnOverScrollListeners.clear();
    }

    ///////////////////////////////////////////////////////////////////////////
    // reflection methods
    ///////////////////////////////////////////////////////////////////////////

    private Field mLastTouchXField;
    private Field mLastTouchYField;

    /**
     * Refresh the cached touch position {@link RecyclerView#mLastTouchX, RecyclerView#mLastTouchY}
     * of {@link RecyclerView} to ensure it will scroll up or down
     * within {@code Math.abs(mTouchY[mTouchY.length-1] - mTouchY[mTouchY.length-2])} px
     * or scroll left or right not more than
     * {@code Math.abs(mTouchX[mTouchX.length-1] - mTouchX[mTouchX.length-2])} px
     * when it receives touch event again.
     */
    private void invalidateParentCachedTouchPos() {
        try {
            if (getLayoutManager().canScrollVertically()) {
                if (mLastTouchYField == null) {
                    mLastTouchYField = RecyclerView.class.getDeclaredField("mLastTouchY");
                    mLastTouchYField.setAccessible(true);
                }
                mLastTouchYField.set(this, mTouchY[mTouchY.length - 2]);

            } else if (getLayoutManager().canScrollHorizontally()) {
                if (mLastTouchXField == null) {
                    mLastTouchXField = RecyclerView.class.getDeclaredField("mLastTouchX");
                    mLastTouchXField.setAccessible(true);
                }
                mLastTouchXField.set(this, mTouchX[mTouchX.length - 2]);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static class TopWrappedDividerItemDecoration extends RecyclerView.ItemDecoration {
        public static final int HORIZONTAL = LinearLayout.HORIZONTAL;
        public static final int VERTICAL = LinearLayout.VERTICAL;

        private static final String TAG = "TopWrappedDividerItemDecoration";
        private static final int[] ATTRS = new int[]{android.R.attr.listDivider};

        private Drawable mDivider;

        /**
         * Current orientation. Either {@link #HORIZONTAL} or {@link #VERTICAL}.
         */
        private int mOrientation;

        private final Rect mBounds = new Rect();

        /**
         * Creates a divider {@link RecyclerView.ItemDecoration} that can be used with a
         * {@link LinearLayoutManager}.
         *
         * @param context     Current context, it will be used to access resources.
         * @param orientation Divider orientation. Should be {@link #HORIZONTAL} or {@link #VERTICAL}.
         */
        @SuppressLint("LongLogTag")
        public TopWrappedDividerItemDecoration(Context context, int orientation) {
            final TypedArray a = context.obtainStyledAttributes(ATTRS);
            mDivider = a.getDrawable(0);
            if (mDivider == null) {
                Log.w(TAG, "@android:attr/listDivider was not set in the theme used for this "
                        + "DividerItemDecoration. Please set that attribute all call setDrawable()");
            }
            a.recycle();
            setOrientation(orientation);
        }

        /**
         * Sets the orientation for this divider. This should be called if
         * {@link RecyclerView.LayoutManager} changes orientation.
         *
         * @param orientation {@link #HORIZONTAL} or {@link #VERTICAL}
         */
        public void setOrientation(int orientation) {
            if (orientation != HORIZONTAL && orientation != VERTICAL) {
                throw new IllegalArgumentException(
                        "Invalid orientation. It should be either HORIZONTAL or VERTICAL");
            }
            mOrientation = orientation;
        }

        /**
         * Sets the {@link Drawable} for this divider.
         *
         * @param drawable Drawable that should be used as a divider.
         */
        public void setDrawable(@NonNull Drawable drawable) {
            mDivider = drawable;
        }

        @Override
        public void onDraw(Canvas c, RecyclerView parent, RecyclerView.State state) {
            if (parent.getLayoutManager() == null || mDivider == null)
                return;
            if (mOrientation == VERTICAL)
                drawVertical(c, parent);
            else
                drawHorizontal(c, parent);
        }

        private void drawVertical(Canvas canvas, RecyclerView parent) {
            canvas.save();
            final int left, right;
            if (parent.getClipToPadding()) {
                left = parent.getPaddingLeft();
                right = parent.getWidth() - parent.getPaddingRight();
                canvas.clipRect(left, parent.getPaddingTop(), right,
                        parent.getHeight() - parent.getPaddingBottom());
            } else {
                left = 0;
                right = parent.getWidth();
            }

            final int childCount = parent.getChildCount();
            for (int i = 0; i < childCount; i++) {
                View child = parent.getChildAt(i);
                parent.getDecoratedBoundsWithMargins(child, mBounds);
                final int bottom = mBounds.bottom + Math.round(child.getTranslationY());
                final int top = bottom - mDivider.getIntrinsicHeight();
                mDivider.setBounds(left, top, right, bottom);
                mDivider.draw(canvas);
                // draw the divider of recycler's top edge
                if (i == 0) {
                    mDivider.setBounds(left, parent.getPaddingTop(), right,
                            parent.getPaddingTop() + mDivider.getIntrinsicHeight());
                    mDivider.draw(canvas);
                }
            }
            canvas.restore();
        }

        private void drawHorizontal(Canvas canvas, RecyclerView parent) {
            canvas.save();
            final int top, bottom;
            if (parent.getClipToPadding()) {
                top = parent.getPaddingTop();
                bottom = parent.getHeight() - parent.getPaddingBottom();
                canvas.clipRect(parent.getPaddingLeft(), top,
                        parent.getWidth() - parent.getPaddingRight(), bottom);
            } else {
                top = 0;
                bottom = parent.getHeight();
            }

            final int childCount = parent.getChildCount();
            for (int i = 0; i < childCount; i++) {
                View child = parent.getChildAt(i);
                parent.getLayoutManager().getDecoratedBoundsWithMargins(child, mBounds);
                final int right = mBounds.right + Math.round(child.getTranslationX());
                final int left = right - mDivider.getIntrinsicWidth();
                mDivider.setBounds(left, top, right, bottom);
                mDivider.draw(canvas);
                // draw the divider of recycler's start edge
                if (i == 0) {
                    mDivider.setBounds(parent.getPaddingLeft(), top,
                            parent.getPaddingLeft() + mDivider.getIntrinsicWidth(), bottom);
                    mDivider.draw(canvas);
                }
            }
            canvas.restore();
        }

        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent,
                                   RecyclerView.State state) {
            if (mDivider == null) {
                outRect.set(0, 0, 0, 0);
                return;
            }
            if (mOrientation == VERTICAL) {
                if (parent.getLayoutManager().getPosition(view) == 0)
                    outRect.top += mDivider.getIntrinsicHeight();
                outRect.bottom += mDivider.getIntrinsicHeight();
            } else {
                if (parent.getLayoutManager().getPosition(view) == 0)
                    outRect.left += mDivider.getIntrinsicWidth();
                outRect.right += mDivider.getIntrinsicWidth();
            }
        }
    }
}