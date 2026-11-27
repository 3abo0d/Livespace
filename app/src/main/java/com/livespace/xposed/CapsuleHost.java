package com.livespace.xposed;

import android.content.Context;
import android.content.res.AssetManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.lang.reflect.Method;

/** Builds the 16.1 NotificationCapsuleContainer and attaches it (inside a SafeHost) to the keyguard NotificationPanelView.
 *  Crash-loop guard: 3 SystemUI starts within 15 minutes is the limit; after that the capsule stays off until things are stable for 15 minutes.
 *  Manual re-enable: su -c 'rm /data/user/0/com.android.systemui/files/livespace_attempts'
 *  Manual kill switch: su -c 'touch /data/local/tmp/livespace/disable' (remove the file to re-enable). */
final class CapsuleHost {
  static final String CONTAINER = "com.oplus.systemui.notification.lockscreen.capsule.NotificationCapsuleContainer";
  static final String TAG = "livespace_capsule";
  private static boolean armed;
  private static boolean tripped;
  private static boolean containerFailed;

  static void attach(ViewGroup panel, ClassLoader host) {
    try {
      if (panel.findViewWithTag(TAG) != null) return;
      if (CapsuleGuard.disabled || !guard(panel.getContext())) return;
      CapsuleGuard.installNet();
      installResourcesHook();
      if (!containerFailed) {
        try {
          ClassLoader cl = CapsuleLoader.get(host);
          if (cl == null) throw new IllegalStateException("capsule.dex not available");
          ensureResources(panel.getContext());
          Class<?> c = cl.loadClass(CONTAINER);
          View v = (View) c.getConstructor(Context.class).newInstance(panel.getContext());
          SafeHost safe = new SafeHost(panel.getContext(), v);
          safe.setTag(TAG);
          panel.addView(safe, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
          UdfpsAnchor.install(panel, safe);
          Log.i("capsule container attached (guarded)");
          final Context uiCtx = panel.getContext(); final ClassLoader capCl = cl; final View cont = v;
          safe.post(() -> DemoCapsule.add(uiCtx, capCl, cont));
          return;
        } catch (Throwable t) { containerFailed = true; Log.e("attach failed, showing placeholder", t); }
      }
      View pill = FallbackPill.create(panel.getContext());
      pill.setTag(TAG);
      panel.addView(pill, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
      UdfpsAnchor.install(panel, pill);
      Log.i("placeholder pill attached");
    } catch (Throwable t) { Log.e("attach failed", t); }
  }

  static final int PROBE_ID = 0x7e0d008d;  // layout/capsule_item_view in the relocated resource package

  /** Adds the relocated 16.1 resources (package id 0x7e, built by tools/make_capsule_res.py) to an AssetManager. */
  static boolean addTo(AssetManager am) {
    File f = new File(CapsuleLoader.DIR, "capsule_res.apk");
    if (!f.canRead()) { Log.w("cannot read " + f + " (run scripts/build_capsule_dex.sh)"); return false; }
    try {
      Method m = AssetManager.class.getDeclaredMethod("addAssetPath", String.class);
      m.setAccessible(true);
      Object cookie = m.invoke(am, f.getAbsolutePath());
      return cookie instanceof Integer && ((Integer) cookie) != 0;
    } catch (Throwable t) { Log.e("addAssetPath failed", t); return false; }
  }

  /** Android rebuilds Resources/AssetManager on configuration or theme changes, which drops paths added at runtime.
   *  Check that the capsule layout still resolves; if not, add the package again. */
  static void ensureResources(Context ctx) {
    try { ctx.getResources().getResourceName(PROBE_ID); return; } catch (Throwable ignored) { }
    boolean ok = addTo(ctx.getAssets());
    Log.i("capsule resources were missing, re-added: " + ok);
  }

  /** Add the package to every AssetManager Android creates from now on (SystemUI process only). */
  private static boolean hookInstalled;
  static synchronized void installResourcesHook() {
    if (hookInstalled) return;
    hookInstalled = true;
    try {
      Class<?> rm = Class.forName("android.app.ResourcesManager");
      XposedBridge.hookAllMethods(rm, "createAssetManager", new XC_MethodHook() {
        @Override protected void afterHookedMethod(MethodHookParam p) {
          try {
            if (CapsuleGuard.disabled) return;
            Object r = p.getResult();
            if (r instanceof AssetManager) addTo((AssetManager) r);
          } catch (Throwable ignored) { }
        }
      });
      Log.i("resources hook installed");
    } catch (Throwable t) { Log.e("resources hook failed", t); }
  }

  private static boolean guard(Context ctx) {
    if (tripped) return false;
    if (new File(CapsuleLoader.DIR, "disable").exists()) { Log.w("disable file present, capsule off"); return false; }
    if (armed) return true;
    try {
      File flag = new File(ctx.getFilesDir(), "livespace_attempts");
      CapsuleGuard.stateFile = flag;
      int n = 0; long last = 0;
      if (flag.exists()) {
        try (BufferedReader r = new BufferedReader(new FileReader(flag))) {
          String[] p = r.readLine().trim().split(",");
          n = Integer.parseInt(p[0]); last = Long.parseLong(p[1]);
        } catch (Throwable ignored) { }
      }
      long now = System.currentTimeMillis();
      n = (now - last > 15 * 60_000L) ? 1 : n + 1;
      CapsuleGuard.writeState(n, now);
      if (n > 3) { tripped = true; Log.w("crash-loop guard tripped (" + n + " SystemUI starts within 15 min), capsule off until stable"); return false; }
      armed = true;
    } catch (Throwable t) { Log.e("guard failed, continuing", t); armed = true; }
    return true;
  }
}
