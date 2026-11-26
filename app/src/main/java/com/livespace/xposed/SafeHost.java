package com.livespace.xposed;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/** Wraps the 16.1 container. Every view callback is guarded: if the capsule throws, it is removed and logged instead of crashing SystemUI.
 *  It is also inactive whenever the keyguard is not showing (e.g. shade pulled down on the home screen). */
final class SafeHost extends FrameLayout {
  private boolean dead;
  private boolean locked = true;
  private long lastCheck;

  SafeHost(Context ctx, View child) {
    super(ctx);
    addView(child, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
  }

  private boolean active() {
    if (dead || CapsuleGuard.disabled) return false;
    long now = SystemClock.uptimeMillis();
    if (now - lastCheck > 500) {
      lastCheck = now;
      try {
        KeyguardManager km = (KeyguardManager) getContext().getSystemService(Context.KEYGUARD_SERVICE);
        locked = km == null || km.isKeyguardLocked();
      } catch (Throwable t) { locked = true; }
    }
    return locked;
  }

  private void kill(String where, Throwable t) {
    dead = true;
    CapsuleGuard.trip(where, t);
    post(() -> { try { removeAllViews(); setVisibility(View.GONE); } catch (Throwable ignored) { } });
  }

  @Override protected void onMeasure(int w, int h) {
    if (!active()) { setMeasuredDimension(0, 0); return; }
    try { super.onMeasure(w, h); } catch (Throwable t) { kill("measure", t); setMeasuredDimension(0, 0); }
  }

  @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
    if (!active()) return;
    try { super.onLayout(changed, l, t, r, b); } catch (Throwable e) { kill("layout", e); }
  }

  @Override protected void dispatchDraw(Canvas canvas) {
    if (!active()) return;
    try { super.dispatchDraw(canvas); } catch (Throwable e) { kill("draw", e); }
  }

  @Override public boolean dispatchTouchEvent(MotionEvent ev) {
    if (!active()) return false;
    try { return super.dispatchTouchEvent(ev); } catch (Throwable e) { kill("touch", e); return false; }
  }

  @Override public void dispatchConfigurationChanged(Configuration newConfig) {
    try { super.dispatchConfigurationChanged(newConfig); } catch (Throwable e) { kill("configuration", e); }
  }
}
