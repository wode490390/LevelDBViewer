package com.nukkitx.leveldbviewer.util;

import java.util.Arrays;
import java.util.Comparator;

public enum LevelDBKey {
    DATA_2D('-'),
    DATA_2D_LEGACY('.'),
    LEGACY_TERRAIN('0'),
    BLOCK_ENTITIES('1'),
    ENTITIES('2'),
    PENDING_TICKS('3'),
    BLOCK_EXTRA_DATA('4'),
    BIOME_STATE('5'),
    STATE_FINALIZATION('6'),
    BORDER_BLOCKS('8'),
    HARDCODED_SPAWNERS('9'),
    FLAGS('f'),
    VERSION('v'),
    SUBCHUNK_PREFIX('/');

    private static final LevelDBKey[] VALUES;

    static {
        LevelDBKey[] values = values();
        VALUES = Arrays.copyOf(values, values.length - 1);
    }

    private final byte encoded;

    LevelDBKey(char encoded) {
        this.encoded = (byte) encoded;
    }

    public static LevelDBKey fromBytes(byte[] key) {
        if (key.length == 9) {
            for (LevelDBKey dbKey : VALUES) {
                if (key[8] == dbKey.encoded) {
                    return dbKey;
                }
            }
        } else if (key.length == 10) {
            if (key[8] == SUBCHUNK_PREFIX.encoded) {
                return SUBCHUNK_PREFIX;
            }
        }
        return null;
    }

    public long getChunkXZ(byte[] key) {
        return ((long) this.getChunkX(key)) + ((long) this.getChunkZ(key));
    }

    public int getChunkX(byte[] key) {
        return key[0] | (key[1] << 8) | (key[2] << 16) | (key[3] << 24);
    }

    public int getChunkZ(byte[] key) {
        return key[4] | (key[5] << 8) | (key[6] << 16) | (key[7] << 24);
    }

    public int getSubChunkY(byte[] key) {
        return key[9];
    }

    public String toString(byte[] key) {
        String toString = "(" + getChunkX(key) + ", " + getChunkZ(key);
        if (this == LevelDBKey.SUBCHUNK_PREFIX) {
            toString += ", " + getSubChunkY(key);
        }
        return toString + "): " + name();
    }
}
