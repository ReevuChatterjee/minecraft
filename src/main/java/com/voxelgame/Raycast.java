package com.voxelgame;

import org.joml.Vector3f;
import org.joml.Vector3i;

/**
 * DDA (Digital Differential Analyzer) raycasting for voxel selection
 * Based on "A Fast Voxel Traversal Algorithm" by John Amanatides
 */
public class Raycast {
    private static final float MAX_DISTANCE = 5.0f;
    
    public static class RaycastResult {
        public Vector3i blockPosition;
        public Vector3i previousPosition; // For block placement
        public boolean hit;
        
        public RaycastResult() {
            this.hit = false;
        }
    }
    
    public static RaycastResult raycast(Vector3f origin, Vector3f direction, World world) {
        RaycastResult result = new RaycastResult();
        
        // Normalize direction
        Vector3f dir = new Vector3f(direction).normalize();
        
        // Current voxel position
        int x = (int) Math.floor(origin.x);
        int y = (int) Math.floor(origin.y);
        int z = (int) Math.floor(origin.z);
        
        // Direction to step in each axis (-1, 0, or 1)
        int stepX = (int) Math.signum(dir.x);
        int stepY = (int) Math.signum(dir.y);
        int stepZ = (int) Math.signum(dir.z);
        
        // tMax: distance along ray to next voxel boundary
        float tMaxX = intBound(origin.x, dir.x);
        float tMaxY = intBound(origin.y, dir.y);
        float tMaxZ = intBound(origin.z, dir.z);
        
        // tDelta: how far to move along ray to cross one voxel
        float tDeltaX = stepX / dir.x;
        float tDeltaY = stepY / dir.y;
        float tDeltaZ = stepZ / dir.z;
        
        // Track previous position for block placement
        int prevX = x, prevY = y, prevZ = z;
        
        // Traverse voxels
        while (true) {
            // Check if current block is solid
            byte blockType = world.getBlock(x, y, z);
            if (blockType != BlockType.AIR.id) {
                result.hit = true;
                result.blockPosition = new Vector3i(x, y, z);
                result.previousPosition = new Vector3i(prevX, prevY, prevZ);
                return result;
            }
            
            // Store previous position
            prevX = x;
            prevY = y;
            prevZ = z;
            
            // Step to next voxel
            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) {
                    if (tMaxX > MAX_DISTANCE) break;
                    x += stepX;
                    tMaxX += tDeltaX;
                } else {
                    if (tMaxZ > MAX_DISTANCE) break;
                    z += stepZ;
                    tMaxZ += tDeltaZ;
                }
            } else {
                if (tMaxY < tMaxZ) {
                    if (tMaxY > MAX_DISTANCE) break;
                    y += stepY;
                    tMaxY += tDeltaY;
                } else {
                    if (tMaxZ > MAX_DISTANCE) break;
                    z += stepZ;
                    tMaxZ += tDeltaZ;
                }
            }
        }
        
        return result;
    }
    
    /**
     * Calculate distance to next integer boundary
     */
    private static float intBound(float s, float ds) {
        if (ds < 0) {
            return intBound(-s, -ds);
        } else {
            s = mod(s, 1);
            return (1 - s) / ds;
        }
    }
    
    private static float mod(float value, float modulus) {
        return (value % modulus + modulus) % modulus;
    }
}