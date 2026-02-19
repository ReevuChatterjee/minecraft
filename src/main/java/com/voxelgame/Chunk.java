package com.voxelgame;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL33C.*;

public class Chunk {
    public static final int CHUNK_SIZE = 16;
    public static final int CHUNK_HEIGHT = 256;
    
    private int chunkX, chunkZ;
    private byte[][][] blocks; // [x][y][z]
    
    private int vao, vbo;
    private int vertexCount;
    
    private SimplexNoise noise;
    private static TextureAtlas textureAtlas; // Shared across all chunks
    
    public static void setTextureAtlas(TextureAtlas atlas) {
        textureAtlas = atlas;
    }
    
    public Chunk(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.blocks = new byte[CHUNK_SIZE][CHUNK_HEIGHT][CHUNK_SIZE];
        this.noise = new SimplexNoise();
    }
    
    public void generate() {
        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                int worldX = chunkX * CHUNK_SIZE + x;
                int worldZ = chunkZ * CHUNK_SIZE + z;
                
                // Generate height using noise
                double scale = 0.02;
                double height = noise.noise(worldX * scale, worldZ * scale);
                height = (height + 1) / 2; // Normalize to 0-1
                int terrainHeight = (int) (height * 40 + 60); // Height range 60-100
                
                // Fill blocks
                for (int y = 0; y < CHUNK_HEIGHT; y++) {
                    if (y == 0) {
                        blocks[x][y][z] = BlockType.BEDROCK.id;
                    } else if (y < terrainHeight - 4) {
                        blocks[x][y][z] = BlockType.STONE.id;
                    } else if (y < terrainHeight - 1) {
                        blocks[x][y][z] = BlockType.DIRT.id;
                    } else if (y == terrainHeight - 1) {
                        blocks[x][y][z] = BlockType.GRASS.id;
                    } else {
                        blocks[x][y][z] = BlockType.AIR.id;
                    }
                }
            }
        }
    }
    
    public void buildMesh() {
        List<Float> vertices = new ArrayList<>();
        
        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int y = 0; y < CHUNK_HEIGHT; y++) {
                for (int z = 0; z < CHUNK_SIZE; z++) {
                    byte blockType = blocks[x][y][z];
                    if (blockType == BlockType.AIR.id) continue;
                    
                    int worldX = chunkX * CHUNK_SIZE + x;
                    int worldZ = chunkZ * CHUNK_SIZE + z;
                    
                    float[] uvs = textureAtlas.getUVs(blockType);
                    
                    // Check each face and only add if exposed
                    // Top face
                    if (shouldRenderFace(x, y + 1, z)) {
                        addTopFace(vertices, worldX, y, worldZ, blockType, uvs);
                    }
                    // Bottom face
                    if (shouldRenderFace(x, y - 1, z)) {
                        addBottomFace(vertices, worldX, y, worldZ, blockType, uvs);
                    }
                    // North face (+Z)
                    if (shouldRenderFace(x, y, z + 1)) {
                        addNorthFace(vertices, worldX, y, worldZ, blockType, uvs);
                    }
                    // South face (-Z)
                    if (shouldRenderFace(x, y, z - 1)) {
                        addSouthFace(vertices, worldX, y, worldZ, blockType, uvs);
                    }
                    // East face (+X)
                    if (shouldRenderFace(x + 1, y, z)) {
                        addEastFace(vertices, worldX, y, worldZ, blockType, uvs);
                    }
                    // West face (-X)
                    if (shouldRenderFace(x - 1, y, z)) {
                        addWestFace(vertices, worldX, y, worldZ, blockType, uvs);
                    }
                }
            }
        }
        
        vertexCount = vertices.size() / 8; // 8 floats per vertex (3 pos + 2 uv + 3 color)
        
        if (vertexCount == 0) return;
        
        // Convert to float array
        float[] vertexArray = new float[vertices.size()];
        for (int i = 0; i < vertices.size(); i++) {
            vertexArray[i] = vertices.get(i);
        }
        
        // Clean up old buffers if they exist
        if (vao != 0) glDeleteVertexArrays(vao);
        if (vbo != 0) glDeleteBuffers(vbo);
        
        // Create VAO and VBO
        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, vertexArray, GL_STATIC_DRAW);
        
        // Position attribute
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 8 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        
        // UV attribute
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 8 * Float.BYTES, 3 * Float.BYTES);
        glEnableVertexAttribArray(1);
        
        // Color/brightness attribute
        glVertexAttribPointer(2, 3, GL_FLOAT, false, 8 * Float.BYTES, 5 * Float.BYTES);
        glEnableVertexAttribArray(2);
        
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }
    
    private boolean shouldRenderFace(int x, int y, int z) {
        if (x < 0 || x >= CHUNK_SIZE || z < 0 || z >= CHUNK_SIZE) return true;
        if (y < 0 || y >= CHUNK_HEIGHT) return false;
        return blocks[x][y][z] == BlockType.AIR.id;
    }
    
    private void addTopFace(List<Float> vertices, float x, float y, float z, byte type, float[] uvs) {
        float brightness = 1.0f;
        addQuad(vertices,
            x, y + 1, z, uvs[0], uvs[1],
            x + 1, y + 1, z, uvs[2], uvs[3],
            x + 1, y + 1, z + 1, uvs[4], uvs[5],
            x, y + 1, z + 1, uvs[6], uvs[7],
            brightness, brightness, brightness);
    }
    
    private void addBottomFace(List<Float> vertices, float x, float y, float z, byte type, float[] uvs) {
        float brightness = 0.5f;
        addQuad(vertices,
            x, y, z + 1, uvs[0], uvs[1],
            x + 1, y, z + 1, uvs[2], uvs[3],
            x + 1, y, z, uvs[4], uvs[5],
            x, y, z, uvs[6], uvs[7],
            brightness, brightness, brightness);
    }
    
    private void addNorthFace(List<Float> vertices, float x, float y, float z, byte type, float[] uvs) {
        float brightness = 0.8f;
        addQuad(vertices,
            x, y, z + 1, uvs[0], uvs[1],
            x + 1, y, z + 1, uvs[2], uvs[3],
            x + 1, y + 1, z + 1, uvs[4], uvs[5],
            x, y + 1, z + 1, uvs[6], uvs[7],
            brightness, brightness, brightness);
    }
    
    private void addSouthFace(List<Float> vertices, float x, float y, float z, byte type, float[] uvs) {
        float brightness = 0.8f;
        addQuad(vertices,
            x + 1, y, z, uvs[0], uvs[1],
            x, y, z, uvs[2], uvs[3],
            x, y + 1, z, uvs[4], uvs[5],
            x + 1, y + 1, z, uvs[6], uvs[7],
            brightness, brightness, brightness);
    }
    
    private void addEastFace(List<Float> vertices, float x, float y, float z, byte type, float[] uvs) {
        float brightness = 0.7f;
        addQuad(vertices,
            x + 1, y, z + 1, uvs[0], uvs[1],
            x + 1, y, z, uvs[2], uvs[3],
            x + 1, y + 1, z, uvs[4], uvs[5],
            x + 1, y + 1, z + 1, uvs[6], uvs[7],
            brightness, brightness, brightness);
    }
    
    private void addWestFace(List<Float> vertices, float x, float y, float z, byte type, float[] uvs) {
        float brightness = 0.7f;
        addQuad(vertices,
            x, y, z, uvs[0], uvs[1],
            x, y, z + 1, uvs[2], uvs[3],
            x, y + 1, z + 1, uvs[4], uvs[5],
            x, y + 1, z, uvs[6], uvs[7],
            brightness, brightness, brightness);
    }
    
    private void addQuad(List<Float> vertices, 
                        float x1, float y1, float z1, float u1, float v1,
                        float x2, float y2, float z2, float u2, float v2,
                        float x3, float y3, float z3, float u3, float v3,
                        float x4, float y4, float z4, float u4, float v4,
                        float r, float g, float b) {
        // First triangle
        vertices.add(x1); vertices.add(y1); vertices.add(z1);
        vertices.add(u1); vertices.add(v1);
        vertices.add(r); vertices.add(g); vertices.add(b);
        
        vertices.add(x2); vertices.add(y2); vertices.add(z2);
        vertices.add(u2); vertices.add(v2);
        vertices.add(r); vertices.add(g); vertices.add(b);
        
        vertices.add(x3); vertices.add(y3); vertices.add(z3);
        vertices.add(u3); vertices.add(v3);
        vertices.add(r); vertices.add(g); vertices.add(b);
        
        // Second triangle
        vertices.add(x1); vertices.add(y1); vertices.add(z1);
        vertices.add(u1); vertices.add(v1);
        vertices.add(r); vertices.add(g); vertices.add(b);
        
        vertices.add(x3); vertices.add(y3); vertices.add(z3);
        vertices.add(u3); vertices.add(v3);
        vertices.add(r); vertices.add(g); vertices.add(b);
        
        vertices.add(x4); vertices.add(y4); vertices.add(z4);
        vertices.add(u4); vertices.add(v4);
        vertices.add(r); vertices.add(g); vertices.add(b);
    }
    
    public void render() {
        if (vertexCount == 0) return;
        
        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, vertexCount);
        glBindVertexArray(0);
    }
    
    public byte getBlock(int x, int y, int z) {
        if (x < 0 || x >= CHUNK_SIZE || y < 0 || y >= CHUNK_HEIGHT || z < 0 || z >= CHUNK_SIZE) {
            return BlockType.AIR.id;
        }
        return blocks[x][y][z];
    }
    
    public void setBlock(int x, int y, int z, byte blockType) {
        if (x < 0 || x >= CHUNK_SIZE || y < 0 || y >= CHUNK_HEIGHT || z < 0 || z >= CHUNK_SIZE) {
            return;
        }
        blocks[x][y][z] = blockType;
    }
    
    public void cleanup() {
        if (vao != 0) glDeleteVertexArrays(vao);
        if (vbo != 0) glDeleteBuffers(vbo);
    }
}