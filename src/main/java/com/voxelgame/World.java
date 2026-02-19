package com.voxelgame;

import org.joml.Vector3i;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

public class World {
    private Map<ChunkPosition, Chunk> chunks;
    private ShaderProgram shader;
    private TextureAtlas textureAtlas;
    private int renderDistance = 8; // Chunks
    
    public World() {
        chunks = new HashMap<>();
        shader = new ShaderProgram();
        textureAtlas = new TextureAtlas();
        Chunk.setTextureAtlas(textureAtlas);
        
        // Generate initial chunks
        generateInitialChunks();
    }
    
    private void generateInitialChunks() {
        int radius = renderDistance / 2;
        for (int x = -radius; x < radius; x++) {
            for (int z = -radius; z < radius; z++) {
                ChunkPosition pos = new ChunkPosition(x, z);
                Chunk chunk = new Chunk(x, z);
                chunk.generate();
                chunk.buildMesh();
                chunks.put(pos, chunk);
            }
        }
        System.out.println("Generated " + chunks.size() + " chunks");
    }
    
    /**
     * Update chunks based on camera position - dynamic loading
     */
    public void update(Camera camera) {
        // Get camera chunk position
        int cameraChunkX = (int) Math.floor(camera.getPosition().x / Chunk.CHUNK_SIZE);
        int cameraChunkZ = (int) Math.floor(camera.getPosition().z / Chunk.CHUNK_SIZE);
        
        // Check which chunks should exist
        int radius = renderDistance / 2;
        List<ChunkPosition> shouldExist = new ArrayList<>();
        
        for (int x = cameraChunkX - radius; x < cameraChunkX + radius; x++) {
            for (int z = cameraChunkZ - radius; z < cameraChunkZ + radius; z++) {
                shouldExist.add(new ChunkPosition(x, z));
            }
        }
        
        // Remove chunks that are too far
        List<ChunkPosition> toRemove = new ArrayList<>();
        for (ChunkPosition pos : chunks.keySet()) {
            if (!shouldExist.contains(pos)) {
                toRemove.add(pos);
            }
        }
        
        for (ChunkPosition pos : toRemove) {
            chunks.get(pos).cleanup();
            chunks.remove(pos);
        }
        
        // Add new chunks that don't exist yet
        for (ChunkPosition pos : shouldExist) {
            if (!chunks.containsKey(pos)) {
                Chunk chunk = new Chunk(pos.x, pos.z);
                chunk.generate();
                chunk.buildMesh();
                chunks.put(pos, chunk);
            }
        }
    }
    
    /**
     * Get block at world coordinates
     */
    public byte getBlock(int worldX, int worldY, int worldZ) {
        if (worldY < 0 || worldY >= Chunk.CHUNK_HEIGHT) {
            return BlockType.AIR.id;
        }
        
        // Convert to chunk coordinates
        int chunkX = Math.floorDiv(worldX, Chunk.CHUNK_SIZE);
        int chunkZ = Math.floorDiv(worldZ, Chunk.CHUNK_SIZE);
        
        ChunkPosition pos = new ChunkPosition(chunkX, chunkZ);
        Chunk chunk = chunks.get(pos);
        
        if (chunk == null) {
            return BlockType.AIR.id;
        }
        
        // Local block coordinates within chunk
        int localX = Math.floorMod(worldX, Chunk.CHUNK_SIZE);
        int localZ = Math.floorMod(worldZ, Chunk.CHUNK_SIZE);
        
        return chunk.getBlock(localX, worldY, localZ);
    }
    
    /**
     * Set block at world coordinates
     */
    public void setBlock(int worldX, int worldY, int worldZ, byte blockType) {
        if (worldY < 0 || worldY >= Chunk.CHUNK_HEIGHT) {
            return;
        }
        
        // Convert to chunk coordinates
        int chunkX = Math.floorDiv(worldX, Chunk.CHUNK_SIZE);
        int chunkZ = Math.floorDiv(worldZ, Chunk.CHUNK_SIZE);
        
        ChunkPosition pos = new ChunkPosition(chunkX, chunkZ);
        Chunk chunk = chunks.get(pos);
        
        if (chunk == null) {
            return;
        }
        
        // Local block coordinates within chunk
        int localX = Math.floorMod(worldX, Chunk.CHUNK_SIZE);
        int localZ = Math.floorMod(worldZ, Chunk.CHUNK_SIZE);
        
        chunk.setBlock(localX, worldY, localZ, blockType);
        chunk.buildMesh();
        
        // Rebuild neighboring chunks if block is on edge
        if (localX == 0) {
            rebuildChunk(chunkX - 1, chunkZ);
        } else if (localX == Chunk.CHUNK_SIZE - 1) {
            rebuildChunk(chunkX + 1, chunkZ);
        }
        
        if (localZ == 0) {
            rebuildChunk(chunkX, chunkZ - 1);
        } else if (localZ == Chunk.CHUNK_SIZE - 1) {
            rebuildChunk(chunkX, chunkZ + 1);
        }
    }
    
    private void rebuildChunk(int chunkX, int chunkZ) {
        ChunkPosition pos = new ChunkPosition(chunkX, chunkZ);
        Chunk chunk = chunks.get(pos);
        if (chunk != null) {
            chunk.buildMesh();
        }
    }
    
    /**
     * Break block with validation
     */
    public boolean breakBlock(Vector3i position) {
        byte blockType = getBlock(position.x, position.y, position.z);
        
        // Can't break air or bedrock
        if (blockType == BlockType.AIR.id || blockType == BlockType.BEDROCK.id) {
            return false;
        }
        
        setBlock(position.x, position.y, position.z, BlockType.AIR.id);
        return true;
    }
    
    /**
     * Place block with validation
     */
    public boolean placeBlock(Vector3i position, byte blockType) {
        byte existingBlock = getBlock(position.x, position.y, position.z);
        
        // Can't place in non-air blocks
        if (existingBlock != BlockType.AIR.id) {
            return false;
        }
        
        setBlock(position.x, position.y, position.z, blockType);
        return true;
    }
    
    public void render(Camera camera) {
        shader.use();
        shader.setMatrix4f("projection", camera.getProjectionMatrix());
        shader.setMatrix4f("view", camera.getViewMatrix());
        
        textureAtlas.bind();
        
        for (Chunk chunk : chunks.values()) {
            chunk.render();
        }
        
        textureAtlas.unbind();
        shader.stop();
    }
    
    public void cleanup() {
        for (Chunk chunk : chunks.values()) {
            chunk.cleanup();
        }
        textureAtlas.cleanup();
        shader.cleanup();
    }
    
    // Helper class for chunk positioning
    static class ChunkPosition {
        final int x, z;
        
        ChunkPosition(int x, int z) {
            this.x = x;
            this.z = z;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ChunkPosition that = (ChunkPosition) o;
            return x == that.x && z == that.z;
        }
        
        @Override
        public int hashCode() {
            return 31 * x + z;
        }
    }
}