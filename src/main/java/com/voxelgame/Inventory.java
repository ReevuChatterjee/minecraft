package com.voxelgame;

/**
 * Player inventory system with hotbar slots.
 * Each slot holds a block type and a count.
 */
public class Inventory {
    public static final int HOTBAR_SIZE = 9;

    private byte[] slotTypes;
    private int[] slotCounts;
    private int selectedSlot = 0;

    public Inventory() {
        slotTypes = new byte[HOTBAR_SIZE];
        slotCounts = new int[HOTBAR_SIZE];

        // Starter inventory
        slotTypes[0] = BlockType.STONE.id;      slotCounts[0] = 64;
        slotTypes[1] = BlockType.DIRT.id;        slotCounts[1] = 64;
        slotTypes[2] = BlockType.GRASS.id;       slotCounts[2] = 64;
        slotTypes[3] = BlockType.SAND.id;        slotCounts[3] = 64;
        slotTypes[4] = BlockType.WOOD.id;        slotCounts[4] = 64;
        slotTypes[5] = BlockType.LEAVES.id;      slotCounts[5] = 64;
        slotTypes[6] = BlockType.COBBLESTONE.id;  slotCounts[6] = 64;
        slotTypes[7] = BlockType.PLANKS.id;      slotCounts[7] = 64;
        slotTypes[8] = BlockType.BEDROCK.id;     slotCounts[8] = 0; // Empty slot
    }

    /**
     * Add a block to the inventory. Tries to stack with existing slots first,
     * then uses the first empty slot.
     */
    public boolean addItem(byte blockType) {
        if (blockType == BlockType.AIR.id) return false;

        // Try to stack with existing same-type slot
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            if (slotTypes[i] == blockType && slotCounts[i] > 0 && slotCounts[i] < 64) {
                slotCounts[i]++;
                return true;
            }
        }

        // Try to fill an empty slot
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            if (slotCounts[i] <= 0) {
                slotTypes[i] = blockType;
                slotCounts[i] = 1;
                return true;
            }
        }

        return false; // Inventory full
    }

    /**
     * Get the block type of the currently selected slot.
     * Returns AIR if the slot is empty.
     */
    public byte getSelectedBlockType() {
        if (slotCounts[selectedSlot] <= 0) {
            return BlockType.AIR.id;
        }
        return slotTypes[selectedSlot];
    }

    /**
     * Decrease count of the selected slot by 1.
     * Returns true if a block was consumed.
     */
    public boolean consumeSelected() {
        if (slotCounts[selectedSlot] <= 0) {
            return false;
        }
        slotCounts[selectedSlot]--;
        if (slotCounts[selectedSlot] <= 0) {
            slotTypes[selectedSlot] = BlockType.AIR.id;
        }
        return true;
    }

    public void setSelectedSlot(int slot) {
        if (slot >= 0 && slot < HOTBAR_SIZE) {
            selectedSlot = slot;
        }
    }

    public int getSelectedSlot() {
        return selectedSlot;
    }

    /**
     * Scroll selection (positive = right, negative = left)
     */
    public void scrollSelection(int direction) {
        selectedSlot = ((selectedSlot + direction) % HOTBAR_SIZE + HOTBAR_SIZE) % HOTBAR_SIZE;
    }

    public byte getSlotType(int slot) {
        if (slot < 0 || slot >= HOTBAR_SIZE) return BlockType.AIR.id;
        return slotTypes[slot];
    }

    public int getSlotCount(int slot) {
        if (slot < 0 || slot >= HOTBAR_SIZE) return 0;
        return slotCounts[slot];
    }

    public void setSlot(int slot, byte type, int count) {
        if (slot >= 0 && slot < HOTBAR_SIZE) {
            slotTypes[slot] = type;
            slotCounts[slot] = count;
        }
    }
}
