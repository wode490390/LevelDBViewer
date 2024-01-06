package com.nukkitx.leveldbviewer.util;

import java.util.Arrays;

public enum LevelDBKey {
    HEIGHTMAP_AND_3D_BIOMES('+'),
    NEW_VERSION(','), // since 1.16.100?
    HEIGHTMAP_AND_2D_BIOMES('-'), // obsolete since 1.18
    HEIGHTMAP_AND_2D_BIOME_COLORS('.'), // obsolete since 1.0
    LEGACY_TERRAIN('0'), // obsolete since 1.0
    BLOCK_ENTITIES('1'),
    ENTITIES('2'),
    PENDING_SCHEDULED_TICKS('3'),
    LEGACY_BLOCK_EXTRA_DATA('4'), // obsolete since 1.2.13
    BIOME_STATES('5'), //TODO: is this still applicable to 1.18.0?
    FINALIZATION('6'),
    CONVERTER_TAG('7'), // ???
    BORDER_BLOCKS('8'),
    HARDCODED_SPAWNERS('9'),
    PENDING_RANDOM_TICKS(':'),
    XXHASH_CHECKSUMS(';'), // obsolete since 1.18
    GENERATION_SEED('<'),
    GENERATED_BEFORE_CNC_BLENDING('='),
    BLENDING_BIOME_HEIGHT('>'),
    OLD_VERSION('v'),
    META_DATA_HASH('?'),
    BLENDING_DATA('@'),
    ENTITY_DIGEST_VERSION('A'), // since 1.18.30
    @Deprecated
    NUKKIT_DATA('f'), //TODO
    SUBCHUNK_PREFIX('/'),
    ;

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
        if (key.length == 14) {
            if (key[12] == SUBCHUNK_PREFIX.encoded) {
                return SUBCHUNK_PREFIX;
            }
            return null;
        }
        if (key.length == 13) {
            for (LevelDBKey dbKey : VALUES) {
                if (key[12] == dbKey.encoded) {
                    return dbKey;
                }
            }
            return null;
        }
        if (key.length == 10) {
            if (key[8] == SUBCHUNK_PREFIX.encoded) {
                return SUBCHUNK_PREFIX;
            }
            return null;
        }
        if (key.length == 9) {
            for (LevelDBKey dbKey : VALUES) {
                if (key[8] == dbKey.encoded) {
                    return dbKey;
                }
            }
        }
        return null;
    }

    public static int getDimension(byte[] key) {
        if (key.length == 13 || key.length == 14) {
            return key[8] | (key[9] << 8) | (key[10] << 16) | (key[11] << 24);
        }
        return 0;
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
        if (key.length == 14) {
            return key[13];
        }
        return key[9];
    }

    public String toString(byte[] key) {
        String toString = "(" + getChunkX(key) + ", " + getChunkZ(key);
        if (this == LevelDBKey.SUBCHUNK_PREFIX) {
            toString += " | " + getSubChunkY(key);
        }
        return toString + "): " + name();
    }
}
