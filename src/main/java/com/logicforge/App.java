package com.logicforge;

import java.io.IOException;
import java.nio.file.Path;

import com.logicforge.controller.BreadboardController;
import com.logicforge.controller.MainController;
import com.logicforge.controller.WelcomeController;
import com.logicforge.persistence.LogicForgeFileCodec;
import com.logicforge.persistence.RecentProjects;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Screen;
import javafx.stage.Stage;

/** JavaFX application entry point. */
public final class App extends Application {

    private static final String APP_TITLE = "LogicForge — Visual Digital Logic Simulator";

    private Stage stage;
    private Scene scene;
    private MainController activeEditor;
    private BreadboardController activeBreadboard;
    private double initialWindowWidth;
    private double initialWindowHeight;

    @Override
    public void start(Stage stage) throws IOException {
        this.stage = stage;

        Rectangle2D visualBounds = Screen.getPrimary().getVisualBounds();
        initialWindowWidth = Math.min(1380, visualBounds.getWidth() * 0.94);
        initialWindowHeight = Math.min(820, visualBounds.getHeight() * 0.90);

        stage.setTitle(APP_TITLE);
        stage.setMinWidth(Math.min(960, visualBounds.getWidth() * 0.85));
        stage.setMinHeight(Math.min(620, visualBounds.getHeight() * 0.80));

        stage.setX(visualBounds.getMinX() + (visualBounds.getWidth() - initialWindowWidth) / 2.0);
        stage.setY(visualBounds.getMinY() + (visualBounds.getHeight() - initialWindowHeight) / 2.0);
        stage.setOnCloseRequest(event -> {
            boolean closeAllowed = activeEditor != null
                    ? activeEditor.confirmCloseRequest()
                    : activeBreadboard == null || activeBreadboard.confirmCloseRequest();
            if (!closeAllowed) {
                event.consume();
            }
        });

        showWelcome();
        stage.show();
    }

    private void showWelcome() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    App.class.getResource("/com/logicforge/welcome-view.fxml"));
            Parent root = loader.load();
            WelcomeController controller = loader.getController();
            controller.setActions(
                    () -> showEditor(null),
                    () -> showBreadboard(null),
                    this::showProject,
                    stage::close);
            activeEditor = null;
            activeBreadboard = null;
            installFreshScene(root);
            stage.setTitle(APP_TITLE);
        } catch (IOException exception) {
            showStartupError("Could not load the LogicForge welcome screen", exception);
        }
    }

    private void showEditor(Path projectPath) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    App.class.getResource("/com/logicforge/main-view.fxml"));
            Parent root = loader.load();
            MainController controller = loader.getController();
            controller.setHomeAction(this::showWelcome);
            controller.setBreadboardAction(() -> showBreadboard(null));
            controller.setProjectOpenAction(this::showProject);
            controller.setDocumentStateListener(this::handleDocumentStateChanged);
            if (projectPath != null) {
                controller.openProject(projectPath);
            } else {
                controller.startNewProject();
            }

            activeEditor = controller;
            activeBreadboard = null;
            installFreshScene(root);
            controller.publishDocumentState();
        } catch (IOException | IllegalArgumentException exception) {
            showStartupError("Could not open the LogicForge project", exception);
            showWelcome();
        }
    }

    private void showBreadboard(Path projectPath) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    App.class.getResource("/com/logicforge/breadboard-view.fxml"));
            Parent root = loader.load();
            BreadboardController controller = loader.getController();
            controller.setHomeAction(this::showWelcome);
            controller.setSchematicAction(() -> showEditor(null));
            controller.setProjectOpenAction(this::showProject);
            controller.setDocumentStateListener(this::handleDocumentStateChanged);
            if (projectPath == null) {
                controller.startNewProject();
            } else {
                controller.openProject(projectPath);
            }
            activeEditor = null;
            activeBreadboard = controller;
            installFreshScene(root);
            controller.publishDocumentState();
        } catch (IOException | IllegalArgumentException exception) {
            showStartupError("Could not open the LogicForge breadboard", exception);
            showWelcome();
        }
    }

    private void showProject(Path projectPath) {
        try {
            String mode = LogicForgeFileCodec.read(projectPath).workspaceMode();
            if ("BREADBOARD".equals(mode)) {
                showBreadboard(projectPath);
            } else {
                showEditor(projectPath);
            }
        } catch (IOException | IllegalArgumentException exception) {
            showStartupError("Could not open the LogicForge project", exception);
        }
    }

    private void handleDocumentStateChanged(Path projectPath, boolean dirty) {
        if (projectPath != null) {
            RecentProjects.remember(projectPath);
        }
        String projectName = projectPath == null
                ? "Untitled Project"
                : projectPath.getFileName().toString();
        String mode = activeBreadboard == null ? "Schematic" : "Breadboard";
        stage.setTitle((dirty ? "*" : "") + projectName
                + " — LogicForge • " + mode);
    }

    private void installFreshScene(Parent root) {
        double width = scene == null ? initialWindowWidth : scene.getWidth();
        double height = scene == null ? initialWindowHeight : scene.getHeight();
        Scene replacement = new Scene(root, width, height);
        replacement.getStylesheets().add(
                App.class.getResource("/com/logicforge/styles.css").toExternalForm());
        ThemeManager.apply(replacement);
        scene = replacement;
        stage.setScene(replacement);
    }

    private void showStartupError(String header, Exception exception) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("LogicForge");
        alert.setHeaderText(header);
        alert.setContentText(exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage());
        if (stage != null && stage.isShowing()) {
            alert.initOwner(stage);
        }
        if (scene != null) {
            alert.getDialogPane().getStylesheets().setAll(scene.getStylesheets());
        }
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
