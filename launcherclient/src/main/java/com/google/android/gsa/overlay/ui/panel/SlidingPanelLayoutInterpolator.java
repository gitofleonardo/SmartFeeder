package com.google.android.gsa.overlay.ui.panel;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.animation.Interpolator;

import androidx.dynamicanimation.animation.DynamicAnimation;

import com.google.android.gsa.overlay.ui.anim.SpringAnimationBuilder;

final class SlidingPanelLayoutInterpolator extends AnimatorListenerAdapter implements Interpolator {

    private ValueAnimator mAnimator;
    int mFinalX;
    private final SlidingPanelLayout slidingPanelLayout;

    public SlidingPanelLayoutInterpolator(SlidingPanelLayout slidingPanelLayoutVar) {
        this.slidingPanelLayout = slidingPanelLayoutVar;
    }

    public final void cancelAnimation() {
        if (this.mAnimator != null) {
            this.mAnimator.removeAllListeners();
            this.mAnimator.cancel();
            this.mAnimator = null;
        }
    }

    public final void animate(int finalX, int duration) {
        cancelAnimation();
        this.mFinalX = finalX;
        if (duration > 0) {
            this.mAnimator = createSpringAnimator(0);
            this.mAnimator.addListener(this);
            this.mAnimator.start();
            return;
        }
        onAnimationEnd(null);
    }

    public void animate(int finalX, float velocity) {
        cancelAnimation();
        this.mFinalX = finalX;
        this.mAnimator = createSpringAnimator(velocity);
        this.mAnimator.addListener(this);
        this.mAnimator.start();
    }

    private ValueAnimator createSpringAnimator(float velocity) {
        SpringAnimationBuilder builder = new SpringAnimationBuilder(slidingPanelLayout.getContext());
        return builder.setDampingRatio(.8f)
                .setStiffness(300)
                .setStartValue(SlidingPanelLayout.PANEL_X.get(slidingPanelLayout))
                .setEndValue(mFinalX)
                .setStartVelocity(velocity)
                .setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_PIXELS)
                .build(slidingPanelLayout, SlidingPanelLayout.PANEL_X);
    }

    public final boolean isFinished() {
        return this.mAnimator == null;
    }

    public final void onAnimationEnd(Animator animator) {
        this.mAnimator = null;
        this.slidingPanelLayout.setPanelX(this.mFinalX);
        SlidingPanelLayout slidingPanelLayoutVar = this.slidingPanelLayout;
        if (slidingPanelLayoutVar.mSettling) {
            slidingPanelLayoutVar.mSettling = false;
            if (slidingPanelLayoutVar.panelX == 0) {
                slidingPanelLayoutVar.cnO();
                slidingPanelLayoutVar.mIsPanelOpen = false;
                slidingPanelLayoutVar.mIsPageMoving = false;
                if (slidingPanelLayoutVar.dragCallback != null) {
                    slidingPanelLayoutVar.dragCallback.onPanelClose();
                }
            } else if (slidingPanelLayoutVar.panelX == slidingPanelLayoutVar.getMeasuredWidth()) {
                slidingPanelLayoutVar.cnG();
            }
        }
    }

    public final float getInterpolation(float f) {
        float f2 = f - 1.0f;
        return (f2 * (((f2 * f2) * f2) * f2)) + 1.0f;
    }
}