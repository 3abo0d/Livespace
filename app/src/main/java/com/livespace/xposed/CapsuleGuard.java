package com.livespace.xposed;

import android.os.Handler;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.io.File;
import java.io.FileWriter;

/** Keeps capsule problems from taking SystemUI down: once any capsule code throws, the capsule is switched off and the full stack trace goes to the LSPosed log. */
final class CapsuleGuard {
  static volatile boolean disabled;
  private static boolean netInstalled;
  static File stateFile;

  /** True when a capsule/ module class appears anywhere in the exception's stack (or its causes). */
  static boolean isOurs(Throwable t) {
    for (int i = 0; t != null && i < 8; i++, t = t.getCause()) {
      for (StackTraceElement e : t.getStackTrace()) {
        String c = e.getClassName();
        if (c.startsWith("com.oplus.systemui.notification.lockscreen") || c.startsWith("ls.") || c.startsWith("com.livespace.")) return true;
      }
    }
    return false;
  }

  static void trip(String where, Throwable t) {
    if (!disabled) Log.e("capsule disabled after error in " + where, t);
    disabled = true;
    writeState(100, System.currentTimeMillis());  // makes the next SystemUI start skip the capsule too
  }

  static void writeState(int n, long when) {
    try (FileWriter w = new FileWriter(stateFile)) { w.write(n + "," + when); } catch (Throwable ignored) { }
  }

  /** Last line of defence: an exception thrown out of a handler message / animation callback that came from capsule code is swallowed instead of killing SystemUI. */
  static synchronized void installNet() {
    if (netInstalled) return;
    netInstalled = true;
    try {
      XposedBridge.hookAllMethods(Handler.class, "dispatchMessage", new XC_MethodHook() {
        @Override protected void afterHookedMethod(MethodHookParam p) {
          Throwable t = p.getThrowable();
          if (t != null && isOurs(t)) { p.setThrowable(null); trip("handler message", t); }
        }
      });
      Log.i("safety net installed");
    } catch (Throwable t) { Log.e("safety net failed", t); }
  }
}
