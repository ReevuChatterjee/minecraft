package com.voxelgame;

import java.nio.ByteBuffer;
import java.util.Random;

import static org.lwjgl.opengl.GL33C.*;

/**
 * Manages texture atlas for block textures
 * Atlas is a single image containing all block textures in a grid
 */
public class TextureAtlas {
    private int textureId;
    private int atlasSize = 16; // 16x16 textures in atlas
    private int textureSize = 16; // Each texture is 16x16 pixels
    private Random texRandom = new Random(12345); // Fixed seed for consistent textures

    public TextureAtlas() {
        createProceduralAtlas();
    }

    /**
     * Create a procedural texture atlas (no image file needed)
     */
    private void createProceduralAtlas() {
        int atlasPixelSize = atlasSize * textureSize;
        ByteBuffer imageData = ByteBuffer.allocateDirect(atlasPixelSize * atlasPixelSize * 4);

        // Generate textures procedurally
        for (int ty = 0; ty < atlasSize; ty++) {
            for (int tx = 0; tx < atlasSize; tx++) {
                generateTexture(imageData, tx, ty);
            }
        }

        imageData.flip();

        // Upload to GPU
        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);

        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, atlasPixelSize, atlasPixelSize,
                     0, GL_RGBA, GL_UNSIGNED_BYTE, imageData);

        glBindTexture(GL_TEXTURE_2D, 0);

        System.out.println("Created procedural texture atlas: " + atlasPixelSize + "x" + atlasPixelSize);
    }

    /**
     * Generate a single texture in the atlas
     */
    private void generateTexture(ByteBuffer buffer, int atlasX, int atlasY) {
        int index = atlasY * atlasSize + atlasX;

        // Map indices to block types with distinct patterns
        for (int py = 0; py < textureSize; py++) {
            for (int px = 0; px < textureSize; px++) {
                float[] color = getPixelColor(index, px, py);

                int r = (int) Math.max(0, Math.min(255, color[0] * 255));
                int g = (int) Math.max(0, Math.min(255, color[1] * 255));
                int b = (int) Math.max(0, Math.min(255, color[2] * 255));
                int a = 255;

                int bufferX = atlasX * textureSize + px;
                int bufferY = atlasY * textureSize + py;
                int atlasPixelSize = atlasSize * textureSize;
                int bufferPos = (bufferY * atlasPixelSize + bufferX) * 4;

                buffer.put(bufferPos, (byte) r);
                buffer.put(bufferPos + 1, (byte) g);
                buffer.put(bufferPos + 2, (byte) b);
                buffer.put(bufferPos + 3, (byte) a);
            }
        }
    }

    private float[] getPixelColor(int blockIndex, int px, int py) {
        float noise = texRandom.nextFloat() * 0.15f - 0.075f;

        switch (blockIndex) {
            case 1: // Grass - green with darker patches
                float gn = texRandom.nextFloat() * 0.15f - 0.05f;
                return new float[]{0.18f + gn, 0.72f + gn * 1.5f, 0.15f + gn};

            case 2: // Dirt - brown with speckles
                float dn = texRandom.nextFloat() * 0.12f - 0.06f;
                return new float[]{0.55f + dn, 0.36f + dn, 0.18f + dn};

            case 3: // Stone - gray with mineral flecks
                float sn = texRandom.nextFloat() * 0.18f - 0.09f;
                return new float[]{0.48f + sn, 0.48f + sn, 0.50f + sn};

            case 4: // Bedrock - dark with noise
                float bn = texRandom.nextFloat() * 0.1f - 0.05f;
                return new float[]{0.18f + bn, 0.18f + bn, 0.2f + bn};

            case 5: // Sand - warm yellow
                float san = texRandom.nextFloat() * 0.08f - 0.04f;
                return new float[]{0.90f + san, 0.85f + san, 0.54f + san};

            case 6: // Wood - brown bark with vertical grain lines
                float wn = texRandom.nextFloat() * 0.06f - 0.03f;
                boolean grain = (px % 4 == 0) || (px % 4 == 1 && py % 3 == 0);
                float woodBase = grain ? 0.42f : 0.52f;
                return new float[]{woodBase + wn + 0.08f, woodBase + wn - 0.08f, woodBase + wn - 0.22f};

            case 7: // Leaves - dark green with holes
                float ln = texRandom.nextFloat() * 0.2f - 0.05f;
                return new float[]{0.08f + ln * 0.3f, 0.42f + ln, 0.06f + ln * 0.2f};

            case 8: // Cobblestone - patchy gray
                float cn = texRandom.nextFloat() * 0.2f - 0.1f;
                boolean patch = ((px + py) % 5 < 2);
                float cobBase = patch ? 0.35f : 0.45f;
                return new float[]{cobBase + cn, cobBase + cn, cobBase + cn + 0.02f};

            case 9: // Planks - light brown with horizontal grain
                float pn = texRandom.nextFloat() * 0.06f - 0.03f;
                boolean plankGrain = (py % 4 == 0);
                float plankBase = plankGrain ? 0.62f : 0.72f;
                return new float[]{plankBase + pn + 0.05f, plankBase + pn - 0.05f, plankBase + pn - 0.25f};

            case 10: // Raw Beef - red marbled meat
                float mn = texRandom.nextFloat() * 0.12f - 0.06f;
                boolean fat = ((px + py * 3) % 7 < 2);
                return fat ? new float[]{0.9f + mn, 0.8f + mn, 0.75f + mn}
                           : new float[]{0.7f + mn, 0.12f + mn * 0.5f, 0.12f + mn * 0.5f};

            case 11: // Raw Mutton - pinkish
                float mtn = texRandom.nextFloat() * 0.1f - 0.05f;
                boolean mutFat = ((px * 2 + py) % 6 < 2);
                return mutFat ? new float[]{0.88f + mtn, 0.82f + mtn, 0.78f + mtn}
                              : new float[]{0.82f + mtn, 0.42f + mtn, 0.42f + mtn};

            default: // Magenta for unknown
                return new float[]{1.0f, 0.0f, 1.0f};
        }
    }

    /**
     * Get UV coordinates for a block type
     */
    public float[] getUVs(byte blockType) {
        int atlasX = blockType % atlasSize;
        int atlasY = blockType / atlasSize;

        float uvSize = 1.0f / atlasSize;
        float u = atlasX * uvSize;
        float v = atlasY * uvSize;

        return new float[]{
            u, v,                    // Bottom-left
            u + uvSize, v,           // Bottom-right
            u + uvSize, v + uvSize,  // Top-right
            u, v + uvSize            // Top-left
        };
    }

    public void bind() {
        glBindTexture(GL_TEXTURE_2D, textureId);
    }

    public void unbind() {
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    public void cleanup() {
        glDeleteTextures(textureId);
    }
}