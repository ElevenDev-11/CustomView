package cn.elevendev.bannerlayout.layoutmanager;

import static androidx.recyclerview.widget.RecyclerView.NO_POSITION;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.OrientationHelper;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;


@SuppressWarnings({"WeakerAccess", "unused", "SameParameterValue"})
public  class BannerLayoutManager extends RecyclerView.LayoutManager {
    public static final int DETERMINE_BY_MAX_AND_MIN = -1;
    public static final int HORIZONTAL = OrientationHelper.HORIZONTAL;
    public static final int VERTICAL = OrientationHelper.VERTICAL;
    private static final int DIRECTION_NO_WHERE = -1;
    private static final int DIRECTION_FORWARD = 0;
    private static final int DIRECTION_BACKWARD = 1;
    protected static final int INVALID_SIZE = Integer.MAX_VALUE;
    private SparseArray<View> positionCache = new SparseArray<>();

    protected int mDecoratedMeasurement;
    protected int mDecoratedMeasurementInOther;
    int mOrientation;
    protected int mSpaceMain;
    protected int mSpaceInOther;
    protected float mOffset;
    protected OrientationHelper mOrientationHelper;

    private boolean mReverseLayout = false;
    private boolean mShouldReverseLayout = false;
    private boolean mSmoothScrollbarEnabled = true;
    private int mPendingScrollPosition = NO_POSITION;
    private SavedState mPendingSavedState = null;

    protected float mInterval;
    OnPageChangeListener onPageChangeListener;
    private boolean mRecycleChildrenOnDetach;
    private boolean mInfinite = true;
    private boolean mEnableBringCenterToFront;
    private int mLeftItems;
    private int mRightItems;
    private int mMaxVisibleItemCount = DETERMINE_BY_MAX_AND_MIN;

    private Interpolator mSmoothScrollInterpolator;
    private int mDistanceToBottom = INVALID_SIZE;
    private View currentFocusView;
    private int itemSpace = 20;
    private float centerScale = 1.2f;
    private float  moveSpeed=1.0f;


    protected float getDistanceRatio() {
        if (moveSpeed == 0) return Float.MAX_VALUE;
        return 1 / moveSpeed;
    }


    protected float setInterval() {
        return mDecoratedMeasurement * ((centerScale - 1) / 2 + 1) + itemSpace;
    }

    public void setItemSpace(int itemSpace) {
        this.itemSpace = itemSpace;
    }

    public void setCenterScale(float centerScale) {
        this.centerScale = centerScale;
    }
    public void setMoveSpeed(float moveSpeed) {
        assertNotInLayoutOrScroll(null);
        if (this.moveSpeed == moveSpeed) return;
        this.moveSpeed = moveSpeed;
    }
    protected void setItemViewProperty(@NonNull View itemView, float targetOffset) {
        float scale = calculateScale(targetOffset + mSpaceMain);
        itemView.setScaleX(scale);
        itemView.setScaleY(scale);
    }
    /**
     * @param x 想要缩放的视图的起始位置
     * @return 当前滚动的缩放比例 mOffset
     */
    private float calculateScale(float x) {
        float deltaX = Math.abs(x - (mOrientationHelper.getTotalSpace() - mDecoratedMeasurement) / 2f);
        float diff = 0f;
        if ((mDecoratedMeasurement - deltaX) > 0) diff = mDecoratedMeasurement - deltaX;
        return (centerScale - 1f) / mDecoratedMeasurement * diff + 1;
    }

    /**
     * 因为 api 21 以下不支持提升
     * 所以可以在这里设置高度以支持它低于 api 21
     */
    protected float setViewElevation(View itemView, float targetOffset) {
        return 0;
    }

    /**
     * 创建一个水平的 ViewPagerLayoutManager
     */
    public BannerLayoutManager(Context context) {
        this(context, HORIZONTAL, false);
    }

    /**
     * @param orientation   布局方向
     */
    public BannerLayoutManager(Context context, int orientation) {
        this(context,orientation,false);
    }

    public BannerLayoutManager(Context context, int orientation, boolean reverseLayout) {
        setEnableBringCenterToFront(true);
        setMaxVisibleItemCount(3);
        setOrientation(orientation);
        setReverseLayout(reverseLayout);
        setAutoMeasureEnabled(true);
        setItemPrefetchEnabled(false);
    }

    @Override
    public RecyclerView.LayoutParams generateDefaultLayoutParams() {
        return new RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /**
     * 返回 LayoutManager 脱离时是否会回收子项
     * 回收视图
     *
     * @return true 如果 LayoutManager 在分离时将回收其子项
     * 回收视图。
     */
    public boolean getRecycleChildrenOnDetach() {
        return mRecycleChildrenOnDetach;
    }

    /**
     * 设置LayoutManager脱离后是否回收其children
     * 回收视图
     * @param recycleChildrenOnDetach 子项是否应该在分离中被回收。
     */
    public void setRecycleChildrenOnDetach(boolean recycleChildrenOnDetach) {
        mRecycleChildrenOnDetach = recycleChildrenOnDetach;
    }

    @Override
    public void onDetachedFromWindow(RecyclerView view, RecyclerView.Recycler recycler) {
        super.onDetachedFromWindow(view, recycler);
        if (mRecycleChildrenOnDetach) {
            removeAndRecycleAllViews(recycler);
            recycler.clear();
        }
    }

    @Override
    public Parcelable onSaveInstanceState() {
        if (mPendingSavedState != null) {
            return new SavedState(mPendingSavedState);
        }
        SavedState savedState = new SavedState();
        savedState.position = mPendingScrollPosition;
        savedState.offset = mOffset;
        savedState.isReverseLayout = mShouldReverseLayout;
        return savedState;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (state instanceof SavedState) {
            mPendingSavedState = new SavedState((SavedState) state);
            requestLayout();
        }
    }


    @Override
    public boolean canScrollHorizontally() {
        return mOrientation == HORIZONTAL;
    }


    @Override
    public boolean canScrollVertically() {
        return mOrientation == VERTICAL;
    }

    /**
     * 返回布局的当前方向
     */
    public int getOrientation() {
        return mOrientation;
    }

    /**
     * 设置布局的方向
     */
    public void setOrientation(int orientation) {
        if (orientation != HORIZONTAL && orientation != VERTICAL) {
            throw new IllegalArgumentException("invalid orientation:" + orientation);
        }
        assertNotInLayoutOrScroll(null);
        if (orientation == mOrientation) {
            return;
        }
        mOrientation = orientation;
        mOrientationHelper = null;
        mDistanceToBottom = INVALID_SIZE;
        removeAllViews();
    }

    /**
     * 返回最大可见项数
     */
    public int getMaxVisibleItemCount() {
        return mMaxVisibleItemCount;
    }

    /**
     * 设置最大可见项数
     */
    public void setMaxVisibleItemCount(int mMaxVisibleItemCount) {
        assertNotInLayoutOrScroll(null);
        if (this.mMaxVisibleItemCount == mMaxVisibleItemCount) return;
        this.mMaxVisibleItemCount = mMaxVisibleItemCount;
        removeAllViews();
    }

    /**
     * 计算视图布局顺序。 （例如从头到尾或从头到尾）
     */
    private void resolveShouldLayoutReverse() {
        if (mOrientation == HORIZONTAL && getLayoutDirection() == ViewCompat.LAYOUT_DIRECTION_RTL) {
            mReverseLayout = !mReverseLayout;
        }
    }

    /**
     * 如果视图是从布局的相反方向布局的则返回
     */
    public boolean getReverseLayout() {
        return mReverseLayout;
    }


    public void setReverseLayout(boolean reverseLayout) {
        assertNotInLayoutOrScroll(null);
        if (reverseLayout == mReverseLayout) {
            return;
        }
        mReverseLayout = reverseLayout;
        removeAllViews();
    }

    public void setSmoothScrollInterpolator(Interpolator smoothScrollInterpolator) {
        this.mSmoothScrollInterpolator = smoothScrollInterpolator;
    }

    @Override
    public void smoothScrollToPosition(RecyclerView recyclerView, RecyclerView.State state, int position) {
        final int offsetPosition = getOffsetToPosition(position);
        if (mOrientation == VERTICAL) {
            recyclerView.smoothScrollBy(0, offsetPosition, mSmoothScrollInterpolator);
        } else {
            recyclerView.smoothScrollBy(offsetPosition, 0, mSmoothScrollInterpolator);
        }
    }
    @Override
    public void scrollToPosition(int position) {
        if (!mInfinite && (position < 0 || position >= getItemCount())) return;
        mPendingScrollPosition = position;
        mOffset = mShouldReverseLayout ? position * -mInterval : position * mInterval;
        requestLayout();
    }

    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, @NonNull RecyclerView.State state) {
        if (state.getItemCount() == 0) {
            removeAndRecycleAllViews(recycler);
            mOffset = 0;
            return;
        }

        ensureLayoutState();
        resolveShouldLayoutReverse();

        View scrap = recycler.getViewForPosition(0);
        measureChildWithMargins(scrap, 0, 0);
        mDecoratedMeasurement = mOrientationHelper.getDecoratedMeasurement(scrap);
        mDecoratedMeasurementInOther = mOrientationHelper.getDecoratedMeasurementInOther(scrap);
        mSpaceMain = (mOrientationHelper.getTotalSpace() - mDecoratedMeasurement) / 2;
        if (mDistanceToBottom == INVALID_SIZE) {
            mSpaceInOther = (getTotalSpaceInOther() - mDecoratedMeasurementInOther) / 2;
        } else {
            mSpaceInOther =getTotalSpaceInOther() - mDecoratedMeasurementInOther - mDistanceToBottom;
        }

        mInterval = setInterval();
        setUp();
        mLeftItems = (int) Math.abs(minRemoveOffset() / mInterval) + 1;
        mRightItems = (int) Math.abs(maxRemoveOffset() / mInterval) + 1;

        if (mPendingSavedState != null) {
            mShouldReverseLayout = mPendingSavedState.isReverseLayout;
            mPendingScrollPosition = mPendingSavedState.position;
            mOffset = mPendingSavedState.offset;
        }

        if (mPendingScrollPosition != NO_POSITION) {
            mOffset = mShouldReverseLayout ?
                    mPendingScrollPosition * -mInterval : mPendingScrollPosition * mInterval;
        }

        detachAndScrapAttachedViews(recycler);
        layoutItems(recycler);
    }
    public int getTotalSpaceInOther() {
        if (mOrientation == HORIZONTAL) {
            return getHeight() - getPaddingTop()
                    - getPaddingBottom();
        } else {
            return getWidth() - getPaddingLeft()
                    - getPaddingRight();
        }
    }
    @Override
    public void onLayoutCompleted(RecyclerView.State state) {
        super.onLayoutCompleted(state);
        mPendingSavedState = null;
        mPendingScrollPosition = NO_POSITION;
    }

    @Override
    public boolean onAddFocusables(RecyclerView recyclerView, ArrayList<View> views,
                                   int direction, int focusableMode) {
        final int currentPosition = getCurrentPosition();
        final View currentView = findViewByPosition(currentPosition);
        if (currentView == null) return true;
        if (recyclerView.hasFocus()) {
            final int movement = getMovement(direction);
            if (movement != DIRECTION_NO_WHERE) {
                final int targetPosition = movement == DIRECTION_BACKWARD ?
                        currentPosition - 1 : currentPosition + 1;
                recyclerView.smoothScrollToPosition(targetPosition);
            }
        } else {
            currentView.addFocusables(views, direction, focusableMode);
        }
        return true;
    }

    @Override
    public View onFocusSearchFailed(View focused, int focusDirection, RecyclerView.Recycler recycler,
                                    RecyclerView.State state) {
        return null;
    }

    private int getMovement(int direction) {
        if (mOrientation == VERTICAL) {
            if (direction == View.FOCUS_UP) {
                return mShouldReverseLayout ? DIRECTION_FORWARD : DIRECTION_BACKWARD;
            } else if (direction == View.FOCUS_DOWN) {
                return mShouldReverseLayout ? DIRECTION_BACKWARD : DIRECTION_FORWARD;
            } else {
                return DIRECTION_NO_WHERE;
            }
        } else {
            if (direction == View.FOCUS_LEFT) {
                return mShouldReverseLayout ? DIRECTION_FORWARD : DIRECTION_BACKWARD;
            } else if (direction == View.FOCUS_RIGHT) {
                return mShouldReverseLayout ? DIRECTION_BACKWARD : DIRECTION_FORWARD;
            } else {
                return DIRECTION_NO_WHERE;
            }
        }
    }

    void ensureLayoutState() {
        if (mOrientationHelper == null) {
            mOrientationHelper = OrientationHelper.createOrientationHelper(this, mOrientation);
        }
    }

    /**
     * 可以在这里设置自己的属性或更改现有属性，如 mSpaceMain 和 mSpaceInOther
     */
    protected void setUp() {

    }

    private float getProperty(int position) {
        return mShouldReverseLayout ? position * -mInterval : position * mInterval;
    }

    @Override
    public void onAdapterChanged(RecyclerView.Adapter oldAdapter, RecyclerView.Adapter newAdapter) {
        removeAllViews();
        mOffset = 0;
    }


    @Override
    public int computeHorizontalScrollOffset(RecyclerView.State state) {
        return computeScrollOffset();
    }

    @Override
    public int computeVerticalScrollOffset(RecyclerView.State state) {
        return computeScrollOffset();
    }

    @Override
    public int computeHorizontalScrollExtent(RecyclerView.State state) {
        return computeScrollExtent();
    }

    @Override
    public int computeVerticalScrollExtent(RecyclerView.State state) {
        return computeScrollExtent();
    }

    @Override
    public int computeHorizontalScrollRange(RecyclerView.State state) {
        return computeScrollRange();
    }

    @Override
    public int computeVerticalScrollRange(RecyclerView.State state) {
        return computeScrollRange();
    }

    private int computeScrollOffset() {
        if (getChildCount() == 0) {
            return 0;
        }

        if (!mSmoothScrollbarEnabled) {
            return !mShouldReverseLayout ?
                    getCurrentPosition() : getItemCount() - getCurrentPosition() - 1;
        }

        final float realOffset = getOffsetOfRightAdapterPosition();
        return !mShouldReverseLayout ? (int) realOffset : (int) ((getItemCount() - 1) * mInterval + realOffset);
    }

    private int computeScrollExtent() {
        if (getChildCount() == 0) {
            return 0;
        }

        if (!mSmoothScrollbarEnabled) {
            return 1;
        }

        return (int) mInterval;
    }

    private int computeScrollRange() {
        if (getChildCount() == 0) {
            return 0;
        }

        if (!mSmoothScrollbarEnabled) {
            return getItemCount();
        }

        return (int) (getItemCount() * mInterval);
    }

    @Override
    public int scrollHorizontallyBy(int dx, RecyclerView.Recycler recycler, RecyclerView.State state) {
        if (mOrientation == VERTICAL) {
            return 0;
        }
        return scrollBy(dx, recycler, state);
    }

    @Override
    public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
        if (mOrientation == HORIZONTAL) {
            return 0;
        }
        return scrollBy(dy, recycler, state);
    }

    private int scrollBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
        if (getChildCount() == 0 || dy == 0) {
            return 0;
        }
        ensureLayoutState();
        int willScroll = dy;

        float realDx = dy / getDistanceRatio();
        if (Math.abs(realDx) < 0.00000001f) {
            return 0;
        }
        float targetOffset = mOffset + realDx;

        if (!mInfinite && targetOffset < getMinOffset()) {
            willScroll -= (targetOffset - getMinOffset()) * getDistanceRatio();
        } else if (!mInfinite && targetOffset > getMaxOffset()) {
            willScroll = (int) ((getMaxOffset() - mOffset) * getDistanceRatio());
        }

        realDx = willScroll / getDistanceRatio();

        mOffset += realDx;

        layoutItems(recycler);

        return willScroll;
    }

    private void layoutItems(RecyclerView.Recycler recycler) {
        detachAndScrapAttachedViews(recycler);
        positionCache.clear();

        final int itemCount = getItemCount();
        if (itemCount == 0) return;

        final int currentPos = mShouldReverseLayout ?
                -getCurrentPositionOffset() : getCurrentPositionOffset();
        int start = currentPos - mLeftItems;
        int end = currentPos + mRightItems;

        // handle max visible count
        if (useMaxVisibleCount()) {
            boolean isEven = mMaxVisibleItemCount % 2 == 0;
            if (isEven) {
                int offset = mMaxVisibleItemCount / 2;
                start = currentPos - offset + 1;
                end = currentPos + offset + 1;
            } else {
                int offset = (mMaxVisibleItemCount - 1) / 2;
                start = currentPos - offset;
                end = currentPos + offset + 1;
            }
        }

        if (!mInfinite) {
            if (start < 0) {
                start = 0;
                if (useMaxVisibleCount()) end = mMaxVisibleItemCount;
            }
            if (end > itemCount) end = itemCount;
        }

        float lastOrderWeight = Float.MIN_VALUE;
        for (int i = start; i < end; i++) {
            if (useMaxVisibleCount() || !removeCondition(getProperty(i) - mOffset)) {
                int adapterPosition = i;
                if (i >= itemCount) {
                    adapterPosition %= itemCount;
                } else if (i < 0) {
                    int delta = (-adapterPosition) % itemCount;
                    if (delta == 0) delta = itemCount;
                    adapterPosition = itemCount - delta;
                }
                final View scrap = recycler.getViewForPosition(adapterPosition);
                measureChildWithMargins(scrap, 0, 0);
                resetViewProperty(scrap);
                final float targetOffset = getProperty(i) - mOffset;
                layoutScrap(scrap, targetOffset);
                final float orderWeight = mEnableBringCenterToFront ?
                        setViewElevation(scrap, targetOffset) : adapterPosition;
                if (orderWeight > lastOrderWeight) {
                    addView(scrap);
                } else {
                    addView(scrap, 0);
                }
                if (i == currentPos) currentFocusView = scrap;
                lastOrderWeight = orderWeight;
                positionCache.put(i, scrap);
            }
        }

        currentFocusView.requestFocus();
    }

    private boolean useMaxVisibleCount() {
        return mMaxVisibleItemCount != DETERMINE_BY_MAX_AND_MIN;
    }

    private boolean removeCondition(float targetOffset) {
        return targetOffset > maxRemoveOffset() || targetOffset < minRemoveOffset();
    }

    private void resetViewProperty(@NonNull View v) {
        v.setRotation(0);
        v.setRotationY(0);
        v.setRotationX(0);
        v.setScaleX(1f);
        v.setScaleY(1f);
        v.setAlpha(1f);
    }

        float getMaxOffset() {
        return !mShouldReverseLayout ? (getItemCount() - 1) * mInterval : 0;
    }

        float getMinOffset() {
        return !mShouldReverseLayout ? 0 : -(getItemCount() - 1) * mInterval;
    }

    private void layoutScrap(View scrap, float targetOffset) {

        final int left = calItemLeft(scrap, targetOffset);
        final int top = calItemTop(scrap, targetOffset);
        if (mOrientation == VERTICAL) {
            layoutDecorated(scrap, mSpaceInOther + left, mSpaceMain + top,
                    mSpaceInOther + left + mDecoratedMeasurementInOther,
                    mSpaceMain + top + mDecoratedMeasurement);
        } else {
            layoutDecorated(scrap, mSpaceMain + left, mSpaceInOther + top, mSpaceMain
                    + left + mDecoratedMeasurement, mSpaceInOther + top + mDecoratedMeasurementInOther);
        }
        setItemViewProperty(scrap, targetOffset);
    }

    protected int calItemLeft(View itemView, float targetOffset) {
        return mOrientation == VERTICAL ? 0 : (int) targetOffset;
    }

    protected int calItemTop(View itemView, float targetOffset) {
        return mOrientation == VERTICAL ? (int) targetOffset : 0;
    }

    /**
     * 当目标偏移量达到这个值时，
     * 视图将被删除和回收
     */
    protected float maxRemoveOffset() {
        return mOrientationHelper.getTotalSpace() - mSpaceMain;
    }

    protected float minRemoveOffset() {
        return -mDecoratedMeasurement - mOrientationHelper.getStartAfterPadding() - mSpaceMain;
    }


    public int getCurrentPosition() {
        if (getItemCount() == 0) return 0;

        int position = getCurrentPositionOffset();
        if (!mInfinite) return Math.abs(position);

        position = !mShouldReverseLayout ?
                (position >= 0 ?
                        position % getItemCount() :
                        getItemCount() + position % getItemCount()) :
                (position > 0 ?
                        getItemCount() - position % getItemCount() :
                        -position % getItemCount());
        return position == getItemCount() ? 0 : position;
    }

    @Override
    public View findViewByPosition(int position) {
        final int itemCount = getItemCount();
        if (itemCount == 0) return null;
        for (int i = 0; i < positionCache.size(); i++) {
            final int key = positionCache.keyAt(i);
            if (key >= 0) {
                if (position == key % itemCount) return positionCache.valueAt(i);
            } else {
                int delta = key % itemCount;
                if (delta == 0) delta = -itemCount;
                if (itemCount + delta == position) return positionCache.valueAt(i);
            }
        }
        return null;
    }

    private int getCurrentPositionOffset() {
        return Math.round(mOffset / mInterval);
    }


    private float getOffsetOfRightAdapterPosition() {
        if (mShouldReverseLayout)
            return mInfinite ?
                    (mOffset <= 0 ?
                            (mOffset % (mInterval * getItemCount())) :
                            (getItemCount() * -mInterval + mOffset % (mInterval * getItemCount()))) :
                    mOffset;
        else
            return mInfinite ?
                    (mOffset >= 0 ?
                            (mOffset % (mInterval * getItemCount())) :
                            (getItemCount() * mInterval + mOffset % (mInterval * getItemCount()))) :
                    mOffset;
    }

    /**
     * 中心和当前位置之间的 dy
     */
    public int getOffsetToCenter() {
        if (mInfinite)
            return (int) ((getCurrentPositionOffset() * mInterval - mOffset) * getDistanceRatio());
        return (int) ((getCurrentPosition() *
                (!mShouldReverseLayout ? mInterval : -mInterval) - mOffset) * getDistanceRatio());
    }

    public int getOffsetToPosition(int position) {
        if (mInfinite)
            return (int) (((getCurrentPositionOffset() +
                    (!mShouldReverseLayout ? position - getCurrentPosition() : getCurrentPosition() - position)) *
                    mInterval - mOffset) * getDistanceRatio());
        return (int) ((position *
                (!mShouldReverseLayout ? mInterval : -mInterval) - mOffset) * getDistanceRatio());
    }

    public void setOnPageChangeListener(OnPageChangeListener onPageChangeListener) {
        this.onPageChangeListener = onPageChangeListener;
    }

    public void setInfinite(boolean enable) {
        assertNotInLayoutOrScroll(null);
        if (enable == mInfinite) {
            return;
        }
        mInfinite = enable;
        requestLayout();
    }

    public boolean getInfinite() {
        return mInfinite;
    }

    public int getDistanceToBottom() {
        return mDistanceToBottom == INVALID_SIZE ?
                (getTotalSpaceInOther() - mDecoratedMeasurementInOther) / 2 : mDistanceToBottom;
    }

    public void setDistanceToBottom(int mDistanceToBottom) {
        assertNotInLayoutOrScroll(null);
        if (this.mDistanceToBottom == mDistanceToBottom) return;
        this.mDistanceToBottom = mDistanceToBottom;
        removeAllViews();
    }


    public void setSmoothScrollbarEnabled(boolean enabled) {
        mSmoothScrollbarEnabled = enabled;
    }

    public void setEnableBringCenterToFront(boolean bringCenterToTop) {
        assertNotInLayoutOrScroll(null);
        if (mEnableBringCenterToFront == bringCenterToTop) {
            return;
        }
        this.mEnableBringCenterToFront = bringCenterToTop;
        requestLayout();
    }

    public boolean getEnableBringCenterToFront() {
        return mEnableBringCenterToFront;
    }


    public boolean getSmoothScrollbarEnabled() {
        return mSmoothScrollbarEnabled;
    }

    private static class SavedState implements Parcelable {
        int position;
        float offset;
        boolean isReverseLayout;

        SavedState() {

        }

        SavedState(@NonNull Parcel in) {
            position = in.readInt();
            offset = in.readFloat();
            isReverseLayout = in.readInt() == 1;
        }

        public SavedState(@NonNull SavedState other) {
            position = other.position;
            offset = other.offset;
            isReverseLayout = other.isReverseLayout;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeInt(position);
            dest.writeFloat(offset);
            dest.writeInt(isReverseLayout ? 1 : 0);
        }

        public static final Creator<SavedState> CREATOR
                = new Creator<SavedState>() {
            @Override
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            @NonNull
            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

    public interface OnPageChangeListener {
        void onPageSelected(int position);

        void onPageScrollStateChanged(int state);
    }
}
