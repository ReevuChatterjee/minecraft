package com.voxelgame;

import org.lwjgl.glfw.*;
import org.joml.Vector3i;
import static org.lwjgl.glfw.GLFW.*;

public class InputHandler {
    private long window;
    private Camera camera;
    private World world;
    
    private double lastX = 400;
    private double lastY = 300;
    private boolean firstMouse = true;
    private float sensitivity = 0.1f;
    
    private boolean[] keys = new boolean[GLFW_KEY_LAST];
    
    private byte selectedBlockType = BlockType.STONE.id;
    
    public InputHandler(long window, Camera camera, World world) {
        this.window = window;
        this.camera = camera;
        this.world = world;
        
        setupCallbacks();
        
        // Capture mouse
        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
    }
    
    private void setupCallbacks() {
        // Keyboard callback
        glfwSetKeyCallback(window, (window, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS) {
                glfwSetWindowShouldClose(window, true);
            }
            
            // Toggle collision
            if (key == GLFW_KEY_C && action == GLFW_PRESS) {
                camera.toggleCollision();
            }
            
            // Hotbar selection (1-5 keys for different blocks)
            if (action == GLFW_PRESS) {
                switch (key) {
                    case GLFW_KEY_1:
                        selectedBlockType = BlockType.STONE.id;
                        System.out.println("Selected: STONE");
                        break;
                    case GLFW_KEY_2:
                        selectedBlockType = BlockType.DIRT.id;
                        System.out.println("Selected: DIRT");
                        break;
                    case GLFW_KEY_3:
                        selectedBlockType = BlockType.GRASS.id;
                        System.out.println("Selected: GRASS");
                        break;
                }
            }
            
            if (key >= 0 && key < keys.length) {
                if (action == GLFW_PRESS) {
                    keys[key] = true;
                } else if (action == GLFW_RELEASE) {
                    keys[key] = false;
                }
            }
        });
        
        // Mouse movement callback
        glfwSetCursorPosCallback(window, (window, xpos, ypos) -> {
            if (firstMouse) {
                lastX = xpos;
                lastY = ypos;
                firstMouse = false;
            }
            
            double xOffset = xpos - lastX;
            double yOffset = lastY - ypos; // Reversed: y ranges from bottom to top
            
            lastX = xpos;
            lastY = ypos;
            
            xOffset *= sensitivity;
            yOffset *= sensitivity;
            
            camera.rotate((float) xOffset, (float) yOffset);
        });
        
        // Mouse button callback
        glfwSetMouseButtonCallback(window, (window, button, action, mods) -> {
            if (button == GLFW_MOUSE_BUTTON_LEFT && action == GLFW_PRESS) {
                breakBlock();
            }
            
            if (button == GLFW_MOUSE_BUTTON_RIGHT && action == GLFW_PRESS) {
                placeBlock();
            }
        });
    }
    
    private void breakBlock() {
        Raycast.RaycastResult result = Raycast.raycast(
            camera.getPosition(),
            camera.getFront(),
            world
        );
        
        if (result.hit) {
            if (world.breakBlock(result.blockPosition)) {
                System.out.println("Broke block at: " + result.blockPosition);
            }
        }
    }
    
    private void placeBlock() {
        Raycast.RaycastResult result = Raycast.raycast(
            camera.getPosition(),
            camera.getFront(),
            world
        );
        
        if (result.hit) {
            // Place block at the position before the hit block
            Vector3i placePos = result.previousPosition;
            
            // Don't place block where player is standing
            if (!isPlayerAtPosition(placePos)) {
                if (world.placeBlock(placePos, selectedBlockType)) {
                    System.out.println("Placed block at: " + placePos);
                }
            }
        }
    }
    
    private boolean isPlayerAtPosition(Vector3i blockPos) {
        org.joml.Vector3f camPos = camera.getPosition();
        
        // Check if block would intersect with player
        // Player is roughly 1.8 blocks tall
        int playerMinY = (int) Math.floor(camPos.y - 1.8f);
        int playerMaxY = (int) Math.floor(camPos.y);
        int playerX = (int) Math.floor(camPos.x);
        int playerZ = (int) Math.floor(camPos.z);
        
        return blockPos.x == playerX && 
               blockPos.z == playerZ && 
               blockPos.y >= playerMinY && 
               blockPos.y <= playerMaxY;
    }
    
    public void update(float deltaTime) {
        boolean sprinting = keys[GLFW_KEY_LEFT_CONTROL];
        
        // Movement
        if (keys[GLFW_KEY_W]) {
            camera.move(Camera.CameraMovement.FORWARD, deltaTime, sprinting, world);
        }
        if (keys[GLFW_KEY_S]) {
            camera.move(Camera.CameraMovement.BACKWARD, deltaTime, sprinting, world);
        }
        if (keys[GLFW_KEY_A]) {
            camera.move(Camera.CameraMovement.LEFT, deltaTime, sprinting, world);
        }
        if (keys[GLFW_KEY_D]) {
            camera.move(Camera.CameraMovement.RIGHT, deltaTime, sprinting, world);
        }
        if (keys[GLFW_KEY_SPACE]) {
            camera.move(Camera.CameraMovement.UP, deltaTime, sprinting, world);
        }
        if (keys[GLFW_KEY_LEFT_SHIFT]) {
            camera.move(Camera.CameraMovement.DOWN, deltaTime, sprinting, world);
        }
    }
}