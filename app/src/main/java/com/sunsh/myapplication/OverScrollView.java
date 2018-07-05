package com.sunsh.myapplication;

import android.support.annotation.IntDef;
import android.view.MotionEvent;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Created by sunsh on 2018/6/7.
 */

public interface OverScrollView {
    int OVERSCROLL_EDGE_UNSPECIFIED = 0;
    int OVERSCROLL_EDGE_TOP = 1;
    int OVERSCROLL_EDGE_BOTTOM = 2;
    int OVERSCROLL_EDGE_TOP_OR_BOTTOM = OVERSCROLL_EDGE_TOP | OVERSCROLL_EDGE_BOTTOM;
    int OVERSCROLL_EDGE_START = 4;
    int OVERSCROLL_EDGE_END = 8;
    int OVERSCROLL_EDGE_START_OR_END = OVERSCROLL_EDGE_START | OVERSCROLL_EDGE_END;

    @IntDef({
            OVERSCROLL_EDGE_UNSPECIFIED,
            OVERSCROLL_EDGE_TOP, OVERSCROLL_EDGE_BOTTOM, OVERSCROLL_EDGE_TOP_OR_BOTTOM,
            OVERSCROLL_EDGE_START, OVERSCROLL_EDGE_END, OVERSCROLL_EDGE_START_OR_END
    })
    @Retention(RetentionPolicy.SOURCE)
    @interface OverScrollEdge {
    }

    int OVERSCROLL_STATE_IDLE = 0;
    int OVERSCROLL_STATE_TOUCH_SCROLL = 1;
    int OVERSCROLL_STATE_AUTO_SCROLL = 2;

    @IntDef({
            OVERSCROLL_STATE_IDLE, OVERSCROLL_STATE_TOUCH_SCROLL, OVERSCROLL_STATE_AUTO_SCROLL
    })
    @Retention(RetentionPolicy.SOURCE)
    @interface OverScrollState {
    }

    boolean handleOverScroll(MotionEvent ev);

    // @formatter:off
    void addOnOverScrollListener(OnOverScrollListener listener);
    void removeOnOverScrollListener(OnOverScrollListener listener);
    void clearOnOverScrollListeners();
    // @formatter:on

    interface OnOverScrollListener {
        void onOverScrollStart(OverScrollView view, @OverScrollEdge int edge);

        void onOverScrollEnd(OverScrollView view, @OverScrollEdge int edge);

        void onOverScrollDistanceChange(OverScrollView view, float distance);

        void onOverScrollStateChange(OverScrollView view, @OverScrollState int state);
    }
}
