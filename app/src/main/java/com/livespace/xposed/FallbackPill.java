package com.livespace.xposed;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

/** Visible stand-in used when the real 16.1 capsule cannot be built yet, so placement above the fingerprint scanner can be checked. */
final class FallbackPill {
  static View create(Context ctx) {
    float d = ctx.getResources().getDisplayMetrics().density;
    TextView tv = new TextView(ctx);
    tv.setText("Live Space (placeholder)");
    tv.setTextColor(0xFFFFFFFF);
    tv.setTextSize(14);
    tv.setGravity(Gravity.CENTER);
    int h = (int) (18 * d), v = (int) (10 * d);
    tv.setPadding(h, v, h, v);
    GradientDrawable g = new GradientDrawable();
    g.setColor(0xCC2A2A2A);
    g.setCornerRadius(999 * d);
    tv.setBackground(g);
    return tv;
  }
}
