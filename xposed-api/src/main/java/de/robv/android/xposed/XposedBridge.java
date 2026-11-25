package de.robv.android.xposed;
public final class XposedBridge {
  public static void log(String text) { throw new RuntimeException("stub"); }
  public static void log(Throwable t) { throw new RuntimeException("stub"); }
  public static java.util.Set<XC_MethodHook> hookAllConstructors(Class<?> c, XC_MethodHook cb) { throw new RuntimeException("stub"); }
  public static java.util.Set<XC_MethodHook> hookAllMethods(Class<?> c, String name, XC_MethodHook cb) { throw new RuntimeException("stub"); }
}
