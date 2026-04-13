package com.voxelgame;

public enum BlockType {
    AIR((byte) 0, new float[]{0, 0, 0}),
    GRASS((byte) 1, new float[]{0.2f, 0.8f, 0.2f}),
    DIRT((byte) 2, new float[]{0.6f, 0.4f, 0.2f}),
    STONE((byte) 3, new float[]{0.5f, 0.5f, 0.5f}),
    BEDROCK((byte) 4, new float[]{0.2f, 0.2f, 0.2f}),
    SAND((byte) 5, new float[]{0.92f, 0.87f, 0.56f}),
    WOOD((byte) 6, new float[]{0.55f, 0.35f, 0.15f}),
    LEAVES((byte) 7, new float[]{0.1f, 0.5f, 0.1f}),
    COBBLESTONE((byte) 8, new float[]{0.4f, 0.4f, 0.4f}),
    PLANKS((byte) 9, new float[]{0.75f, 0.6f, 0.35f}),
    RAW_BEEF((byte) 10, new float[]{0.7f, 0.15f, 0.15f}),
    RAW_MUTTON((byte) 11, new float[]{0.85f, 0.45f, 0.45f});

    public final byte id;
    private final float[] color;

    BlockType(byte id, float[] color) {
        this.id = id;
        this.color = color;
    }

    public float[] getColor() {
        return color;
    }

    public static float[] getColor(byte id) {
        for (BlockType type : values()) {
            if (type.id == id) {
                return type.color;
            }
        }
        return new float[]{1.0f, 0.0f, 1.0f};
    }

    public static BlockType getById(byte id) {
        for (BlockType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        return AIR;
    }

    public static String getName(byte id) {
        for (BlockType type : values()) {
            if (type.id == id) {
                return type.name();
            }
        }
        return "UNKNOWN";
    }
}
