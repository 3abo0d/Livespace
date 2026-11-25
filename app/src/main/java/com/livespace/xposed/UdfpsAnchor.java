package com.livespace.xposed;

import android.view.View;
import android.view.ViewGroup;
import java.util.ArrayDeque;
import java.lang.ref.WeakReference;

/** Keeps the capsule above the under-display fingerprint view by lifting it with translationY. */
final class UdfpsAnchor {
  private static WeakReference<View> cached;
  private static float last = Float.NaN;

  static void install(final ViewGroup panel, final View capsule) {
    panel.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
      try { apply(panel, capsule); } catch (Throwable ignored) { }
    });
  }

  static void apply(ViewGroup panel, View capsule) {
    View fp = cached != null ? cached.get() : null;
    if (fp == null || !fp.isAttachedToWindow()) { fp = find(panel.getRootView()); cached = fp == null ? null : new WeakReference<>(fp); }
    float lift = 0f;
    if (fp != null && fp.getHeight() > 0 && fp.isShown()) {
      int[] f = new int[2], p = new int[2];
      fp.getLocationInWindow(f); panel.getLocationInWindow(p);
      float gap = 12f * panel.getResources().getDisplayMetrics().density;
      lift = Math.max(0f, (p[1] + panel.getHeight()) - f[1] + gap);
    }
    if (lift != last) { last = lift; capsule.setTranslationY(-lift); }
  }

  private static View find(View root) {
    if (root == null) return null;
    ArrayDeque<View> q = new ArrayDeque<>(); q.add(root);
    while (!q.isEmpty()) {
      View v = q.poll();
      String n = v.getClass().getName();
      if (n.endsWith("OplusUdfpsView") || n.endsWith("LockIconView") || n.contains("UdfpsView")) return v;
      if (v instanceof ViewGroup) { ViewGroup g = (ViewGroup) v; for (int i = 0; i < g.getChildCount(); i++) q.add(g.getChildAt(i)); }
    }
    return null;
  }
}
