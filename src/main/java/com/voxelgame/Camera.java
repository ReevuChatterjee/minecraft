package com.voxelgame;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public class Camera {
    private Vector3f position;
    private Vector3f front;
    private Vector3f up;
    private Vector3f right;

    private float yaw = -90.0f;   // Initially facing negative Z
    private float pitch = 0.0f;

    private Matrix4f projection;
    private Matrix4f view;

    private float fov = 70.0f;
    private float nearPlane = 0.1f;
    private float farPlane = 1000.0f;

    private float movementSpeed = 4.3f;
    private float sprintMultiplier = 1.5f;

    // Player dimensions
    private float playerWidth = 0.6f;
    private float playerHeight = 1.8f;
    private float eyeHeight = 1.62f; // Eyes are near top of hitbox

    // Physics
    private float velocityY = 0f;
    private boolean onGround = false;
    private boolean flying = true; // Start in flight mode for new worlds
    private static final float GRAVITY = 28.0f;
    private static final float JUMP_FORCE = 9.0f;
    private static final float TERMINAL_VELOCITY = -50.0f;

    public Camera(int width, int height) {
        this(width, height, 70.0f);
    }

    public Camera(int width, int height, float fov) {
        this.fov = fov;
        position = new Vector3f(0, 100, 0); // Start high to fall onto terrain
        front = new Vector3f(0, 0, -1);
        up = new Vector3f(0, 1, 0);
        right = new Vector3f(1, 0, 0);

        projection = new Matrix4f();
        view = new Matrix4f();

        updateProjection(width, height);
        updateVectors();
    }

    public void update(float deltaTime) {
        updateVectors();
        updateViewMatrix();
    }

    /**
     * Apply gravity and vertical collision resolution.
     * Called every frame from Game.update().
     */
    public void updatePhysics(float deltaTime, World world) {
        if (flying) return; // No physics in flight mode

        // Apply gravity
        velocityY -= GRAVITY * deltaTime;
        if (velocityY < TERMINAL_VELOCITY) {
            velocityY = TERMINAL_VELOCITY;
        }

        // Try to move vertically
        float dy = velocityY * deltaTime;
        Vector3f newPos = new Vector3f(position);
        newPos.y += dy;

        if (checkCollision(newPos, world)) {
            // Collision — resolve
            if (velocityY < 0) {
                // Falling — snap to ground
                onGround = true;
                // Find the ground level
                float feetY = position.y - eyeHeight;
                int groundCheck = (int) Math.floor(feetY + dy);
                position.y = groundCheck + 1 + eyeHeight;
            }
            velocityY = 0;
        } else {
            onGround = false;
            position.y = newPos.y;
        }
    }

    public void move(CameraMovement direction, float deltaTime, boolean sprinting, World world) {
        float velocity = movementSpeed * deltaTime;
        if (sprinting) {
            velocity *= sprintMultiplier;
        }

        Vector3f newPosition = new Vector3f(position);

        // In non-flying mode, movement is horizontal only (ignore pitch for forward/back)
        if (!flying) {
            Vector3f flatFront = new Vector3f(front.x, 0, front.z).normalize();
            Vector3f flatRight = new Vector3f(right.x, 0, right.z).normalize();

            switch (direction) {
                case FORWARD:
                    newPosition.add(new Vector3f(flatFront).mul(velocity));
                    break;
                case BACKWARD:
                    newPosition.sub(new Vector3f(flatFront).mul(velocity));
                    break;
                case LEFT:
                    newPosition.sub(new Vector3f(flatRight).mul(velocity));
                    break;
                case RIGHT:
                    newPosition.add(new Vector3f(flatRight).mul(velocity));
                    break;
                case UP: // Jump
                    // Handled by jump()
                    break;
                case DOWN: // Sneak
                    // In survival, sneak just slows down (already applied via velocity)
                    break;
            }
        } else {
            // Flying mode — full 3D movement
            switch (direction) {
                case FORWARD:
                    newPosition.add(new Vector3f(front).mul(velocity));
                    break;
                case BACKWARD:
                    newPosition.sub(new Vector3f(front).mul(velocity));
                    break;
                case LEFT:
                    newPosition.sub(new Vector3f(right).mul(velocity));
                    break;
                case RIGHT:
                    newPosition.add(new Vector3f(right).mul(velocity));
                    break;
                case UP:
                    newPosition.y += velocity;
                    break;
                case DOWN:
                    newPosition.y -= velocity;
                    break;
            }
        }

        // Apply horizontal collision check
        if (!flying && world != null) {
            // Try X movement
            Vector3f testX = new Vector3f(newPosition.x, position.y, position.z);
            if (!checkCollision(testX, world)) {
                position.x = newPosition.x;
            }
            // Try Z movement
            Vector3f testZ = new Vector3f(position.x, position.y, newPosition.z);
            if (!checkCollision(testZ, world)) {
                position.z = newPosition.z;
            }
        } else {
            position.set(newPosition);
        }
    }

    /**
     * Try to jump. Only works when grounded and not flying.
     */
    public void jump() {
        if (!flying && onGround) {
            velocityY = JUMP_FORCE;
            onGround = false;
        }
    }

    /**
     * Toggle between flying and survival mode.
     */
    public void toggleFlying() {
        flying = !flying;
        if (flying) {
            velocityY = 0;
            onGround = false;
        }
        System.out.println("Flight mode: " + (flying ? "ON" : "OFF"));
    }

    public boolean isFlying() {
        return flying;
    }

    public boolean isOnGround() {
        return onGround;
    }

    /**
     * Check if player bounding box collides with any blocks
     */
    private boolean checkCollision(Vector3f pos, World world) {
        // Player bounding box — position is at eye level
        float minX = pos.x - playerWidth / 2;
        float maxX = pos.x + playerWidth / 2;
        float minY = pos.y - eyeHeight;       // Feet
        float maxY = pos.y + (playerHeight - eyeHeight); // Top of head
        float minZ = pos.z - playerWidth / 2;
        float maxZ = pos.z + playerWidth / 2;

        // Check all blocks the player could be touching
        for (int x = (int) Math.floor(minX); x <= Math.floor(maxX); x++) {
            for (int y = (int) Math.floor(minY); y <= Math.floor(maxY); y++) {
                for (int z = (int) Math.floor(minZ); z <= Math.floor(maxZ); z++) {
                    byte block = world.getBlock(x, y, z);
                    if (block != BlockType.AIR.id) {
                        return true; // Collision detected
                    }
                }
            }
        }

        return false; // No collision
    }

    public void rotate(float xOffset, float yOffset) {
        yaw += xOffset;
        pitch += yOffset;

        // Constrain pitch to prevent screen flip
        if (pitch > 89.0f) pitch = 89.0f;
        if (pitch < -89.0f) pitch = -89.0f;

        updateVectors();
    }

    private void updateVectors() {
        // Calculate new front vector
        Vector3f newFront = new Vector3f();
        newFront.x = (float) (Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)));
        newFront.y = (float) Math.sin(Math.toRadians(pitch));
        newFront.z = (float) (Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)));
        front = newFront.normalize();

        // Recalculate right and up vectors
        right = new Vector3f(front).cross(new Vector3f(0, 1, 0)).normalize();
        up = new Vector3f(right).cross(front).normalize();
    }

    private void updateViewMatrix() {
        view.identity();
        Vector3f center = new Vector3f(position).add(front);
        view.lookAt(position, center, up);
    }

    public void updateProjection(int width, int height) {
        float aspectRatio = (float) width / (float) height;
        projection.identity();
        projection.perspective((float) Math.toRadians(fov), aspectRatio, nearPlane, farPlane);
    }

    public Matrix4f getViewMatrix() {
        return view;
    }

    public Matrix4f getProjectionMatrix() {
        return projection;
    }

    public Vector3f getPosition() {
        return position;
    }

    public Vector3f getFront() {
        return front;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public enum CameraMovement {
        FORWARD,
        BACKWARD,
        LEFT,
        RIGHT,
        UP,
        DOWN
    }
}