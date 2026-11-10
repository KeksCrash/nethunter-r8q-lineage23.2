package de.local.meddl;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

final class BoardTheme {
    static final int BG = 0xff11101b;
    static final int SURFACE = 0xff201d32;
    static final int LINE = 0xff3b3551;
    static final int TEXT = 0xfff5f1ff;
    static final int MUTED = 0xffbbb3d2;
    static final int TEAL = 0xff76e4ce;
    static final int VIOLET = 0xffc9aeff;
    static final int AMBER = 0xffffc485;
    static final int RED = 0xffffa1ab;

    static int dp(Context c, int n) {
        return Math.round(c.getResources().getDisplayMetrics().density * n);
    }

    static GradientDrawable shape(Context c, int color, int radius, int stroke) {
        var drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(c, radius));
        if (stroke != 0) drawable.setStroke(dp(c, 1), stroke);
        return drawable;
    }

    static void pad(LinearLayout layout, int horizontal, int vertical) {
        Context c = layout.getContext();
        layout.setPadding(dp(c, horizontal), dp(c, vertical), dp(c, horizontal), dp(c, vertical));
    }

    static LinearLayout card(Context c, int color) {
        var layout = new LinearLayout(c);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackground(shape(c, color, 20, LINE));
        pad(layout, 16, 12);
        return layout;
    }

    static TextView label(Context c, String value, int size, int color, boolean bold) {
        var text = new TextView(c);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        if (bold) text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return text;
    }

    static Button button(Context c, String label, int surface, int text, Runnable action) {
        var button = new Button(c);
        style(button, surface, text);
        button.setText(label);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setMinHeight(dp(c, 48));
        button.setMinimumHeight(dp(c, 48));
        button.setPadding(dp(c, 12), dp(c, 8), dp(c, 12), dp(c, 8));
        button.setGravity(Gravity.CENTER);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    static void style(Button button, int surface, int text) {
        button.setTextColor(text);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x44ffffff),
            shape(button.getContext(), surface, 14, surface == SURFACE ? LINE : 0), null));
        button.setStateListAnimator(null);
    }

    static void row(LinearLayout parent, android.view.View child, int top) {
        var p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(parent.getContext(), top);
        parent.addView(child, p);
    }
}
