package com.voxelgame;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.util.*;

import static org.lwjgl.opengl.GL33C.*;

/**
 * Manages all entities: spawning, updating AI, rendering, and hit detection.
 */
public class EntityManager {
    private List<Entity> entities = new ArrayList<>();
    private int shaderProgram;
    private int vao, vbo;
    private Random random = new Random(42);
    private SimplexNoise spawnNoise = new SimplexNoise();

    // Track which chunk positions have had animals spawned
    private Set<String> spawnedChunks = new HashSet<>();

    private static final int MAX_ENTITIES = 200;

    public EntityManager() {
        createShader();
        createBuffers();
    }

    private void createShader() {
        String vertSrc =
            "#version 330 core\n" +
            "layout(location=0) in vec3 aPos;\n" +
            "layout(location=1) in vec3 aColor;\n" +
            "layout(location=2) in vec3 aNormal;\n" +
            "uniform mat4 projection;\n" +
            "uniform mat4 view;\n" +
            "out vec3 color;\n" +
            "void main(){\n" +
            "  // Simple directional lighting\n" +
            "  vec3 lightDir = normalize(vec3(0.3, 1.0, 0.5));\n" +
            "  float light = max(dot(aNormal, lightDir), 0.0) * 0.5 + 0.5;\n" +
            "  color = aColor * light;\n" +
            "  gl_Position = projection * view * vec4(aPos, 1.0);\n" +
            "}\n";

        String fragSrc =
            "#version 330 core\n" +
            "in vec3 color;\n" +
            "out vec4 FragColor;\n" +
            "void main(){\n" +
            "  FragColor = vec4(color, 1.0);\n" +
            "}\n";

        int vs = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vs, vertSrc);
        glCompileShader(vs);

        int fs = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fs, fragSrc);
        glCompileShader(fs);

        shaderProgram = glCreateProgram();
        glAttachShader(shaderProgram, vs);
        glAttachShader(shaderProgram, fs);
        glLinkProgram(shaderProgram);

        glDeleteShader(vs);
        glDeleteShader(fs);
    }

    private void createBuffers() {
        vao = glGenVertexArrays();
        vbo = glGenBuffers();

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        // Reserve for up to MAX_ENTITIES entities * 216 verts * 9 floats
        glBufferData(GL_ARRAY_BUFFER, MAX_ENTITIES * 216 * 9 * Float.BYTES, GL_DYNAMIC_DRAW);

        // Position (3 floats)
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 9 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        // Color (3 floats)
        glVertexAttribPointer(1, 3, GL_FLOAT, false, 9 * Float.BYTES, 3 * Float.BYTES);
        glEnableVertexAttribArray(1);
        // Normal (3 floats)
        glVertexAttribPointer(2, 3, GL_FLOAT, false, 9 * Float.BYTES, 6 * Float.BYTES);
        glEnableVertexAttribArray(2);

        glBindVertexArray(0);
    }

    /**
     * Try to spawn animals in a chunk area.
     */
    public void spawnInChunk(int chunkX, int chunkZ, World world) {
        String key = chunkX + "," + chunkZ;
        if (spawnedChunks.contains(key)) return;
        spawnedChunks.add(key);

        if (entities.size() >= MAX_ENTITIES) return;

        // Use noise to decide if this chunk gets animals
        double spawnChance = spawnNoise.noise(chunkX * 7.3, chunkZ * 7.3);
        if (spawnChance < 0.2) return; // ~40% of chunks get animals

        int count = 1 + random.nextInt(3); // 1-3 animals per chunk

        for (int i = 0; i < count && entities.size() < MAX_ENTITIES; i++) {
            int localX = 3 + random.nextInt(10);
            int localZ = 3 + random.nextInt(10);
            int worldX = chunkX * 16 + localX;
            int worldZ = chunkZ * 16 + localZ;

            // Find surface height
            int surfaceY = -1;
            for (int y = 120; y > 50; y--) {
                byte block = world.getBlock(worldX, y, worldZ);
                if (block == BlockType.GRASS.id || block == BlockType.DIRT.id) {
                    surfaceY = y + 1;
                    break;
                }
            }

            if (surfaceY > 0) {
                // Don't spawn in trees
                byte blockAbove = world.getBlock(worldX, surfaceY, worldZ);
                byte blockAbove2 = world.getBlock(worldX, surfaceY + 1, worldZ);
                if (blockAbove == BlockType.AIR.id && blockAbove2 == BlockType.AIR.id) {
                    Entity.Type type = random.nextBoolean() ? Entity.Type.COW : Entity.Type.GOAT;
                    entities.add(new Entity(type, worldX + 0.5f, surfaceY, worldZ + 0.5f));
                }
            }
        }
    }

    /**
     * Update all entities.
     */
    public void update(float dt, World world) {
        Iterator<Entity> it = entities.iterator();
        while (it.hasNext()) {
            Entity e = it.next();
            e.update(dt, world);
            if (e.dead) {
                it.remove();
            }
        }
    }

    /**
     * Check if a ray hits any entity. Returns the hit entity or null.
     */
    public Entity raycastEntities(Vector3f origin, Vector3f direction, float maxDist) {
        Entity closest = null;
        float closestDist = maxDist;

        for (Entity e : entities) {
            if (e.dead) continue;

            float[] aabb = e.getAABB();
            float dist = rayIntersectsAABB(origin, direction, aabb);
            if (dist >= 0 && dist < closestDist) {
                closestDist = dist;
                closest = e;
            }
        }

        return closest;
    }

    /**
     * Ray-AABB intersection test. Returns distance or -1 if no hit.
     */
    private float rayIntersectsAABB(Vector3f origin, Vector3f dir, float[] aabb) {
        float tmin = (aabb[0] - origin.x) / (dir.x != 0 ? dir.x : 0.00001f);
        float tmax = (aabb[3] - origin.x) / (dir.x != 0 ? dir.x : 0.00001f);
        if (tmin > tmax) { float tmp = tmin; tmin = tmax; tmax = tmp; }

        float tymin = (aabb[1] - origin.y) / (dir.y != 0 ? dir.y : 0.00001f);
        float tymax = (aabb[4] - origin.y) / (dir.y != 0 ? dir.y : 0.00001f);
        if (tymin > tymax) { float tmp = tymin; tymin = tymax; tymax = tmp; }

        if ((tmin > tymax) || (tymin > tmax)) return -1;
        if (tymin > tmin) tmin = tymin;
        if (tymax < tmax) tmax = tymax;

        float tzmin = (aabb[2] - origin.z) / (dir.z != 0 ? dir.z : 0.00001f);
        float tzmax = (aabb[5] - origin.z) / (dir.z != 0 ? dir.z : 0.00001f);
        if (tzmin > tzmax) { float tmp = tzmin; tzmin = tzmax; tzmax = tmp; }

        if ((tmin > tzmax) || (tzmin > tmax)) return -1;
        if (tzmin > tmin) tmin = tzmin;

        return tmin >= 0 ? tmin : -1;
    }

    /**
     * Render all entities.
     */
    public void render(Camera camera) {
        if (entities.isEmpty()) return;

        // Build mesh for all entities
        float[] vertices = new float[entities.size() * 216 * 9]; // 216 verts per entity max
        int idx = 0;

        for (Entity e : entities) {
            if (e.dead) continue;

            float px = e.position.x;
            float py = e.position.y;
            float pz = e.position.z;
            float cosY = (float) Math.cos(Math.toRadians(e.yaw));
            float sinY = (float) Math.sin(Math.toRadians(e.yaw));

            // Draw body
            idx = addBox(vertices, idx, px, py, pz,
                e.type.bodyW, e.type.bodyH, e.type.bodyL,
                e.type.bodyColor, cosY, sinY);

            // Draw head (attached to front of body, slightly higher)
            float headOffsetForward = e.type.bodyL * 0.5f + e.type.headL * 0.3f;
            float headX = px + sinY * headOffsetForward;
            float headZ = pz + cosY * headOffsetForward;
            float headY = py + e.type.bodyH * 0.5f;

            idx = addBox(vertices, idx, headX, headY, headZ,
                e.type.headW, e.type.headH, e.type.headL,
                e.type.headColor, cosY, sinY);

            // Draw 4 legs
            float legW = 0.15f, legH = 0.4f, legL = 0.15f;
            float[][] legOffsets = {
                {-e.type.bodyW * 0.35f, -legH, -e.type.bodyL * 0.3f},
                { e.type.bodyW * 0.35f, -legH, -e.type.bodyL * 0.3f},
                {-e.type.bodyW * 0.35f, -legH,  e.type.bodyL * 0.3f},
                { e.type.bodyW * 0.35f, -legH,  e.type.bodyL * 0.3f},
            };
            for (float[] leg : legOffsets) {
                float lx = px + leg[0] * cosY - leg[2] * sinY;
                float lz = pz + leg[0] * sinY + leg[2] * cosY;
                float ly = py + leg[1];
                idx = addBox(vertices, idx, lx, ly, lz, legW, legH, legL,
                    e.type.bodyColor, cosY, sinY);
            }
        }

        if (idx == 0) return;

        glUseProgram(shaderProgram);

        // Set projection and view matrices
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer fb = stack.mallocFloat(16);
            camera.getProjectionMatrix().get(fb);
            glUniformMatrix4fv(glGetUniformLocation(shaderProgram, "projection"), false, fb);

            camera.getViewMatrix().get(fb);
            glUniformMatrix4fv(glGetUniformLocation(shaderProgram, "view"), false, fb);
        }

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0, java.util.Arrays.copyOf(vertices, idx));
        glDrawArrays(GL_TRIANGLES, 0, idx / 9);
        glBindVertexArray(0);
    }

    /**
     * Add a rotated box to the vertex array.
     */
    private int addBox(float[] v, int idx, float cx, float cy, float cz,
                       float w, float h, float l, float[] color,
                       float cosY, float sinY) {
        float hw = w * 0.5f;
        float hl = l * 0.5f;

        // 8 corners of the box (local space, then rotated)
        float[][] corners = new float[8][3];
        float[] lx = {-hw, hw, hw, -hw, -hw, hw, hw, -hw};
        float[] ly = {0, 0, 0, 0, h, h, h, h};
        float[] lz = {-hl, -hl, hl, hl, -hl, -hl, hl, hl};

        for (int i = 0; i < 8; i++) {
            corners[i][0] = cx + lx[i] * cosY - lz[i] * sinY;
            corners[i][1] = cy + ly[i];
            corners[i][2] = cz + lx[i] * sinY + lz[i] * cosY;
        }

        // 6 faces, each with 2 triangles (6 vertices)
        int[][] faces = {
            {0, 1, 2, 3}, // bottom
            {4, 7, 6, 5}, // top
            {0, 3, 7, 4}, // left
            {1, 5, 6, 2}, // right
            {0, 4, 5, 1}, // front
            {3, 2, 6, 7}, // back
        };
        float[][] normals = {
            {0, -1, 0}, {0, 1, 0},
            {-cosY, 0, -sinY}, {cosY, 0, sinY},
            {-sinY, 0, cosY}, {sinY, 0, -cosY}
        };

        for (int f = 0; f < 6; f++) {
            int[] face = faces[f];
            float[] n = normals[f];
            float[] c = color;

            // Triangle 1: v0, v1, v2
            idx = addVertex(v, idx, corners[face[0]], c, n);
            idx = addVertex(v, idx, corners[face[1]], c, n);
            idx = addVertex(v, idx, corners[face[2]], c, n);
            // Triangle 2: v0, v2, v3
            idx = addVertex(v, idx, corners[face[0]], c, n);
            idx = addVertex(v, idx, corners[face[2]], c, n);
            idx = addVertex(v, idx, corners[face[3]], c, n);
        }

        return idx;
    }

    private int addVertex(float[] v, int idx, float[] pos, float[] color, float[] normal) {
        v[idx++] = pos[0]; v[idx++] = pos[1]; v[idx++] = pos[2];
        v[idx++] = color[0]; v[idx++] = color[1]; v[idx++] = color[2];
        v[idx++] = normal[0]; v[idx++] = normal[1]; v[idx++] = normal[2];
        return idx;
    }

    public int getEntityCount() {
        return entities.size();
    }

    public void cleanup() {
        glDeleteVertexArrays(vao);
        glDeleteBuffers(vbo);
        glDeleteProgram(shaderProgram);
    }
}
