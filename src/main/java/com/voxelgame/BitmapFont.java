package com.voxelgame;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import org.lwjgl.system.MemoryStack;

import static org.lwjgl.opengl.GL33C.*;

/**
 * Simple procedural bitmap font for rendering debug text.
 * Generates a texture atlas of ASCII characters 32-126.
 */
public class BitmapFont {
    private int textureId;
    private int vao, vbo;
    private int shaderProgram;

    private static final int CHAR_WIDTH = 6;
    private static final int CHAR_HEIGHT = 9;
    private static final int ATLAS_COLS = 16;
    private static final int ATLAS_ROWS = 6; // chars 32..127
    private static final int TEX_WIDTH = ATLAS_COLS * CHAR_WIDTH;
    private static final int TEX_HEIGHT = ATLAS_ROWS * CHAR_HEIGHT;

    // Simple 6x9 bitmap font data for printable ASCII chars (space through ~)
    // Each character is 9 rows of 6-bit bitmaps (MSB = leftmost pixel)
    private static final long[][] FONT_DATA = createFontData();

    public BitmapFont() {
        createTexture();
        createShader();
        createBuffers();
    }

    private void createTexture() {
        ByteBuffer pixels = ByteBuffer.allocateDirect(TEX_WIDTH * TEX_HEIGHT * 4);

        for (int charIdx = 0; charIdx < ATLAS_COLS * ATLAS_ROWS; charIdx++) {
            int ascii = charIdx + 32;
            int col = charIdx % ATLAS_COLS;
            int row = charIdx / ATLAS_COLS;

            for (int cy = 0; cy < CHAR_HEIGHT; cy++) {
                long rowBits = 0;
                if (ascii >= 32 && ascii <= 126 && charIdx < FONT_DATA.length) {
                    rowBits = FONT_DATA[charIdx][cy];
                }
                for (int cx = 0; cx < CHAR_WIDTH; cx++) {
                    boolean on = ((rowBits >> (CHAR_WIDTH - 1 - cx)) & 1) == 1;
                    int px = col * CHAR_WIDTH + cx;
                    int py = row * CHAR_HEIGHT + cy;
                    int idx = (py * TEX_WIDTH + px) * 4;

                    pixels.put(idx, (byte) 255);
                    pixels.put(idx + 1, (byte) 255);
                    pixels.put(idx + 2, (byte) 255);
                    pixels.put(idx + 3, on ? (byte) 255 : (byte) 0);
                }
            }
        }

        pixels.flip();

        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, TEX_WIDTH, TEX_HEIGHT, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    private void createShader() {
        String vertSrc =
            "#version 330 core\n" +
            "layout(location=0) in vec2 aPos;\n" +
            "layout(location=1) in vec2 aUV;\n" +
            "layout(location=2) in vec3 aColor;\n" +
            "uniform mat4 projection;\n" +
            "out vec2 uv;\n" +
            "out vec3 color;\n" +
            "void main(){\n" +
            "  gl_Position = projection * vec4(aPos, 0, 1);\n" +
            "  uv = aUV;\n" +
            "  color = aColor;\n" +
            "}\n";

        String fragSrc =
            "#version 330 core\n" +
            "in vec2 uv;\n" +
            "in vec3 color;\n" +
            "out vec4 FragColor;\n" +
            "uniform sampler2D fontTex;\n" +
            "void main(){\n" +
            "  vec4 t = texture(fontTex, uv);\n" +
            "  if(t.a < 0.1) discard;\n" +
            "  FragColor = vec4(color * t.rgb, t.a);\n" +
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
        // Reserve space for up to 256 characters (256 * 6 vertices * 7 floats)
        glBufferData(GL_ARRAY_BUFFER, 256 * 6 * 7 * Float.BYTES, GL_DYNAMIC_DRAW);

        // Position (2 floats)
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 7 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        // UV (2 floats)
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 7 * Float.BYTES, 2 * Float.BYTES);
        glEnableVertexAttribArray(1);
        // Color (3 floats)
        glVertexAttribPointer(2, 3, GL_FLOAT, false, 7 * Float.BYTES, 4 * Float.BYTES);
        glEnableVertexAttribArray(2);

        glBindVertexArray(0);
    }

    /**
     * Draw text at screen coordinates.
     * @param text The string to render
     * @param x Left position in pixels
     * @param y Top position in pixels
     * @param scale Scale factor (2.0 = double size)
     * @param r Red (0-1)
     * @param g Green (0-1)
     * @param b Blue (0-1)
     * @param screenWidth Window width for projection
     * @param screenHeight Window height for projection
     */
    public void drawText(String text, float x, float y, float scale,
                         float r, float g, float b,
                         int screenWidth, int screenHeight) {
        if (text == null || text.isEmpty()) return;

        int len = Math.min(text.length(), 256);
        float[] vertices = new float[len * 6 * 7];
        int idx = 0;

        float charW = CHAR_WIDTH * scale;
        float charH = CHAR_HEIGHT * scale;
        float uvW = (float) CHAR_WIDTH / TEX_WIDTH;
        float uvH = (float) CHAR_HEIGHT / TEX_HEIGHT;

        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            int charIdx = c - 32;
            if (charIdx < 0 || charIdx >= ATLAS_COLS * ATLAS_ROWS) charIdx = 0;

            int col = charIdx % ATLAS_COLS;
            int row = charIdx / ATLAS_COLS;
            float u0 = col * uvW;
            float v0 = row * uvH;
            float u1 = u0 + uvW;
            float v1 = v0 + uvH;

            float px = x + i * charW;
            float py = y;

            // Triangle 1
            vertices[idx++] = px;        vertices[idx++] = py;        vertices[idx++] = u0; vertices[idx++] = v0; vertices[idx++] = r; vertices[idx++] = g; vertices[idx++] = b;
            vertices[idx++] = px + charW; vertices[idx++] = py;       vertices[idx++] = u1; vertices[idx++] = v0; vertices[idx++] = r; vertices[idx++] = g; vertices[idx++] = b;
            vertices[idx++] = px + charW; vertices[idx++] = py + charH; vertices[idx++] = u1; vertices[idx++] = v1; vertices[idx++] = r; vertices[idx++] = g; vertices[idx++] = b;
            // Triangle 2
            vertices[idx++] = px;        vertices[idx++] = py;        vertices[idx++] = u0; vertices[idx++] = v0; vertices[idx++] = r; vertices[idx++] = g; vertices[idx++] = b;
            vertices[idx++] = px + charW; vertices[idx++] = py + charH; vertices[idx++] = u1; vertices[idx++] = v1; vertices[idx++] = r; vertices[idx++] = g; vertices[idx++] = b;
            vertices[idx++] = px;        vertices[idx++] = py + charH; vertices[idx++] = u0; vertices[idx++] = v1; vertices[idx++] = r; vertices[idx++] = g; vertices[idx++] = b;
        }

        glUseProgram(shaderProgram);

        // Orthographic projection
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer fb = stack.mallocFloat(16);
            new org.joml.Matrix4f().ortho(0, screenWidth, screenHeight, 0, -1, 1).get(fb);
            glUniformMatrix4fv(glGetUniformLocation(shaderProgram, "projection"), false, fb);
        }

        glBindTexture(GL_TEXTURE_2D, textureId);
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0, vertices);
        glDrawArrays(GL_TRIANGLES, 0, len * 6);
        glBindVertexArray(0);
    }

    public void cleanup() {
        glDeleteTextures(textureId);
        glDeleteVertexArrays(vao);
        glDeleteBuffers(vbo);
        glDeleteProgram(shaderProgram);
    }

    /**
     * Creates a minimal bitmap font for ASCII 32-126.
     * Each character is represented as 9 rows of 6-bit patterns.
     */
    private static long[][] createFontData() {
        long[][] data = new long[96][]; // 95 printable chars + 1 extra

        // Initialize all to blank
        for (int i = 0; i < data.length; i++) {
            data[i] = new long[]{0, 0, 0, 0, 0, 0, 0, 0, 0};
        }

        // Space (32) - all zeros, already done

        // ! (33)
        data[1] = new long[]{0b001000, 0b001000, 0b001000, 0b001000, 0b001000, 0b000000, 0b001000, 0b000000, 0b000000};
        // " (34)
        data[2] = new long[]{0b010100, 0b010100, 0b000000, 0b000000, 0b000000, 0b000000, 0b000000, 0b000000, 0b000000};
        // # (35)
        data[3] = new long[]{0b010100, 0b111110, 0b010100, 0b010100, 0b111110, 0b010100, 0b000000, 0b000000, 0b000000};
        // 0 (48)
        data[16] = new long[]{0b011100, 0b100010, 0b110010, 0b101010, 0b100110, 0b100010, 0b011100, 0b000000, 0b000000};
        // 1 (49)
        data[17] = new long[]{0b001000, 0b011000, 0b001000, 0b001000, 0b001000, 0b001000, 0b011100, 0b000000, 0b000000};
        // 2 (50)
        data[18] = new long[]{0b011100, 0b100010, 0b000010, 0b001100, 0b010000, 0b100000, 0b111110, 0b000000, 0b000000};
        // 3 (51)
        data[19] = new long[]{0b011100, 0b100010, 0b000010, 0b001100, 0b000010, 0b100010, 0b011100, 0b000000, 0b000000};
        // 4 (52)
        data[20] = new long[]{0b000100, 0b001100, 0b010100, 0b100100, 0b111110, 0b000100, 0b000100, 0b000000, 0b000000};
        // 5 (53)
        data[21] = new long[]{0b111110, 0b100000, 0b111100, 0b000010, 0b000010, 0b100010, 0b011100, 0b000000, 0b000000};
        // 6 (54)
        data[22] = new long[]{0b011100, 0b100000, 0b100000, 0b111100, 0b100010, 0b100010, 0b011100, 0b000000, 0b000000};
        // 7 (55)
        data[23] = new long[]{0b111110, 0b000010, 0b000100, 0b001000, 0b010000, 0b010000, 0b010000, 0b000000, 0b000000};
        // 8 (56)
        data[24] = new long[]{0b011100, 0b100010, 0b100010, 0b011100, 0b100010, 0b100010, 0b011100, 0b000000, 0b000000};
        // 9 (57)
        data[25] = new long[]{0b011100, 0b100010, 0b100010, 0b011110, 0b000010, 0b000010, 0b011100, 0b000000, 0b000000};
        // : (58)
        data[26] = new long[]{0b000000, 0b001000, 0b000000, 0b000000, 0b001000, 0b000000, 0b000000, 0b000000, 0b000000};
        // . (46)
        data[14] = new long[]{0b000000, 0b000000, 0b000000, 0b000000, 0b000000, 0b000000, 0b001000, 0b000000, 0b000000};
        // , (44)
        data[12] = new long[]{0b000000, 0b000000, 0b000000, 0b000000, 0b000000, 0b001000, 0b001000, 0b010000, 0b000000};
        // - (45)
        data[13] = new long[]{0b000000, 0b000000, 0b000000, 0b111110, 0b000000, 0b000000, 0b000000, 0b000000, 0b000000};
        // / (47)
        data[15] = new long[]{0b000010, 0b000100, 0b000100, 0b001000, 0b010000, 0b010000, 0b100000, 0b000000, 0b000000};
        // + (43)
        data[11] = new long[]{0b000000, 0b001000, 0b001000, 0b111110, 0b001000, 0b001000, 0b000000, 0b000000, 0b000000};

        // A-Z (65-90 → indices 33-58)
        data[33] = new long[]{0b011100, 0b100010, 0b100010, 0b111110, 0b100010, 0b100010, 0b100010, 0b000000, 0b000000}; // A
        data[34] = new long[]{0b111100, 0b100010, 0b100010, 0b111100, 0b100010, 0b100010, 0b111100, 0b000000, 0b000000}; // B
        data[35] = new long[]{0b011100, 0b100010, 0b100000, 0b100000, 0b100000, 0b100010, 0b011100, 0b000000, 0b000000}; // C
        data[36] = new long[]{0b111100, 0b100010, 0b100010, 0b100010, 0b100010, 0b100010, 0b111100, 0b000000, 0b000000}; // D
        data[37] = new long[]{0b111110, 0b100000, 0b100000, 0b111100, 0b100000, 0b100000, 0b111110, 0b000000, 0b000000}; // E
        data[38] = new long[]{0b111110, 0b100000, 0b100000, 0b111100, 0b100000, 0b100000, 0b100000, 0b000000, 0b000000}; // F
        data[39] = new long[]{0b011100, 0b100010, 0b100000, 0b100110, 0b100010, 0b100010, 0b011100, 0b000000, 0b000000}; // G
        data[40] = new long[]{0b100010, 0b100010, 0b100010, 0b111110, 0b100010, 0b100010, 0b100010, 0b000000, 0b000000}; // H
        data[41] = new long[]{0b011100, 0b001000, 0b001000, 0b001000, 0b001000, 0b001000, 0b011100, 0b000000, 0b000000}; // I
        data[42] = new long[]{0b000010, 0b000010, 0b000010, 0b000010, 0b000010, 0b100010, 0b011100, 0b000000, 0b000000}; // J
        data[43] = new long[]{0b100010, 0b100100, 0b101000, 0b110000, 0b101000, 0b100100, 0b100010, 0b000000, 0b000000}; // K
        data[44] = new long[]{0b100000, 0b100000, 0b100000, 0b100000, 0b100000, 0b100000, 0b111110, 0b000000, 0b000000}; // L
        data[45] = new long[]{0b100010, 0b110110, 0b101010, 0b101010, 0b100010, 0b100010, 0b100010, 0b000000, 0b000000}; // M
        data[46] = new long[]{0b100010, 0b110010, 0b101010, 0b100110, 0b100010, 0b100010, 0b100010, 0b000000, 0b000000}; // N
        data[47] = new long[]{0b011100, 0b100010, 0b100010, 0b100010, 0b100010, 0b100010, 0b011100, 0b000000, 0b000000}; // O
        data[48] = new long[]{0b111100, 0b100010, 0b100010, 0b111100, 0b100000, 0b100000, 0b100000, 0b000000, 0b000000}; // P
        data[49] = new long[]{0b011100, 0b100010, 0b100010, 0b100010, 0b101010, 0b100100, 0b011010, 0b000000, 0b000000}; // Q
        data[50] = new long[]{0b111100, 0b100010, 0b100010, 0b111100, 0b101000, 0b100100, 0b100010, 0b000000, 0b000000}; // R
        data[51] = new long[]{0b011100, 0b100010, 0b100000, 0b011100, 0b000010, 0b100010, 0b011100, 0b000000, 0b000000}; // S
        data[52] = new long[]{0b111110, 0b001000, 0b001000, 0b001000, 0b001000, 0b001000, 0b001000, 0b000000, 0b000000}; // T
        data[53] = new long[]{0b100010, 0b100010, 0b100010, 0b100010, 0b100010, 0b100010, 0b011100, 0b000000, 0b000000}; // U
        data[54] = new long[]{0b100010, 0b100010, 0b100010, 0b100010, 0b010100, 0b010100, 0b001000, 0b000000, 0b000000}; // V
        data[55] = new long[]{0b100010, 0b100010, 0b100010, 0b101010, 0b101010, 0b110110, 0b100010, 0b000000, 0b000000}; // W
        data[56] = new long[]{0b100010, 0b100010, 0b010100, 0b001000, 0b010100, 0b100010, 0b100010, 0b000000, 0b000000}; // X
        data[57] = new long[]{0b100010, 0b100010, 0b010100, 0b001000, 0b001000, 0b001000, 0b001000, 0b000000, 0b000000}; // Y
        data[58] = new long[]{0b111110, 0b000010, 0b000100, 0b001000, 0b010000, 0b100000, 0b111110, 0b000000, 0b000000}; // Z

        // a-z (97-122 → indices 65-90): Use uppercase glyphs for simplicity
        for (int i = 0; i < 26; i++) {
            data[65 + i] = data[33 + i].clone();
        }

        // ( (40 → index 8)
        data[8] = new long[]{0b000100, 0b001000, 0b010000, 0b010000, 0b010000, 0b001000, 0b000100, 0b000000, 0b000000};
        // ) (41 → index 9)
        data[9] = new long[]{0b010000, 0b001000, 0b000100, 0b000100, 0b000100, 0b001000, 0b010000, 0b000000, 0b000000};
        // [ (91 → index 59)
        data[59] = new long[]{0b011100, 0b010000, 0b010000, 0b010000, 0b010000, 0b010000, 0b011100, 0b000000, 0b000000};
        // ] (93 → index 61)
        data[61] = new long[]{0b011100, 0b000100, 0b000100, 0b000100, 0b000100, 0b000100, 0b011100, 0b000000, 0b000000};
        // = (61 → index 29)
        data[29] = new long[]{0b000000, 0b000000, 0b111110, 0b000000, 0b111110, 0b000000, 0b000000, 0b000000, 0b000000};
        // x for multiply (already handled in X above)

        return data;
    }
}
