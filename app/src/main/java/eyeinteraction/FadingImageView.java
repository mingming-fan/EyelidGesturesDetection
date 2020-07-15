package eyeinteraction;

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.util.TypedValue;

public class FadingImageView extends android.support.v7.widget.AppCompatImageView {
    private FadeSide mFadeSide;

    private Context c;

    public enum FadeSide {
        RIGHT_SIDE, LEFT_SIDE, NONE
    }

    public FadingImageView(Context c, AttributeSet attrs, int defStyle) {
        super(c, attrs, defStyle);

        this.c = c;

        init();
    }

    public FadingImageView(Context c, AttributeSet attrs) {
        super(c, attrs);

        this.c = c;

        init();
    }

    public FadingImageView(Context c) {
        super(c);

        this.c = c;

        init();
    }

    private void init() {
        // Enable horizontal fading
        this.setHorizontalFadingEdgeEnabled(true);
        // Apply default fading length
        this.setEdgeLength(0);
        // Apply default side
        this.setFadeDirection(FadeSide.NONE);
    }

    public void setFadeDirection(FadeSide side) {
        this.mFadeSide = side;
    }

    public void setEdgeLength(int length) {
        this.setFadingEdgeLength(getPixels(length));
    }

    @Override
    protected float getLeftFadingEdgeStrength() {
        return mFadeSide.equals(FadeSide.LEFT_SIDE) ? 1.0f : 0.0f;
    }

    @Override
    protected float getRightFadingEdgeStrength() {
        return mFadeSide.equals(FadeSide.RIGHT_SIDE) ? 1.0f : 0.0f;
    }

    @Override
    public boolean hasOverlappingRendering() {
        return true;
    }

    @Override
    public boolean onSetAlpha(int alpha) {
        return false;
    }

    private int getPixels(int dipValue) {
        Resources r = c.getResources();

        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                dipValue, r.getDisplayMetrics());
    }
}