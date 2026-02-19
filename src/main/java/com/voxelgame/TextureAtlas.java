package com.voxelgame;

import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL33C.*;
import static org.lwjgl.stb.STBImage.*;

/**
 * Manages texture atlas for block textures
 * Atlas is a single image containing all block textures in a grid
 */
public class TextureAtlas {
    private int textureId;
    private int atlasSize = 16; // 16x16 textures in atlas
    private int textureSize = 16; // Each texture is 16x16 pixels
    
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
        // Calculate base color based on atlas position
        int index = atlasY * atlasSize + atlasX;
        
        // Map indices to block types
        float[] baseColor;
        if (index == 1) { // Grass
            baseColor = new float[]{0.2f, 0.8f, 0.2f};
        } else if (index == 2) { // Dirt
            baseColor = new float[]{0.6f, 0.4f, 0.2f};
        } else if (index == 3) { // Stone
            baseColor = new float[]{0.5f, 0.5f, 0.5f};
        } else if (index == 4) { // Bedrock
            baseColor = new float[]{0.2f, 0.2f, 0.2f};
        } else { // Default
            baseColor = new float[]{1.0f, 0.0f, 1.0f}; // Magenta
        }
        
        // Generate texture pixels with some variation
        for (int py = 0; py < textureSize; py++) {
            for (int px = 0; px < textureSize; px++) {
                // Add noise for texture detail
                float noise = (float) Math.random() * 0.2f - 0.1f;
                
                int r = (int) Math.max(0, Math.min(255, (baseColor[0] + noise) * 255));
                int g = (int) Math.max(0, Math.min(255, (baseColor[1] + noise) * 255));
                int b = (int) Math.max(0, Math.min(255, (baseColor[2] + noise) * 255));
                int a = 255;
                
                // Calculate position in buffer
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
    
    /**
     * Get UV coordinates for a block type
     */
    public float[] getUVs(byte blockType) {
        // Map block type to atlas position
        int atlasX = blockType % atlasSize;
        int atlasY = blockType / atlasSize;
        
        float uvSize = 1.0f / atlasSize;
        float u = atlasX * uvSize;
        float v = atlasY * uvSize;
        
        // Return UV coordinates for the 4 corners of the texture
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