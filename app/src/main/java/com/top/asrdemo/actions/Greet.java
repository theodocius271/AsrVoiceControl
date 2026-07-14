package com.top.asrdemo.actions;

import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.top.asrdemo.R;

public class Greet implements Action {
    private final ViewGroup parent;
    private TextView greetingView;

    public Greet(ViewGroup parent) {
        this.parent = parent;
    }

    @Override
    public void run() {
        if (greetingView != null) {
            return;
        }

        TextView view = new TextView(parent.getContext());
        view.setText("Hello, I'm TopVoiceControl");
        view.setTextSize(32);
        view.setTextColor(Color.BLACK);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(16), dp(16), dp(16), dp(16));
        view.setBackgroundResource(R.drawable.glass_background);

        ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        parent.addView(view, layoutParams);
        greetingView = view;
    }

    private int dp(int i) {
        float density = parent.getResources().getDisplayMetrics().density;
        return Math.round(density * i);
    }

    @Override
    public void close() {
        if (greetingView == null) {
            return;
        }

        parent.removeView(greetingView);
        greetingView = null;
    }
}
