package com.voxelgame;

import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL33C.*;

/**
 * OpenGL-based HUD for crosshair, hotbar, and debug info.
 * Renders using orthographic projection over the 3D scene.
 */
public class HUD {
    private int shaderProgram;
    private int vao, vbo;
    private BitmapFont font;

    // FPS tracking
    private int frameCount = 0;
    private float fpsTimer = 0f;
    private int currentFps = 0;

    public HUD() {
        createShader();
        createBuffers();
        font = new BitmapFont();
    }

    private void createShader() {
        String vertSrc =
            "#version 330 core\n" +
            "layout(location=0) in vec2 aPos;\n" +
            "layout(location=1) in vec3 aColor;\n" +
            "uniform mat4 projection;\n" +
            "out vec3 color;\n" +
            "void main(){\n" +
            "  gl_Position = projection * vec4(aPos, 0, 1);\n" +
            "  color = aColor;\n" +
            "}\n";

        String fragSrc =
            "#version 330 core\n" +
            "in vec3 color;\n" +
            "out vec4 FragColor;\n" +
            "uniform float alpha;\n" +
            "void main(){\n" +
            "  FragColor = vec4(color, alpha);\n" +
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
        // Reserve for dynamic data
        glBufferData(GL_ARRAY_BUFFER, 4096 * Float.BYTES, GL_DYNAMIC_DRAW);

        // Position (2 floats)
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 5 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        // Color (3 floats)
        glVertexAttribPointer(1, 3, GL_FLOAT, false, 5 * Float.BYTES, 2 * Float.BYTES);
        glEnableVertexAttribArray(1);

        glBindVertexArray(0);
    }

    /**
     * Render the entire HUD overlay.
     */
    public void render(int width, int height, Camera camera, Inventory inventory, float deltaTime) {
        // Update FPS
        frameCount++;
        fpsTimer += deltaTime;
        if (fpsTimer >= 1.0f) {
            currentFps = frameCount;
            frameCount = 0;
            fpsTimer -= 1.0f;
        }

        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);  // Ortho projection flips Y → reverses winding
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        glUseProgram(shaderProgram);
        setProjection(width, height);

        // Draw crosshair
        drawCrosshair(width, height);

        // Draw hotbar
        drawHotbar(width, height, inventory);

        // Draw debug info
        drawDebugInfo(width, height, camera);

        glDisable(GL_BLEND);
        glEnable(GL_CULL_FACE);
        glEnable(GL_DEPTH_TEST);
    }

    private void setProjection(int width, int height) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer fb = stack.mallocFloat(16);
            new Matrix4f().ortho(0, width, height, 0, -1, 1).get(fb);
            glUniformMatrix4fv(glGetUniformLocation(shaderProgram, "projection"), false, fb);
        }
    }

    private void drawCrosshair(int width, int height) {
        float cx = width / 2f;
        float cy = height / 2f;
        float size = 12f;
        float thickness = 2f;

        float[] vertices = new float[]{
            // Horizontal bar
            cx - size, cy - thickness / 2, 1, 1, 1,
            cx + size, cy - thickness / 2, 1, 1, 1,
            cx + size, cy + thickness / 2, 1, 1, 1,
            cx - size, cy - thickness / 2, 1, 1, 1,
            cx + size, cy + thickness / 2, 1, 1, 1,
            cx - size, cy + thickness / 2, 1, 1, 1,
            // Vertical bar
            cx - thickness / 2, cy - size, 1, 1, 1,
            cx + thickness / 2, cy - size, 1, 1, 1,
            cx + thickness / 2, cy + size, 1, 1, 1,
            cx - thickness / 2, cy - size, 1, 1, 1,
            cx + thickness / 2, cy + size, 1, 1, 1,
            cx - thickness / 2, cy + size, 1, 1, 1,
        };

        glUniform1f(glGetUniformLocation(shaderProgram, "alpha"), 0.85f);
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0, vertices);
        glDrawArrays(GL_TRIANGLES, 0, 12);
        glBindVertexArray(0);
    }

    private void drawHotbar(int width, int height, Inventory inventory) {
        float slotSize = 48f;
        float padding = 4f;
        float totalWidth = Inventory.HOTBAR_SIZE * (slotSize + padding) - padding;
        float startX = (width - totalWidth) / 2f;
        float startY = height - slotSize - 16f;

        float[] vertices = new float[Inventory.HOTBAR_SIZE * 2 * 6 * 5]; // slots + block indicators, 2 quads each
        int idx = 0;

        for (int i = 0; i < Inventory.HOTBAR_SIZE; i++) {
            float x = startX + i * (slotSize + padding);
            float y = startY;

            // Slot background
            float bgR, bgG, bgB;
            if (i == inventory.getSelectedSlot()) {
                bgR = 0.8f; bgG = 0.8f; bgB = 0.8f; // Selected = bright
            } else {
                bgR = 0.2f; bgG = 0.2f; bgB = 0.2f; // Normal = dark
            }

            // Background quad
            idx = addQuad(vertices, idx, x, y, slotSize, slotSize, bgR, bgG, bgB);

            // Block color indicator (inner rectangle)
            byte blockType = inventory.getSlotType(i);
            int count = inventory.getSlotCount(i);
            if (count > 0 && blockType != BlockType.AIR.id) {
                float[] blockColor = BlockType.getColor(blockType);
                float innerPad = 6f;
                float innerSize = slotSize - innerPad * 2;
                idx = addQuad(vertices, idx, x + innerPad, y + innerPad,
                              innerSize, innerSize,
                              blockColor[0], blockColor[1], blockColor[2]);
            }
        }

        glUniform1f(glGetUniformLocation(shaderProgram, "alpha"), 0.75f);
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0, java.util.Arrays.copyOf(vertices, idx));
        glDrawArrays(GL_TRIANGLES, 0, idx / 5);
        glBindVertexArray(0);

        // Draw slot counts using bitmap font
        for (int i = 0; i < Inventory.HOTBAR_SIZE; i++) {
            int count = inventory.getSlotCount(i);
            if (count > 0) {
                float x = startX + i * (slotSize + padding) + 2;
                float y2 = startY + slotSize - 14;
                font.drawText(String.valueOf(count), x, y2, 1.5f,
                              1f, 1f, 1f, width, height);
            }
        }

        // Draw slot number labels
        for (int i = 0; i < Inventory.HOTBAR_SIZE; i++) {
            float x = startX + i * (slotSize + padding) + slotSize - 10;
            float y2 = startY + 2;
            float labelR = (i == inventory.getSelectedSlot()) ? 0f : 0.7f;
            float labelG = (i == inventory.getSelectedSlot()) ? 0f : 0.7f;
            float labelB = (i == inventory.getSelectedSlot()) ? 0f : 0.7f;
            font.drawText(String.valueOf(i + 1), x, y2, 1.0f,
                          labelR, labelG, labelB, width, height);
        }
    }

    private void drawDebugInfo(int width, int height, Camera camera) {
        float scale = 2.0f;
        float x = 8;
        float y = 8;
        float lineH = 20;

        // FPS
        font.drawText("FPS: " + currentFps, x, y, scale, 1f, 1f, 0f, width, height);
        y += lineH;

        // Position
        String posStr = String.format("XYZ: %.1f / %.1f / %.1f",
            camera.getPosition().x, camera.getPosition().y, camera.getPosition().z);
        font.drawText(posStr, x, y, scale, 1f, 1f, 1f, width, height);
        y += lineH;

        // Direction
        String dirStr = String.format("YAW: %.0f PITCH: %.0f", camera.getYaw(), camera.getPitch());
        font.drawText(dirStr, x, y, scale, 0.8f, 0.8f, 0.8f, width, height);
        y += lineH;

        // Flight mode
        String mode = camera.isFlying() ? "FLYING" : "SURVIVAL";
        font.drawText("MODE: " + mode, x, y, scale, 0.5f, 1f, 0.5f, width, height);
    }

    private int addQuad(float[] vertices, int idx,
                        float x, float y, float w, float h,
                        float r, float g, float b) {
        // Triangle 1
        vertices[idx++] = x;     vertices[idx++] = y;     vertices[idx++] = r; vertices[idx++] = g; vertices[idx++] = b;
        vertices[idx++] = x + w; vertices[idx++] = y;     vertices[idx++] = r; vertices[idx++] = g; vertices[idx++] = b;
        vertices[idx++] = x + w; vertices[idx++] = y + h; vertices[idx++] = r; vertices[idx++] = g; vertices[idx++] = b;
        // Triangle 2
        vertices[idx++] = x;     vertices[idx++] = y;     vertices[idx++] = r; vertices[idx++] = g; vertices[idx++] = b;
        vertices[idx++] = x + w; vertices[idx++] = y + h; vertices[idx++] = r; vertices[idx++] = g; vertices[idx++] = b;
        vertices[idx++] = x;     vertices[idx++] = y + h; vertices[idx++] = r; vertices[idx++] = g; vertices[idx++] = b;
        return idx;
    }

    public void cleanup() {
        font.cleanup();
        glDeleteVertexArrays(vao);
        glDeleteBuffers(vbo);
        glDeleteProgram(shaderProgram);
    }
}
