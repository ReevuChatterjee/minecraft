package com.voxelgame;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Stage;

/**
 * JavaFX game launcher — entry point for the Minecraft Clone.
 * Shows a settings/info screen before launching the LWJGL game.
 */
public class GameLauncher {

    /**
     * Main entry point. Uses a non-Application class to avoid
     * JavaFX module-path restrictions on classpath.
     */
    public static void main(String[] args) {
        Application.launch(LauncherApp.class, args);
    }

    // ──────────────────────────────────────────────────────────────────
    // Inner Application class
    // ──────────────────────────────────────────────────────────────────
    public static class LauncherApp extends Application {

        private static int renderDistance = 8;
        private static int fov = 70;

        @Override
        public void start(Stage stage) {
            // Keep JavaFX toolkit alive after launcher closes
            Platform.setImplicitExit(false);

            // ══════════════════════════════════════════════════════════
            //  TOP SECTION — scrollable content
            // ══════════════════════════════════════════════════════════

            VBox content = new VBox(12);
            content.setPadding(new Insets(24, 28, 16, 28));
            content.setAlignment(Pos.TOP_CENTER);

            // ── Title ────────────────────────────────────────────────
            Text title = new Text("MINECRAFT CLONE");
            title.setFont(Font.font("Consolas", FontWeight.BOLD, 36));
            title.setFill(Color.web("#e0e0e0"));
            title.setEffect(new DropShadow(10, Color.web("#00d4ff")));

            Text subtitle = new Text("Voxel Engine  •  Java + LWJGL + OpenGL");
            subtitle.setFont(Font.font("Consolas", FontWeight.NORMAL, 12));
            subtitle.setFill(Color.web("#8899aa"));

            // ── Settings ─────────────────────────────────────────────
            VBox settingsBox = createSection("SETTINGS");

            Label rdLabel = new Label("Render Distance: " + renderDistance + " chunks");
            rdLabel.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 12px;");
            Slider rdSlider = createSlider(2, 16, renderDistance);
            rdSlider.valueProperty().addListener((obs, old, val) -> {
                renderDistance = val.intValue();
                rdLabel.setText("Render Distance: " + renderDistance + " chunks");
            });

            Label fovLabel = new Label("Field of View: " + fov);
            fovLabel.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 12px;");
            Slider fovSlider = createSlider(40, 120, fov);
            fovSlider.valueProperty().addListener((obs, old, val) -> {
                fov = val.intValue();
                fovLabel.setText("Field of View: " + fov);
            });

            settingsBox.getChildren().addAll(rdLabel, rdSlider, fovLabel, fovSlider);

            // ── Controls ─────────────────────────────────────────────
            VBox controlsBox = createSection("CONTROLS");
            String[][] controls = {
                {"WASD", "Move"},
                {"Mouse", "Look around"},
                {"SPACE", "Jump / Fly up"},
                {"SHIFT", "Fly down"},
                {"CTRL", "Sprint"},
                {"F", "Toggle flight"},
                {"L-Click", "Break block"},
                {"R-Click", "Place block"},
                {"1-9", "Select slot"},
                {"Scroll", "Cycle hotbar"},
                {"F5 / F9", "Save / Load"},
                {"ESC", "Quit"}
            };

            GridPane controlGrid = new GridPane();
            controlGrid.setHgap(16);
            controlGrid.setVgap(2);
            for (int i = 0; i < controls.length; i++) {
                Label key = new Label(controls[i][0]);
                key.setStyle("-fx-text-fill: #00d4ff; -fx-font-family: Consolas; -fx-font-size: 11px; -fx-font-weight: bold;");
                key.setMinWidth(90);
                Label action = new Label(controls[i][1]);
                action.setStyle("-fx-text-fill: #aabbcc; -fx-font-size: 11px;");
                controlGrid.add(key, 0, i);
                controlGrid.add(action, 1, i);
            }
            controlsBox.getChildren().add(controlGrid);

            content.getChildren().addAll(title, subtitle, settingsBox, controlsBox);

            // ══════════════════════════════════════════════════════════
            //  BOTTOM BAR — always visible, never scrolled away
            // ══════════════════════════════════════════════════════════

            // Play button
            Button playButton = new Button("PLAY");
            playButton.setMinWidth(200);
            playButton.setMinHeight(50);
            String playStyle =
                "-fx-background-color: linear-gradient(to right, #00b4d8, #0077b6);" +
                "-fx-text-fill: white;" +
                "-fx-font-size: 22px;" +
                "-fx-font-weight: bold;" +
                "-fx-font-family: Consolas;" +
                "-fx-background-radius: 8;" +
                "-fx-cursor: hand;";
            String playHoverStyle =
                "-fx-background-color: linear-gradient(to right, #00d4ff, #0096c7);" +
                "-fx-text-fill: white;" +
                "-fx-font-size: 22px;" +
                "-fx-font-weight: bold;" +
                "-fx-font-family: Consolas;" +
                "-fx-background-radius: 8;" +
                "-fx-cursor: hand;";

            playButton.setStyle(playStyle);
            playButton.setOnMouseEntered(e -> playButton.setStyle(playHoverStyle));
            playButton.setOnMouseExited(e -> playButton.setStyle(playStyle));

            playButton.setOnAction(e -> {
                stage.close();
                launchGame();
            });

            Text footer = new Text("LWJGL 3  |  OpenGL 3.3  |  JavaFX 21  |  JOML");
            footer.setFont(Font.font("Consolas", 10));
            footer.setFill(Color.web("#556677"));

            VBox bottomBar = new VBox(10);
            bottomBar.setAlignment(Pos.CENTER);
            bottomBar.setPadding(new Insets(16, 28, 20, 28));
            bottomBar.setStyle("-fx-background-color: #0d1b2a;");
            bottomBar.getChildren().addAll(playButton, footer);

            // ══════════════════════════════════════════════════════════
            //  ASSEMBLE — content on top, play button pinned to bottom
            // ══════════════════════════════════════════════════════════

            BorderPane root = new BorderPane();
            root.setStyle("-fx-background-color: #1a1a2e;");
            root.setCenter(content);
            root.setBottom(bottomBar);

            Scene scene = new Scene(root, 460, 660);
            stage.setScene(scene);
            stage.setTitle("Minecraft Clone - Launcher");
            stage.setResizable(false);
            stage.show();
        }

        private VBox createSection(String titleText) {
            VBox box = new VBox(6);
            box.setPadding(new Insets(10));
            box.setStyle(
                "-fx-background-color: rgba(255,255,255,0.05);" +
                "-fx-background-radius: 6;" +
                "-fx-border-color: rgba(255,255,255,0.08);" +
                "-fx-border-radius: 6;"
            );
            Label header = new Label(titleText);
            header.setStyle(
                "-fx-text-fill: #e0e0e0; -fx-font-size: 14px; -fx-font-weight: bold; -fx-font-family: Consolas;"
            );
            box.getChildren().add(header);
            return box;
        }

        private Slider createSlider(double min, double max, double value) {
            Slider slider = new Slider(min, max, value);
            slider.setShowTickMarks(true);
            slider.setShowTickLabels(true);
            slider.setMajorTickUnit((max - min) / 4);
            slider.setBlockIncrement(1);
            slider.setSnapToTicks(true);
            return slider;
        }

        private void launchGame() {
            Thread gameThread = new Thread(() -> {
                try {
                    Game game = new Game();
                    game.setRenderDistance(renderDistance);
                    game.setFov(fov);
                    game.run();
                } finally {
                    Platform.exit();
                }
            }, "Game-Thread");
            gameThread.setDaemon(false);
            gameThread.start();
        }
    }
}
