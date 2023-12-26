package cn.elevendev.bannerlayout.layoutmanager;

import android.view.animation.DecelerateInterpolator;
import android.widget.Scroller;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.LayoutManager;


public class CenterSnapHelper extends RecyclerView.OnFlingListener {

    RecyclerView mRecyclerView;
    Scroller mGravityScroller;


    private boolean snapToCenter = false;

    private final RecyclerView.OnScrollListener mScrollListener =
            new RecyclerView.OnScrollListener() {

                boolean mScrolled = false;

                @Override
                public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                    super.onScrollStateChanged(recyclerView, newState);

                    final BannerLayoutManager layoutManager =
                            (BannerLayoutManager) recyclerView.getLayoutManager();
                    final BannerLayoutManager.OnPageChangeListener onPageChangeListener =
                            layoutManager.onPageChangeListener;
                    if (onPageChangeListener != null) {
                        onPageChangeListener.onPageScrollStateChanged(newState);
                    }

                    if (newState == RecyclerView.SCROLL_STATE_IDLE && mScrolled) {
                        mScrolled = false;
                        if (!snapToCenter) {
                            snapToCenter = true;
                            snapToCenterView(layoutManager, onPageChangeListener);
                        } else {
                            snapToCenter = false;
                        }
                    }
                }

                @Override
                public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                    if (dx != 0 || dy != 0) {
                        mScrolled = true;
                    }
                }
            };

    @Override
    public boolean onFling(int velocityX, int velocityY) {
        BannerLayoutManager layoutManager = (BannerLayoutManager) mRecyclerView.getLayoutManager();
        if (layoutManager == null) {
            return false;
        }
        RecyclerView.Adapter adapter = mRecyclerView.getAdapter();
        if (adapter == null) {
            return false;
        }

        if (!layoutManager.getInfinite() &&
                (layoutManager.mOffset == layoutManager.getMaxOffset()
                        || layoutManager.mOffset == layoutManager.getMinOffset())) {
            return false;
        }

        final int minFlingVelocity = mRecyclerView.getMinFlingVelocity();
        mGravityScroller.fling(0, 0, velocityX, velocityY,
                Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE);

        if (layoutManager.mOrientation == BannerLayoutManager.VERTICAL
                && Math.abs(velocityY) > minFlingVelocity) {
            final int currentPosition = layoutManager.getCurrentPosition();
            final int offsetPosition = (int) (mGravityScroller.getFinalY() /
                    layoutManager.mInterval / layoutManager.getDistanceRatio());
            mRecyclerView.smoothScrollToPosition(layoutManager.getReverseLayout() ?
                    currentPosition - offsetPosition : currentPosition + offsetPosition);
            return true;
        } else if (layoutManager.mOrientation == BannerLayoutManager.HORIZONTAL
                && Math.abs(velocityX) > minFlingVelocity) {
            final int currentPosition = layoutManager.getCurrentPosition();
            final int offsetPosition = (int) (mGravityScroller.getFinalX() /
                    layoutManager.mInterval / layoutManager.getDistanceRatio());
            mRecyclerView.smoothScrollToPosition(layoutManager.getReverseLayout() ?
                    currentPosition - offsetPosition : currentPosition + offsetPosition);
            return true;
        }

        return true;
    }

    /**
     * 回收视图
     */
    public void attachToRecyclerView(@Nullable RecyclerView recyclerView)
            throws IllegalStateException {
        if (mRecyclerView == recyclerView) {
            return;
        }
        if (mRecyclerView != null) {
            destroyCallbacks();
        }
        mRecyclerView = recyclerView;
        if (mRecyclerView != null) {
            final LayoutManager layoutManager = mRecyclerView.getLayoutManager();
            if (!(layoutManager instanceof BannerLayoutManager)) return;

            setupCallbacks();
            mGravityScroller = new Scroller(mRecyclerView.getContext(),
                    new DecelerateInterpolator());

            snapToCenterView((BannerLayoutManager) layoutManager,
                    ((BannerLayoutManager) layoutManager).onPageChangeListener);
        }
    }

    void snapToCenterView(@NonNull BannerLayoutManager layoutManager,
                          BannerLayoutManager.OnPageChangeListener listener) {
        final int delta = layoutManager.getOffsetToCenter();
        if (delta != 0) {
            if (layoutManager.getOrientation()
                    == BannerLayoutManager.VERTICAL)
                mRecyclerView.smoothScrollBy(0, delta);
            else
                mRecyclerView.smoothScrollBy(delta, 0);
        } else {
            // 将其设置为 false 以使 smoothScrollToPosition 保持触发侦听器
            snapToCenter = false;
        }

        if (listener != null)
            listener.onPageSelected(layoutManager.getCurrentPosition());
    }


    void setupCallbacks() throws IllegalStateException {
        if (mRecyclerView.getOnFlingListener() != null) {
            throw new IllegalStateException("An instance of OnFlingListener already set.");
        }
        mRecyclerView.addOnScrollListener(mScrollListener);
        mRecyclerView.setOnFlingListener(this);
    }


    void destroyCallbacks() {
        mRecyclerView.removeOnScrollListener(mScrollListener);
        mRecyclerView.setOnFlingListener(null);
    }
}
