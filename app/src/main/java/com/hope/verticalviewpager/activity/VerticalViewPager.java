package com.hope.verticalviewpager.activity;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.FrameLayout;
import android.widget.Scroller;

import com.hope.verticalviewpager.R;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * Created by Hope on 15/11/20.
 */
public class VerticalViewPager extends FrameLayout {

    private static final String TAG = VerticalViewPager.class.getSimpleName();

    private static final int SNAP_VELOCITY_DIP_PER_SECOND = 300;

    protected static final float FRICTION = 2.0f;

    private static final int ANIM_DURATION = 400;

    private static final int DURATION = 500;

    /** 往下拖拽 */
    private static final int TOUCH_DRAG_DOWN = 1;

    private static final int TOUCH_DRAG_DOWN_ROLL_BACK = 9;
    /** 往上拖拽 */
    private static final int TOUCH_DRAG_UP = 2;

    private static final int TOUCH_DRAG_UP_ROLL_BACK = 5;

    private static final int TOUCH_DRAG_NORMAL = 3;

    private static final int TOUCH_STATE_ANIM = 4;

    private int mTouchState = TOUCH_DRAG_NORMAL;

    private static final int CHILD_COUNT = 5;

    private int mMinSlideDistance;

    private int childPaddingLeft;
    private int childPaddingRight;
    private int childPaddingBottom;
    private int childPaddingTop;

    private float childOffset;

    private int mChildLayoutId;

    private Scroller mScroller;

    private int mCurrentItem = 1;

    private OnVerticalPagerListener mListener;

    private boolean isUserRefresh = true;

    private List<Object> mDataSource = new ArrayList<>();

    private float mTouchDownY;
    private float mTouchMoveY;

    private float mLastDistanceY;

    /** 速度跟踪 */
    private VelocityTracker mVelocityTracker;

    private int mMaximumVelocity;

    private int mDensityAdjustedSnapVelocity;

    public VerticalViewPager(Context context) {
        super(context);
    }

    public VerticalViewPager(Context context, AttributeSet attrs) {
        super(context, attrs);

        init(attrs);
    }

    public VerticalViewPager(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        init(attrs);
    }

    private void init(AttributeSet attrs) {
        TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.VerticalViewPager);
        mChildLayoutId = a.getResourceId(R.styleable.VerticalViewPager_child_layout_id, 0);

        if(mChildLayoutId == 0) {
            new RuntimeException("Please set child layout id");
        }

        childOffset = a.getFloat(R.styleable.VerticalViewPager_childOffset, DisplayUtil.dip2px(getContext(), 10));
        childPaddingTop = a.getInt(R.styleable.VerticalViewPager_childPaddingTop, DisplayUtil.dip2px(getContext(), 100));
        childPaddingLeft  = a.getInt(R.styleable.VerticalViewPager_childPaddingLeft, DisplayUtil.dip2px(getContext(), 30));
        childPaddingBottom  = a.getInt(R.styleable.VerticalViewPager_childPaddingBottom, DisplayUtil.dip2px(getContext(), 30));
        childPaddingRight = a.getInt(R.styleable.VerticalViewPager_childPaddingRight, DisplayUtil.dip2px(getContext(), 30));
        a.recycle();

        for (int i = 0; i < CHILD_COUNT; i++) {
            View child = LayoutInflater.from(getContext()).inflate(mChildLayoutId, null);
            child.setId(i);
            child.setVisibility(View.VISIBLE);
            addView(child, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        }

        DisplayMetrics displayMetrics = new DisplayMetrics();
        ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getMetrics(displayMetrics);
        mDensityAdjustedSnapVelocity = (int) (displayMetrics.density * SNAP_VELOCITY_DIP_PER_SECOND);

        final ViewConfiguration configuration = ViewConfiguration.get(getContext());
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();

        mScroller = new Scroller(getContext(), new DecelerateInterpolator());
    }

    public void setListener(OnVerticalPagerListener listener) {
        this.mListener = listener;

        notifyDataSetChanged();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int totalChildCount = getChildCount();

        int mTotalChildOffset = 0;

        for (int i = 0; i < totalChildCount; i++) {
            View childView = getChildAt(i);

            int width = widthMeasureSpec - childPaddingLeft - childPaddingRight + mTotalChildOffset;
            int height = heightMeasureSpec - childPaddingTop - childPaddingBottom + mTotalChildOffset;

            childView.measure(width, height);

            if(i == totalChildCount - 2) {
                getChildAt(i + 1).measure(width, height);
                break;
            }
            mTotalChildOffset += childOffset;
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        if(isUserRefresh) {

            mMinSlideDistance = getHeight() / 5;

            isUserRefresh = false;
            int totalChildCount = getChildCount();
            int mTotalChildOffset = 0;

            getHideView().setVisibility(View.GONE);
            if(mDataSource != null && mDataSource.size() > 0) {
                int surplus = mDataSource.size() - mCurrentItem;

                for (int i = 1; i < totalChildCount; i++) {
                    getChildAt(i).setVisibility(View.VISIBLE);
                }

                switch (surplus) {
                    case 0:
                        getChildAt(1).setVisibility(View.GONE);
                        getChildAt(2).setVisibility(View.GONE);
                        break;
                    case 1:
                        getChildAt(1).setVisibility(View.GONE);
                        break;
                    default:

                        break;
                }
            }

            for (int i = 0 ; i < totalChildCount; i++) {
                View childView = getChildAt(i);
                int l = (getWidth() - childView.getMeasuredWidth()) / 2;
                int t = childPaddingTop - mTotalChildOffset;

                childView.layout(left + l, t,
                        left + l + childView.getMeasuredWidth(), t + childView.getMeasuredHeight());

                MarginLayoutParams childParam = (MarginLayoutParams) childView.getLayoutParams();
                childParam.setMargins(childView.getLeft(), childView.getTop(), childView.getRight(), 0);
                if(i == totalChildCount - 2) { //把最后一个藏起来
                    View nextView = getChildAt(i + 1);

                    MarginLayoutParams params = (MarginLayoutParams) nextView.getLayoutParams();
                    params.setMargins(childView.getLeft(), -nextView.getMeasuredHeight(), childView.getRight(), 0);
                    break;
                }

                mTotalChildOffset += childOffset * 2;
            }

            notifyDataSetChanged();
        }
    }

    public int getCurrentItem() {
        return mCurrentItem;
    }

    public void notifyDataSetChanged() {
        for (int i = 1; i < CHILD_COUNT - 1; i++) {
            if(mListener != null) {
                if(getChildAt(i).getVisibility() == View.GONE) {
                    continue;
                }
                int value = mCurrentItem - 1 + (3 - i);
                mListener.onPageSelected(getChildAt(i), value);
            }
        }
        if(mCurrentItem != 1 && mListener != null) {
            mListener.onPageSelected(getMoveView(), mCurrentItem - 2);
        }
    }

    public void setDataSource(List<? extends Object> list) {
        mDataSource.clear();

        if(list != null && list.size() > 0) {
            mDataSource.addAll(list);
        }
        notifyDataSetChanged();
    }

    private View getMoveView() {
        return getChildAt(getChildCount() - 1);
    }

    private View getTopView() {
        return getChildAt(getChildCount() - 2);
    }

    private View getHideView() {
        return getChildAt(0);
    }

    private float mInterceptDownY;
    private float mInterceptMoveY;

    private int mFinalPaddingTop;

    private void moveDown(float distance) {
        int top =  Math.min(-getMoveView().getMeasuredHeight() + (int) distance, getFinalPaddingTop());

        MarginLayoutParams params = (MarginLayoutParams) getMoveView().getLayoutParams();
        params.topMargin = top;

        getMoveView().setLayoutParams(params);
    }

    private int getFinalPaddingTop() {

        if(mFinalPaddingTop == 0) {
            mFinalPaddingTop = getTopView().getTop();
        }
        return mFinalPaddingTop;
    }

    private void moveUp(float distance) {
        int left = getTopView().getLeft();
        int top = Math.min(getFinalPaddingTop() + (int) distance, getFinalPaddingTop());
        getTopView().layout(left, top, left + getTopView().getWidth(), top + getTopView().getHeight());
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if(!mScroller.isFinished()) {
            return false;
        }
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                
                mInterceptDownY = ev.getY();
                break;
            case MotionEvent.ACTION_MOVE:
                mInterceptMoveY = ev.getY();

                float distanceY = mInterceptMoveY - mInterceptDownY;

                boolean isIntercept = resetIntercept((int)distanceY);
                mTouchDownY = mInterceptDownY;

                return isIntercept;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                break;
        }
        return false;
    }

    private boolean resetIntercept(int distance) {
        boolean isIntercept = true;
        if(mDataSource == null || mDataSource.size() == 0) {
            return false;
        }
        if((Math.abs(distance) < 30 && mTouchState == TOUCH_DRAG_NORMAL) || mTouchState == TOUCH_STATE_ANIM) {
            isIntercept = false;
        }

        if(mCurrentItem == 1 && distance > 0) {
            isIntercept = false;
        }
        if(mCurrentItem >= mDataSource.size() && distance < 0) {
            isIntercept = false;
        }
        return isIntercept;
    }

    private boolean isIntercept = false;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if(!mScroller.isFinished()) {
            return false;
        }
        if (mVelocityTracker == null)
            mVelocityTracker = VelocityTracker.obtain();

        mVelocityTracker.addMovement(event);

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mTouchDownY = event.getY();
                break;
            case MotionEvent.ACTION_MOVE:
                mTouchMoveY = event.getY();
                mLastDistanceY = (mTouchMoveY - mTouchDownY) / FRICTION;
                isIntercept = resetIntercept((int)mLastDistanceY);
                if(isIntercept) {
                    if(mTouchState == TOUCH_DRAG_NORMAL) {
                        mTouchState = mLastDistanceY > 0 ? TOUCH_DRAG_DOWN : TOUCH_DRAG_UP;
                    }

                    switch (mTouchState) {
                        case TOUCH_DRAG_DOWN:
                            mLastDistanceY = Math.max(mLastDistanceY, 0);
                            moveDown(mLastDistanceY);
                            break;
                        case TOUCH_DRAG_UP:
                            mLastDistanceY = Math.min(mLastDistanceY, 0);
                            moveUp(mLastDistanceY);
                            break;
                    }
                }
               return isIntercept;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                if(mLastDistanceY != 0 && isIntercept) {
//                    mTouchState = mLastDistanceY > 0 ? TOUCH_DRAG_DOWN : TOUCH_DRAG_UP;
                    mLastDistanceY = (mTouchMoveY - mTouchDownY) / FRICTION;


                    final VelocityTracker velocityTracker = mVelocityTracker;
                    // 计算当前速度
                    velocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);

                    // y方向的速度
                    int velocity = (int) velocityTracker.getYVelocity();

                    switch (mTouchState) {
                        case TOUCH_DRAG_DOWN:
                            if(Math.abs(velocity) > mDensityAdjustedSnapVelocity || Math.abs(mLastDistanceY) > mMinSlideDistance) { //满足滑动条件
                                mScroller.startScroll(0, 0, 0,
                                        getFinalPaddingTop() + getTopView().getMeasuredHeight() - (int) mLastDistanceY, DURATION);
                            } else {
                                mTouchState = TOUCH_DRAG_DOWN_ROLL_BACK;

                                mScroller.startScroll(0, 0, 0, Math.abs((int)mLastDistanceY), DURATION);
                            }

                            break;
                        case TOUCH_DRAG_UP:
                            if(Math.abs(velocity) > mDensityAdjustedSnapVelocity || Math.abs(mLastDistanceY) > mMinSlideDistance) { //满足滑动条件
                                mScroller.startScroll(0, 0, 0,
                                        getFinalPaddingTop() + getTopView().getMeasuredHeight() - (int) mLastDistanceY, DURATION);
                            } else {
                                mTouchState = TOUCH_DRAG_UP_ROLL_BACK;

                                mScroller.startScroll(0, 0, 0, Math.abs((int)mLastDistanceY), DURATION);
                            }
                            break;
                    }
                } else {
                    mTouchState = TOUCH_DRAG_NORMAL;
                }
                break;
        }
        return true;
    }

    @Override
    public void computeScroll() {
        super.computeScroll();
        if (mScroller.computeScrollOffset()) {
            switch (mTouchState) {
                case TOUCH_DRAG_DOWN:
                    moveDown(mLastDistanceY + mScroller.getCurrY());
                    if(mScroller.isFinished()) {
                        dragDownFinishedAnim();
                    }
                    break;
                case TOUCH_DRAG_DOWN_ROLL_BACK:
                    moveDown(mLastDistanceY - mScroller.getCurrY());
                    if(mScroller.isFinished()) {
                        mTouchState = TOUCH_DRAG_NORMAL;
                    }
                    break;
                case TOUCH_DRAG_UP_ROLL_BACK:
                    moveUp(mLastDistanceY + mScroller.getCurrY());
                    if(mScroller.isFinished()) {
                        mTouchState = TOUCH_DRAG_NORMAL;
                    }
                    break;
                case TOUCH_DRAG_UP:
                    moveUp(mLastDistanceY - mScroller.getCurrY());
                    if(mScroller.isFinished()) {
                        dragUpFinishedAnim();
                    }
                    break;
            }
        }
        postInvalidate();
    }

    private boolean isUpward = false;
    private boolean isChangeItem = false;

    private void dragUpFinishedAnim() {
        isChangeItem = false;
        isUpward = true;

        for (int i = getChildCount() - 1; i > 0 ; i--) {
            if(mDataSource.size() - mCurrentItem <= 2 && getChildAt(i).getVisibility() == View.GONE) {
                continue;
            }
            ExpandAnimation mExpandAnimation = new ExpandAnimation(false, false, getChildAt(i));
            mExpandAnimation.start();
        }
    }

    private void dragDownFinishedAnim() {
        isUpward = false;
        isChangeItem = false;
        for (int i = 1; i < getChildCount() - 1; i++) {
            if(getChildAt(i).getVisibility() == View.GONE) {
                continue;
            }
            NarrowAnimation mNarrowAnimation = new NarrowAnimation(false, false, getChildAt(i));
            mNarrowAnimation.setHide(i == 1 ? true : false);

            mNarrowAnimation.start();
        }
    }

    private float mNarrowWidthScale = 0;
    private float mNarrowHightScale = 0;
    private float mExpandHeightScale = 0;
    private float mExpandWidthScale = 0;

    private class NarrowAnimation extends AnimationSet implements Animation.AnimationListener {

        protected View mCurrentView;

        protected int childHeight;
        protected int childWidth;

        protected boolean isHide = false;

        protected TranslateAnimation mTranslateAnim;

        protected ScaleAnimation mScaleAnim;

        protected AlphaAnimation mAlphaAnim;

        public NarrowAnimation(boolean shareInterpolator,boolean isHide, View view) {
            super(shareInterpolator);

            this.isHide = isHide;
            addView(view);
        }

        @Override
        public void onAnimationStart(Animation animation) {
            mTouchState = TOUCH_STATE_ANIM;
        }

        @Override
        public synchronized void onAnimationEnd(Animation animation) {
            mTouchState = TOUCH_DRAG_NORMAL;
            if(!isChangeItem) {
                mCurrentItem = isUpward ? ++mCurrentItem : --mCurrentItem;
            }

            isChangeItem = true;
            isUserRefresh = true;
            mCurrentView.clearAnimation();

            requestLayout();
        }

        @Override
        public void onAnimationRepeat(Animation animation) {

        }

        public void addView(View view) {

            this.mCurrentView = view;
            childHeight = this.mCurrentView.getMeasuredHeight();
            childWidth = this.mCurrentView.getMeasuredWidth();
            mCurrentView.setAnimation(this);

            setDuration(ANIM_DURATION);
            setFillAfter(true);
            setAnimationListener(this);
        }

        public void setHide(boolean isHide) {
            this.isHide = isHide;
        }

        @Override
        public void start() {
            if(mTranslateAnim == null) {
                mTranslateAnim = new TranslateAnimation(Animation.ABSOLUTE, 0,
                        Animation.ABSOLUTE, 0, Animation.ABSOLUTE, 0, Animation.ABSOLUTE, childOffset);

                addAnimation(mTranslateAnim);
            }
            if(mScaleAnim == null) {
                if(mNarrowHightScale == 0) {
                    mNarrowHightScale = 1 - childOffset / childHeight;;
                }
                if(mNarrowWidthScale == 0) {
                    mNarrowWidthScale = 1- childOffset / childWidth;
                }
                mScaleAnim = new ScaleAnimation(1.0F, mNarrowWidthScale,
                        1.0F, mNarrowHightScale,
                        Animation.RELATIVE_TO_SELF, 0.5F, Animation.RELATIVE_TO_SELF, 1.0F);

                addAnimation(mScaleAnim);
            }

            if(isHide) {
                if(mAlphaAnim == null) {
                    mAlphaAnim = new AlphaAnimation(1.0F, 0.0F);
                }
                addAnimation(mAlphaAnim);
            }

            super.start();
        }

    }

    private class ExpandAnimation extends NarrowAnimation {

        public ExpandAnimation(boolean shareInterpolator, boolean isHide, View view) {
            super(shareInterpolator, isHide, view);
        }

        @Override
        public void start() {
            if(mTranslateAnim == null) {
                mTranslateAnim = new TranslateAnimation(Animation.ABSOLUTE, 0,
                        Animation.ABSOLUTE, 0, Animation.ABSOLUTE, 0, Animation.ABSOLUTE, -childOffset);

                addAnimation(mTranslateAnim);
            }
            if(mScaleAnim == null) {
                if(mExpandHeightScale == 0) {
                    mExpandHeightScale = 1 + childOffset / childHeight;;
                }
                if(mExpandWidthScale == 0) {
                    mExpandWidthScale = 1 + childOffset / childWidth;
                }
                mScaleAnim = new ScaleAnimation(1.0F, mExpandWidthScale,
                        1.0F, mExpandHeightScale,
                        Animation.RELATIVE_TO_SELF, 0.5F, Animation.RELATIVE_TO_SELF, 1.0F);

                addAnimation(mScaleAnim);
            }

            if(isHide) {
                if(mAlphaAnim == null) {
                    mAlphaAnim = new AlphaAnimation(1.0F, 0.0F);
                }
                addAnimation(mAlphaAnim);
            }

            super.start();
        }
    }

    public interface OnVerticalPagerListener {

        public void onPageSelected(View childView, int position);
    }
}
