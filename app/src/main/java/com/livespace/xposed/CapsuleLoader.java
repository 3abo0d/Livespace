package com.livespace.xposed;

import dalvik.system.DexClassLoader;
import java.io.File;

/** Loads the 16.1 capsule classes (classes3.dex + classes8.dex of the 16.1 SystemUI) on top of the live SystemUI classloader.
 *  Parent-first: any class that already exists in 16.0.x SystemUI is used from the device, only 16.1-only classes come from the dex. */
final class CapsuleLoader {
  static final String DIR = "/data/local/tmp/livespace";
  static final String[] DEX = { "classes3.dex", "classes8.dex" };
  private static ClassLoader loader;
  private static boolean failed;

  static synchronized ClassLoader get(ClassLoader host) {
    if (loader != null) return loader;
    if (failed) return null;
    StringBuilder path = new StringBuilder();
    for (String n : DEX) {
      File f = new File(DIR, n);
      if (f.canRead()) { if (path.length() > 0) path.append(File.pathSeparator); path.append(f.getAbsolutePath()); }
      else Log.w("cannot read " + f + " (missing or SELinux denied; run scripts/install_dex.sh)");
    }
    if (path.length() == 0) { failed = true; return null; }
    try {
      loader = new DexClassLoader(path.toString(), null, null, host);
      Log.i("capsule dex loaded: " + path);
      return loader;
    } catch (Throwable t) { failed = true; Log.e("DexClassLoader failed", t); return null; }
  }
}
