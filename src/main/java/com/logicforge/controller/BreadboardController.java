package com.logicforge.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.imageio.ImageIO;

import com.logicforge.ThemeManager;
import com.logicforge.model.BreadboardCircuit;
import com.logicforge.model.BreadboardCircuit.Jumper;
import com.logicforge.model.BreadboardCircuit.Occupant;
import com.logicforge.model.BreadboardCircuit.OccupantKind;
import com.logicforge.model.BreadboardCircuit.PlacedIc;
import com.logicforge.model.BreadboardCircuit.Signal;
import com.logicforge.model.BreadboardDemos;
import com.logicforge.model.BreadboardLayout;
import com.logicforge.model.BreadboardLayout.Hole;
import com.logicforge.model.BreadboardLayout.HoleKind;
import com.logicforge.model.IcCatalog;
import com.logicforge.model.IcDefinition;
import com.logicforge.persistence.LogicForgeFileCodec;
import com.logicforge.persistence.LogicForgeProjectFile;
import com.logicforge.persistence.LogicForgeProjectFile.BreadboardData;
import com.logicforge.view.BreadboardIcView;
import com.logicforge.view.BreadboardWireRouter;
import com.logicforge.view.BreadboardWireRouter.RoutedJumper;
import com.logicforge.view.WirePathRenderer;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Scale;
import javafx.stage.FileChooser;
import javafx.util.Duration;

/** Separate, socket-level breadboard workspace for physical 74HC packages. */
public final class BreadboardController {

    private static final double JUMPER_DROP_TOLERANCE = 13;
    private static final double MIN_ZOOM = 0.5;
    private static final double MAX_ZOOM = 2.0;
    private static final double ZOOM_STEP = 0.1;
    private static final int MAX_HISTORY_SIZE = 100;
    private static final String IC_CLIPBOARD_PREFIX = "LOGICFORGE_BREADBOARD_IC:";

    @FXML private VBox icPaletteList;
    @FXML private TextField paletteSearchField;
    @FXML private ScrollPane boardScroll;
    @FXML private Pane zoomContainer;
    @FXML private Pane boardRoot;
    @FXML private Pane boardBackgroundLayer;
    @FXML private Pane jumperLayer;
    @FXML private Pane holeLayer;
    @FXML private Pane controlLayer;
    @FXML private Pane icLayer;
    @FXML private Label statusMessageLabel;
    @FXML private Label boardSizeLabel;
    @FXML private Label icCountLabel;
    @FXML private Label jumperCountLabel;
    @FXML private Label diagnosticLabel;
    @FXML private Label selectedItemLabel;
    @FXML private Label selectedDetailsLabel;
    @FXML private Label zoomLabel;
    @FXML private MenuItem undoItem;
    @FXML private MenuItem redoItem;
    @FXML private MenuItem copyItem;
    @FXML private MenuItem pasteItem;
    @FXML private MenuItem deleteItem;
    @FXML private CheckMenuItem lightThemeItem;

    private BreadboardCircuit circuit = new BreadboardCircuit(BreadboardLayout.Size.HALF);
    private final Map<String, javafx.scene.Node> holeViews = new LinkedHashMap<>();
    private final Map<Integer, BreadboardIcView> icViews = new LinkedHashMap<>();
    private final Map<Integer, javafx.scene.shape.Path> jumperViews = new LinkedHashMap<>();
    private final Deque<BreadboardData> undoHistory = new ArrayDeque<>();
    private final Deque<BreadboardData> redoHistory = new ArrayDeque<>();
    private final Scale boardScale = new Scale(1, 1);
    private Path currentFile;
    private BreadboardData cleanData = circuit.toData();
    private boolean documentDirty;
    private Selection selection = Selection.none();
    private String jumperStartHoleId;
    private javafx.scene.shape.Path jumperPreview;
    private double icDragOffsetX;
    private double icDragOffsetY;
    private double currentZoom = 1.0;
    private Runnable homeAction = () -> { };
    private Runnable schematicAction = () -> { };
    private Consumer<Path> projectOpenAction = ignored -> { };
    private BiConsumer<Path, Boolean> documentStateListener = (path, dirty) -> { };

    @FXML
    private void initialize() {
        lightThemeItem.setSelected(ThemeManager.isLightTheme());
        boardRoot.setManaged(false);
        boardRoot.relocate(0, 0);
        boardRoot.getTransforms().setAll(boardScale);
        paletteSearchField.textProperty().addListener((observable, oldValue, newValue) ->
                rebuildPalette(newValue));
        installBoardDropTarget();
        installBoardZoomGesture();
        rebuildPalette("");
        rebuildBoard();
        Platform.runLater(this::installKeyboardShortcuts);
    }

    public void setHomeAction(Runnable action) {
        homeAction = action == null ? () -> { } : action;
    }

    public void setSchematicAction(Runnable action) {
        schematicAction = action == null ? () -> { } : action;
    }

    public void setProjectOpenAction(Consumer<Path> action) {
        projectOpenAction = action == null ? ignored -> { } : action;
    }

    public void setDocumentStateListener(BiConsumer<Path, Boolean> listener) {
        documentStateListener = listener == null ? (path, dirty) -> { } : listener;
    }

    public void startNewProject() {
        circuit = new BreadboardCircuit(BreadboardLayout.Size.HALF);
        undoHistory.clear();
        redoHistory.clear();
        currentFile = null;
        selection = Selection.none();
        cleanData = circuit.toData();
        documentDirty = false;
        rebuildBoard();
        publishDocumentState();
        Platform.runLater(this::centerBoard);
    }

    public void openProject(Path path) throws IOException {
        LogicForgeProjectFile project = LogicForgeFileCodec.read(path);
        if (!"BREADBOARD".equals(project.workspaceMode())) {
            throw new IllegalArgumentException(
                    "This project belongs to the schematic workspace");
        }
        if (project.formatVersion() < 5
                || project.formatVersion() > LogicForgeProjectFile.CURRENT_FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported breadboard project version");
        }
        circuit = BreadboardCircuit.fromData(project.breadboard());
        undoHistory.clear();
        redoHistory.clear();
        currentFile = path.toAbsolutePath().normalize();
        selection = Selection.none();
        cleanData = circuit.toData();
        documentDirty = false;
        rebuildBoard();
        statusMessageLabel.setText("Opened " + path.getFileName());
        publishDocumentState();
        Platform.runLater(this::centerBoard);
    }

    public void publishDocumentState() {
        documentStateListener.accept(currentFile, documentDirty);
    }

    public boolean confirmCloseRequest() {
        return confirmDiscardChanges("exiting LogicForge");
    }

    private void rebuildPalette(String filter) {
        String query = filter == null ? "" : filter.strip().toLowerCase();
        icPaletteList.getChildren().clear();
        for (IcDefinition definition : IcCatalog.all()) {
            String searchable = (definition.partNumber() + " " + definition.name()
                    + " " + definition.packageType().label()).toLowerCase();
            if (!searchable.contains(query)) {
                continue;
            }
            Label part = new Label(definition.partNumber());
            part.getStyleClass().add("breadboard-palette-part");
            Label name = new Label(definition.name());
            name.setWrapText(true);
            name.getStyleClass().add("breadboard-palette-name");
            Label packageLabel = new Label(definition.packageType().label());
            packageLabel.getStyleClass().add("breadboard-palette-package");
            VBox card = new VBox(3, part, name, packageLabel);
            card.setMaxWidth(Double.MAX_VALUE);
            card.getStyleClass().add("breadboard-palette-card");
            card.setOnDragDetected(event -> {
                Dragboard dragboard = card.startDragAndDrop(TransferMode.COPY);
                ClipboardContent content = new ClipboardContent();
                content.putString("BREADBOARD_IC:" + definition.id());
                dragboard.setContent(content);
                event.consume();
            });
            Tooltip.install(card, new Tooltip(
                    "Drag " + definition.description() + " onto the breadboard"));
            icPaletteList.getChildren().add(card);
        }
        if (icPaletteList.getChildren().isEmpty()) {
            Label empty = new Label("No matching ICs");
            empty.getStyleClass().add("palette-empty");
            icPaletteList.getChildren().add(empty);
        }
    }

    private void installBoardDropTarget() {
        boardRoot.setOnDragOver(event -> {
            String value = event.getDragboard().getString();
            if (value != null && value.startsWith("BREADBOARD_IC:")) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });
        boardRoot.setOnDragDropped(event -> {
            String value = event.getDragboard().getString();
            if (value == null || !value.startsWith("BREADBOARD_IC:")) {
                event.setDropCompleted(false);
                event.consume();
                return;
            }
            IcDefinition definition = IcCatalog.find(value.substring("BREADBOARD_IC:".length()));
            Point2D point = boardRoot.sceneToLocal(event.getSceneX(), event.getSceneY());
            BreadboardData before = circuit.toData();
            BreadboardCircuit.PlacementResult result = definition == null
                    ? BreadboardCircuit.PlacementResult.failure("")
                    : circuit.placeIc(definition, point.getX(), point.getY());
            if (result.succeeded()) {
                selection = Selection.ic(result.placedIc().id());
                recordChange(before);
                markDirty();
                rebuildBoard();
                BreadboardIcView view = icViews.get(result.placedIc().id());
                if (view != null) {
                    animateInsertion(view);
                }
                statusMessageLabel.setText(
                        definition.partNumber() + " snapped into unoccupied sockets");
            } else {
                statusMessageLabel.setText(
                        "Placement blocked • one or more IC sockets are already occupied");
            }
            event.setDropCompleted(result.succeeded());
            event.consume();
        });
    }

    private void rebuildBoard() {
        BreadboardLayout layout = circuit.layout();
        double width = layout.width();
        double height = layout.height();
        for (Pane layer : List.of(
                boardBackgroundLayer, jumperLayer, holeLayer, controlLayer, icLayer)) {
            layer.setPrefSize(width, height);
            layer.setMinSize(width, height);
            layer.setMaxSize(width, height);
        }
        boardRoot.setPrefSize(width, height);
        boardRoot.setMinSize(width, height);
        boardRoot.setMaxSize(width, height);
        resizeZoomContainer();

        boardBackgroundLayer.getChildren().clear();
        jumperLayer.getChildren().clear();
        holeLayer.getChildren().clear();
        controlLayer.getChildren().clear();
        icLayer.getChildren().clear();
        holeViews.clear();
        jumperViews.clear();
        icViews.clear();

        drawBoardShell(layout);
        drawHoles(layout);
        drawControlPanels(layout);
        drawJumpers(layout);
        drawIntegratedCircuits(layout);
        refreshDashboard();
    }

    private void drawBoardShell(BreadboardLayout layout) {
        for (int module = 0; module < layout.size().modules(); module++) {
            double moduleLeft = layout.moduleLeft(module) - 28;
            Rectangle shell = new Rectangle(
                    moduleLeft, layout.boardTop(),
                    18 * BreadboardLayout.HOLE_PITCH + 56,
                    layout.boardBottom() - layout.boardTop());
            shell.setArcWidth(22);
            shell.setArcHeight(22);
            shell.getStyleClass().add("breadboard-shell");

            Rectangle trench = new Rectangle(
                    layout.trenchLeft(module) - BreadboardLayout.HOLE_PITCH / 2.0,
                    layout.boardTop() + 8,
                    layout.trenchWidth(),
                    layout.boardBottom() - layout.boardTop() - 16);
            trench.getStyleClass().add("breadboard-center-trench");
            boardBackgroundLayer.getChildren().addAll(shell, trench);

            double top = layout.hole("M" + module + "R0C0").y() - 12;
            double bottom = layout.hole(
                    "M" + module + "R" + (layout.size().rows() - 1) + "C0").y() + 12;
            for (int column : new int[] {0, 17}) {
                Line positive = new Line(
                        layout.moduleLeft(module) + column * BreadboardLayout.HOLE_PITCH,
                        top,
                        layout.moduleLeft(module) + column * BreadboardLayout.HOLE_PITCH,
                        bottom);
                positive.getStyleClass().addAll("breadboard-rail-line", "positive");
                boardBackgroundLayer.getChildren().add(positive);
            }
            for (int column : new int[] {1, 18}) {
                Line negative = new Line(
                        layout.moduleLeft(module) + column * BreadboardLayout.HOLE_PITCH,
                        top,
                        layout.moduleLeft(module) + column * BreadboardLayout.HOLE_PITCH,
                        bottom);
                negative.getStyleClass().addAll("breadboard-rail-line", "negative");
                boardBackgroundLayer.getChildren().add(negative);
            }
        }
    }

    private void drawHoles(BreadboardLayout layout) {
        for (Hole hole : layout.holes()) {
            if (!hole.kind().isPhysicalSocket()) {
                continue;
            }
            Circle view = new Circle(
                    hole.x(), hole.y(), BreadboardLayout.HOLE_RADIUS);
            view.getStyleClass().add("breadboard-hole");
            if (hole.kind() == HoleKind.POSITIVE_RAIL) {
                view.getStyleClass().add("positive");
            } else if (hole.kind() == HoleKind.NEGATIVE_RAIL) {
                view.getStyleClass().add("negative");
            }
            if (circuit.isOccupied(hole.id())) {
                view.getStyleClass().add("occupied");
            }
            addSignalClass(view, circuit.signalAt(hole.id()));
            addConnectedWireColor(view, hole.id());
            installConnectionPoint(view, hole);
            holeLayer.getChildren().add(view);
            holeViews.put(hole.id(), view);
        }
    }

    private void drawControlPanels(BreadboardLayout layout) {
        double panelLeft = layout.controlLeft();
        Rectangle switchPanel = new Rectangle(
                panelLeft,
                layout.switchPanelTop(),
                layout.switchPanelWidth(),
                layout.switchPanelBottom() - layout.switchPanelTop());
        switchPanel.setArcWidth(18);
        switchPanel.setArcHeight(18);
        switchPanel.getStyleClass().add("breadboard-control-panel");
        controlLayer.getChildren().add(switchPanel);

        Label switchTitle = new Label("16-BIT INPUT SWITCHES");
        switchTitle.relocate(panelLeft + 14, layout.switchPanelTop() + 9);
        switchTitle.getStyleClass().add("breadboard-control-title");
        controlLayer.getChildren().add(switchTitle);

        for (int channel = 0; channel < BreadboardLayout.CONTROL_CHANNEL_COUNT; channel++) {
            double x = layout.controlPinX(channel);
            ToggleButton toggle = new ToggleButton(circuit.switchState(channel) ? "1" : "0");
            toggle.setSelected(circuit.switchState(channel));
            toggle.setPrefSize(20, 34);
            toggle.relocate(x - 10, layout.switchPanelTop() + 43);
            toggle.getStyleClass().add("breadboard-input-switch");
            int selectedChannel = channel;
            toggle.setOnAction(event -> {
                BreadboardData before = circuit.toData();
                circuit.setSwitchState(selectedChannel, toggle.isSelected());
                recordChange(before);
                markDirty();
                rebuildBoard();
                statusMessageLabel.setText(
                        "Switch " + (selectedChannel + 1) + " = "
                                + (toggle.isSelected() ? "HIGH" : "LOW"));
            });
            controlLayer.getChildren().add(toggle);

            Label number = new Label(Integer.toString(channel + 1));
            number.setPrefWidth(22);
            number.setLayoutX(x - 11);
            number.setLayoutY(layout.switchPanelTop() + 28);
            number.getStyleClass().add("breadboard-control-number");
            controlLayer.getChildren().add(number);

            addControlConnectionPoint(layout, layout.hole(layout.switchHoleId(channel)));
        }

        drawPowerSource(layout, layout.hole(layout.vccHoleId()), "VCC", "vcc");
        drawPowerSource(layout, layout.hole(layout.groundHoleId()), "GND", "gnd");

        Rectangle bulbPanel = new Rectangle(
                panelLeft,
                layout.bulbPanelTop(),
                layout.switchPanelWidth(),
                98);
        bulbPanel.setArcWidth(18);
        bulbPanel.setArcHeight(18);
        bulbPanel.getStyleClass().add("breadboard-control-panel");
        controlLayer.getChildren().add(bulbPanel);

        Label bulbTitle = new Label("16-BIT OUTPUT BULBS");
        bulbTitle.relocate(panelLeft + 14, layout.bulbPanelTop() + 8);
        bulbTitle.getStyleClass().add("breadboard-control-title");
        controlLayer.getChildren().add(bulbTitle);

        for (int channel = 0; channel < BreadboardLayout.CONTROL_CHANNEL_COUNT; channel++) {
            Hole hole = layout.hole(layout.bulbHoleId(channel));
            addControlConnectionPoint(layout, hole);
            Signal signal = circuit.bulbSignal(channel);
            Circle bulb = new Circle(hole.x(), layout.bulbPanelTop() + 48, 8);
            bulb.getStyleClass().add("breadboard-output-bulb");
            addSignalClass(bulb, signal);
            addConnectedWireColor(bulb, hole.id());
            if (signal == Signal.HIGH) {
                bulb.getStyleClass().add("on");
            } else if (signal == Signal.CONFLICT) {
                bulb.getStyleClass().add("conflict");
            }
            Tooltip.install(bulb, new Tooltip(
                    "Output " + (channel + 1) + " • " + signal));
            controlLayer.getChildren().add(bulb);

            Label number = new Label(Integer.toString(channel + 1));
            number.setPrefWidth(22);
            number.setLayoutX(hole.x() - 11);
            number.setLayoutY(layout.bulbPanelTop() + 65);
            number.getStyleClass().add("breadboard-control-number");
            controlLayer.getChildren().add(number);
        }
    }

    private void drawPowerSource(
            BreadboardLayout layout,
            Hole hole,
            String text,
            String style) {

        Label label = new Label(text);
        label.setPrefWidth(42);
        label.setLayoutX(hole.x() - 21);
        label.setLayoutY(layout.switchPanelTop() + 53);
        label.getStyleClass().addAll("breadboard-power-label", style);
        controlLayer.getChildren().add(label);

        Line pin = new Line(hole.x(), hole.y() - 18, hole.x(), hole.y());
        pin.getStyleClass().addAll("breadboard-control-lead", "power-source-pin", style);
        if (circuit.isOccupied(hole.id())) {
            pin.getStyleClass().add("occupied");
        }
        addSignalClass(pin, circuit.signalAt(hole.id()));
        addConnectedWireColor(pin, hole.id());
        Rectangle hitTarget = invisibleConnectionTarget(hole);
        installConnectionPoint(hitTarget, hole);
        controlLayer.getChildren().addAll(pin, hitTarget);
        holeViews.put(hole.id(), pin);
    }

    private void addControlConnectionPoint(BreadboardLayout layout, Hole hole) {
        double panelEdgeY = hole.kind() == HoleKind.SWITCH_OUTPUT
                ? layout.switchPanelBottom()
                : layout.bulbPanelTop();
        Line pin = new Line(hole.x(), panelEdgeY, hole.x(), hole.y());
        pin.getStyleClass().add("breadboard-control-lead");
        if (circuit.isOccupied(hole.id())) {
            pin.getStyleClass().add("occupied");
        }
        addSignalClass(pin, circuit.signalAt(hole.id()));
        addConnectedWireColor(pin, hole.id());
        Rectangle hitTarget = invisibleConnectionTarget(hole);
        installConnectionPoint(hitTarget, hole);
        controlLayer.getChildren().addAll(pin, hitTarget);
        holeViews.put(hole.id(), pin);
    }

    private void addConnectedWireColor(javafx.scene.Node view, String holeId) {
        circuit.jumpers().stream()
                .filter(jumper -> jumper.startHoleId().equals(holeId)
                        || jumper.endHoleId().equals(holeId))
                .findFirst()
                .ifPresent(jumper -> view.getStyleClass().add(
                        "wire-color-" + jumper.color().toLowerCase(java.util.Locale.ROOT)));
    }

    private Rectangle invisibleConnectionTarget(Hole hole) {
        Rectangle target = new Rectangle(hole.x() - 11, hole.y() - 11, 22, 22);
        target.getStyleClass().add("breadboard-control-hit-target");
        return target;
    }

    private void installConnectionPoint(javafx.scene.Node view, Hole hole) {
        view.setOnMousePressed(event -> beginJumperDrag(hole, event));
        view.setOnMouseDragged(this::updateJumperPreview);
        view.setOnMouseReleased(this::finishJumperDrag);
        Tooltip.install(view, new Tooltip(holeTooltip(hole)));
    }

    private void drawJumpers(BreadboardLayout layout) {
        for (RoutedJumper routed : BreadboardWireRouter.routeAll(
                circuit.jumpers(), circuit.integratedCircuits(), layout)) {
            Jumper jumper = routed.jumper();
            Hole start = layout.hole(jumper.startHoleId());
            Hole end = layout.hole(jumper.endHoleId());
            javafx.scene.shape.Path path = new javafx.scene.shape.Path();
            WirePathRenderer.apply(path, routed.points(), routed.bridgeCrossings());
            path.getStyleClass().addAll(
                    "breadboard-jumper", "jumper-" + jumper.color().toLowerCase());
            addSignalClass(path, circuit.signalAt(start.id()));
            if (selection.kind() == SelectionKind.JUMPER && selection.id() == jumper.id()) {
                path.getStyleClass().add("selected");
            }
            path.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY) {
                    selection = Selection.jumper(jumper.id());
                    rebuildBoard();
                    event.consume();
                }
            });
            Tooltip.install(path, new Tooltip(
                    "Jumper " + jumper.id() + " • " + start.id() + " → " + end.id()
                            + " • " + circuit.signalAt(start.id())));
            jumperLayer.getChildren().add(path);
            jumperViews.put(jumper.id(), path);
        }
    }

    private void drawIntegratedCircuits(BreadboardLayout layout) {
        for (PlacedIc placed : circuit.integratedCircuits()) {
            BreadboardIcView view = new BreadboardIcView(placed, layout);
            view.setSelectedVisual(
                    selection.kind() == SelectionKind.IC && selection.id() == placed.id());
            installIcInteraction(view);
            icLayer.getChildren().add(view);
            icViews.put(placed.id(), view);
        }
    }

    private void beginJumperDrag(Hole hole, MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        if (circuit.isOccupied(hole.id())) {
            Occupant occupant = circuit.occupant(hole.id());
            selection = occupant.kind() == OccupantKind.IC_PIN
                    ? Selection.ic(occupant.itemId())
                    : Selection.jumper(occupant.itemId());
            rebuildBoard();
            event.consume();
            return;
        }
        jumperStartHoleId = hole.id();
        jumperPreview = new javafx.scene.shape.Path();
        jumperPreview.getStyleClass().addAll("breadboard-jumper", "jumper-preview");
        WirePathRenderer.apply(
                jumperPreview,
                List.of(new Point2D(hole.x(), hole.y()), new Point2D(hole.x(), hole.y())),
                List.of());
        jumperLayer.getChildren().add(jumperPreview);
        event.consume();
    }

    private void updateJumperPreview(MouseEvent event) {
        if (jumperPreview == null) {
            return;
        }
        Point2D point = boardRoot.sceneToLocal(event.getSceneX(), event.getSceneY());
        Hole start = circuit.layout().hole(jumperStartHoleId);
        WirePathRenderer.apply(
                jumperPreview,
                BreadboardWireRouter.preview(
                        new Point2D(start.x(), start.y()), point,
                        circuit.integratedCircuits(), circuit.layout()),
                List.of());
        event.consume();
    }

    private void finishJumperDrag(MouseEvent event) {
        if (jumperPreview == null || jumperStartHoleId == null) {
            return;
        }
        Point2D point = boardRoot.sceneToLocal(event.getSceneX(), event.getSceneY());
        Hole target = circuit.layout().nearestHole(
                point.getX(), point.getY(), JUMPER_DROP_TOLERANCE);
        jumperLayer.getChildren().remove(jumperPreview);
        jumperPreview = null;
        String startId = jumperStartHoleId;
        jumperStartHoleId = null;
        if (target == null) {
            statusMessageLabel.setText("Jumper cancelled • release directly over an empty socket");
            return;
        }
        BreadboardData before = circuit.toData();
        BreadboardCircuit.JumperResult result = circuit.addJumper(startId, target.id());
        if (result.succeeded()) {
            selection = Selection.jumper(result.jumper().id());
            recordChange(before);
            markDirty();
            rebuildBoard();
            statusMessageLabel.setText("Jumper snapped into two sockets");
        } else {
            statusMessageLabel.setText(result.error());
        }
        event.consume();
    }

    private void installIcInteraction(BreadboardIcView view) {
        view.setOnMousePressed(event -> {
            if (event.getButton() != MouseButton.PRIMARY) {
                return;
            }
            selection = Selection.ic(view.getIcId());
            view.setSelectedVisual(true);
            Point2D point = boardRoot.sceneToLocal(event.getSceneX(), event.getSceneY());
            icDragOffsetX = point.getX() - view.getLayoutX();
            icDragOffsetY = point.getY() - view.getLayoutY();
            refreshDashboard();
            event.consume();
        });
        view.setOnMouseDragged(event -> {
            Point2D point = boardRoot.sceneToLocal(event.getSceneX(), event.getSceneY());
            view.relocate(point.getX() - icDragOffsetX, point.getY() - icDragOffsetY);
            event.consume();
        });
        view.setOnMouseReleased(event -> {
            BreadboardData before = circuit.toData();
            double centerX = view.getLayoutX() + view.getWidth() / 2.0;
            double centerY = view.getLayoutY() + view.getHeight() / 2.0;
            boolean moved = circuit.moveIc(view.getIcId(), centerX, centerY);
            if (moved) {
                recordChange(before);
                markDirty();
                statusMessageLabel.setText("IC snapped across the centre trench");
            } else {
                statusMessageLabel.setText(
                        "Move blocked • each IC lead requires an unoccupied socket");
            }
            rebuildBoard();
            event.consume();
        });
    }

    private void refreshDashboard() {
        boardSizeLabel.setText(circuit.layout().size().label());
        icCountLabel.setText(Integer.toString(circuit.integratedCircuits().size()));
        jumperCountLabel.setText(Integer.toString(circuit.jumpers().size()));
        long unpowered = circuit.integratedCircuits().stream()
                .filter(ic -> !ic.isPowered())
                .count();
        if (circuit.conflictCount() > 0) {
            diagnosticLabel.setText(circuit.conflictCount() + " conflicting net(s)");
            diagnosticLabel.getStyleClass().setAll("breadboard-diagnostic", "conflict");
        } else if (unpowered > 0) {
            diagnosticLabel.setText(unpowered + " unpowered IC(s)");
            diagnosticLabel.getStyleClass().setAll("breadboard-diagnostic", "warning");
        } else {
            diagnosticLabel.setText("All nets stable");
            diagnosticLabel.getStyleClass().setAll("breadboard-diagnostic", "stable");
        }

        deleteItem.setDisable(selection.kind() == SelectionKind.NONE);
        undoItem.setDisable(undoHistory.isEmpty());
        redoItem.setDisable(redoHistory.isEmpty());
        copyItem.setDisable(selection.kind() != SelectionKind.IC);
        pasteItem.setDisable(!clipboardDefinitionId().isPresent());
        if (selection.kind() == SelectionKind.IC) {
            PlacedIc selected = circuit.integratedCircuits().stream()
                    .filter(ic -> ic.id() == selection.id())
                    .findFirst().orElse(null);
            if (selected == null) {
                selection = Selection.none();
                refreshDashboard();
                return;
            }
            selectedItemLabel.setText(selected.label());
            selectedDetailsLabel.setText(
                    selected.definition().description()
                            + "\nModule " + (selected.moduleIndex() + 1)
                            + ", rows " + (selected.firstRow() + 1)
                            + "–" + (selected.firstRow()
                                    + selected.definition().packageType().pinCount() / 2)
                            + "\nPower: " + (selected.isPowered() ? "connected" : "missing/incorrect")
                            + "\nPins: one physical lead per occupied socket");
        } else if (selection.kind() == SelectionKind.JUMPER) {
            Jumper selected = circuit.jumpers().stream()
                    .filter(jumper -> jumper.id() == selection.id())
                    .findFirst().orElse(null);
            if (selected == null) {
                selection = Selection.none();
                refreshDashboard();
                return;
            }
            selectedItemLabel.setText("Jumper " + selected.id());
            selectedDetailsLabel.setText(
                    selected.startHoleId() + " → " + selected.endHoleId()
                            + "\nSignal: " + circuit.signalAt(selected.startHoleId())
                            + "\nEach endpoint exclusively occupies its socket.");
        } else {
            selectedItemLabel.setText("No selection");
            selectedDetailsLabel.setText(
                    "Drag from any empty socket to another empty socket to add a jumper. "
                            + "Five terminal holes in each strip are internally connected; "
                            + "red and blue rails are visual bus guides and must be powered explicitly.");
        }
    }

    private String holeTooltip(Hole hole) {
        String strip = switch (hole.kind()) {
            case POSITIVE_RAIL -> "red bus rail (not automatically powered)";
            case NEGATIVE_RAIL -> "blue bus rail (not automatically grounded)";
            case TERMINAL -> "five-socket terminal strip";
            case SWITCH_OUTPUT -> "switch " + (hole.row() + 1) + " output";
            case BULB_INPUT -> "bulb " + (hole.row() + 1) + " input";
            case VCC_SOURCE -> "VCC logic-HIGH source";
            case GND_SOURCE -> "GND logic-LOW source";
        };
        return hole.id() + " • " + strip
                + "\nSignal: " + circuit.signalAt(hole.id())
                + (circuit.isOccupied(hole.id())
                        ? "\nOccupied: " + circuit.occupant(hole.id()).kind()
                        : "\nEmpty • drag from here to create a jumper");
    }

    private static void addSignalClass(javafx.scene.Node node, Signal signal) {
        switch (signal) {
            case HIGH -> node.getStyleClass().add("signal-high");
            case LOW -> node.getStyleClass().add("signal-low");
            case CONFLICT -> node.getStyleClass().add("signal-conflict");
            case FLOATING -> { }
        }
    }

    private void animateInsertion(BreadboardIcView view) {
        view.setOpacity(0.30);
        view.setTranslateY(-32);
        FadeTransition fade = new FadeTransition(Duration.millis(190), view);
        fade.setToValue(1.0);
        TranslateTransition slide = new TranslateTransition(Duration.millis(190), view);
        slide.setToY(0);
        new ParallelTransition(fade, slide).play();
    }

    private void markDirty() {
        boolean dirty = !circuit.toData().equals(cleanData);
        if (documentDirty != dirty) {
            documentDirty = dirty;
            publishDocumentState();
        }
    }

    private void recordChange(BreadboardData before) {
        if (before.equals(circuit.toData())) {
            return;
        }
        undoHistory.addFirst(before);
        while (undoHistory.size() > MAX_HISTORY_SIZE) {
            undoHistory.removeLast();
        }
        redoHistory.clear();
    }

    @FXML
    private void handleUndo() {
        if (undoHistory.isEmpty()) {
            return;
        }
        redoHistory.addFirst(circuit.toData());
        restoreHistory(undoHistory.removeFirst(), "Undo complete");
    }

    @FXML
    private void handleRedo() {
        if (redoHistory.isEmpty()) {
            return;
        }
        undoHistory.addFirst(circuit.toData());
        restoreHistory(redoHistory.removeFirst(), "Redo complete");
    }

    private void restoreHistory(BreadboardData data, String message) {
        circuit = BreadboardCircuit.fromData(data);
        selection = Selection.none();
        markDirty();
        rebuildBoard();
        statusMessageLabel.setText(message);
    }

    @FXML
    private void handleCopy() {
        if (selection.kind() != SelectionKind.IC) {
            return;
        }
        PlacedIc selected = circuit.integratedCircuits().stream()
                .filter(ic -> ic.id() == selection.id())
                .findFirst().orElse(null);
        if (selected == null) {
            return;
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(IC_CLIPBOARD_PREFIX + selected.definition().id());
        Clipboard.getSystemClipboard().setContent(content);
        pasteItem.setDisable(false);
        statusMessageLabel.setText(selected.definition().partNumber() + " copied");
    }

    @FXML
    private void handlePaste() {
        Optional<String> definitionId = clipboardDefinitionId();
        if (definitionId.isEmpty()) {
            statusMessageLabel.setText("Copy an IC before pasting");
            return;
        }
        IcDefinition definition = IcCatalog.find(definitionId.get());
        if (definition == null) {
            statusMessageLabel.setText("The copied IC is not available in this version");
            return;
        }
        BreadboardLayout.IcPlacement placement = firstFreePlacement(definition);
        if (placement == null) {
            statusMessageLabel.setText("No unoccupied row group can fit the copied IC");
            return;
        }
        BreadboardData before = circuit.toData();
        BreadboardCircuit.PlacementResult result = circuit.placeIc(
                definition,
                placement.x() + placement.width() / 2.0,
                placement.y() + placement.height() / 2.0);
        if (!result.succeeded()) {
            statusMessageLabel.setText("Could not paste IC into the selected board");
            return;
        }
        selection = Selection.ic(result.placedIc().id());
        recordChange(before);
        markDirty();
        rebuildBoard();
        statusMessageLabel.setText(definition.partNumber() + " pasted into free sockets");
    }

    private Optional<String> clipboardDefinitionId() {
        String value = Clipboard.getSystemClipboard().getString();
        return value != null && value.startsWith(IC_CLIPBOARD_PREFIX)
                ? Optional.of(value.substring(IC_CLIPBOARD_PREFIX.length()))
                : Optional.empty();
    }

    private BreadboardLayout.IcPlacement firstFreePlacement(IcDefinition definition) {
        BreadboardLayout layout = circuit.layout();
        int pinsPerSide = definition.packageType().pinCount() / 2;
        for (int module = 0; module < layout.size().modules(); module++) {
            for (int row = 0; row <= layout.size().rows() - pinsPerSide; row++) {
                BreadboardLayout.IcPlacement candidate = layout.placement(
                        module, row, definition.packageType().pinCount());
                if (candidate.pinHoleIds().stream().noneMatch(circuit::isOccupied)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    @FXML
    private void handleDeleteSelected() {
        BreadboardData before = circuit.toData();
        boolean removed = switch (selection.kind()) {
            case IC -> circuit.removeIc(selection.id());
            case JUMPER -> circuit.removeJumper(selection.id());
            case NONE -> false;
        };
        if (removed) {
            selection = Selection.none();
            recordChange(before);
            markDirty();
            rebuildBoard();
            statusMessageLabel.setText("Selection removed");
        }
    }

    @FXML
    private void handleClearBoard() {
        if (circuit.isEmpty() || !confirmAction(
                "Clear breadboard?", "All ICs and jumpers will be removed.")) {
            return;
        }
        BreadboardData before = circuit.toData();
        circuit.clear();
        selection = Selection.none();
        recordChange(before);
        markDirty();
        rebuildBoard();
        statusMessageLabel.setText("Breadboard cleared");
    }

    @FXML private void handleMiniBoard() { changeBoardSize(BreadboardLayout.Size.MINI); }
    @FXML private void handleHalfBoard() { changeBoardSize(BreadboardLayout.Size.HALF); }
    @FXML private void handleFullBoard() { changeBoardSize(BreadboardLayout.Size.FULL); }
    @FXML private void handleDoubleBoard() { changeBoardSize(BreadboardLayout.Size.DOUBLE); }
    @FXML private void handleTripleBoard() { changeBoardSize(BreadboardLayout.Size.TRIPLE); }
    @FXML private void handleQuadrupleBoard() { changeBoardSize(BreadboardLayout.Size.QUADRUPLE); }
    @FXML private void handleQuintupleBoard() { changeBoardSize(BreadboardLayout.Size.QUINTUPLE); }

    @FXML
    private void handleLoadFullAdderDemo() {
        loadDemo(
                "the one-bit full-adder demo",
                BreadboardDemos::fullAdder,
                "Full adder loaded • switches 1–3 are A, B, and carry-in");
    }

    @FXML
    private void handleLoadTwosComplementDemo() {
        loadDemo(
                "the two's-complement subtractor demo",
                BreadboardDemos::twosComplementSubtractor,
                "Two's-complement subtractor loaded • A − B = A + NOT(B) + 1");
    }

    private void loadDemo(
            String description,
            Supplier<BreadboardCircuit> factory,
            String message) {

        if (!confirmDiscardChanges("loading " + description)) {
            return;
        }
        BreadboardCircuit demo;
        try {
            demo = factory.get();
        } catch (RuntimeException exception) {
            showError("Could not load breadboard demo", exception.getMessage());
            return;
        }
        circuit = demo;
        undoHistory.clear();
        redoHistory.clear();
        currentFile = null;
        selection = Selection.none();
        cleanData = new BreadboardCircuit(BreadboardLayout.Size.HALF).toData();
        documentDirty = false;
        markDirty();
        rebuildBoard();
        centerBoard();
        statusMessageLabel.setText(message);
    }

    private void changeBoardSize(BreadboardLayout.Size size) {
        if (size == circuit.layout().size()) {
            return;
        }
        if (!circuit.isEmpty() && !confirmAction(
                "Change breadboard size?",
                "Changing the physical board clears its current ICs and jumpers.")) {
            return;
        }
        BreadboardData before = circuit.toData();
        circuit = new BreadboardCircuit(size);
        selection = Selection.none();
        recordChange(before);
        markDirty();
        rebuildBoard();
        centerBoard();
        statusMessageLabel.setText(size.label() + " selected");
    }

    @FXML
    private void handleSave() {
        saveProject();
    }

    private boolean saveProject() {
        Path destination = currentFile;
        if (destination == null) {
            FileChooser chooser = projectChooser("Save Breadboard Project");
            chooser.setInitialFileName("LogicForge-breadboard.lgf");
            File selected = chooser.showSaveDialog(boardRoot.getScene().getWindow());
            if (selected == null) {
                return false;
            }
            destination = selected.toPath();
            if (!destination.getFileName().toString().toLowerCase().endsWith(".lgf")) {
                destination = destination.resolveSibling(destination.getFileName() + ".lgf");
            }
        }
        LogicForgeProjectFile project = new LogicForgeProjectFile(
                LogicForgeProjectFile.CURRENT_FORMAT_VERSION,
                List.of(), List.of(), 1, 1, Map.of(), List.of(),
                "BREADBOARD", circuit.toData());
        try {
            LogicForgeFileCodec.write(destination, project);
            currentFile = destination.toAbsolutePath().normalize();
            cleanData = circuit.toData();
            documentDirty = false;
            publishDocumentState();
            statusMessageLabel.setText("Saved " + destination.getFileName());
            return true;
        } catch (IOException | IllegalArgumentException exception) {
            showError("Could not save breadboard", exception.getMessage());
            return false;
        }
    }

    @FXML
    private void handleOpen() {
        FileChooser chooser = projectChooser("Open LogicForge Project");
        File selected = chooser.showOpenDialog(boardRoot.getScene().getWindow());
        if (selected != null && confirmDiscardChanges("opening another project")) {
            projectOpenAction.accept(selected.toPath());
        }
    }

    @FXML
    private void handleNewBreadboard() {
        if (confirmDiscardChanges("creating a new breadboard")) {
            startNewProject();
        }
    }

    @FXML
    private void handleNewSchematic() {
        if (confirmDiscardChanges("creating a schematic project")) {
            schematicAction.run();
        }
    }

    @FXML
    private void handleHome() {
        if (confirmDiscardChanges("returning home")) {
            homeAction.run();
        }
    }

    @FXML
    private void handleExit() {
        if (confirmCloseRequest()) {
            Platform.exit();
        }
    }

    @FXML
    private void handleToggleLightTheme() {
        ThemeManager.setLightTheme(lightThemeItem.isSelected());
        ThemeManager.apply(boardRoot.getScene());
        statusMessageLabel.setText(lightThemeItem.isSelected()
                ? "Light theme enabled" : "Dark theme enabled");
    }

    @FXML
    private void handleZoomIn() {
        setZoom(currentZoom + ZOOM_STEP);
    }

    @FXML
    private void handleZoomOut() {
        setZoom(currentZoom - ZOOM_STEP);
    }

    @FXML
    private void handleResetZoom() {
        setZoom(1.0);
    }

    private void installBoardZoomGesture() {
        boardScroll.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (!event.isControlDown()) {
                return;
            }
            if (event.getDeltaY() > 0) {
                handleZoomIn();
            } else if (event.getDeltaY() < 0) {
                handleZoomOut();
            }
            event.consume();
        });
    }

    private void setZoom(double requestedZoom) {
        double newZoom = Math.round(
                clamp(requestedZoom, MIN_ZOOM, MAX_ZOOM) * 10.0) / 10.0;
        if (Math.abs(newZoom - currentZoom) < 0.0001) {
            return;
        }

        Bounds viewport = boardScroll.getViewportBounds();
        double boardWidth = circuit.layout().width();
        double boardHeight = circuit.layout().height();
        double logicalCenterX = contentCenter(
                boardWidth * currentZoom,
                viewport.getWidth(), boardScroll.getHvalue()) / currentZoom;
        double logicalCenterY = contentCenter(
                boardHeight * currentZoom,
                viewport.getHeight(), boardScroll.getVvalue()) / currentZoom;

        currentZoom = newZoom;
        boardScale.setX(currentZoom);
        boardScale.setY(currentZoom);
        resizeZoomContainer();
        zoomLabel.setText(String.format("%.0f%%", currentZoom * 100));

        Platform.runLater(() -> {
            Bounds updatedViewport = boardScroll.getViewportBounds();
            boardScroll.setHvalue(scrollValueForCenter(
                    logicalCenterX * currentZoom,
                    circuit.layout().width() * currentZoom,
                    updatedViewport.getWidth()));
            boardScroll.setVvalue(scrollValueForCenter(
                    logicalCenterY * currentZoom,
                    circuit.layout().height() * currentZoom,
                    updatedViewport.getHeight()));
        });
    }

    private void resizeZoomContainer() {
        double scaledWidth = circuit.layout().width() * currentZoom;
        double scaledHeight = circuit.layout().height() * currentZoom;
        zoomContainer.setPrefSize(scaledWidth, scaledHeight);
        zoomContainer.setMinSize(scaledWidth, scaledHeight);
    }

    private static double contentCenter(
            double contentSize,
            double viewportSize,
            double scrollValue) {

        if (contentSize <= viewportSize) {
            return contentSize / 2.0;
        }
        return scrollValue * (contentSize - viewportSize) + viewportSize / 2.0;
    }

    private static double scrollValueForCenter(
            double desiredCenter,
            double contentSize,
            double viewportSize) {

        if (contentSize <= viewportSize) {
            return 0;
        }
        return clamp(
                (desiredCenter - viewportSize / 2.0) / (contentSize - viewportSize),
                0, 1);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    @FXML
    private void handleExportPng() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Breadboard as PNG");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PNG Image", "*.png"));
        chooser.setInitialFileName("LogicForge-breadboard.png");
        File selected = chooser.showSaveDialog(boardRoot.getScene().getWindow());
        if (selected == null) {
            return;
        }
        if (!selected.getName().toLowerCase().endsWith(".png")) {
            selected = new File(selected.getParentFile(), selected.getName() + ".png");
        }
        try {
            SnapshotParameters parameters = new SnapshotParameters();
            parameters.setFill(Color.TRANSPARENT);
            WritableImage image = boardRoot.snapshot(parameters, null);
            ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", selected);
            statusMessageLabel.setText("Exported " + selected.getName());
        } catch (IOException | RuntimeException exception) {
            showError("Could not export breadboard", exception.getMessage());
        }
    }

    private boolean confirmDiscardChanges(String action) {
        if (!documentDirty) {
            return true;
        }
        ButtonType save = new ButtonType("Save", ButtonData.YES);
        ButtonType discard = new ButtonType("Discard", ButtonData.NO);
        ButtonType cancel = new ButtonType("Cancel", ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Unsaved Breadboard Project");
        alert.setHeaderText("Save changes before " + action + "?");
        alert.getButtonTypes().setAll(save, discard, cancel);
        styleDialog(alert);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent()
                && result.get() != cancel
                && (result.get() == discard || saveProject());
    }

    private boolean confirmAction(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("LogicForge Breadboard");
        alert.setHeaderText(header);
        alert.setContentText(message);
        styleDialog(alert);
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    private void showError(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("LogicForge");
        alert.setHeaderText(header);
        alert.setContentText(message == null ? "Unknown error" : message);
        styleDialog(alert);
        alert.showAndWait();
    }

    private void styleDialog(Alert alert) {
        if (boardRoot.getScene() != null) {
            alert.getDialogPane().getStylesheets().setAll(
                    boardRoot.getScene().getStylesheets());
            alert.initOwner(boardRoot.getScene().getWindow());
        }
    }

    private void installKeyboardShortcuts() {
        Scene scene = boardRoot.getScene();
        if (scene == null) {
            Platform.runLater(this::installKeyboardShortcuts);
            return;
        }
        scene.getAccelerators().put(shortcut(KeyCode.N), this::handleNewBreadboard);
        scene.getAccelerators().put(shortcut(KeyCode.O), this::handleOpen);
        scene.getAccelerators().put(shortcut(KeyCode.S), this::handleSave);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.DELETE),
                this::handleDeleteSelected);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.BACK_SPACE),
                this::handleDeleteSelected);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleGlobalKeyPressed);
    }

    private void handleGlobalKeyPressed(KeyEvent event) {
        if (paletteSearchField.isFocused()) {
            return;
        }
        if (event.isShortcutDown() && event.getCode() == KeyCode.Z) {
            handleUndo();
            event.consume();
        } else if (event.isShortcutDown() && event.getCode() == KeyCode.Y) {
            handleRedo();
            event.consume();
        } else if (event.isShortcutDown() && event.getCode() == KeyCode.C) {
            handleCopy();
            event.consume();
        } else if (event.isShortcutDown() && event.getCode() == KeyCode.V) {
            handlePaste();
            event.consume();
        } else if (event.isShortcutDown()
                && (event.getCode() == KeyCode.EQUALS
                        || event.getCode() == KeyCode.PLUS
                        || event.getCode() == KeyCode.ADD)) {
            handleZoomIn();
            event.consume();
        } else if (event.isShortcutDown()
                && (event.getCode() == KeyCode.MINUS
                        || event.getCode() == KeyCode.SUBTRACT)) {
            handleZoomOut();
            event.consume();
        } else if (event.isShortcutDown() && event.getCode() == KeyCode.DIGIT0) {
            handleResetZoom();
            event.consume();
        }
    }

    private static KeyCodeCombination shortcut(KeyCode code) {
        return new KeyCodeCombination(code, KeyCombination.SHORTCUT_DOWN);
    }

    private void centerBoard() {
        boardScroll.setHvalue(0.5);
        boardScroll.setVvalue(0.5);
    }

    private static FileChooser projectChooser(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("LogicForge Project (*.lgf)", "*.lgf"));
        return chooser;
    }

    private enum SelectionKind {
        NONE,
        IC,
        JUMPER
    }

    private record Selection(SelectionKind kind, int id) {
        static Selection none() { return new Selection(SelectionKind.NONE, 0); }
        static Selection ic(int id) { return new Selection(SelectionKind.IC, id); }
        static Selection jumper(int id) { return new Selection(SelectionKind.JUMPER, id); }
    }
}
