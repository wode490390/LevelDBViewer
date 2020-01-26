package com.nukkitx.leveldbviewer.util;

import java.util.Comparator;

public final class Utils {

    private Utils() {
        throw new AssertionError();
    }

    @SuppressWarnings("rawtypes")
    private static final Comparator NULL_COMPARATOR = (o1, o2) -> {
        if (o1 == null) {
            if (o2 == null) {
                return 0;
            }
            return -1;
        } else {
            if (o2 != null) {
                return 0;
            }
            return 1;
        }
    };

    @SuppressWarnings("unchecked")
    public static <T> Comparator<T> nullComparator() {
        return (Comparator<T>) NULL_COMPARATOR;
    }
}
