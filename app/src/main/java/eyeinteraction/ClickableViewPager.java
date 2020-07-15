package eyeinteraction;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

public class ClickableViewPager extends ViewPager {

    private final String TAG = ClickableViewPager.this.getClass().getSimpleName();

    @Nullable
    private OnClickListener onClickListener;

    public ClickableViewPager(final Context context) {
        this(context, null);
    }

    public ClickableViewPager(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        final GestureDetector onSingleTapConfirmedGestureDetector =
                new GestureDetector(context, new OnSingleTapConfirmedGestureListener(this));
        setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                onSingleTapConfirmedGestureDetector.onTouchEvent(event);
                return false;
            }
        });
    }

    @Override
    public void setOnClickListener(@Nullable final OnClickListener onClickListener) {
        this.onClickListener = onClickListener;
    }

    private class OnSingleTapConfirmedGestureListener extends GestureDetector.SimpleOnGestureListener {

        @NonNull
        private final View view;

        public OnSingleTapConfirmedGestureListener(@NonNull final View view) {
            this.view = view;
        }

        @Override
        public boolean onSingleTapConfirmed(final MotionEvent e) {
            if (onClickListener != null) {
                //Log.i(TAG, "clicking...");
                onClickListener.onClick(view);
            }
            return true;
        }
    }
}
