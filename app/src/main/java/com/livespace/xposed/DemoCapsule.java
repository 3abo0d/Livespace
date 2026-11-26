package com.livespace.xposed;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.io.File;
import java.lang.reflect.Method;

/** Puts one real 16.1 CapsuleItemView (with a text label) into the container so the capsule can be seen before live data is wired up.
 *  Turn off: su -c 'touch /data/local/tmp/livespace/nodemo' and restart SystemUI. */
final class DemoCapsule {
  private static final String P = "com.oplus.systemui.notification.lockscreen.";

  static void add(Context ctx, ClassLoader cl, View container) {
    try {
      if (new File(CapsuleLoader.DIR, "nodemo").exists()) { Log.i("demo capsule skipped (nodemo file)"); return; }
      Class<?> contentCls = cl.loadClass(P + "notification.CapsuleNotificationContainerView");
      View content = (View) contentCls.getConstructor(Context.class).newInstance(ctx);
      TextView tv = new TextView(ctx);
      tv.setText("Live Space");
      tv.setTextColor(0xFFFFFFFF);
      tv.setTextSize(14);
      tv.setGravity(Gravity.CENTER);
      ((ViewGroup) content).addView(tv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
      Class<?> dataCls = cl.loadClass(P + "notification.CapsuleNotificationData");
      Object data = dataCls.getConstructor(String.class, contentCls, Integer.class, boolean.class)
          .newInstance("livespace_demo", content, Integer.valueOf(0xFF2A2A2A), Boolean.TRUE);
      Class<?> itemCls = cl.loadClass(P + "capsule.CapsuleItemView");
      View item = (View) itemCls.getConstructor(Context.class).newInstance(ctx);
      Method attach = null;
      for (Method m : itemCls.getMethods()) if (m.getName().equals("attachCapsuleEntry") && m.getParameterTypes().length == 2) attach = m;
      if (attach == null) throw new NoSuchMethodException("attachCapsuleEntry");
      attach.invoke(item, data, "demo");
      container.getClass().getMethod("addContainerView", View.class).invoke(container, item);
      Log.i("demo capsule added");
    } catch (Throwable t) { Log.e("demo capsule failed", t); }
  }
}
