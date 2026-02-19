package com.voxelgame;

public enum BlockType {
    AIR((byte) 0, new float[]{0, 0, 0}),
    GRASS((byte) 1, new float[]{0.2f, 0.8f, 0.2f}),
    DIRT((byte) 2, new float[]{0.6f, 0.4f, 0.2f}),
    STONE((byte) 3, new float[]{0.5f, 0.5f, 0.5f}),
    BEDROCK((byte) 4, new float[]{0.2f, 0.2f, 0.2f});
    
    public final byte id;
    private final float[] color;
    
    BlockType(byte id, float[] color) {
        this.id = id;
        this.color = color;
    }
    
    public static float[] getColor(byte id) {
        for (BlockType type : values()) {
            if (type.id == id) {
                return type.color;
            }
        }
        return new float[]{1.0f, 0.0f, 1.0f}; // Magenta for unknown
    }
} 
    

