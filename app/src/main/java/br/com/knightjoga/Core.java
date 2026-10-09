package br.com.knightjoga;
public final class Core {
 static { System.loadLibrary("lunibridge"); }
 private Core() {}
 public static native String open(String path, byte[] rom, String romPath, String saveDir, boolean multitap);
 public static native void setMultitap(boolean enabled);
 public static native boolean reset();
 public static native void step(int[] inputs);
 public static native int[] pixels();
 public static native short[] audio();
 public static native int width();
 public static native int height();
 public static native boolean hasPicture();
 public static native int rate();
 public static native double fps();
 public static native byte[] snapshot();
 public static native boolean restore(byte[] state);
 public static native byte[] saveRam();
 public static native void loadRam(byte[] ram);
 public static native boolean setCheats(String[] enabledCodes);
 public static native void close();
}
