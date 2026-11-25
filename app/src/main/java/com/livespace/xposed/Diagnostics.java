package com.livespace.xposed;

import de.robv.android.xposed.XposedHelpers;

/** Logs which relevant classes exist on this ROM so logcat/LSPosed log shows what 16.0.x really has. */
final class Diagnostics {
  static final String[] CLASSES = {
    "com.android.systemui.shade.NotificationPanelView",
    "com.oplus.systemui.keyguard.notificationcapsule.KeyguardNotificationCapsuleView",
    "com.oplus.systemui.keyguard.notificationcapsule.KeyguardNotificationCapsuleViewControllerImpl",
    "com.oplus.systemui.keyguard.ui.view.OplusKeyguardBottomAreaView",
    "com.oplus.systemui.notification.lockscreen.capsule.NotificationCapsuleContainer",
    "com.oplus.systemui.statusbar.seeding.livealert.LitePluginLoadManager",
    "com.oplus.systemui.media.controls.pipeline.OplusMediaPlayerManager",
  };
  static void report(ClassLoader cl) {
    for (String c : CLASSES) Log.i((XposedHelpers.findClassIfExists(c, cl) != null ? "present  " : "MISSING  ") + c);
  }
}
