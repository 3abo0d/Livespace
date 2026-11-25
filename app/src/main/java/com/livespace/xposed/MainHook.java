package com.livespace.xposed;

import android.view.View;
import android.view.ViewGroup;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {
  static final String PANEL = "com.android.systemui.shade.NotificationPanelView";

  @Override
  public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpp) {
    if (!"com.android.systemui".equals(lpp.packageName)) return;
    final ClassLoader host = lpp.classLoader;
    try {
      Diagnostics.report(host);
      Class<?> panel = XposedHelpers.findClassIfExists(PANEL, host);
      if (panel == null) { Log.w("NotificationPanelView not found, nothing to hook"); return; }
      XposedBridge.hookAllConstructors(panel, new XC_MethodHook() {
        @Override protected void afterHookedMethod(MethodHookParam p) {
          try {
            final View v = (View) p.thisObject;
            v.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
              @Override public void onViewAttachedToWindow(View x) { CapsuleHost.attach((ViewGroup) x, host); }
              @Override public void onViewDetachedFromWindow(View x) { }
            });
          } catch (Throwable t) { Log.e("panel hook failed", t); }
        }
      });
      Log.i("hooked " + PANEL);
    } catch (Throwable t) { Log.e("handleLoadPackage failed", t); }
  }
}
