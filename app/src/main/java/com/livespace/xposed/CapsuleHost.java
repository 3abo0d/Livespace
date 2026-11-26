package com.livespace.xposed;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.content.res.AssetManager;
import java.io.File;
import java.lang.reflect.Method;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.io.FileReader;

/** Builds the 16.1 NotificationCapsuleContainer and attaches it to the keyguard NotificationPanelView.
 *  Crash-loop guard: if SystemUI died 3 times in a row shortly after attaching, the capsule stays off.
 *  Re-enable: su -c 'rm /data/user/0/com.android.systemui/files/livespace_attempts'
 *  Manual kill switch: su -c 'touch /data/local/tmp/livespace/disable' (remove the file to re-enable). */
final class CapsuleHost {
  static final String CONTAINER = "com.oplus.systemui.notification.lockscreen.capsule.NotificationCapsuleContainer";
  static final String TAG = "livespace_capsule";
  private static boolean armed;
  private static boolean resAdded;

  static void attach(ViewGroup panel, ClassLoader host) {
    try {
      if (panel.findViewWithTag(TAG) != null) return;
      if (!guard(panel.getContext(), panel)) return;
      ClassLoader cl = CapsuleLoader.get(host);
      if (cl == null) return;
      addResources(panel.getContext());
      Class<?> c = cl.loadClass(CONTAINER);
      View v = (View) c.getConstructor(Context.class).newInstance(panel.getContext());
      v.setTag(TAG);
      panel.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
      UdfpsAnchor.install(panel, v);
      Log.i("capsule container attached");
    } catch (Throwable t) { Log.e("attach failed", t); }
  }

  /** Adds the relocated 16.1 resources (package id 0x7e, built by tools/make_capsule_res.py) to SystemUI's AssetManager. */
  private static void addResources(Context ctx) {
    if (resAdded) return;
    File f = new File(CapsuleLoader.DIR, "capsule_res.apk");
    if (!f.canRead()) { Log.w("cannot read " + f + " (run scripts/build_capsule_dex.sh)"); return; }
    try {
      Method m = AssetManager.class.getDeclaredMethod("addAssetPath", String.class);
      m.setAccessible(true);
      Object cookie = m.invoke(ctx.getAssets(), f.getAbsolutePath());
      resAdded = true;
      Log.i("resources added, cookie=" + cookie);
    } catch (Throwable t) { Log.e("addAssetPath failed", t); }
  }

  private static boolean guard(Context ctx, View anchor) {
    if (new File(CapsuleLoader.DIR, "disable").exists()) { Log.w("disable file present, capsule off"); return false; }
    if (armed) return true;
    try {
      final File flag = new File(ctx.getFilesDir(), "livespace_attempts");
      int n = 0;
      if (flag.exists()) { try (BufferedReader r = new BufferedReader(new FileReader(flag))) { n = Integer.parseInt(r.readLine().trim()); } catch (Throwable ignored) { } }
      if (n >= 3) { Log.w("crash-loop guard tripped (" + n + " unstable starts), capsule off"); return false; }
      write(flag, n + 1);
      armed = true;
      anchor.postDelayed(() -> write(flag, 0), 60_000);
    } catch (Throwable t) { Log.e("guard failed, continuing", t); }
    return true;
  }

  private static void write(File f, int n) {
    try (FileWriter w = new FileWriter(f)) { w.write(Integer.toString(n)); } catch (Throwable ignored) { }
  }
}
