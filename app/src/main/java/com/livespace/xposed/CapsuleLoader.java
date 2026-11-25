package com.livespace.xposed;

import com.livespace.shim.Adapters;
import dalvik.system.DexClassLoader;
import java.io.File;

/** Loads capsule.dex (built on the phone by tools/make_capsule_dex.py from the 16.1 SystemUI, patched for 16.0.x).
 *  Resolution order: live SystemUI classes first, then this module's com.livespace.* classes, then capsule.dex. */
final class CapsuleLoader {
  static final String DIR = "/data/local/tmp/livespace";
  static final String DEX = "capsule.dex";
  private static ClassLoader loader;
  private static boolean failed;

  static synchronized ClassLoader get(ClassLoader host) {
    if (loader != null) return loader;
    if (failed) return null;
    File f = new File(DIR, DEX);
    if (!f.canRead()) {
      Log.w("cannot read " + f + " (missing or SELinux denied; run scripts/build_capsule_dex.sh)");
      failed = true;
      return null;
    }
    try {
      Adapters.host = host;
      loader = new DexClassLoader(f.getAbsolutePath(), null, null, new Bridge(host, CapsuleLoader.class.getClassLoader()));
      Log.i("capsule dex loaded: " + f + " (" + f.length() + " bytes)");
      return loader;
    } catch (Throwable t) {
      failed = true;
      Log.e("DexClassLoader failed", t);
      return null;
    }
  }

  private static final class Bridge extends ClassLoader {
    private final ClassLoader module;
    Bridge(ClassLoader host, ClassLoader module) { super(host); this.module = module; }
    @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
      if (name.startsWith("com.livespace.")) return module.loadClass(name);
      throw new ClassNotFoundException(name);
    }
  }
}
