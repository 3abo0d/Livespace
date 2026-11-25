package com.livespace.shim;

import java.lang.reflect.Constructor;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** Called from the patched capsule dex in place of members that exist in 16.1 SystemUI but not in 16.0.x. */
public final class Adapters {
  public static volatile ClassLoader host;
  private static final String P = "com.android.systemui.statusbar.notification.AnimatableProperty";

  /** 16.1: AnimatableProperty.from(name, setter, getter, animatorTag, startTag, endTag). 16.0.x lost the static helper but kept its inner classes. */
  public static Object animatableFrom(String name, BiConsumer<?, ?> setter, Function<?, ?> getter, int animatorTag, int startTag, int endTag) {
    try {
      Class<?> c5 = host.loadClass(P + "$5");
      Constructor<?> k5 = c5.getDeclaredConstructor(String.class, Function.class, BiConsumer.class);
      k5.setAccessible(true);
      Object property = k5.newInstance(name, getter, setter);
      Class<?> c6 = host.loadClass(P + "$6");
      Constructor<?> k6 = c6.getDeclaredConstructor(int.class, int.class, int.class, c5);
      k6.setAccessible(true);
      return k6.newInstance(startTag, endTag, animatorTag, property);
    } catch (Throwable t) {
      throw new RuntimeException("animatableFrom failed", t);
    }
  }
}
