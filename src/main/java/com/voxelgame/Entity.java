package com.voxelgame;

import org.joml.Vector3f;
import java.util.Random;

/**
 * A living entity in the world (cow, goat).
 * Has simple wandering AI and can be killed for food.
 */
public class Entity {
    public enum Type {
        COW(3, BlockType.RAW_BEEF.id, new float[]{0.55f, 0.30f, 0.10f},
            new float[]{0.9f, 0.9f, 0.85f}, 1.0f, 0.7f, 1.4f, 0.5f, 0.5f, 0.6f),
        GOAT(2, BlockType.RAW_MUTTON.id, new float[]{0.75f, 0.72f, 0.68f},
            new float[]{0.55f, 0.50f, 0.45f}, 0.8f, 0.6f, 1.0f, 0.4f, 0.4f, 0.45f);

        public final int health;
        public final byte dropItem;
        public final float[] bodyColor;
        public final float[] headColor;
        // Body dimensions
        public final float bodyW, bodyH, bodyL;
        // Head dimensions
        public final float headW, headH, headL;

        Type(int health, byte dropItem, float[] bodyColor, float[] headColor,
             float bodyW, float bodyH, float bodyL,
             float headW, float headH, float headL) {
            this.health = health;
            this.dropItem = dropItem;
            this.bodyColor = bodyColor;
            this.headColor = headColor;
            this.bodyW = bodyW;
            this.bodyH = bodyH;
            this.bodyL = bodyL;
            this.headW = headW;
            this.headH = headH;
            this.headL = headL;
        }
    }

    public enum State { IDLE, WALKING }

    public Vector3f position;
    public float yaw; // degrees
    public Type type;
    public int health;
    public State state;
    public float stateTimer;
    public float walkDirX, walkDirZ;
    public float velocityY;
    public boolean onGround;
    public boolean dead;

    private static final float GRAVITY = 20.0f;
    private static final float MOVE_SPEED = 1.5f;
    private Random random;

    public Entity(Type type, float x, float y, float z) {
        this.type = type;
        this.position = new Vector3f(x, y, z);
        this.health = type.health;
        this.state = State.IDLE;
        this.stateTimer = 2.0f;
        this.yaw = (float)(Math.random() * 360);
        this.random = new Random((long)(x * 10000 + z * 100 + y));
        this.velocityY = 0;
        this.onGround = false;
        this.dead = false;
    }

    /**
     * Update AI and physics.
     */
    public void update(float dt, World world) {
        if (dead) return;

        // Update state timer
        stateTimer -= dt;
        if (stateTimer <= 0) {
            switchState();
        }

        // Movement
        if (state == State.WALKING) {
            float newX = position.x + walkDirX * MOVE_SPEED * dt;
            float newZ = position.z + walkDirZ * MOVE_SPEED * dt;

            // Simple collision: check if destination is walkable
            int checkY = (int) Math.floor(position.y);
            byte blockAtFeet = world.getBlock((int) Math.floor(newX), checkY, (int) Math.floor(newZ));
            byte blockAbove = world.getBlock((int) Math.floor(newX), checkY + 1, (int) Math.floor(newZ));

            if (blockAtFeet == BlockType.AIR.id && blockAbove == BlockType.AIR.id) {
                position.x = newX;
                position.z = newZ;
                // Face movement direction
                yaw = (float) Math.toDegrees(Math.atan2(walkDirX, walkDirZ));
            } else {
                // Hit a wall, turn around
                switchState();
            }
        }

        // Gravity
        velocityY -= GRAVITY * dt;
        if (velocityY < -30.0f) velocityY = -30.0f;

        // Apply vertical velocity
        float newY = position.y + velocityY * dt;

        // Ground collision
        int feetBlockY = (int) Math.floor(newY);
        byte blockBelow = world.getBlock((int) Math.floor(position.x), feetBlockY, (int) Math.floor(position.z));

        if (blockBelow != BlockType.AIR.id && blockBelow != BlockType.LEAVES.id) {
            position.y = feetBlockY + 1.0f;
            velocityY = 0;
            onGround = true;
        } else {
            position.y = newY;
            onGround = false;
        }

        // Despawn if fallen into void
        if (position.y < -10) {
            dead = true;
        }
    }

    private void switchState() {
        if (state == State.IDLE) {
            // Start walking in random direction
            state = State.WALKING;
            float angle = random.nextFloat() * 360.0f;
            walkDirX = (float) Math.sin(Math.toRadians(angle));
            walkDirZ = (float) Math.cos(Math.toRadians(angle));
            stateTimer = 2.0f + random.nextFloat() * 4.0f; // Walk 2-6 seconds
        } else {
            // Stop and idle
            state = State.IDLE;
            stateTimer = 1.5f + random.nextFloat() * 3.5f; // Idle 1.5-5 seconds
        }
    }

    /**
     * Deal damage. Returns true if killed.
     */
    public boolean damage(int amount) {
        health -= amount;
        if (health <= 0) {
            dead = true;
            return true;
        }
        // Knockback: push away from hit direction
        velocityY = 4.0f;
        return false;
    }

    /**
     * Get axis-aligned bounding box: [minX, minY, minZ, maxX, maxY, maxZ]
     */
    public float[] getAABB() {
        float hw = type.bodyW * 0.5f;
        float hl = type.bodyL * 0.5f;
        return new float[]{
            position.x - hw, position.y, position.z - hl,
            position.x + hw, position.y + type.bodyH, position.z + hl
        };
    }
}
