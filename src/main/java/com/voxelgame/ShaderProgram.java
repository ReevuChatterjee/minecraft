package com.voxelgame;

import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL33C.*;

public class ShaderProgram {
    private int programId;
    private int vertexShaderId;
    private int fragmentShaderId;
    
    public ShaderProgram() {
        programId = glCreateProgram();
        if (programId == 0) {
            throw new RuntimeException("Could not create shader program");
        }
        
        createVertexShader();
        createFragmentShader();
        link();
    }
    
    private void createVertexShader() {
        String vertexShaderCode = 
            "#version 330 core\n" +
            "layout (location = 0) in vec3 aPos;\n" +
            "layout (location = 1) in vec2 aTexCoord;\n" +
            "layout (location = 2) in vec3 aBrightness;\n" +
            "\n" +
            "uniform mat4 projection;\n" +
            "uniform mat4 view;\n" +
            "\n" +
            "out vec2 TexCoord;\n" +
            "out vec3 Brightness;\n" +
            "\n" +
            "void main() {\n" +
            "    gl_Position = projection * view * vec4(aPos, 1.0);\n" +
            "    TexCoord = aTexCoord;\n" +
            "    Brightness = aBrightness;\n" +
            "}\n";
        
        vertexShaderId = glCreateShader(GL_VERTEX_SHADER);
        if (vertexShaderId == 0) {
            throw new RuntimeException("Error creating vertex shader");
        }
        
        glShaderSource(vertexShaderId, vertexShaderCode);
        glCompileShader(vertexShaderId);
        
        if (glGetShaderi(vertexShaderId, GL_COMPILE_STATUS) == 0) {
            throw new RuntimeException("Error compiling vertex shader: " + 
                glGetShaderInfoLog(vertexShaderId, 1024));
        }
        
        glAttachShader(programId, vertexShaderId);
    }
    
    private void createFragmentShader() {
        String fragmentShaderCode =
            "#version 330 core\n" +
            "in vec2 TexCoord;\n" +
            "in vec3 Brightness;\n" +
            "out vec4 FragColor;\n" +
            "\n" +
            "uniform sampler2D textureSampler;\n" +
            "\n" +
            "void main() {\n" +
            "    vec4 texColor = texture(textureSampler, TexCoord);\n" +
            "    FragColor = vec4(texColor.rgb * Brightness, texColor.a);\n" +
            "}\n";
        
        fragmentShaderId = glCreateShader(GL_FRAGMENT_SHADER);
        if (fragmentShaderId == 0) {
            throw new RuntimeException("Error creating fragment shader");
        }
        
        glShaderSource(fragmentShaderId, fragmentShaderCode);
        glCompileShader(fragmentShaderId);
        
        if (glGetShaderi(fragmentShaderId, GL_COMPILE_STATUS) == 0) {
            throw new RuntimeException("Error compiling fragment shader: " + 
                glGetShaderInfoLog(fragmentShaderId, 1024));
        }
        
        glAttachShader(programId, fragmentShaderId);
    }
    
    private void link() {
        glLinkProgram(programId);
        if (glGetProgrami(programId, GL_LINK_STATUS) == 0) {
            throw new RuntimeException("Error linking shader program: " + 
                glGetProgramInfoLog(programId, 1024));
        }
        
        if (vertexShaderId != 0) {
            glDetachShader(programId, vertexShaderId);
        }
        if (fragmentShaderId != 0) {
            glDetachShader(programId, fragmentShaderId);
        }
        
        glValidateProgram(programId);
        if (glGetProgrami(programId, GL_VALIDATE_STATUS) == 0) {
            System.err.println("Warning validating shader code: " + 
                glGetProgramInfoLog(programId, 1024));
        }
    }
    
    public void use() {
        glUseProgram(programId);
    }
    
    public void stop() {
        glUseProgram(0);
    }
    
    public void setMatrix4f(String name, Matrix4f matrix) {
        int location = glGetUniformLocation(programId, name);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer fb = stack.mallocFloat(16);
            matrix.get(fb);
            glUniformMatrix4fv(location, false, fb);
        }
    }
    
    public void cleanup() {
        stop();
        if (programId != 0) {
            glDeleteProgram(programId);
        }
    }
}