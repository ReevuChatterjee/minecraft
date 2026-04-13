package com.voxelgame;

import java.io.*;
import java.util.*;

/**
 * Simple save/load system that persists modified blocks, player position, and inventory.
 * Uses a custom text-based format (no JSON library dependency needed).
 */
public class WorldSave {
    private static final String SAVE_FILE = "world_save.dat";

    /**
     * Save the current world state.
     */
    public static void save(World world, Camera camera, Inventory inventory) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(SAVE_FILE))) {
            // Save player position
            writer.println("[PLAYER]");
            writer.printf("%.4f,%.4f,%.4f,%.2f,%.2f,%b%n",
                camera.getPosition().x, camera.getPosition().y, camera.getPosition().z,
                camera.getYaw(), camera.getPitch(), camera.isFlying());

            // Save inventory
            writer.println("[INVENTORY]");
            for (int i = 0; i < Inventory.HOTBAR_SIZE; i++) {
                writer.printf("%d,%d%n", inventory.getSlotType(i), inventory.getSlotCount(i));
            }
            writer.printf("selected=%d%n", inventory.getSelectedSlot());

            // Save modified blocks
            writer.println("[BLOCKS]");
            Map<String, Byte> modifications = world.getModifiedBlocks();
            for (Map.Entry<String, Byte> entry : modifications.entrySet()) {
                writer.printf("%s,%d%n", entry.getKey(), entry.getValue());
            }

            writer.println("[END]");
            System.out.println("World saved! (" + modifications.size() + " modified blocks)");
        } catch (IOException e) {
            System.err.println("Failed to save: " + e.getMessage());
        }
    }

    /**
     * Load world state from file.
     */
    public static boolean load(World world, Camera camera, Inventory inventory) {
        File file = new File(SAVE_FILE);
        if (!file.exists()) {
            System.out.println("No save file found.");
            return false;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            String section = "";
            int invSlot = 0;
            Map<String, Byte> modifications = new HashMap<>();

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                if (line.startsWith("[")) {
                    section = line;
                    invSlot = 0;
                    continue;
                }

                switch (section) {
                    case "[PLAYER]": {
                        String[] parts = line.split(",");
                        if (parts.length >= 5) {
                            float x = Float.parseFloat(parts[0]);
                            float y = Float.parseFloat(parts[1]);
                            float z = Float.parseFloat(parts[2]);
                            float yaw = Float.parseFloat(parts[3]);
                            float pitch = Float.parseFloat(parts[4]);
                            camera.getPosition().set(x, y, z);
                            camera.rotate(yaw - camera.getYaw(), pitch - camera.getPitch());
                        }
                        break;
                    }
                    case "[INVENTORY]": {
                        if (line.startsWith("selected=")) {
                            int sel = Integer.parseInt(line.substring(9));
                            inventory.setSelectedSlot(sel);
                        } else {
                            String[] parts = line.split(",");
                            if (parts.length == 2 && invSlot < Inventory.HOTBAR_SIZE) {
                                byte type = Byte.parseByte(parts[0]);
                                int count = Integer.parseInt(parts[1]);
                                inventory.setSlot(invSlot, type, count);
                                invSlot++;
                            }
                        }
                        break;
                    }
                    case "[BLOCKS]": {
                        // Format: x,y,z,blockType
                        int lastComma = line.lastIndexOf(',');
                        if (lastComma > 0) {
                            String key = line.substring(0, lastComma);
                            byte type = Byte.parseByte(line.substring(lastComma + 1));
                            modifications.put(key, type);
                        }
                        break;
                    }
                }
            }

            // Apply block modifications to world
            world.applyModifications(modifications);
            System.out.println("World loaded! (" + modifications.size() + " modified blocks)");
            return true;
        } catch (Exception e) {
            System.err.println("Failed to load: " + e.getMessage());
            return false;
        }
    }
}
