package com.livespace.xposed;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/** Builds the 16.1 NotificationCapsuleContainer and attaches it to the keyguard NotificationPanelView.
 *  In 16.1 the container is declared in the panel layout and found with findViewById; here we add it programmatically. */
final class CapsuleHost {
  static final String CONTAINER = "com.oplus.systemui.notification.lockscreen.capsule.NotificationCapsuleContainer";
  static final String TAG = "livespace_capsule";

  static void attach(ViewGroup panel, ClassLoader host) {
    try {
      if (panel.findViewWithTag(TAG) != null) return;
      ClassLoader cl = CapsuleLoader.get(host);
      if (cl == null) return;
      Class<?> c = cl.loadClass(CONTAINER);
      View v = (View) c.getConstructor(Context.class).newInstance(panel.getContext());
      v.setTag(TAG);
      panel.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
      UdfpsAnchor.install(panel, v);
      Log.i("capsule container attached");
    } catch (Throwable t) { Log.e("attach failed", t); }
  }
}
