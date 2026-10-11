package com.bytebazaar.util;
public final class MaskUtil {
    private MaskUtil() {}
    public static String account(String n) {
        if (n == null || n.length() < 4) return "XXXX";
        return "XXXX XXXX " + n.substring(n.length() - 4);
    }
}
