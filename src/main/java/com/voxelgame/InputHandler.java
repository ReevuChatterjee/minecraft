package com.voxelgame;

import org.lwjgl.glfw.*;
import org.joml.Vector3i;
import static org.lwjgl.glfw.GLFW.*;

public class InputHandler {
    private long window;
    private Camera camera;
    private World world;
    private Inventory inventory;
    private EntityManager entityManager;

    private double lastX = 400;
    private double lastY = 300;
    private boolean firstMouse = true;
    private float sensitivity = 0.1f;

    private boolean[] keys = new boolean[GLFW_KEY_LAST];

    // Cooldowns (in seconds)
    private float breakCooldown = 0f;
    private float placeCooldown = 0f;
    private static final float ACTION_COOLDOWN = 0.25f;

    public InputHandler(long window, Camera camera, World world, Inventory inventory, EntityManager entityManager) {
        this.window = window;
        this.camera = camera;
        this.world = world;
        this.inventory = inventory;
        this.entityManager = entityManager;

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

            // Toggle flight mode
            if (key == GLFW_KEY_F && action == GLFW_PRESS) {
                camera.toggleFlying();
            }

            // Hotbar selection (1-9 keys)
            if (action == GLFW_PRESS) {
                if (key >= GLFW_KEY_1 && key <= GLFW_KEY_9) {
                    int slot = key - GLFW_KEY_1;
                    inventory.setSelectedSlot(slot);
                }
            }

            // Save / Load
            if (key == GLFW_KEY_F5 && action == GLFW_PRESS) {
                WorldSave.save(world, camera, inventory);
            }
            if (key == GLFW_KEY_F9 && action == GLFW_PRESS) {
                WorldSave.load(world, camera, inventory);
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
                if (breakCooldown <= 0) {
                    breakBlock();
                    breakCooldown = ACTION_COOLDOWN;
                }
            }

            if (button == GLFW_MOUSE_BUTTON_RIGHT && action == GLFW_PRESS) {
                if (placeCooldown <= 0) {
                    placeBlock();
                    placeCooldown = ACTION_COOLDOWN;
                }
            }
        });

        // Scroll wheel callback for hotbar selection
        glfwSetScrollCallback(window, (window, xoffset, yoffset) -> {
            if (yoffset > 0) {
                inventory.scrollSelection(-1); // Scroll up = move left
            } else if (yoffset < 0) {
                inventory.scrollSelection(1);  // Scroll down = move right
            }
        });
    }

    private void breakBlock() {
        // 1. Try hitting an entity first
        Entity hitEntity = entityManager.raycastEntities(
            camera.getPosition(), 
            camera.getFront(), 
            Raycast.MAX_DISTANCE
        );
        
        if (hitEntity != null) {
            boolean killed = hitEntity.damage(1);
            if (killed) {
                inventory.addItem(hitEntity.type.dropItem);
                System.out.println("Killed " + hitEntity.type + " and got " + BlockType.getName(hitEntity.type.dropItem));
            } else {
                System.out.println("Hit " + hitEntity.type + " (health: " + hitEntity.health + ")");
            }
            return; // Don't break blocks behind the entity
        }

        // 2. Try breaking a block
        Raycast.RaycastResult result = Raycast.raycast(
            camera.getPosition(),
            camera.getFront(),
            world
        );

        if (result.hit) {
            byte brokenType = world.breakBlock(result.blockPosition);
            if (brokenType != BlockType.AIR.id) {
                // Add broken block to inventory
                inventory.addItem(brokenType);
                System.out.println("Broke " + BlockType.getName(brokenType) +
                    " at: " + result.blockPosition);
            }
        }
    }

    private void placeBlock() {
        byte selectedType = inventory.getSelectedBlockType();
        if (selectedType == BlockType.AIR.id) {
            System.out.println("No block selected or slot empty!");
            return;
        }

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
                if (world.placeBlock(placePos, selectedType)) {
                    inventory.consumeSelected();
                    System.out.println("Placed " + BlockType.getName(selectedType) +
                        " at: " + placePos);
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
        // Update cooldowns
        if (breakCooldown > 0) breakCooldown -= deltaTime;
        if (placeCooldown > 0) placeCooldown -= deltaTime;

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

        // Jump / Fly up
        if (keys[GLFW_KEY_SPACE]) {
            if (camera.isFlying()) {
                camera.move(Camera.CameraMovement.UP, deltaTime, sprinting, world);
            } else {
                camera.jump();
            }
        }

        // Sneak / Fly down
        if (keys[GLFW_KEY_LEFT_SHIFT]) {
            if (camera.isFlying()) {
                camera.move(Camera.CameraMovement.DOWN, deltaTime, sprinting, world);
            }
            // In survival mode, shift doesn't move down (could add sneaking later)
        }
    }
}