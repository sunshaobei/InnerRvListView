package com.sunsh.myapplication.listener;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.ViewCompat;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.JELLY_BEAN_MR1;

/**
 * Created by sunsh on 2018/6/7.
 */

public class OverFlyingDetector extends GestureDetector.SimpleOnGestureListener {
    // @formatter:off
    private final View mView;

    private final OnOverFlyingListener mOnOverFlyingListener;

    private final OverFlyingHandler mHandler;

    private final GestureDetector mGestureDetector;

    private final int mTouchSlop;

    /** 手指放下到抬起时的水平位移改变量，向水平结束端滑为正 */
    private float mDeltaX;
    /** 手指放下到抬起时的的竖直位移改变量，向下滑为正 */
    private float mDeltaY;

    private final float mOverFlyingMinimumVelocity; // 800 dp/s
    private final float mOverFlyingMaximumVelocity; // 8000 dp/s

    private static final float RATIO_OVER_DIST_TO_VELOCITY = 1f / 100f;

    private float mOverFlyingDistX;
    private float mOverFlyingDistY;

    private static final int BASE_DURATION_OVERFLYING = 64; // ms
    // @formatter:on

    public float getOverFlyingMinimumVelocity() {
        return mOverFlyingMinimumVelocity;
    }

    public float getOverFlyingMaximumVelocity() {
        return mOverFlyingMaximumVelocity;
    }

    public float getOverFlyingMinimumDistance() {
        return mOverFlyingMinimumVelocity * RATIO_OVER_DIST_TO_VELOCITY;
    }

    public float getOverFlyingMaximumDistance() {
        return mOverFlyingMaximumVelocity * RATIO_OVER_DIST_TO_VELOCITY;
    }

    public static int getOverFlyingMinimumDuration() {
        return BASE_DURATION_OVERFLYING;
    }

    public static int getOverFlyingMaximumDuration() {
        return BASE_DURATION_OVERFLYING * 2;
    }

    public OverFlyingDetector(@NonNull View view, @NonNull OnOverFlyingListener listener) {
        this(view, listener, null);
    }

    public OverFlyingDetector(@NonNull View view, @NonNull OnOverFlyingListener listener,
                              @Nullable Handler handler) {
        mView = view;
        mOnOverFlyingListener = listener;
        mHandler = handler == null ? new OverFlyingHandler() : new OverFlyingHandler(handler);
        mGestureDetector = new GestureDetector(view.getContext(), this, mHandler);

        ViewConfiguration vc = ViewConfiguration.get(view.getContext());
        mTouchSlop = vc.getScaledTouchSlop();
        mOverFlyingMaximumVelocity = vc.getScaledMaximumFlingVelocity();
        mOverFlyingMinimumVelocity = mOverFlyingMaximumVelocity / 10f;
    }

    public final void onTouchEvent(MotionEvent ev) {
        mGestureDetector.onTouchEvent(ev);
    }

    /*
     * fling到两端时才触发OverFling，获取速度并采用演示策略估算View是否滚动到边界
     * 1.监听fling动作 2.获取手指滑动速度（存在滑动但非fling的状态）
     */
    @Override
    public boolean onFling(MotionEvent downEvent, MotionEvent upEvent, float velocityX, float velocityY) {
        mDeltaX = isViewLayoutRtl() ? downEvent.getX() - upEvent.getX() : upEvent.getX() - downEvent.getX();
        mDeltaY = upEvent.getY() - downEvent.getY();
        final float absDX = Math.abs(mDeltaX);
        final float absDY = Math.abs(mDeltaY);

        final float absVy = Math.abs(velocityY);
        final float absVx = Math.abs(velocityX);
        // 上下flying
        if (absDY > absDX && absDY >= mTouchSlop && absVy >= mOverFlyingMinimumVelocity) {
            mOverFlyingDistY = absVy * RATIO_OVER_DIST_TO_VELOCITY;
            mHandler.sendEmptyMessage(OverFlyingHandler.MSG_START_COMPUTE_FLYING);

            // 左右flying
        } else if (absDX > absDY && absDX >= mTouchSlop && absVx >= mOverFlyingMinimumVelocity) {
            mOverFlyingDistX = absVx * RATIO_OVER_DIST_TO_VELOCITY;
            mHandler.sendEmptyMessage(OverFlyingHandler.MSG_START_COMPUTE_FLYING);
        }
        return false;
    }

    @SuppressLint("HandlerLeak")
    private class OverFlyingHandler extends Handler {
        // @formatter:off
        /** 开始计算 */
        private static final int MSG_START_COMPUTE_FLYING = 0;
        /** 继续计算 */
        private static final int MSG_CONTINUE_COMPUTE_FLYING = 1;
        /** 停止计算 */
        private static final int MSG_STOP_COMPUTE_FLYING = 2;

        /** 最大计算次数 */
        private static final int MAX_COMPUTE_TIMES = 100;  // 10ms计算一次,总共计算100次
        /** 每次计算的时间间隔 */
        private static final int TIME_INTERVAL_COMPUTE = 10; // ms

        /** 当前计算次数 */
        private int mCurrComputeTimes = 0;
        // @formatter:on

        public OverFlyingHandler() {
        }

        public OverFlyingHandler(Handler handler) {
            super(handler.getLooper());
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_START_COMPUTE_FLYING:
                    // 先停止正在进行的计算
                    removeCallbacksAndMessages(null);
                    mCurrComputeTimes = -1; //这里没有break,写作-1方便计数
                case MSG_CONTINUE_COMPUTE_FLYING:
                    mCurrComputeTimes++;

                    // 顶部发生过度滚动
                    if (mDeltaY > 0 && isViewAtTop()) {
                        mOnOverFlyingListener.onTopEdgeOverFling(mOverFlyingDistY,
                                computeOverflyingDuration(mOverFlyingDistY));
                        break;

                        // 底部发生过度滚动
                    } else if (mDeltaY < 0 && isViewAtBottom()) {
                        mOnOverFlyingListener.onBottomEdgeOverFling(mOverFlyingDistY,
                                computeOverflyingDuration(mOverFlyingDistY));
                        break;

                        // 水平开始端发生过度滚动
                    } else if (mDeltaX > 0 && isViewAtStart()) {
                        mOnOverFlyingListener.onStartEdgeOverFling(mOverFlyingDistX,
                                computeOverflyingDuration(mOverFlyingDistX));
                        break;

                        // 水平结束端发生过度滚动
                    } else if (mDeltaX < 0 && isViewAtEnd()) {
                        mOnOverFlyingListener.onEndEdgeOverFling(mOverFlyingDistX,
                                computeOverflyingDuration(mOverFlyingDistX));
                        break;
                    }

                    // 计算未超时，继续发送消息并循环计算
                    if (mCurrComputeTimes < MAX_COMPUTE_TIMES)
                        sendEmptyMessageDelayed(MSG_CONTINUE_COMPUTE_FLYING, TIME_INTERVAL_COMPUTE);
                    break;
                case MSG_STOP_COMPUTE_FLYING:
                    removeCallbacksAndMessages(null);
                    break;
            }
        }

        private int computeOverflyingDuration(float dist) {
            final float ratio = dist / getOverFlyingMaximumDistance();
            return (int) (BASE_DURATION_OVERFLYING * (1 + ratio) + 0.5f);
        }
    }

    protected boolean isViewAtStart() {
        // noinspection deprecation
        return !ViewCompat.canScrollHorizontally(mView, isViewLayoutRtl() ? 1 : -1);
    }

    protected boolean isViewAtEnd() {
        // noinspection deprecation
        return !ViewCompat.canScrollHorizontally(mView, isViewLayoutRtl() ? -1 : 1);
    }

    protected boolean isViewAtTop() {
        // noinspection deprecation
        return !ViewCompat.canScrollVertically(mView, -1);
    }

    protected boolean isViewAtBottom() {
        // noinspection deprecation
        return !ViewCompat.canScrollVertically(mView, 1);
    }

    protected boolean isViewLayoutRtl() {
        return SDK_INT >= JELLY_BEAN_MR1 && mView.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
    }

    public interface OnOverFlyingListener {
        void onTopEdgeOverFling(float overHeight, int duration);

        void onBottomEdgeOverFling(float overHeight, int duration);

        void onStartEdgeOverFling(float overWidth, int duration);

        void onEndEdgeOverFling(float overWidth, int duration);
    }

    public static class SimpleOnOverFlyingListener implements OnOverFlyingListener {

        @Override
        public void onTopEdgeOverFling(float overHeight, int duration) {

        }

        @Override
        public void onBottomEdgeOverFling(float overHeight, int duration) {

        }

        @Override
        public void onStartEdgeOverFling(float overWidth, int duration) {

        }

        @Override
        public void onEndEdgeOverFling(float overWidth, int duration) {

        }
    }
}
