package com.livespace.xposed;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import java.io.File;
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

  static void attach(ViewGroup panel, ClassLoader host) {
    try {
      if (panel.findViewWithTag(TAG) != null) return;
      if (!guard(panel.getContext(), panel)) return;
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
