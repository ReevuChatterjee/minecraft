package com.voxelgame;

import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL33C.*;

public class CloudManager {
    private int shaderProgram;
    private int vao, vbo;
    private SimplexNoise noise;
    
    private float timeOffset = 0;
    
    private static final int CLOUD_HEIGHT = 160;
    private static final int CLOUD_SCALE = 8; // Each cloud voxel is 8x8x8
    private static final int CLOUD_RADIUS = 16; // Render 16 cloud voxels around player
    
    public CloudManager() {
        noise = new SimplexNoise(12345); // Fixed seed for clouds
        createShader();
        createBuffers();
    }

    private void createShader() {
        String vertSrc =
            "#version 330 core\n" +
            "layout(location=0) in vec3 aPos;\n" +
            "layout(location=1) in float aAlpha;\n" +
            "uniform mat4 projection;\n" +
            "uniform mat4 view;\n" +
            "out float alpha;\n" +
            "void main(){\n" +
            "  alpha = aAlpha;\n" +
            "  gl_Position = projection * view * vec4(aPos, 1.0);\n" +
            "}\n";

        String fragSrc =
            "#version 330 core\n" +
            "in float alpha;\n" +
            "out vec4 FragColor;\n" +
            "void main(){\n" +
            "  FragColor = vec4(1.0, 1.0, 1.0, alpha);\n" +
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
        
        // 32x32 max grid = 1024 chunks. 1 chunk = 1 box = 36 verts = 36 * 4 floats (x,y,z,a)
        int maxVerts = (CLOUD_RADIUS * 2 + 1) * (CLOUD_RADIUS * 2 + 1) * 36;
        glBufferData(GL_ARRAY_BUFFER, maxVerts * 4 * Float.BYTES, GL_DYNAMIC_DRAW);

        glVertexAttribPointer(0, 3, GL_FLOAT, false, 4 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        
        glVertexAttribPointer(1, 1, GL_FLOAT, false, 4 * Float.BYTES, 3 * Float.BYTES);
        glEnableVertexAttribArray(1);

        glBindVertexArray(0);
    }

    public void update(float dt) {
        // Clouds move steadily along X and slightly along Z
        timeOffset += dt * 2.0f; // Speed of clouds
    }

    public void render(Camera camera) {
        float camX = camera.getPosition().x;
        float camZ = camera.getPosition().z;
        
        int centerCX = (int) Math.floor(camX / CLOUD_SCALE);
        int centerCZ = (int) Math.floor(camZ / CLOUD_SCALE);
        
        // Build mesh
        int maxVerts = (CLOUD_RADIUS * 2 + 1) * (CLOUD_RADIUS * 2 + 1) * 36;
        float[] vertices = new float[maxVerts * 4];
        int idx = 0;
        
        for (int cx = -CLOUD_RADIUS; cx <= CLOUD_RADIUS; cx++) {
            for (int cz = -CLOUD_RADIUS; cz <= CLOUD_RADIUS; cz++) {
                int worldCX = centerCX + cx;
                int worldCZ = centerCZ + cz;
                
                // Sample noise (offset to make it move)
                // We sample noise coordinates based on world block position.
                double sampleX = (worldCX * CLOUD_SCALE - timeOffset) * 0.02;
                double sampleZ = (worldCZ * CLOUD_SCALE - timeOffset * 0.3) * 0.02;
                
                double n = noise.noise(sampleX, sampleZ);
                
                if (n > 0.4) { // Cloud threshold
                    // Calculate alpha dropoff at edges of render distance to fade smoothly
                    float dist = (float) Math.sqrt(cx * cx + cz * cz);
                    float alpha = 0.85f; // Base semi-transparency
                    if (dist > CLOUD_RADIUS - 4) {
                        alpha *= 1.0f - ((dist - (CLOUD_RADIUS - 4)) / 4.0f);
                    }
                    if (alpha < 0) alpha = 0;
                    
                    float startX = worldCX * CLOUD_SCALE;
                    float startZ = worldCZ * CLOUD_SCALE;
                    float height = CLOUD_HEIGHT;
                    
                    // Simple box logic (8 corners)
                    float[] lx = {0, CLOUD_SCALE, CLOUD_SCALE, 0, 0, CLOUD_SCALE, CLOUD_SCALE, 0};
                    float[] ly = {0, 0, 0, 0, CLOUD_SCALE/2.0f, CLOUD_SCALE/2.0f, CLOUD_SCALE/2.0f, CLOUD_SCALE/2.0f}; // Height is half of width
                    float[] lz = {0, 0, CLOUD_SCALE, CLOUD_SCALE, 0, 0, CLOUD_SCALE, CLOUD_SCALE};
                    
                    float[][] corners = new float[8][3];
                    for(int i=0; i<8; i++){
                        corners[i][0] = startX + lx[i];
                        corners[i][1] = height + ly[i];
                        corners[i][2] = startZ + lz[i];
                    }
                    
                    int[][] faces = {
                        {0, 1, 2, 3}, {4, 7, 6, 5},
                        {0, 3, 7, 4}, {1, 5, 6, 2},
                        {0, 4, 5, 1}, {3, 2, 6, 7}
                    };
                    
                    for (int[] face : faces) {
                        // Tri 1
                        idx = addVert(vertices, idx, corners[face[0]], alpha);
                        idx = addVert(vertices, idx, corners[face[1]], alpha);
                        idx = addVert(vertices, idx, corners[face[2]], alpha);
                        // Tri 2
                        idx = addVert(vertices, idx, corners[face[0]], alpha);
                        idx = addVert(vertices, idx, corners[face[2]], alpha);
                        idx = addVert(vertices, idx, corners[face[3]], alpha);
                    }
                }
            }
        }
        
        if (idx == 0) return;
        
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_CULL_FACE); // Render both sides of clouds just in case

        glUseProgram(shaderProgram);
        
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
        glDrawArrays(GL_TRIANGLES, 0, idx / 4);
        glBindVertexArray(0);
        
        glEnable(GL_CULL_FACE);
        glDisable(GL_BLEND);
    }
    
    private int addVert(float[] v, int idx, float[] pos, float alpha) {
        v[idx++] = pos[0];
        v[idx++] = pos[1];
        v[idx++] = pos[2];
        v[idx++] = alpha;
        return idx;
    }

    public void cleanup() {
        glDeleteVertexArrays(vao);
        glDeleteBuffers(vbo);
        glDeleteProgram(shaderProgram);
    }
}
