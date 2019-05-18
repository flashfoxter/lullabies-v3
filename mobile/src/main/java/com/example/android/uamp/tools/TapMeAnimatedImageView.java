package com.example.android.uamp.tools;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;

import com.example.android.uamp.R;


public class TapMeAnimatedImageView extends android.support.v7.widget.AppCompatImageView {
    public TapMeAnimatedImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setImageDrawable(ContextCompat.getDrawable(
                context, R.drawable.ic_tap_animated));

        Drawable circle = ContextCompat.getDrawable(context, R.drawable.circle);
        setBackground(circle);
    }

    @Override
    protected void onAttachedToWindow() {
        // It's important to note that the start() method called on
        // the AnimationDrawable cannot be called during the onCreate()
        // method of your Activity, because the AnimationDrawable
        // is not yet fully attached to the window.
        super.onAttachedToWindow();
        AnimationDrawable tapAnimation = (AnimationDrawable) getDrawable();
        tapAnimation.start();
    }
}
