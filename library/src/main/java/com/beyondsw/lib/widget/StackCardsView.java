package com.beyondsw.lib.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.database.Observable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;


/**
 * Created by wensefu on 2017/2/10.
 */

public class StackCardsView extends FrameLayout {

    private static final String TAG = "StackCardsView";

    public static boolean DEBUG = true;
    /*-------------------------------------------*/
    /**
     * 左滑
     * --> 1
     */
    public static final int SWIPE_LEFT = 1;

    /**
     * 右滑, 左移运算符，num << 1,相当于num乘以2
     * 1<<1  --> 2
     */
    public static final int SWIPE_RIGHT = 1 << 1;

    /**
     * 上滑
     * 1<<1  --> 4
     */
    public static final int SWIPE_UP = 1 << 2;

    /**
     * 下滑
     * 1<<1  --> 8
     */
    public static final int SWIPE_DOWN = 1 << 3;

    /**
     * 任意方向滑动
     */
    public static final int SWIPE_ALL = SWIPE_LEFT | SWIPE_RIGHT | SWIPE_UP | SWIPE_DOWN;

    /**
     * 禁止滑动
     */
    public static final int SWIPE_NONE = 0;
    /*-------------------------------------------*/

    private Adapter mAdapter;

    /*-------------------------------------------*/
    /**
     * 默认静止时最多可以看到的卡片数
     */
    private static final int MAX_VISIBLE_CNT = 3;

    /**
     * 默认层叠效果高度(dp)
     */
    private static final int EDGE_HEIGHT = 8;

    /**
     * 默认相对前一张卡片的缩放比例
     */
    private static final float SCALE_FACTOR = .8f;

    /**
     * 默认相对前一张卡片的透明度比例
     */
    private static final float ALPHA_FACTOR = .8f;

    /**
     * 默认可以消失的滑动距离与控件宽度比
     */
    private static final float DISMISS_FACTOR = .4f;

    /**
     * 默认卡片消失时的透明度
     */
    private static final float DISMISS_ALPHA = .3f;

    /**
     * 拖动的敏感度
     */
    private static final float DRAG_SENSITIVITY = 2f;
    /*-------------------------------------------*/

    /**
     * -2147483648 无效值.
     */
    private static final int INVALID_SIZE = Integer.MIN_VALUE;


    private int mItemWidth;
    private int mItemHeight;
    private int mMaxVisibleCnt;
    private float mScaleFactor;
    private float mAlphaFactor;
    private float mDismissFactor;
    private int mLayerEdgeHeight;
    private float mDismissAlpha;
    private float mDragSensitivity;
    private float mDismissDistance;

    private InnerDataObserver mDataObserver;
    private boolean mHasRegisteredObserver;

    private ISwipeTouchHelper mTouchHelper;
    private List<OnCardSwipedListener> mCardSwipedListeners;

    private boolean mNeedAdjustChildren;

    private Runnable mPendingTask;

    private float[] mScaleArray;
    private float[] mAlphaArray;
    private float[] mTranslationYArray;

    private int mLastLeft;
    private int mLastTop;
    private int mLastRight;
    private int mLastBottom;

    public StackCardsView(Context context) {
        this(context, null);
    }

    public StackCardsView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public StackCardsView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        /**
         * getChildDrawingOrder 用于 返回当前迭代子视图的索引.就是说 获取当前正在绘制的视图索引.如果需要改变ViewGroup
         * 子视图绘制的顺序,则需要重载这个方法.并且需要先调用 setChildrenDrawingOrderEnabled(boolean) 方法来启用子视图排序功能.
         * isChildrenDrawingOrderEnabled()则是 ,获取当前这个ViewGroup是否是按照顺序进行绘制的
         */
        setChildrenDrawingOrderEnabled(true);

        final TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.StackCardsView, defStyleAttr, 0);

        mItemWidth = a.getDimensionPixelSize(R.styleable.StackCardsView_itemWidth, INVALID_SIZE);
        if (mItemWidth == INVALID_SIZE) {
            throw new IllegalArgumentException("itemWidth must be specified");
        }
        mItemHeight = a.getDimensionPixelSize(R.styleable.StackCardsView_itemHeight, INVALID_SIZE);
        if (mItemHeight == INVALID_SIZE) {
            throw new IllegalArgumentException("itemHeight must be specified");
        }
        mMaxVisibleCnt = a.getInt(R.styleable.StackCardsView_maxVisibleCnt, MAX_VISIBLE_CNT);
        mScaleFactor = a.getFloat(R.styleable.StackCardsView_scaleFactor, SCALE_FACTOR);
        mAlphaFactor = a.getFloat(R.styleable.StackCardsView_alphaFactor, ALPHA_FACTOR);
        mDismissFactor = a.getFloat(R.styleable.StackCardsView_dismissFactor, DISMISS_FACTOR);
        mLayerEdgeHeight = a.getDimensionPixelSize(R.styleable.StackCardsView_edgeHeight, (int) dp2px(context, EDGE_HEIGHT));
        mDismissAlpha = a.getFloat(R.styleable.StackCardsView_dismissAlpha, DISMISS_ALPHA);
        mDragSensitivity = a.getFloat(R.styleable.StackCardsView_dragSensitivity, DRAG_SENSITIVITY);

        a.recycle();
    }

    //    系统方法
    public static float dp2px(Context context, float dp) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, dm);
    }

    /*----------------卡片移除监听-------------------*/
    public interface OnCardSwipedListener {

        void onCardDismiss(int direction);

        void onCardScrolled(View view, float progress, int direction);
    }

    public void addOnCardSwipedListener(OnCardSwipedListener listener) {
        if (mCardSwipedListeners == null) {
            mCardSwipedListeners = new ArrayList<>();
            mCardSwipedListeners.add(listener);
        } else if (!mCardSwipedListeners.contains(listener)) {
            mCardSwipedListeners.add(listener);
        }
    }

    public void removeOnCardSwipedListener(OnCardSwipedListener listener) {
        if (mCardSwipedListeners != null && mCardSwipedListeners.contains(listener)) {
            mCardSwipedListeners.remove(listener);
        }
    }
    /*-----------------------------------*/

    /*---------------todo:不明所以--------------------*/
    @Override
    public void addView(View child) {
        throw new UnsupportedOperationException("addView(View) is not supported");
    }

    @Override
    public void addView(View child, int index) {
        throw new UnsupportedOperationException("addView(View, int) is not supported");
    }

    @Override
    public void removeView(View child) {
        throw new UnsupportedOperationException("removeView(View) is not supported");
    }

    @Override
    public void removeViewAt(int index) {
        throw new UnsupportedOperationException("removeViewAt(int) is not supported");
    }

    @Override
    public void removeAllViews() {
        throw new UnsupportedOperationException("removeAllViews() is not supported");
    }
    /*-----------------------------------*/

    float getDragSensitivity() {
        return mDragSensitivity;
    }

    public float getDismissDistance() {
        if (mDismissDistance > 0) {
            return mDismissDistance;
        }
        mDismissDistance = getWidth() * mDismissFactor;
        return mDismissDistance;
    }

    float getDismissAlpha() {
        return mDismissAlpha;
    }

    /*-------------------onLayout----------------------------*/
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        if (mNeedAdjustChildren) {

            adjustChildren();
            if (mTouchHelper != null) {
                mTouchHelper.onChildChanged();
            }
            mNeedAdjustChildren = false;

        }

        int cnt = getChildCount();
        if (cnt > 0) {
            View last = getChildAt(cnt - 1);
            mLastLeft = last.getLeft();
            mLastTop = last.getTop();
            mLastRight = last.getRight();
            mLastBottom = last.getBottom();
        }
    }

    private void adjustChildren() {

        final int cnt = getChildCount();
        if (cnt == 0) {
            return;
        }

        float scale = 0;
        float alpha;
        float translationY = 0;
        int half_childHeight = 0;

//        可见视图中最后边的那个最小的那个.
        int maxVisibleIndex = Math.min(cnt, mMaxVisibleCnt) - 1;

//        透明,缩放,移动.可是个数的数组.
        mScaleArray = new float[cnt];
        mAlphaArray = new float[cnt];
        mTranslationYArray = new float[cnt];

        for (int i = 0; i <= maxVisibleIndex; i++) {

            View child = getChildAt(i);

            if (half_childHeight == 0) {
                half_childHeight = child.getMeasuredHeight() / 2;
            }

//            pow:返回第一个参数的第二个参数次幂的值.
            scale = (float) Math.pow(mScaleFactor, i);
            mScaleArray[i] = scale;

            alpha = (float) Math.pow(mAlphaFactor, i);
            mAlphaArray[i] = alpha;

            translationY = half_childHeight * (1 - scale) + mLayerEdgeHeight * i;
            mTranslationYArray[i] = translationY;

//           摆放操作.
            child.setScaleX(scale);
            child.setScaleY(scale);
            child.setAlpha(alpha);
            child.setTranslationY(translationY);

        }

//        最后不可见的地方,除过可见的以外的子孩子.也就是和最后一个可见的子孩子大小放置位置都一样但是透明度为0.
        for (int i = maxVisibleIndex + 1; i < cnt; i++) {

            View child = getChildAt(i);

            mScaleArray[i] = scale;
            mAlphaArray[i] = 0;
            mTranslationYArray[i] = translationY;

            child.setScaleX(scale);
            child.setScaleY(scale);
            child.setAlpha(0);
            child.setTranslationY(translationY);

        }
    }

    /*-------------------------------------------------*/
    void onCoverStatusChanged(boolean idle) {
        if (idle) {
            if (mPendingTask != null) {
                mPendingTask.run();
                mPendingTask = null;
            }
        }
    }

    void onCardDismissed(int direction) {
        if (mCardSwipedListeners != null) {
            for (OnCardSwipedListener listener : mCardSwipedListeners) {
                listener.onCardDismiss(direction);
            }
        }
    }

    void tryAppendChild() {
        final int childCount = getChildCount();
        if (mAdapter.getCount() > childCount) {
            View view = mAdapter.getView(childCount, null, StackCardsView.this);
            addViewInLayout(view, -1, buildLayoutParams(mAdapter, childCount), true);
            view.layout(mLastLeft, mLastTop, mLastRight, mLastBottom);
            if (mTouchHelper != null) {
                mTouchHelper.onChildAppend();
            }
        }
    }

    void onCoverScrolled(View scrollingView, float progress, int direction) {
        if (mCardSwipedListeners != null) {
            for (OnCardSwipedListener listener : mCardSwipedListeners) {
                listener.onCardScrolled(scrollingView, progress, direction);
            }
        }
    }

    void updateChildrenProgress(float progress, View scrollingView) {
        final int cnt = getChildCount();
        int startIndex = indexOfChild(scrollingView) + 1;
        if (startIndex >= cnt) {
            return;
        }
        float oriScale;
        float oriAlpha;
        float oriTranslationY;
        float maxScale;
        float maxAlpha;
        float maxTranslationY;
        float progressScale;
        for (int i = startIndex; i < cnt; i++) {
            View child = getChildAt(i);
            int oriIndex = Math.min(mScaleArray.length - 1, i - startIndex + 1);
            if (child.getVisibility() != View.GONE) {
                if (mScaleArray != null) {
                    oriScale = mScaleArray[oriIndex];
                    maxScale = mScaleArray[i - startIndex];
                    progressScale = oriScale + (maxScale - oriScale) * progress;
                    child.setScaleX(progressScale);
                    child.setScaleY(progressScale);
                }

                if (mAlphaArray != null) {
                    oriAlpha = mAlphaArray[oriIndex];
                    maxAlpha = mAlphaArray[i - startIndex];
                    child.setAlpha(oriAlpha + (maxAlpha - oriAlpha) * progress);
                }

                if (mTranslationYArray != null) {
                    oriTranslationY = mTranslationYArray[oriIndex];
                    maxTranslationY = mTranslationYArray[i - startIndex];
                    child.setTranslationY(oriTranslationY + (maxTranslationY - oriTranslationY) * progress);
                }
            }
        }
    }
    /*-------------------------------------------------*/

    /**
     * 绘制顺序倒序.
     *
     * @param childCount
     * @param i
     * @return
     */
    @Override
    protected int getChildDrawingOrder(int childCount, int i) {
        return childCount - 1 - i;
    }


    /**
     * view绑定activity的时候,在onresume之后调用,一般在此修改尺寸,在oncreate中会报空指针的.
     */
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        safeRegisterObserver();
    }

    /**
     * 分离的时候移除view.
     */
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        safeUnRegisterObserver();
    }

    /**
     * 移除观察者
     */
    private void safeUnRegisterObserver() {
        if (mAdapter != null && mDataObserver != null && mHasRegisteredObserver) {
            mAdapter.unregisterDataObserver(mDataObserver);
            mHasRegisteredObserver = false;
        }
    }

    /**
     * 添加观察者
     */
    private void safeRegisterObserver() {
        safeUnRegisterObserver();
        if (mDataObserver == null) {
            mDataObserver = new InnerDataObserver();
        }
        if (mAdapter != null) {
            mAdapter.registerDataObserver(mDataObserver);
            mHasRegisteredObserver = true;
        }
    }

    //    参数构建.
    private LayoutParams buildLayoutParams(Adapter adapter, int position) {
        return new LayoutParams(mItemWidth, mItemHeight, Gravity.CENTER)
                .swipeDirection(adapter.getSwipeDirection(position))
                .dismissDirection(adapter.getDismissDirection(position))
                .fastDismissAllowed(adapter.isFastDismissAllowed(position))
                .maxRotation(adapter.getMaxRotation(position));
    }

    /**
     * 数据初始化和变化的时候排列子孩子.
     */
    private void initChildren() {

        int cnt = mAdapter == null ? 0 : mAdapter.getCount();

        if (cnt == 0) {
            /**
             * removeAllViewsInLayout()和removeAllViews()都有移除子view的功能,但是removeAllViewsInLayout() 需要先测量当前的布局,
             * 一旦调用该方法,只能移除已经自身布局中已计算好的所包含的子view. 相比而言, removeAllViews() 也调用了removeAllViewsInLayout(),
             * 但是后面还调用了requestLayout(),这个方法是当View的布局发生改变会调用它来更新当前视图, 移除子View会更加彻底. 所以除非必要,
             * 还是推荐使用removeAllViews()这个方法.
             */
            removeAllViewsInLayout();

        } else {

            removeAllViewsInLayout();

//            可见视图
            cnt = Math.min(cnt, mMaxVisibleCnt + 1);
            for (int i = 0; i < cnt; i++) {
//                系统方法和addView类似,但是这个addViewInLayout后边必须调用requestLayout();
                addViewInLayout(
                        mAdapter.getView(i, null, this),
                        -1,
                        buildLayoutParams(mAdapter, i),
                        true);
            }
        }
        mNeedAdjustChildren = true;
        requestLayout();
    }

    /**
     * 设置适配器:先移除后添加,初始化数据.
     *
     * @param adapter
     */
    public void setAdapter(Adapter adapter) {
        safeUnRegisterObserver();
        mAdapter = adapter;
        safeRegisterObserver();
        initChildren();
    }

    public void removeCover(int direction) {
        if (mTouchHelper != null) {
            mTouchHelper.removeCover(direction);
        }
    }

    /**
     * 内部的数据观察者,注意其添加视图和删除视图所采用的方法.
     */
    private class InnerDataObserver extends CardDataObserver {

        @Override
        public void onDataSetChanged() {
            super.onDataSetChanged();

//            当前没有 子view在拖动，做消失动画等,如果有则不进行数据刷新,等待空闲状态时再刷新
            if (mTouchHelper != null && !mTouchHelper.isCoverIdle()) {
//                启动了延时任务.
                mPendingTask = new Runnable() {
                    @Override
                    public void run() {
                        initChildren();
                    }
                };
            } else {
                initChildren();
            }
        }

        @Override
        public void onItemInserted(int position) {
            super.onItemInserted(position);

        }

        @Override
        public void onItemRemoved(int position) {
            View toRemove = getChildAt(position);
            removeViewInLayout(toRemove);
            requestLayout();
        }
    }

    /*-----------------------事件处理交给了mTouchHelper-------------------------------*/
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (mTouchHelper == null) {
            mTouchHelper = new SwipeTouchHelper(this);
        }
        return mTouchHelper.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return mTouchHelper.onTouchEvent(ev);
    }

    /*----------------------本类参数构建-----------------------------------*/
    public static class LayoutParams extends FrameLayout.LayoutParams {

        public int swipeDirection = SWIPE_ALL;
        public int dismissDirection = SWIPE_ALL;
        public boolean fastDismissAllowed = true;
        public float maxRotation;


        public LayoutParams(int width, int height, int gravity) {
            super(width, height);
            this.gravity = gravity;
        }


        public LayoutParams swipeDirection(int direction) {
            this.swipeDirection = direction;
            return this;
        }

        public LayoutParams dismissDirection(int direction) {
            this.dismissDirection = direction;
            return this;
        }

        public LayoutParams fastDismissAllowed(boolean allowed) {
            this.fastDismissAllowed = allowed;
            return this;
        }

        public LayoutParams maxRotation(float maxRotation) {
            this.maxRotation = maxRotation;
            return this;
        }
    }

    /*---------------------------------------------------------*/

    /**
     * 卡片的适配器
     */
    public static abstract class Adapter {

        private final CardDataObservable mObservable = new CardDataObservable();

        public final void registerDataObserver(CardDataObserver observer) {
            mObservable.registerObserver(observer);
        }

        public final void unregisterDataObserver(CardDataObserver observer) {
            mObservable.unregisterObserver(observer);
        }

        public abstract int getCount();

        public abstract View getView(int position, View convertView, ViewGroup parent);

        public final void notifyDataSetChanged() {
            mObservable.notifyDataSetChanged();
        }

        public final void notifyItemInserted(int position) {
            mObservable.notifyItemInserted(position);
        }

        public final void notifyItemRemoved(int position) {
            mObservable.notifyItemRemoved(position);
        }

        public int getSwipeDirection(int position) {
            return SWIPE_ALL;
        }

        public int getDismissDirection(int position) {
            return SWIPE_ALL;
        }

        public boolean isFastDismissAllowed(int position) {
            return true;
        }

        public int getMaxRotation(int position) {
            return 0;
        }
    }
    /*---------------------------------------------------------*/

    /**
     * 卡片数据管着着,卡片增加,减少,数据更新.
     */
    public static abstract class CardDataObserver {

        public void onDataSetChanged() {

        }

        public void onItemInserted(int position) {

        }

        public void onItemRemoved(int position) {

        }
    }

    /*--------------------------------*/
    static class CardDataObservable extends Observable<CardDataObserver> {

        public void notifyDataSetChanged() {
            for (int i = mObservers.size() - 1; i >= 0; i--) {
                mObservers.get(i).onDataSetChanged();
            }
        }

        public void notifyItemInserted(int position) {
            for (int i = mObservers.size() - 1; i >= 0; i--) {
                mObservers.get(i).onItemInserted(position);
            }
        }

        public void notifyItemRemoved(int position) {
            for (int i = mObservers.size() - 1; i >= 0; i--) {
                mObservers.get(i).onItemRemoved(position);
            }
        }
    }
    /*--------------------------------*/

    private static void log(String tag, String msg) {
        if (StackCardsView.DEBUG) {
            Log.d(tag, msg);
        }
    }
}
