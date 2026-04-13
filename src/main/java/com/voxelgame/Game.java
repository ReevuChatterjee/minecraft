package com.voxelgame;

import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.*;

import java.nio.*;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

public class Game {
    private long window;
    private int width = 1280;
    private int height = 720;
    private String title = "Minecraft Clone";

    private Camera camera;
    private InputHandler inputHandler;
    private World world;
    private Inventory inventory;
    private HUD hud;
    private EntityManager entityManager;

    private double lastFrameTime;
    private float deltaTime;

    // Launcher-configurable settings
    private int launcherRenderDistance = 8;
    private float launcherFov = 70.0f;

    public void setRenderDistance(int rd) { this.launcherRenderDistance = rd; }
    public void setFov(int fov) { this.launcherFov = fov; }

    public void run() {
        System.out.println("Starting game...");
        init();
        loop();
        cleanup();
    }

    private void init() {
        // Setup error callback
        GLFWErrorCallback.createPrint(System.err).set();

        // Initialize GLFW
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        // Configure GLFW
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GL_TRUE);

        // Create window
        window = glfwCreateWindow(width, height, title, NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("Failed to create the GLFW window");
        }

        // Center window
        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);
            glfwGetWindowSize(window, pWidth, pHeight);
            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
            glfwSetWindowPos(
                window,
                (vidmode.width() - pWidth.get(0)) / 2,
                (vidmode.height() - pHeight.get(0)) / 2
            );
        }

        // Make the OpenGL context current
        glfwMakeContextCurrent(window);
        // Enable v-sync
        glfwSwapInterval(1);
        // Show window
        glfwShowWindow(window);

        // Create OpenGL capabilities
        GL.createCapabilities();

        // Set viewport
        glViewport(0, 0, width, height);

        // OpenGL settings
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glClearColor(0.53f, 0.81f, 0.92f, 1.0f); // Sky blue

        // Initialize game components with launcher settings
        camera = new Camera(width, height, launcherFov);
        world = new World(launcherRenderDistance);
        inventory = new Inventory();
        entityManager = new EntityManager();
        inputHandler = new InputHandler(window, camera, world, inventory, entityManager);
        hud = new HUD();

        // Set window resize callback
        glfwSetFramebufferSizeCallback(window, (window, w, h) -> {
            width = w;
            height = h;
            glViewport(0, 0, w, h);
            camera.updateProjection(w, h);
        });

        lastFrameTime = glfwGetTime();

        System.out.println("Initialization complete");
        System.out.println("Controls:");
        System.out.println("  WASD - Move  |  Mouse - Look");
        System.out.println("  SPACE - Jump/Fly Up  |  SHIFT - Fly Down");
        System.out.println("  F - Toggle Flight  |  CTRL - Sprint");
        System.out.println("  Left Click - Break  |  Right Click - Place");
        System.out.println("  1-9 / Scroll - Select Block");
        System.out.println("  F5 - Save  |  F9 - Load  |  ESC - Quit");
    }

    private void loop() {
        while (!glfwWindowShouldClose(window)) {
            // Calculate delta time
            double currentTime = glfwGetTime();
            deltaTime = (float) (currentTime - lastFrameTime);
            lastFrameTime = currentTime;

            // Cap delta time to prevent physics explosions on lag spikes
            if (deltaTime > 0.1f) deltaTime = 0.1f;

            // Input
            inputHandler.update(deltaTime);

            // Update
            update();

            // Render
            render();

            // Swap buffers and poll events
            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    private void update() {
        camera.updatePhysics(deltaTime, world);
        camera.update(deltaTime);
        world.update(camera); // Dynamic chunk loading

        // Spawn animals near camera
        int camChunkX = (int) Math.floor(camera.getPosition().x / 16);
        int camChunkZ = (int) Math.floor(camera.getPosition().z / 16);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                entityManager.spawnInChunk(camChunkX + dx, camChunkZ + dz, world);
            }
        }

        entityManager.update(deltaTime, world);
    }

    private void render() {
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        // Render 3D world
        world.render(camera);

        // Render entities (animals)
        entityManager.render(camera);

        // Render 2D HUD overlay
        hud.render(width, height, camera, inventory, deltaTime);
    }

    private void cleanup() {
        entityManager.cleanup();
        hud.cleanup();
        world.cleanup();
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
        glfwSetErrorCallback(null).free();
    }

    public static void main(String[] args) {
        new Game().run();
    }
}