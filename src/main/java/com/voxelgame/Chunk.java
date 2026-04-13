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
        // Store terrain heights for tree placement
        int[][] terrainHeights = new int[CHUNK_SIZE][CHUNK_SIZE];

        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                int worldX = chunkX * CHUNK_SIZE + x;
                int worldZ = chunkZ * CHUNK_SIZE + z;

                // Generate height using noise
                double scale = 0.02;
                double height = noise.noise(worldX * scale, worldZ * scale);
                height = (height + 1) / 2; // Normalize to 0-1
                int terrainHeight = (int) (height * 40 + 60); // Height range 60-100
                terrainHeights[x][z] = terrainHeight;

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

        // Generate trees
        generateTrees(terrainHeights);
    }

    /**
     * Place trees on grass blocks using a secondary noise function.
     * Trees stay 3 blocks from chunk edges to avoid cross-boundary issues.
     */
    private void generateTrees(int[][] terrainHeights) {
        // Use a different noise scale to decide tree placement
        double treeScale = 0.5;
        int margin = 3; // Stay away from chunk edges

        for (int x = margin; x < CHUNK_SIZE - margin; x++) {
            for (int z = margin; z < CHUNK_SIZE - margin; z++) {
                int worldX = chunkX * CHUNK_SIZE + x;
                int worldZ = chunkZ * CHUNK_SIZE + z;

                // Use noise to deterministically decide if a tree goes here
                double treeNoise = noise.noise(worldX * treeScale, worldZ * treeScale);

                // Only place trees where noise is high enough
                if (treeNoise > 0.4) {
                    // Check minimum distance from other potential trees
                    // by using a grid-like hash
                    int gridX = Math.floorDiv(worldX, 4);
                    int gridZ = Math.floorDiv(worldZ, 4);
                    double gridNoise = noise.noise(gridX * 13.7, gridZ * 13.7);
                    int treeLocalX = Math.floorMod((int)(Math.abs(gridNoise) * 1000), 4);
                    int treeLocalZ = Math.floorMod((int)(Math.abs(gridNoise) * 7777), 4);

                    if (Math.floorMod(worldX, 4) == treeLocalX &&
                        Math.floorMod(worldZ, 4) == treeLocalZ) {

                        int groundY = terrainHeights[x][z] - 1;

                        // Only place on grass
                        if (groundY > 0 && groundY < CHUNK_HEIGHT - 10 &&
                            blocks[x][groundY][z] == BlockType.GRASS.id) {
                            placeTree(x, groundY + 1, z);
                        }
                    }
                }
            }
        }
    }

    /**
     * Place a tree at the given position (trunk base).
     */
    private void placeTree(int x, int baseY, int z) {
        // Trunk height: 4-6 blocks (deterministic from position)
        int trunkHeight = 4 + ((x * 7 + z * 13) % 3); // 4, 5, or 6

        // Place trunk
        for (int y = baseY; y < baseY + trunkHeight && y < CHUNK_HEIGHT; y++) {
            setBlockSafe(x, y, z, BlockType.WOOD.id);
        }

        // Place leaves canopy
        int topY = baseY + trunkHeight;

        // Bottom leaf layer (3x3 to 5x5)
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                // Skip corners for a rounder look
                if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                setBlockSafe(x + dx, topY - 2, z + dz, BlockType.LEAVES.id);
                setBlockSafe(x + dx, topY - 1, z + dz, BlockType.LEAVES.id);
            }
        }

        // Top leaf layers (3x3 then 1x1)
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                setBlockSafe(x + dx, topY, z + dz, BlockType.LEAVES.id);
            }
        }
        // Very top
        setBlockSafe(x, topY + 1, z, BlockType.LEAVES.id);
    }

    /**
     * Set a block only if coordinates are within this chunk's bounds.
     */
    private void setBlockSafe(int x, int y, int z, byte type) {
        if (x >= 0 && x < CHUNK_SIZE && y >= 0 && y < CHUNK_HEIGHT && z >= 0 && z < CHUNK_SIZE) {
            blocks[x][y][z] = type;
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
        if (y < 0 || y >= CHUNK_HEIGHT) return true;
        return blocks[x][y][z] == BlockType.AIR.id;
    }
    
    private void addTopFace(List<Float> vertices, float x, float y, float z, byte type, float[] uvs) {
        float brightness = 1.0f;
        // Winding: CCW from above → normal points +Y (up)
        addQuad(vertices,
            x, y + 1, z + 1, uvs[0], uvs[1],
            x + 1, y + 1, z + 1, uvs[2], uvs[3],
            x + 1, y + 1, z, uvs[4], uvs[5],
            x, y + 1, z, uvs[6], uvs[7],
            brightness, brightness, brightness);
    }
    
    private void addBottomFace(List<Float> vertices, float x, float y, float z, byte type, float[] uvs) {
        float brightness = 0.5f;
        // Winding: CCW from below → normal points -Y (down)
        addQuad(vertices,
            x, y, z, uvs[0], uvs[1],
            x + 1, y, z, uvs[2], uvs[3],
            x + 1, y, z + 1, uvs[4], uvs[5],
            x, y, z + 1, uvs[6], uvs[7],
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