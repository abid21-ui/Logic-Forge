package com.logicforge.controller;

import java.io.File;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

import com.logicforge.persistence.RecentProjects;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/** Controller for the project launcher and recent-project screen. */
public final class WelcomeController {

    @FXML private Button newProjectButton;
    @FXML private Button newBreadboardButton;
    @FXML private Button openProjectButton;
    @FXML private Button exitButton;
    @FXML private VBox recentProjectsList;
    @FXML private Label recentEmptyLabel;

    private Runnable newProjectAction = () -> { };
    private Runnable newBreadboardAction = () -> { };
    private Consumer<Path> openProjectAction = ignored -> { };
    private Runnable exitAction = Platform::exit;
    private boolean shortcutsInstalled;

    @FXML
    private void initialize() {
        rebuildRecentProjects();
        Platform.runLater(this::installKeyboardShortcuts);
    }

    public void setActions(
            Runnable newProjectAction,
            Runnable newBreadboardAction,
            Consumer<Path> openProjectAction,
            Runnable exitAction) {

        this.newProjectAction = Objects.requireNonNull(newProjectAction);
        this.newBreadboardAction = Objects.requireNonNull(newBreadboardAction);
        this.openProjectAction = Objects.requireNonNull(openProjectAction);
        this.exitAction = Objects.requireNonNull(exitAction);
        rebuildRecentProjects();
    }

    @FXML
    private void handleNewProject() {
        newProjectAction.run();
    }

    @FXML
    private void handleNewBreadboard() {
        newBreadboardAction.run();
    }

    @FXML
    private void handleOpenProject() {
        FileChooser chooser = createProjectChooser();
        File selected = chooser.showOpenDialog(openProjectButton.getScene().getWindow());
        if (selected != null) {
            openProjectAction.accept(selected.toPath());
        }
    }

    @FXML
    private void handleExit() {
        exitAction.run();
    }

    private void rebuildRecentProjects() {
        if (recentProjectsList == null || recentEmptyLabel == null) {
            return;
        }
        recentProjectsList.getChildren().clear();
        var projects = RecentProjects.existingProjects();
        recentEmptyLabel.setVisible(projects.isEmpty());
        recentEmptyLabel.setManaged(projects.isEmpty());

        for (Path project : projects) {
            Label icon = new Label("LGF");
            icon.getStyleClass().add("recent-project-icon");

            Label name = new Label(project.getFileName().toString());
            name.getStyleClass().add("recent-project-name");
            name.setMaxWidth(Double.MAX_VALUE);

            Label location = new Label(project.getParent() == null
                    ? project.toString()
                    : project.getParent().toString());
            location.getStyleClass().add("recent-project-path");
            location.setMaxWidth(Double.MAX_VALUE);

            VBox text = new VBox(2, name, location);
            text.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(text, Priority.ALWAYS);

            Button open = new Button("Open");
            open.getStyleClass().add("recent-open-button");
            open.setOnAction(event -> openProjectAction.accept(project));

            HBox row = new HBox(12, icon, text, open);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("recent-project-row");
            Tooltip.install(row, new Tooltip(project.toString()));
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2) {
                    openProjectAction.accept(project);
                }
            });
            recentProjectsList.getChildren().add(row);
        }
    }

    private void installKeyboardShortcuts() {
        if (shortcutsInstalled || newProjectButton == null) {
            return;
        }
        Scene scene = newProjectButton.getScene();
        if (scene == null) {
            Platform.runLater(this::installKeyboardShortcuts);
            return;
        }
        shortcutsInstalled = true;
        scene.getAccelerators().put(shortcut(KeyCode.N), newProjectButton::fire);
        scene.getAccelerators().put(new KeyCodeCombination(
                KeyCode.N, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN),
                newBreadboardButton::fire);
        scene.getAccelerators().put(shortcut(KeyCode.O), openProjectButton::fire);
        scene.getAccelerators().put(shortcut(KeyCode.Q), exitButton::fire);
    }

    private static KeyCodeCombination shortcut(KeyCode keyCode) {
        return new KeyCodeCombination(keyCode, KeyCombination.SHORTCUT_DOWN);
    }

    private static FileChooser createProjectChooser() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open LogicForge Project");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("LogicForge Project (*.lgf)", "*.lgf"));
        return chooser;
    }
}
