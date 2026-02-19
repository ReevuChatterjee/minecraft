# Minecraft Clone - Detailed Code Explanation

## How to Start This Program

### Prerequisites
- Java 17 or higher
- Maven

### Running the Game

**Option 1: Using Maven (recommended)**
```bash
mvn clean compile exec:java -Dexec.mainClass="com.voxelgame.Game"
```

**Option 2: Compile and run manually**
```bash
mvn clean package
java -cp target/classes com.voxelgame.Game
```

### Controls
- **WASD**: Move around
- **Mouse**: Look around
- **Left Click**: Break block
- **Right Click**: Place block
- **ESC**: Close game

---

## 1. pom.xml - Maven Project Configuration

**Purpose**: Defines project dependencies and build configuration

**Key Concepts**:
- **Dependency Management**: Maven automatically downloads and manages libraries
- **LWJGL (Lightweight Java Game Library)**: Provides Java bindings to native libraries
  - `lwjgl-glfw`: Window creation and input handling
  - `lwjgl-opengl`: 3D graphics rendering
  - `lwjgl-stb`: Image loading utilities
- **Native Libraries**: Platform-specific compiled code (`.dll` for Windows, `.dylib` for Mac, `.so` for Linux)
- **JOML**: Java OpenGL Math Library - provides vectors, matrices, quaternions

**Why We Need It**:
Java can't directly access GPU or create windows. LWJGL bridges Java to native OpenGL/system APIs.

---

## 2. Game.java - Main Game Loop

**Purpose**: Orchestrates the entire game - initialization, game loop, and cleanup

**Key Concepts**:

### GLFW Window Management
```java
glfwInit() // Initialize windowing system
glfwCreateWindow() // Create OS window
glfwMakeContextCurrent() // Bind OpenGL context to this window
```
- **GLFW**: Cross-platform library for window/input handling
- **OpenGL Context**: Contains all OpenGL state (textures, shaders, buffers)

### OpenGL Initialization
```java
GL.createCapabilities() // Load OpenGL function pointers
glEnable(GL_DEPTH_TEST) // Enable 3D depth testing
glEnable(GL_CULL_FACE) // Don't render back-facing polygons
glCullFace(GL_BACK) // Cull triangles facing away
```

**Depth Testing**: Ensures closer objects appear in front of farther ones. Without it, rendering order would determine what's visible.

**Face Culling**: Optimization - don't render triangle faces pointing away from camera. Saves ~50% GPU work.

### Game Loop Architecture
```java
while (!glfwWindowShouldClose(window)) {
    // 1. Calculate deltaTime
    // 2. Process input
    // 3. Update game state
    // 4. Render frame
    // 5. Swap buffers
    // 6. Poll events
}
```

**Delta Time**: Time between frames. Ensures movement is frame-rate independent.
- 60 FPS → deltaTime ≈ 0.016 seconds
- Movement = speed × deltaTime → same distance regardless of FPS

**Double Buffering**: Drawing happens to back buffer while front buffer is displayed. Swap prevents tearing/flickering.

### Component Architecture
```java
Camera camera = new Camera()
InputHandler inputHandler = new InputHandler()
World world = new World()
```
**Separation of Concerns**: Each class handles one responsibility
- Camera: View/projection matrices
- InputHandler: User input
- World: Terrain/chunks

---

## 3. Camera.java - First-Person Camera System

**Purpose**: Handles view transformation and player perspective

**Key Concepts**:

### 3D Space Representation
```java
Vector3f position  // Where camera is (x, y, z)
Vector3f front     // Direction camera faces
Vector3f up        // Which way is "up"
Vector3f right     // Right direction (perpendicular to front and up)
```

**Vector Math**: Vectors represent direction and magnitude
- Position: `(0, 80, 0)` = 80 blocks above origin
- Front: `(0, 0, -1)` = facing negative Z (north in Minecraft)

### Euler Angles (Rotation)
```java
float yaw = -90.0f   // Left-right rotation (compass heading)
float pitch = 0.0f   // Up-down rotation (looking up/down)
```

**Yaw**: Horizontal rotation around Y-axis
- 0° = East, 90° = South, 180° = West, 270° = North

**Pitch**: Vertical rotation
- +89° = looking almost straight up
- -89° = looking almost straight down
- Clamped to ±89° to prevent **gimbal lock** (orientation flip)

### Converting Euler Angles to Direction Vector
```java
front.x = cos(yaw) * cos(pitch)
front.y = sin(pitch)
front.z = sin(yaw) * cos(pitch)
```

**Spherical Coordinates**: Convert angles to 3D direction
- Uses trigonometry to find point on unit sphere
- Results in normalized direction vector

### View Matrix (Camera Transformation)
```java
view.lookAt(position, center, up)
```

**View Matrix**: Transforms world coordinates to camera space
- Everything moves relative to camera
- If camera moves right, world appears to move left

**lookAt()**: Builds view matrix from:
- Eye position (camera location)
- Center (what we're looking at)
- Up vector (which way is up)

### Projection Matrix (Perspective)
```java
projection.perspective(fov, aspectRatio, nearPlane, farPlane)
```

**Perspective Projection**: Creates depth illusion
- Objects farther away appear smaller
- **FOV**: Field of view angle (70° = balanced view)
- **Aspect Ratio**: Width/height (prevents stretching)
- **Near/Far Planes**: Only render objects between these distances

**Frustum**: Pyramid-shaped viewing volume
- Near plane: 0.1 units from camera
- Far plane: 1000 units from camera
- Anything outside is clipped (not rendered)

### Movement System
```java
position.add(front.mul(velocity)) // Forward
position.sub(right.mul(velocity)) // Left
```

**Vector Arithmetic**:
- Forward: Move along `front` direction
- Strafe: Move along `right` direction
- Vertical: Change Y directly (no gravity in creative mode)

**Sprint**: Multiplies velocity by 1.5x for faster movement

---

## 4. InputHandler.java - User Input Processing

**Purpose**: Translates keyboard/mouse input into game actions

**Key Concepts**:

### GLFW Callbacks (Event-Driven Programming)
```java
glfwSetKeyCallback(window, (window, key, scancode, action, mods) -> {...})
```

**Callback Functions**: OS calls these when events occur
- Key pressed → callback fires → update state
- Asynchronous: happens whenever user acts

### Input State Tracking
```java
boolean[] keys = new boolean[GLFW_KEY_LAST]
```

**State Array**: Tracks which keys are currently held down
- `keys[GLFW_KEY_W] = true` means W is pressed
- Allows checking multiple simultaneous inputs (W + Shift = sprint forward)

### Mouse Look Implementation
```java
double xOffset = xpos - lastX
double yOffset = lastY - ypos  // Reversed!
```

**Mouse Delta**: Change in position since last frame
- Not absolute position, but movement amount
- Y reversed because screen Y goes down, but pitch goes up

**Cursor Capture**:
```java
glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED)
```
Hides cursor and locks to window - standard FPS behavior

### Mouse Sensitivity
```java
xOffset *= sensitivity  // 0.1 = slower, smoother turning
```
Scales raw mouse movement to comfortable rotation speed

### First Mouse Movement Problem
```java
if (firstMouse) {
    lastX = xpos
    lastY = ypos
    firstMouse = false
}
```
First callback has invalid delta (huge jump). This prevents camera snap on startup.

---

## 5. World.java - World Management

**Purpose**: Manages all chunks and orchestrates rendering

**Key Concepts**:

### Chunk System
```java
Map<ChunkPosition, Chunk> chunks
```

**Spatial Partitioning**: Divide world into manageable pieces
- Easier to render (only nearby chunks)
- Easier to generate (on-demand)
- Easier to modify (update one chunk at a time)

**HashMap for Fast Lookup**:
- Key: ChunkPosition (x, z coordinates)
- Value: Chunk object
- O(1) access time to find chunk at location

### ChunkPosition Helper Class
```java
static class ChunkPosition {
    final int x, z
    equals() and hashCode() overridden
}
```

**Why Override equals/hashCode?**
- HashMap uses these to compare keys
- Default compares memory addresses, not values
- Custom version compares actual coordinates

### Initial Chunk Generation
```java
for (int x = -4; x < 4; x++)
    for (int z = -4; z < 4; z++)
        generate chunk at (x, z)
```

Generates 8×8 = 64 chunks = 128×128 blocks visible area

### Rendering Pipeline
```java
shader.use()
shader.setMatrix4f("projection", ...)
shader.setMatrix4f("view", ...)
for each chunk: chunk.render()
```

**Shader Uniforms**: Pass matrices to GPU once per frame
- All chunks share same view/projection
- Efficiency: set once, use for all chunks

---

## 6. Chunk.java - Terrain Generation and Meshing

**Purpose**: Represents 16×256×16 section of world, generates terrain, builds renderable mesh

**Key Concepts**:

### Voxel Data Structure
```java
byte[][][] blocks = new byte[16][256][16]
```

**3D Array**: `blocks[x][y][z]` stores block type
- X: West-East (0-15)
- Y: Vertical (0-255)
- Z: North-South (0-15)
- `byte`: 1 byte per block = memory efficient (255 block types max)

### Terrain Generation with Noise
```java
double height = noise.noise(worldX * scale, worldZ * scale)
height = (height + 1) / 2  // Normalize to 0-1
int terrainHeight = (int)(height * 40 + 60)  // Scale to 60-100
```

**Simplex Noise**: Returns values -1 to +1
- **Scale (0.02)**: Controls frequency
  - Small = stretched hills
  - Large = tight, noisy terrain
- **Amplitude (40)**: Controls height variation
- **Base (60)**: Minimum terrain height

**Why Noise?**
- Random but continuous (smooth hills)
- Deterministic (same seed → same world)
- Infinite (works at any coordinate)

### Layer Generation Logic
```java
if (y == 0) BEDROCK
else if (y < terrainHeight - 4) STONE
else if (y < terrainHeight - 1) DIRT
else if (y == terrainHeight - 1) GRASS
else AIR
```

Creates realistic layering:
- Bedrock bottom (indestructible)
- Stone core
- Dirt subsurface (3-4 blocks)
- Grass surface
- Air above

### Greedy Meshing (Face Culling)
```java
if (shouldRenderFace(x, y+1, z))
    addTopFace(...)
```

**Optimization**: Only create faces adjacent to air
- Hidden faces between solid blocks = wasted triangles
- Check 6 neighbors before adding face
- Massive performance gain (90%+ faces culled underground)

### Mesh Building
```java
List<Float> vertices = new ArrayList<>()
// For each visible face:
    addQuad(vertices, x1, y1, z1, ...)
```

**Vertex Data**: Each vertex needs:
- Position (x, y, z): 3 floats
- Color (r, g, b): 3 floats
- Total: 6 floats per vertex

**Quad (Rectangle)**: 2 triangles = 6 vertices
```
Triangle 1: v1 → v2 → v3
Triangle 2: v1 → v3 → v4
```

### Directional Lighting
```java
float brightness = 1.0f  // Top face (brightest)
float brightness = 0.5f  // Bottom face (darkest)
float brightness = 0.8f  // North/South
float brightness = 0.7f  // East/West
```

**Fake Ambient Occlusion**: Different brightness per face direction
- Creates depth perception
- Top always brightest (sun from above)
- Sides darker
- Bottom darkest

### OpenGL Buffer Management
```java
vao = glGenVertexArrays()
vbo = glGenBuffers()
glBindVertexArray(vao)
glBindBuffer(GL_ARRAY_BUFFER, vbo)
glBufferData(GL_ARRAY_BUFFER, vertexArray, GL_STATIC_DRAW)
```

**VAO (Vertex Array Object)**: Stores vertex format configuration
**VBO (Vertex Buffer Object)**: GPU memory holding vertex data

**Flow**:
1. Generate VAO/VBO IDs
2. Bind VAO (activate it)
3. Bind VBO and upload data to GPU
4. Configure vertex attributes (tell GPU how to read data)
5. Unbind (cleanup)

### Vertex Attributes
```java
// Position attribute (location = 0)
glVertexAttribPointer(0, 3, GL_FLOAT, false, 6*Float.BYTES, 0)
// Color attribute (location = 1)
glVertexAttribPointer(1, 3, GL_FLOAT, false, 6*Float.BYTES, 3*Float.BYTES)
```

**Stride**: 6 floats between each vertex start (3 pos + 3 color)
**Offset**: Color starts 3 floats in

**Memory Layout**:
```
[x, y, z, r, g, b, x, y, z, r, g, b, ...]
 └─pos─┘ └color┘ └─pos─┘ └color┘
```

---

## 7. BlockType.java - Block Definitions

**Purpose**: Defines block properties (ID and color)

**Key Concepts**:

### Enum Pattern
```java
enum BlockType {
    AIR((byte)0, new float[]{0,0,0}),
    GRASS((byte)1, new float[]{0.2f, 0.8f, 0.2f})
}
```

**Type Safety**: Can't accidentally use invalid block type
**Centralized Data**: All block info in one place

### ID System
```java
byte id
```
IDs stored in chunk array for memory efficiency
- 1 byte = 256 possible block types
- Could expand to `short` for 65,536 types

### RGB Color
```java
float[] color = {r, g, b}  // Values 0.0 to 1.0
```
- Grass: `{0.2, 0.8, 0.2}` = green
- Stone: `{0.5, 0.5, 0.5}` = gray

Later you'd replace this with texture coordinates.

---

## 8. SimplexNoise.java - Procedural Noise Generator

**Purpose**: Generate smooth, continuous random values for terrain

**Key Concepts**:

### Perlin/Simplex Noise
**Gradient Noise**: Based on random gradients at grid points
- Not white noise (completely random)
- Smooth interpolation between points
- Same input → same output (deterministic)

### 2D Noise Function
```java
double noise(double x, double z)
```
Returns value in range [-1, 1] based on (x, z) coordinate

**Properties**:
- **Continuous**: Small input change → small output change
- **Infinite**: Works at any coordinate
- **Seedable**: Different seeds → different patterns

### Skewing and Unskewing
```java
double s = (xin + yin) * F2  // Skew
int i = fastFloor(xin + s)
double t = (i + j) * G2      // Unskew
```

**Simplex Grid**: Uses triangular grid instead of square
- More efficient than Perlin noise
- Fewer gradient evaluations
- Better visual quality

### Gradient Vectors
```java
static final int[] GRAD3 = {1,1,0, -1,1,0, ...}
```
Predefined direction vectors at grid points
- Dot product with distance determines contribution
- Multiple gradients blended together

### Permutation Table
```java
int[] perm = new int[512]
```
**Hash Function**: Maps coordinates to random gradients
- Shuffled array ensures randomness
- Duplicated for overflow safety
- Seeded for reproducibility

---

## 9. ShaderProgram.java - GPU Rendering Pipeline

**Purpose**: Compiles and manages OpenGL shaders

**Key Concepts**:

### Shader Pipeline
```
Vertex Shader → (Rasterization) → Fragment Shader → Screen
```

**Vertex Shader**: Runs per vertex
- Transforms 3D positions to screen space
- Passes data (like color) to fragment shader

**Fragment Shader**: Runs per pixel
- Determines final pixel color
- Receives interpolated data from vertex shader

### Vertex Shader Code
```glsl
#version 330 core
layout (location = 0) in vec3 aPos;    // Input: position
layout (location = 1) in vec3 aColor;  // Input: color

uniform mat4 projection;  // Uniforms: same for all vertices
uniform mat4 view;

out vec3 vertexColor;     // Output: to fragment shader

void main() {
    gl_Position = projection * view * vec4(aPos, 1.0);
    vertexColor = aColor;
}
```

**Matrix Multiplication**:
```
Final Position = Projection × View × World × Vertex
```
- World: Object's position in world (identity for voxels)
- View: Camera transformation
- Projection: Perspective transformation

**Homogeneous Coordinates**: `vec4(aPos, 1.0)`
- 4th component enables translation via matrix multiplication

### Fragment Shader Code
```glsl
#version 330 core
in vec3 vertexColor;   // Interpolated from vertex shader
out vec4 FragColor;    // Output: final pixel color

void main() {
    FragColor = vec4(vertexColor, 1.0);  // 1.0 = fully opaque
}
```

**Rasterization**: Automatically interpolates vertex data across triangle
- 3 vertices with different colors → smooth gradient

### Shader Compilation Process
```java
1. glCreateShader(type)     // Create shader object
2. glShaderSource(id, code) // Upload source code
3. glCompileShader(id)      // Compile to GPU code
4. Check for errors
5. glAttachShader(program, id)
```

### Shader Linking
```java
glLinkProgram(programId)
```
Combines vertex + fragment shaders into complete program

**Why Separate?**
- Modularity: Mix and match shaders
- Different shaders for different materials
- Validation: Ensures outputs match inputs

### Uniform Variables
```java
glGetUniformLocation(programId, "projection")
glUniformMatrix4fv(location, matrix)
```

**Uniforms**: Read-only values from CPU
- Same for all vertices in a draw call
- Efficient for matrices, global state
- Changed per object/frame

---

## Core Graphics Concepts Summary

### 1. **Coordinate Systems**
- **Model Space**: Vertex positions relative to object origin
- **World Space**: Positions in global world coordinates
- **View Space**: Positions relative to camera
- **Clip Space**: After projection, normalized coordinates
- **Screen Space**: Final pixel positions

### 2. **Rendering Pipeline**
```
CPU: Build mesh → Upload to GPU
GPU: Vertex Shader → Rasterization → Fragment Shader → Framebuffer
Display: Swap buffers → Show on screen
```

### 3. **Optimization Techniques**
- **Face Culling**: Don't render back-facing triangles
- **Frustum Culling**: Don't render objects outside view
- **Greedy Meshing**: Combine adjacent faces
- **Batching**: Render similar objects together
- **Static Buffers**: Upload once, reuse many frames

### 4. **Memory Hierarchy**
```
CPU RAM ←→ GPU RAM ←→ GPU Cache ←→ Shader Cores
  (slow)      (fast)     (faster)    (fastest)
```
Minimize CPU↔GPU transfers. Keep data on GPU when possible.

### 5. **Linear Algebra Fundamentals**
- **Vectors**: Direction + magnitude (velocity, position)
- **Matrices**: Transformations (rotation, scale, translation)
- **Dot Product**: Projection, angle between vectors
- **Cross Product**: Perpendicular vector, surface normals
- **Normalization**: Make vector length = 1

---

This architecture provides a solid foundation. Each component is modular and can be enhanced independently!