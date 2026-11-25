package com.livespace.xposed;
import de.robv.android.xposed.XposedBridge;
final class Log {
  static void i(String m) { XposedBridge.log("[Livespace] " + m); }
  static void w(String m) { XposedBridge.log("[Livespace] WARN " + m); }
  static void e(String m, Throwable t) { XposedBridge.log("[Livespace] ERROR " + m); XposedBridge.log(t); }
}
