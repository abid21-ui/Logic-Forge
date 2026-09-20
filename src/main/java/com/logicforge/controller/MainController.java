package com.logicforge.controller;

import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

import com.logicforge.ThemeManager;
import com.logicforge.model.ComponentConfig;
import com.logicforge.model.ComponentOrientation;
import com.logicforge.model.ComponentType;
import com.logicforge.model.CustomComponentDefinition;
import com.logicforge.model.NativeComponentLogic;
import com.logicforge.model.SequentialComponentLogic;
import com.logicforge.model.CustomComponentDefinition.InternalComponent;
import com.logicforge.model.CustomComponentDefinition.InternalWire;
import com.logicforge.model.CustomComponentDefinition.Port;
import com.logicforge.model.CustomComponentRuntime;
import com.logicforge.model.IcCatalog;
import com.logicforge.model.IcDefinition;
import com.logicforge.persistence.CustomComponentLibrary;
import com.logicforge.persistence.LogicForgeFileCodec;
import com.logicforge.persistence.LogicForgeProjectFile;
import com.logicforge.persistence.LogicForgeProjectFile.ComponentData;
import com.logicforge.persistence.LogicForgeProjectFile.PointData;
import com.logicforge.persistence.LogicForgeProjectFile.WireData;
import com.logicforge.view.CircuitNode;
import com.logicforge.view.CustomBlockPreview;
import com.logicforge.view.OrthogonalWireRouter;
import com.logicforge.view.OrthogonalWireRouter.OccupiedSegment;
import com.logicforge.view.WireConnection;
import com.logicforge.view.WireConnection.EditableSegment;
import com.logicforge.view.WireConnection.SegmentEdit;
import com.logicforge.view.WireCrossingDetector;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
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
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Background;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.image.WritableImage;
import javafx.scene.shape.Line;
import javafx.scene.shape.Path;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Transform;
import javafx.embed.swing.SwingFXUtils;
import javafx.stage.FileChooser;
import javafx.util.Duration;

/** Main UI controller for the LogicForge schematic workspace. */
public final class MainController {

    /* Double both dimensions: four times the usable canvas area. */
    private static final double WORKSPACE_WIDTH = 20000;
    private static final double WORKSPACE_HEIGHT = 14000;
    private static final double GRID_SIZE = 20;
    private static final double PIN_DROP_TOLERANCE = 24;
    private static final double PALETTE_SYMBOL_HEIGHT = 52;
    private static final double MARQUEE_DRAG_THRESHOLD = 4;
    private static final double WIRE_SELECTION_SCREEN_TOLERANCE = 11;
    private static final double FANOUT_FIRST_OFFSET = 34;
    private static final double FANOUT_LANE_STEP = 18;
    private static final double PASTE_OFFSET = 40;
    private static final double WIRE_SEGMENT_GRID_SIZE = 10;
    private static final double MIN_ZOOM = 0.40;
    private static final double MAX_ZOOM = 2.00;
    private static final double ZOOM_STEP = 0.10;
    private static final double SIMULATION_TICK_SECONDS = 0.05;
    private static final int MAX_COMBINATIONAL_SETTLE_PASSES = 128;
    private static final int MAX_SEQUENTIAL_CASCADE_PASSES = 12;
    private static final int MAX_HISTORY_SIZE = 100;
    private static final PseudoClass ACTIVE = PseudoClass.getPseudoClass("active");

    @FXML private GridPane ioPaletteGrid;
    @FXML private GridPane gatePaletteGrid;
    @FXML private GridPane combinationalPaletteGrid;
    @FXML private GridPane sequentialPaletteGrid;
    @FXML private GridPane customPaletteGrid;
    @FXML private TextField paletteSearchField;
    @FXML private TitledPane ioPaletteSection;
    @FXML private TitledPane gatePaletteSection;
    @FXML private TitledPane combinationalPaletteSection;
    @FXML private TitledPane sequentialPaletteSection;
    @FXML private TitledPane customPaletteSection;
    @FXML private Label paletteNoResultsLabel;

    @FXML private ScrollPane componentPalettePanel;
    @FXML private ScrollPane propertiesPanel;
    @FXML private HBox statusBar;

    @FXML private CheckMenuItem paletteViewItem;
    @FXML private CheckMenuItem propertiesViewItem;
    @FXML private CheckMenuItem statusBarViewItem;
    @FXML private CheckMenuItem gridViewItem;
    @FXML private CheckMenuItem lightThemeItem;

    @FXML private ScrollPane workspaceScroll;
    @FXML private Pane zoomContainer;
    @FXML private StackPane workspaceStack;
    @FXML private Pane gridLayer;
    @FXML private Pane wireLayer;
    @FXML private Pane componentLayer;

    @FXML private MenuItem homeButton;
    @FXML private MenuItem newButton;
    @FXML private MenuItem openButton;
    @FXML private MenuItem saveButton;
    @FXML private MenuItem exportImageButton;
    @FXML private MenuItem undoButton;
    @FXML private MenuItem redoButton;
    @FXML private MenuItem copyButton;
    @FXML private MenuItem pasteButton;
    @FXML private MenuItem rotateButton;
    @FXML private MenuItem abstractButton;
    @FXML private MenuItem deleteButton;
    @FXML private MenuItem clearButton;
    @FXML private MenuItem zoomOutButton;
    @FXML private MenuItem zoomInButton;
    @FXML private MenuButton viewsButton;
    @FXML private MenuButton demoMenuButton;
    @FXML private ToggleButton runButton;
    @FXML private MenuItem exitButton;

    @FXML private VBox singleSelectionSection;
    @FXML private VBox multiSelectionSection;
    @FXML private VBox multiSelectionList;
    @FXML private VBox overviewSection;
    @FXML private Label multiSelectionHeading;
    @FXML private Label selectedNameLabel;
    @FXML private Label selectedDescriptionLabel;
    @FXML private Label selectedCustomLabelLabel;
    @FXML private Label selectedIdLabel;
    @FXML private Label selectedStateLabel;
    @FXML private Label selectedConfigLabel;
    @FXML private Label selectedPositionLabel;
    @FXML private Label selectedInputsLabel;
    @FXML private Label selectedOutputsLabel;
    @FXML private Label componentCountLabel;
    @FXML private Label wireCountLabel;
    @FXML private Label simulationStatusLabel;
    @FXML private Label statusMessageLabel;
    @FXML private Label cursorPositionLabel;
    @FXML private Label zoomLabel;

    private final Map<Integer, CircuitNode> nodes = new LinkedHashMap<>();
    private final Map<String, CustomComponentDefinition> customDefinitions = new LinkedHashMap<>();
    private final CustomComponentLibrary customComponentLibrary = new CustomComponentLibrary();
    private final Map<ComponentType, Integer> componentLabelCounters = new EnumMap<>(ComponentType.class);
    private final List<WireConnection> wires = new ArrayList<>();
    private final Set<CircuitNode> selectedNodes = new LinkedHashSet<>();
    private final Set<WireConnection> selectedWires = new LinkedHashSet<>();
    private final Map<CircuitNode, Double> clockAccumulators = new HashMap<>();
    private final Deque<CircuitSnapshot> undoHistory = new ArrayDeque<>();
    private final Deque<CircuitSnapshot> redoHistory = new ArrayDeque<>();
    private final Preferences configurationPreferences = Preferences.userNodeForPackage(MainController.class);

    private int nextId = 1;
    private int nextWireId = 1;
    private CircuitNode primarySelectedNode;
    private WireConnection primarySelectedWire;
    private boolean simulationRunning;
    private boolean unstableFeedbackDetected;
    private boolean restoringSnapshot;
    private CircuitSnapshot pendingGroupMoveSnapshot;
    private ClipboardSnapshot clipboard;
    private int pasteGeneration;
    private java.nio.file.Path currentCircuitFile;
    private CircuitSnapshot cleanSnapshot;
    private boolean documentDirty;
    private Runnable homeAction = () -> { };
    private Runnable breadboardAction = () -> { };
    private Consumer<java.nio.file.Path> projectOpenAction;
    private BiConsumer<java.nio.file.Path, Boolean> documentStateListener =
            (path, dirty) -> { };

    private CircuitNode wireDragSource;
    private int wireDragSourceOutputIndex;
    private Path previewWire;

    private WireConnection segmentDragWire;
    private EditableSegment segmentDragCandidate;
    private SegmentEdit activeSegmentEdit;
    private Point2D segmentDragStartPoint;
    private CircuitSnapshot segmentDragStartSnapshot;

    private Rectangle marqueeRectangle;
    private double marqueeStartX;
    private double marqueeStartY;
    private boolean marqueeActive;

    private final Map<CircuitNode, Point2D> groupDragStartPositions = new LinkedHashMap<>();
    private Point2D groupDragStartPointer;
    private CircuitNode groupDragAnchor;

    private final Scale workspaceScale = new Scale(1.0, 1.0, 0, 0);
    private double currentZoom = 1.0;
    private boolean keyboardShortcutsInstalled;
    private Timeline simulationTimeline;

    @FXML
    private void initialize() {
        prepareWorkspaceLayers();
        drawGrid();
        loadCustomComponentLibrary();
        buildPalette();
        installWorkspaceDropTarget();
        installMarqueeSelection();
        installCursorPositionTracking();
        installWorkspaceZoomGesture();
        buildSimulationTimeline();

        simulationRunning = false;
        runButton.setSelected(false);
        runButton.setText("Play");
        zoomLabel.setText("100%");

        paletteViewItem.setSelected(true);
        propertiesViewItem.setSelected(true);
        statusBarViewItem.setSelected(true);
        gridViewItem.setSelected(true);
        lightThemeItem.setSelected(ThemeManager.isLightTheme());

        applySimulationMode();
        updateEditorActionButtons();
        updateDashboard();
        cleanSnapshot = captureSnapshot();
        documentDirty = false;
        Platform.runLater(() -> {
            installKeyboardShortcuts();
            centerWorkspaceView();
        });
    }

    public void setHomeAction(Runnable homeAction) {
        this.homeAction = homeAction == null ? () -> { } : homeAction;
    }

    public void setBreadboardAction(Runnable breadboardAction) {
        this.breadboardAction = breadboardAction == null ? () -> { } : breadboardAction;
    }

    public void setProjectOpenAction(Consumer<java.nio.file.Path> projectOpenAction) {
        this.projectOpenAction = projectOpenAction;
    }

    public void setDocumentStateListener(
            BiConsumer<java.nio.file.Path, Boolean> documentStateListener) {

        this.documentStateListener = documentStateListener == null
                ? (path, dirty) -> { }
                : documentStateListener;
    }

    /** Initializes an empty, clean project when entering from the welcome screen. */
    public void startNewProject() {
        clearWorkspace(true);
        undoHistory.clear();
        redoHistory.clear();
        currentCircuitFile = null;
        markDocumentClean();
        updateEditorActionButtons();
        Platform.runLater(this::centerWorkspaceView);
    }

    /** Opens a validated .lgf project without showing an editor-owned file chooser. */
    public void openProject(java.nio.file.Path projectPath) throws IOException {
        if (projectPath == null) {
            throw new IllegalArgumentException("Project path is required");
        }
        if (!projectPath.getFileName().toString().toLowerCase().endsWith(".lgf")) {
            throw new IllegalArgumentException("LogicForge project files must use the .lgf extension.");
        }

        LogicForgeProjectFile project = LogicForgeFileCodec.read(projectPath);
        boolean importedDefinitions = false;
        for (CustomComponentDefinition definition : project.customDefinitions()) {
            CustomComponentDefinition previous = customDefinitions.put(definition.id(), definition);
            importedDefinitions |= !definition.equals(previous);
        }
        if (importedDefinitions) {
            saveCustomComponentLibrary();
            filterPalette(paletteSearchField.getText());
        }
        CircuitSnapshot snapshot = fromProjectFile(project);
        undoHistory.clear();
        redoHistory.clear();
        restoreSnapshot(snapshot);
        currentCircuitFile = projectPath.toAbsolutePath().normalize();
        markDocumentClean();
        statusMessageLabel.setText("Opened " + projectPath.getFileName());
        updateEditorActionButtons();
        Platform.runLater(this::centerViewOnCircuit);
    }

    public void publishDocumentState() {
        documentStateListener.accept(currentCircuitFile, documentDirty);
    }

    /** Used by Exit and the native window-close button. */
    public boolean confirmCloseRequest() {
        return confirmDiscardChanges("exiting LogicForge");
    }

    private void prepareWorkspaceLayers() {
        for (Pane layer : List.of(gridLayer, wireLayer, componentLayer)) {
            layer.setPrefSize(WORKSPACE_WIDTH, WORKSPACE_HEIGHT);
            layer.setMinSize(WORKSPACE_WIDTH, WORKSPACE_HEIGHT);
            layer.setMaxSize(WORKSPACE_WIDTH, WORKSPACE_HEIGHT);
        }

        workspaceStack.setPrefSize(WORKSPACE_WIDTH, WORKSPACE_HEIGHT);
        workspaceStack.setMinSize(WORKSPACE_WIDTH, WORKSPACE_HEIGHT);
        workspaceStack.setMaxSize(WORKSPACE_WIDTH, WORKSPACE_HEIGHT);
        workspaceStack.setManaged(false);
        workspaceStack.relocate(0, 0);
        workspaceStack.getTransforms().setAll(workspaceScale);

        resizeZoomContainer();
        gridLayer.setMouseTransparent(true);
        wireLayer.setMouseTransparent(true);
        componentLayer.setPickOnBounds(true);
    }

    private void resizeZoomContainer() {
        double scaledWidth = WORKSPACE_WIDTH * currentZoom;
        double scaledHeight = WORKSPACE_HEIGHT * currentZoom;
        zoomContainer.setPrefSize(scaledWidth, scaledHeight);
        zoomContainer.setMinSize(scaledWidth, scaledHeight);
    }

    private void drawGrid() {
        gridLayer.getChildren().clear();
        gridLayer.setBackground(Background.EMPTY);

        for (double x = 0; x <= WORKSPACE_WIDTH; x += GRID_SIZE) {
            Line line = new Line(x, 0, x, WORKSPACE_HEIGHT);
            line.getStyleClass().add(x % 100 == 0 ? "grid-line-major" : "grid-line-minor");
            gridLayer.getChildren().add(line);
        }

        for (double y = 0; y <= WORKSPACE_HEIGHT; y += GRID_SIZE) {
            Line line = new Line(0, y, WORKSPACE_WIDTH, y);
            line.getStyleClass().add(y % 100 == 0 ? "grid-line-major" : "grid-line-minor");
            gridLayer.getChildren().add(line);
        }
    }

    private void loadCustomComponentLibrary() {
        customDefinitions.clear();
        try {
            customDefinitions.putAll(customComponentLibrary.load());
        } catch (IOException | IllegalArgumentException exception) {
            // A damaged optional user library must not prevent the editor from opening.
            Platform.runLater(() -> showFileError(
                    "Could not load custom components",
                    exception.getMessage()));
        }
    }

    private boolean saveCustomComponentLibrary() {
        try {
            customComponentLibrary.save(customDefinitions.values());
            return true;
        } catch (IOException | IllegalArgumentException exception) {
            showFileError("Could not save custom components", exception.getMessage());
            return false;
        }
    }

    private void requestDeleteCustomDefinition(CustomComponentDefinition definition) {
        if (definition == null || simulationRunning) {
            return;
        }

        boolean usedOnWorkspace = nodes.values().stream().anyMatch(node ->
                node.getType() == ComponentType.CUSTOM
                        && definition.id().equals(node.getCustomDefinitionId()));
        List<String> dependentNames = customDefinitions.values().stream()
                .filter(candidate -> !candidate.id().equals(definition.id()))
                .filter(candidate -> candidate.components().stream().anyMatch(component ->
                        component.type() == ComponentType.CUSTOM
                                && definition.id().equals(component.customDefinitionId())))
                .map(CustomComponentDefinition::name)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        if (usedOnWorkspace || !dependentNames.isEmpty()) {
            Alert blocked = new Alert(Alert.AlertType.WARNING);
            blocked.setTitle("Delete Custom Component");
            blocked.setHeaderText("“" + definition.name() + "” is still in use.");
            String details = usedOnWorkspace
                    ? "Delete every instance from the current workspace first."
                    : "Delete these dependent blocks first: " + String.join(", ", dependentNames);
            if (usedOnWorkspace && !dependentNames.isEmpty()) {
                details += "\nDependent blocks: " + String.join(", ", dependentNames);
            }
            blocked.setContentText(details);
            styleDialog(blocked);
            blocked.showAndWait();
            return;
        }

        ButtonType deleteType = new ButtonType("Delete Block", ButtonData.OK_DONE);
        Alert confirmation = new Alert(
                Alert.AlertType.CONFIRMATION,
                "This removes the block from the Custom Components palette. Existing .lgf files that embed it remain self-contained.",
                deleteType,
                ButtonType.CANCEL);
        confirmation.setTitle("Delete Custom Component");
        confirmation.setHeaderText("Delete “" + definition.name() + "”? This cannot be undone.");
        styleDialog(confirmation);
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != deleteType) {
            return;
        }

        customDefinitions.remove(definition.id());
        if (!saveCustomComponentLibrary()) {
            customDefinitions.put(definition.id(), definition);
            return;
        }
        // Historical or clipboard snapshots may reference a now-deleted definition.
        undoHistory.clear();
        redoHistory.clear();
        clipboard = null;
        filterPalette(paletteSearchField.getText());
        updateEditorActionButtons();
        statusMessageLabel.setText("Deleted custom block “" + definition.name() + "”");
    }

    private void buildPalette() {
        paletteSearchField.textProperty().addListener(
                (observable, previous, current) -> filterPalette(current));
        filterPalette("");
    }

    private void filterPalette(String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.strip().toLowerCase();
        int visibleCount = 0;

        visibleCount += populatePalette(ioPaletteGrid, ioPaletteSection, List.of(
                ComponentType.SWITCH,
                ComponentType.LOGIC_INPUT,
                ComponentType.VCC,
                ComponentType.GROUND,
                ComponentType.JUNCTION,
                ComponentType.BULB,
                ComponentType.LOGIC_OUTPUT,
                ComponentType.SEVEN_SEGMENT_DISPLAY), query);

        visibleCount += populatePalette(gatePaletteGrid, gatePaletteSection, List.of(
                ComponentType.AND,
                ComponentType.NAND,
                ComponentType.OR,
                ComponentType.NOR,
                ComponentType.NOT,
                ComponentType.XOR), query);

        visibleCount += populatePalette(
                combinationalPaletteGrid, combinationalPaletteSection, List.of(
                        ComponentType.MULTIPLEXER,
                        ComponentType.DEMULTIPLEXER,
                        ComponentType.DECODER,
                        ComponentType.ENCODER,
                        ComponentType.BCD_TO_7SEGMENT,
                        ComponentType.HALF_ADDER,
                        ComponentType.FULL_ADDER,
                        ComponentType.COMPARATOR), query);

        visibleCount += populatePalette(
                sequentialPaletteGrid, sequentialPaletteSection, List.of(
                ComponentType.CLOCK,
                        ComponentType.SR_FLIP_FLOP,
                        ComponentType.JK_FLIP_FLOP,
                        ComponentType.D_FLIP_FLOP,
                        ComponentType.T_FLIP_FLOP,
                        ComponentType.COUNTER,
                        ComponentType.SHIFT_REGISTER), query);

        visibleCount += populateCustomPalette(query);

        boolean noResults = visibleCount == 0;
        paletteNoResultsLabel.setVisible(noResults);
        paletteNoResultsLabel.setManaged(noResults);
    }

    private int populatePalette(
            GridPane grid,
            TitledPane section,
            List<ComponentType> types,
            String query) {

        grid.getChildren().clear();
        List<ComponentType> matches = types.stream()
                .filter(type -> matchesPaletteSearch(type, query))
                .toList();
        for (int index = 0; index < matches.size(); index++) {
            Node paletteItem = createPaletteItem(matches.get(index));
            GridPane.setHgrow(paletteItem, Priority.ALWAYS);
            GridPane.setFillWidth(paletteItem, true);
            grid.add(paletteItem, index % 2, index / 2);
        }
        boolean visible = !matches.isEmpty();
        section.setVisible(visible);
        section.setManaged(visible);
        if (visible && !query.isBlank()) {
            section.setExpanded(true);
        }
        return matches.size();
    }

    private int populateCustomPalette(String query) {
        customPaletteGrid.getChildren().clear();
        List<CustomComponentDefinition> matches = customDefinitions.values().stream()
                .filter(definition -> matchesCustomPaletteSearch(definition, query))
                .sorted(Comparator.comparing(CustomComponentDefinition::name,
                        String.CASE_INSENSITIVE_ORDER))
                .toList();
        for (int index = 0; index < matches.size(); index++) {
            Node paletteItem = createCustomPaletteItem(matches.get(index));
            GridPane.setHgrow(paletteItem, Priority.ALWAYS);
            GridPane.setFillWidth(paletteItem, true);
            customPaletteGrid.add(paletteItem, index % 2, index / 2);
        }
        boolean visible = !matches.isEmpty();
        customPaletteSection.setVisible(visible);
        customPaletteSection.setManaged(visible);
        if (visible && query != null && !query.isBlank()) {
            customPaletteSection.setExpanded(true);
        }
        return matches.size();
    }

    private static boolean matchesPaletteSearch(ComponentType type, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String searchable = String.join(" ",
                paletteName(type),
                type.getDisplayName(),
                type.getSymbol(),
                type.getDescription()).toLowerCase();
        return searchable.contains(query);
    }

    private static boolean matchesCustomPaletteSearch(
            CustomComponentDefinition definition,
            String query) {

        if (query == null || query.isBlank()) {
            return true;
        }
        String searchable = String.join(" ",
                definition.name(),
                definition.symbol(),
                definition.description(),
                "custom abstracted reusable block",
                String.join(" ", definition.inputNames()),
                String.join(" ", definition.outputNames())).toLowerCase();
        return searchable.contains(query);
    }

    private Node createPaletteItem(ComponentType type) {
        VBox item = new VBox(3);
        item.setAlignment(Pos.CENTER);
        item.setFillWidth(true);
        item.setMinWidth(0);
        item.setMaxWidth(Double.MAX_VALUE);
        item.setMinHeight(88);
        item.setPrefHeight(88);
        item.getStyleClass().add("palette-item");

        Pane symbolHost = new Pane();
        symbolHost.setMinWidth(0);
        symbolHost.setMaxWidth(Double.MAX_VALUE);
        symbolHost.setMinHeight(PALETTE_SYMBOL_HEIGHT);
        symbolHost.setPrefHeight(PALETTE_SYMBOL_HEIGHT);
        symbolHost.setMaxHeight(PALETTE_SYMBOL_HEIGHT);
        symbolHost.getStyleClass().add("palette-symbol-host");

        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(symbolHost.widthProperty());
        clip.heightProperty().bind(symbolHost.heightProperty());
        symbolHost.setClip(clip);

        CircuitNode preview = new CircuitNode(-1, type, defaultConfigFor(type));
        // Workspace components show pin names; palette icons remain intentionally text-free.
        preview.setPinLabelsVisible(false);
        preview.setManaged(false);
        preview.setMouseTransparent(true);
        preview.getStyleClass().add("palette-preview-node");
        double previewScale = type == ComponentType.JUNCTION
                ? 1.0
                : Math.min(
                        0.46,
                        Math.min(108.0 / preview.getNodeWidth(), 48.0 / preview.getNodeHeight()));
        preview.getTransforms().add(new Scale(previewScale, previewScale, 0, 0));
        preview.layoutXProperty().bind(
                symbolHost.widthProperty()
                        .subtract(preview.getNodeWidth() * previewScale)
                        .divide(2.0));
        preview.setLayoutY(Math.max(
                0,
                (PALETTE_SYMBOL_HEIGHT - preview.getNodeHeight() * previewScale) / 2.0));
        symbolHost.getChildren().add(preview);

        Label nameLabel = new Label(paletteName(type));
        nameLabel.setWrapText(true);
        nameLabel.setMaxWidth(Double.MAX_VALUE);
        nameLabel.setAlignment(Pos.CENTER);
        nameLabel.getStyleClass().add("palette-title");

        item.getChildren().addAll(symbolHost, nameLabel);
        installPaletteDragSource(item, type);
        return item;
    }

    private Node createCustomPaletteItem(CustomComponentDefinition definition) {
        VBox item = new VBox(3);
        item.setAlignment(Pos.CENTER);
        item.setFillWidth(true);
        item.setMinWidth(0);
        item.setMaxWidth(Double.MAX_VALUE);
        item.setMinHeight(definition.description().isBlank() ? 88 : 108);
        item.setPrefHeight(definition.description().isBlank() ? 88 : 108);
        item.getStyleClass().addAll("palette-item", "custom-palette-item");

        Pane symbolHost = new Pane();
        symbolHost.setMinWidth(0);
        symbolHost.setMaxWidth(Double.MAX_VALUE);
        symbolHost.setMinHeight(PALETTE_SYMBOL_HEIGHT);
        symbolHost.setPrefHeight(PALETTE_SYMBOL_HEIGHT);
        symbolHost.setMaxHeight(PALETTE_SYMBOL_HEIGHT);
        symbolHost.getStyleClass().add("palette-symbol-host");

        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(symbolHost.widthProperty());
        clip.heightProperty().bind(symbolHost.heightProperty());
        symbolHost.setClip(clip);

        CircuitNode preview = new CircuitNode(
                -1,
                ComponentType.CUSTOM,
                ComponentConfig.defaults(),
                definition,
                customDefinitions::get);
        preview.setPinLabelsVisible(false);
        preview.setManaged(false);
        preview.setMouseTransparent(true);
        preview.getStyleClass().add("palette-preview-node");
        double previewScale = Math.min(
                0.46,
                Math.min(108.0 / preview.getNodeWidth(), 48.0 / preview.getNodeHeight()));
        preview.getTransforms().add(new Scale(previewScale, previewScale, 0, 0));
        preview.layoutXProperty().bind(
                symbolHost.widthProperty()
                        .subtract(preview.getNodeWidth() * previewScale)
                        .divide(2.0));
        preview.setLayoutY(Math.max(
                0,
                (PALETTE_SYMBOL_HEIGHT - preview.getNodeHeight() * previewScale) / 2.0));
        symbolHost.getChildren().add(preview);

        Label nameLabel = new Label(definition.name());
        nameLabel.setWrapText(true);
        nameLabel.setMaxWidth(Double.MAX_VALUE);
        nameLabel.setAlignment(Pos.CENTER);
        nameLabel.getStyleClass().add("palette-title");
        item.getChildren().addAll(symbolHost, nameLabel);
        if (!definition.description().isBlank()) {
            Label descriptionLabel = new Label(definition.description());
            descriptionLabel.setWrapText(true);
            descriptionLabel.setMaxWidth(Double.MAX_VALUE);
            descriptionLabel.setMaxHeight(30);
            descriptionLabel.setAlignment(Pos.TOP_CENTER);
            descriptionLabel.getStyleClass().add("custom-palette-description");
            item.getChildren().add(descriptionLabel);
        }
        Tooltip.install(item, new Tooltip(definition.description().isBlank()
                ? definition.name()
                : definition.name() + "\n" + definition.description()));
        installCustomPaletteDragSource(item, definition.id());

        Button removeButton = new Button("\u00d7");
        removeButton.setFocusTraversable(false);
        removeButton.getStyleClass().add("custom-block-delete");
        removeButton.setTooltip(new Tooltip("Delete saved custom block"));
        removeButton.setOnAction(event -> {
            requestDeleteCustomDefinition(definition);
            event.consume();
        });
        StackPane.setAlignment(removeButton, Pos.TOP_RIGHT);

        StackPane card = new StackPane(item, removeButton);
        card.setMinWidth(0);
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private static ComponentConfig defaultConfigFor(ComponentType type) {
        if (type == ComponentType.CLOCK) {
            return ComponentConfig.clock(1.0);
        }
        return ComponentConfig.bits(type.isVariableInputGate() ? 2 : 1);
    }

    private static String paletteName(ComponentType type) {
        return switch (type) {
            case SWITCH -> "Switch";
            case LOGIC_INPUT -> "Logic Input";
            case VCC -> "VCC";
            case GROUND -> "Ground";
            case JUNCTION -> "Junction";
            case BULB -> "Bulb";
            case LOGIC_OUTPUT -> "Logic Output";
            case SEVEN_SEGMENT_DISPLAY -> "7-Segment";
            case AND -> "AND";
            case NAND -> "NAND";
            case OR -> "OR";
            case NOR -> "NOR";
            case NOT -> "NOT";
            case XOR -> "XOR";
            case MULTIPLEXER -> "MUX";
            case DEMULTIPLEXER -> "DEMUX";
            case DECODER -> "Decoder";
            case ENCODER -> "Encoder";
            case BCD_TO_7SEGMENT -> "BCD → 7-Seg";
            case HALF_ADDER -> "Half Adder";
            case FULL_ADDER -> "Full Adder";
            case COMPARATOR -> "Comparator";
            case CLOCK -> "Clock";
            case SR_FLIP_FLOP -> "SR FF";
            case JK_FLIP_FLOP -> "JK FF";
            case D_FLIP_FLOP -> "D FF";
            case T_FLIP_FLOP -> "T FF";
            case COUNTER -> "Counter";
            case SHIFT_REGISTER -> "Shift Register";
            case INTEGRATED_CIRCUIT -> "Integrated Circuit";
            case CUSTOM -> "Custom Block";
        };
    }

    private void installPaletteDragSource(Node paletteItem, ComponentType type) {
        paletteItem.setOnDragDetected(event -> {
            if (simulationRunning) {
                event.consume();
                return;
            }
            Dragboard dragboard = paletteItem.startDragAndDrop(TransferMode.COPY);
            ClipboardContent content = new ClipboardContent();
            content.putString(type.name());
            dragboard.setContent(content);
            event.consume();
        });
    }

    private void installCustomPaletteDragSource(Node paletteItem, String definitionId) {
        paletteItem.setOnDragDetected(event -> {
            if (simulationRunning) {
                event.consume();
                return;
            }
            Dragboard dragboard = paletteItem.startDragAndDrop(TransferMode.COPY);
            ClipboardContent content = new ClipboardContent();
            content.putString("CUSTOM:" + definitionId);
            dragboard.setContent(content);
            event.consume();
        });
    }

    private void installWorkspaceDropTarget() {
        componentLayer.setOnDragOver(event -> {
            if (!simulationRunning
                    && event.getGestureSource() != componentLayer
                    && event.getDragboard().hasString()
                    && parsePaletteSelection(event.getDragboard().getString()).isPresent()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });

        componentLayer.setOnDragDropped(event -> {
            if (simulationRunning) {
                event.setDropCompleted(false);
                event.consume();
                return;
            }

            Optional<PaletteSelection> selection = parsePaletteSelection(
                    event.getDragboard().getString());
            if (selection.isEmpty()) {
                event.setDropCompleted(false);
                event.consume();
                return;
            }

            Optional<ComponentConfig> config = chooseConfiguration(selection.get().type());
            if (config.isEmpty()) {
                event.setDropCompleted(false);
                event.consume();
                return;
            }

            Point2D point = componentLayer.sceneToLocal(event.getSceneX(), event.getSceneY());
            rememberCurrentState();
            CircuitNode node;
            if (selection.get().type() == ComponentType.CUSTOM) {
                CustomComponentDefinition definition = customDefinitions.get(
                        selection.get().customDefinitionId());
                if (definition == null) {
                    event.setDropCompleted(false);
                    event.consume();
                    return;
                }
                node = addCustomComponent(definition, point.getX(), point.getY());
            } else if (selection.get().type() == ComponentType.INTEGRATED_CIRCUIT) {
                IcDefinition definition = IcCatalog.find(selection.get().icDefinitionId());
                if (definition == null) {
                    event.setDropCompleted(false);
                    event.consume();
                    return;
                }
                node = addIntegratedCircuit(definition, point.getX(), point.getY());
            } else {
                node = addComponent(
                        selection.get().type(), config.get(), point.getX(), point.getY());
            }
            node.relocate(
                    snapAndClamp(point.getX() - node.getNodeWidth() / 2.0,
                            WORKSPACE_WIDTH - node.getNodeWidth()),
                    snapAndClamp(point.getY() - node.getNodeHeight() / 2.0,
                            WORKSPACE_HEIGHT - node.getNodeHeight()));
            node.notifyPositionChanged();
            event.setDropCompleted(true);
            event.consume();
        });
    }

    private Optional<ComponentConfig> chooseConfiguration(ComponentType type) {
        if (!type.isConfigurable()) {
            return Optional.of(defaultConfigFor(type));
        }

        if (type == ComponentType.CLOCK) {
            List<Double> frequencies = List.of(0.5, 1.0, 2.0, 4.0);
            ChoiceDialog<Double> dialog = new ChoiceDialog<>(1.0, frequencies);
            dialog.setTitle("Configure Clock");
            dialog.setHeaderText("Select the clock frequency");
            dialog.setContentText("Frequency (Hz):");
            styleDialog(dialog);
            return dialog.showAndWait().map(ComponentConfig::clock);
        }

        int minimum = type.isVariableInputGate() ? 2 : 1;
        List<Integer> choices = type.isVariableInputGate()
                ? List.of(2, 3, 4)
                : List.of(1, 2, 3, 4);
        int remembered = configurationPreferences.getInt(
                "last-width-" + type.name().toLowerCase(),
                minimum);
        int initial = Math.max(minimum, Math.min(4, remembered));
        ChoiceDialog<Integer> dialog = new ChoiceDialog<>(initial, choices);
        dialog.setTitle("Configure " + type.getDisplayName());
        dialog.setHeaderText(configurationHeader(type));
        dialog.setContentText(type.isVariableInputGate() ? "Number of inputs:" : "Bit width:");
        styleDialog(dialog);
        return dialog.showAndWait().map(value -> {
            configurationPreferences.putInt(
                    "last-width-" + type.name().toLowerCase(), value);
            return ComponentConfig.bits(value);
        });
    }

    private static String configurationHeader(ComponentType type) {
        return switch (type) {
            case MULTIPLEXER -> "1–4 select bits provide 2–16 data inputs";
            case DEMULTIPLEXER -> "1–4 select bits route X to 2–16 outputs";
            case DECODER -> "1–4 input bits provide 2–16 decoded outputs";
            case ENCODER -> "2–16 input lines produce a 1–4 bit code";
            case HALF_ADDER -> "Choose a 1–4 bit adder without carry-in";
            case FULL_ADDER -> "Choose a 1–4 bit ripple-carry adder";
            case COMPARATOR -> "Choose a 1–4 bit unsigned comparator";
            case COUNTER -> "Choose a 1–4 bit rising-edge counter";
            case SHIFT_REGISTER -> "Choose a 1–4 bit serial shift register";
            case BCD_TO_7SEGMENT -> "Four BCD bits drive active-high A–G outputs";
            case AND, NAND, OR, NOR, XOR -> "Choose 2, 3, or 4 gate inputs";
            default -> type.getDisplayName();
        };
    }

    /** Gives every editor-owned dialog the same readable dark/light palette as the workspace. */
    private void styleDialog(Dialog<?> dialog) {
        if (dialog == null || componentLayer.getScene() == null) {
            return;
        }
        dialog.initOwner(componentLayer.getScene().getWindow());
        dialog.getDialogPane().getStylesheets().setAll(
                componentLayer.getScene().getStylesheets());
    }

    private Optional<PaletteSelection> parsePaletteSelection(String rawType) {
        if (rawType != null && rawType.startsWith("CUSTOM:")) {
            String definitionId = rawType.substring("CUSTOM:".length());
            return customDefinitions.containsKey(definitionId)
                    ? Optional.of(new PaletteSelection(ComponentType.CUSTOM, definitionId, ""))
                    : Optional.empty();
        }
        if (rawType != null && rawType.startsWith("IC:")) {
            String definitionId = rawType.substring("IC:".length());
            return IcCatalog.find(definitionId) != null
                    ? Optional.of(new PaletteSelection(
                            ComponentType.INTEGRATED_CIRCUIT, "", definitionId))
                    : Optional.empty();
        }
        try {
            ComponentType type = ComponentType.valueOf(rawType);
            return type == ComponentType.CUSTOM || type == ComponentType.INTEGRATED_CIRCUIT
                    ? Optional.empty()
                    : Optional.of(new PaletteSelection(type, "", ""));
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return Optional.empty();
        }
    }

    private CircuitNode addComponent(ComponentType type, double x, double y) {
        return addComponent(type, defaultConfigFor(type), x, y);
    }

    private CircuitNode addComponent(
            ComponentType type,
            ComponentConfig config,
            double x,
            double y) {

        return addComponent(type, config, null, null, x, y);
    }

    private CircuitNode addCustomComponent(
            CustomComponentDefinition definition,
            double x,
            double y) {

        return addComponent(
                ComponentType.CUSTOM,
                ComponentConfig.defaults(),
                definition,
                null,
                x,
                y);
    }

    private CircuitNode addIntegratedCircuit(IcDefinition definition, double x, double y) {
        return addComponent(
                ComponentType.INTEGRATED_CIRCUIT,
                ComponentConfig.defaults(),
                null,
                definition,
                x,
                y);
    }

    private CircuitNode addComponent(
            ComponentType type,
            ComponentConfig config,
            CustomComponentDefinition customDefinition,
            IcDefinition icDefinition,
            double x,
            double y) {

        CircuitNode node = new CircuitNode(
                nextId++, type, config, customDefinition, customDefinitions::get, icDefinition);
        String labelPrefix = type == ComponentType.CUSTOM
                ? customDefinition.symbol().replaceAll("[^A-Za-z0-9]", "")
                : type == ComponentType.INTEGRATED_CIRCUIT
                        ? icDefinition.partNumber()
                        : type.defaultLabelPrefix();
        if (labelPrefix.isBlank()) {
            labelPrefix = type.defaultLabelPrefix();
        }
        node.setUserLabel(type == ComponentType.JUNCTION
                ? ""
                : labelPrefix + nextLabelNumber(type));
        if (type == ComponentType.JUNCTION) {
            nextLabelNumber(type);
        }
        node.relocate(
                snapAndClamp(x, WORKSPACE_WIDTH - node.getNodeWidth()),
                snapAndClamp(y, WORKSPACE_HEIGHT - node.getNodeHeight()));

        node.setSelectionHandler(this::handleNodeSelectionRequest);
        node.setLabelEditHandler(this::editComponentLabel);
        node.setMoveHandler(new CircuitNode.MoveHandler() {
            @Override
            public void onStarted(CircuitNode moved, double sceneX, double sceneY) {
                beginGroupMove(moved, sceneX, sceneY);
            }

            @Override
            public void onDragged(CircuitNode moved, double sceneX, double sceneY) {
                updateGroupMove(moved, sceneX, sceneY);
            }

            @Override
            public void onFinished(CircuitNode moved) {
                finishGroupMove(moved);
            }
        });
        node.setPositionChangedHandler(moved -> {
            if (groupDragStartPositions.isEmpty()) {
                updateAllWireGeometry();
            } else {
                updateConnectedWireGeometry(selectedNodes);
            }
            if (moved == primarySelectedNode) {
                updatePropertiesPanel();
            }
        });
        node.setSwitchChangedHandler(changed -> {
            if (simulationRunning) {
                evaluateCircuit();
            } else {
                evaluatePausedNetwork();
            }
        });
        node.setSwitchChangeStartedHandler(changed -> rememberCurrentState());
        node.setWireDragHandler(new CircuitNode.WireDragHandler() {
            @Override
            public void onStarted(
                    CircuitNode source,
                    int outputIndex,
                    double sceneX,
                    double sceneY) {
                beginWireDrag(source, outputIndex, sceneX, sceneY);
            }

            @Override
            public void onDragged(
                    CircuitNode source,
                    int outputIndex,
                    double sceneX,
                    double sceneY) {
                updateWireDrag(sceneX, sceneY);
            }

            @Override
            public void onReleased(
                    CircuitNode source,
                    int outputIndex,
                    double sceneX,
                    double sceneY) {
                finishWireDrag(source, outputIndex, sceneX, sceneY);
            }
        });

        node.setEditingLocked(simulationRunning);
        nodes.put(node.getComponentId(), node);
        componentLayer.getChildren().add(node);
        if (type == ComponentType.CLOCK) {
            clockAccumulators.put(node, 0.0);
        }
        selectOnlyNode(node);
        updateDashboard();
        return node;
    }

    private int nextLabelNumber(ComponentType type) {
        return componentLabelCounters.merge(type, 1, Integer::sum);
    }

    private void editComponentLabel(CircuitNode node) {
        if (node == null || simulationRunning) {
            return;
        }

        TextInputDialog dialog = new TextInputDialog(node.getUserLabel());
        dialog.setTitle("Component Label");
        dialog.setHeaderText("Rename " + node.getType().getDisplayName());
        dialog.setContentText("Label (may be empty):");
        styleDialog(dialog);

        dialog.showAndWait().ifPresent(label -> {
            String normalized = label == null ? "" : label.strip();
            if (!normalized.equals(node.getUserLabel())) {
                rememberCurrentState();
                node.setUserLabel(normalized);
                updatePropertiesPanel();
                updateEditorActionButtons();
            }
        });
    }

    private static double snapAndClamp(double value, double max) {
        double snapped = Math.round(value / GRID_SIZE) * GRID_SIZE;
        return Math.max(0, Math.min(max, snapped));
    }

    private void installMarqueeSelection() {
        componentLayer.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
            if ((event.getButton() != MouseButton.PRIMARY
                    && event.getButton() != MouseButton.SECONDARY)
                    || event.getTarget() != componentLayer) {
                return;
            }

            cancelWirePreview();
            Point2D point = componentLayer.sceneToLocal(event.getSceneX(), event.getSceneY());
            WireConnection clickedWire = findNearestWire(point);
            if (clickedWire != null) {
                if (event.getButton() == MouseButton.SECONDARY) {
                    if (clickedWire.getAdjustedBendCount() > 0 && !simulationRunning) {
                        rememberCurrentState();
                        clickedWire.resetBendAnchors();
                        updateAllWireGeometry();
                        updatePropertiesPanel();
                        statusMessageLabel.setText("Wire returned to automatic routing");
                    }
                    event.consume();
                    return;
                }
                handleWireSelectionRequest(clickedWire, event.isControlDown());
                if (!event.isControlDown() && !simulationRunning && selectedWires.size() == 1) {
                    double tolerance = WIRE_SELECTION_SCREEN_TOLERANCE * 1.6 / currentZoom;
                    EditableSegment editable = clickedWire.findEditableSegment(point, tolerance);
                    if (editable != null) {
                        segmentDragWire = clickedWire;
                        segmentDragCandidate = editable;
                        segmentDragStartPoint = point;
                        componentLayer.setCursor(
                                editable.horizontal() ? Cursor.V_RESIZE : Cursor.H_RESIZE);
                    }
                }
                event.consume();
                return;
            }

            if (event.getButton() != MouseButton.PRIMARY) {
                return;
            }

            marqueeStartX = point.getX();
            marqueeStartY = point.getY();
            marqueeActive = false;
            if (!event.isControlDown()) {
                clearSelection();
            }

            removeMarqueeRectangle();
            marqueeRectangle = new Rectangle(marqueeStartX, marqueeStartY, 0, 0);
            marqueeRectangle.setManaged(false);
            marqueeRectangle.setMouseTransparent(true);
            marqueeRectangle.getStyleClass().add("selection-marquee");
            componentLayer.getChildren().add(marqueeRectangle);
            marqueeRectangle.toFront();
            event.consume();
        });

        componentLayer.addEventHandler(MouseEvent.MOUSE_DRAGGED, event -> {
            if (!event.isPrimaryButtonDown()) {
                return;
            }

            Point2D point = componentLayer.sceneToLocal(event.getSceneX(), event.getSceneY());
            if (segmentDragWire != null
                    && segmentDragCandidate != null
                    && segmentDragStartPoint != null) {
                if (activeSegmentEdit == null
                        && point.distance(segmentDragStartPoint) < MARQUEE_DRAG_THRESHOLD) {
                    event.consume();
                    return;
                }

                double coordinate = segmentDragCandidate.horizontal()
                        ? snapWireSegment(point.getY(), WORKSPACE_HEIGHT)
                        : snapWireSegment(point.getX(), WORKSPACE_WIDTH);
                Point2D first = segmentDragCandidate.horizontal()
                        ? new Point2D(segmentDragCandidate.start().getX(), coordinate)
                        : new Point2D(coordinate, segmentDragCandidate.start().getY());
                Point2D second = segmentDragCandidate.horizontal()
                        ? new Point2D(segmentDragCandidate.end().getX(), coordinate)
                        : new Point2D(coordinate, segmentDragCandidate.end().getY());
                if (isWireEditPointBlocked(first) || isWireEditPointBlocked(second)) {
                    event.consume();
                    return;
                }

                if (activeSegmentEdit == null) {
                    segmentDragStartSnapshot = captureSnapshot();
                    activeSegmentEdit = segmentDragWire.beginSegmentEdit(segmentDragCandidate);
                    if (activeSegmentEdit == null) {
                        cancelSegmentDrag();
                        event.consume();
                        return;
                    }
                }
                segmentDragWire.moveSegment(activeSegmentEdit, coordinate);
                updateAllWireGeometry();
                updatePropertiesPanel();
                statusMessageLabel.setText("Adjusting wire section");
                event.consume();
                return;
            }

            if (marqueeRectangle == null) {
                return;
            }

            double width = Math.abs(point.getX() - marqueeStartX);
            double height = Math.abs(point.getY() - marqueeStartY);
            marqueeActive = marqueeActive
                    || Math.hypot(width, height) >= MARQUEE_DRAG_THRESHOLD;

            marqueeRectangle.setX(Math.min(marqueeStartX, point.getX()));
            marqueeRectangle.setY(Math.min(marqueeStartY, point.getY()));
            marqueeRectangle.setWidth(width);
            marqueeRectangle.setHeight(height);
            marqueeRectangle.toFront();
            event.consume();
        });

        componentLayer.addEventHandler(MouseEvent.MOUSE_RELEASED, event -> {
            if (event.getButton() != MouseButton.PRIMARY) {
                return;
            }

            if (segmentDragWire != null) {
                CircuitSnapshot beforeEdit = segmentDragStartSnapshot;
                if (beforeEdit != null && !beforeEdit.equals(captureSnapshot())) {
                    pushUndoSnapshot(beforeEdit);
                    refreshDocumentDirty();
                    statusMessageLabel.setText(
                            "Wire section adjusted • right-click the wire to restore automatic routing");
                }
                cancelSegmentDrag();
                event.consume();
                return;
            }

            if (marqueeRectangle == null) {
                return;
            }

            if (marqueeActive) {
                Bounds selectionBounds = marqueeRectangle.getBoundsInParent();
                for (CircuitNode node : nodes.values()) {
                    if (selectionBounds.intersects(node.getBoundsInParent())) {
                        addNodeToSelection(node);
                    }
                }
                refreshSelectionUi();
            }

            removeMarqueeRectangle();
            marqueeActive = false;
            event.consume();
        });

        componentLayer.addEventHandler(MouseEvent.MOUSE_MOVED, event -> {
            if (simulationRunning
                    || event.getTarget() != componentLayer
                    || selectedWires.size() != 1) {
                componentLayer.setCursor(Cursor.DEFAULT);
                return;
            }
            Point2D point = componentLayer.sceneToLocal(event.getSceneX(), event.getSceneY());
            WireConnection wire = selectedWires.iterator().next();
            double tolerance = WIRE_SELECTION_SCREEN_TOLERANCE * 1.6 / currentZoom;
            EditableSegment segment = wire.findEditableSegment(point, tolerance);
            componentLayer.setCursor(segment == null
                    ? Cursor.DEFAULT
                    : (segment.horizontal() ? Cursor.V_RESIZE : Cursor.H_RESIZE));
        });

        componentLayer.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            if (simulationRunning
                    || event.getButton() != MouseButton.PRIMARY
                    || event.getClickCount() != 2
                    || event.getTarget() != componentLayer) {
                return;
            }
            Point2D point = componentLayer.sceneToLocal(event.getSceneX(), event.getSceneY());
            if (findNearestWire(point) != null) {
                return;
            }
            addJunctionAt(point);
            event.consume();
        });
    }

    private void addJunctionAt(Point2D point) {
        rememberCurrentState();
        CircuitNode junction = addComponent(
                ComponentType.JUNCTION,
                point.getX(),
                point.getY());
        junction.relocate(
                snapAndClamp(
                        point.getX() - junction.getNodeWidth() / 2.0,
                        WORKSPACE_WIDTH - junction.getNodeWidth()),
                snapAndClamp(
                        point.getY() - junction.getNodeHeight() / 2.0,
                        WORKSPACE_HEIGHT - junction.getNodeHeight()));
        junction.notifyPositionChanged();
        statusMessageLabel.setText("Junction added • drag from its dot to create branches");
    }

    private void installCursorPositionTracking() {
        javafx.event.EventHandler<MouseEvent> updater = event -> {
            Point2D point = componentLayer.sceneToLocal(event.getSceneX(), event.getSceneY());
            double x = Math.max(0, Math.min(WORKSPACE_WIDTH, point.getX()));
            double y = Math.max(0, Math.min(WORKSPACE_HEIGHT, point.getY()));
            cursorPositionLabel.setText(String.format(
                    "X %.0f  Y %.0f  •  Grid %d, %d",
                    x,
                    y,
                    Math.round(x / GRID_SIZE),
                    Math.round(y / GRID_SIZE)));
        };
        componentLayer.addEventFilter(MouseEvent.MOUSE_MOVED, updater);
        componentLayer.addEventFilter(MouseEvent.MOUSE_DRAGGED, updater);
    }

    private WireConnection findNearestWire(Point2D workspacePoint) {
        double tolerance = WIRE_SELECTION_SCREEN_TOLERANCE / currentZoom;
        WireConnection nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;

        for (WireConnection wire : wires) {
            double distance = wire.distanceTo(workspacePoint.getX(), workspacePoint.getY());
            if (distance <= tolerance && distance < nearestDistance) {
                nearest = wire;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private void removeMarqueeRectangle() {
        if (marqueeRectangle != null) {
            componentLayer.getChildren().remove(marqueeRectangle);
            marqueeRectangle = null;
        }
    }

    private void handleNodeSelectionRequest(CircuitNode node, boolean toggleSelection) {
        if (toggleSelection) {
            if (selectedNodes.contains(node)) {
                selectedNodes.remove(node);
                node.setSelectedVisual(false);
                if (primarySelectedNode == node) {
                    primarySelectedNode = selectedNodes.stream()
                            .reduce((first, second) -> second)
                            .orElse(null);
                }
            } else {
                selectedNodes.add(node);
                node.setSelectedVisual(true);
                primarySelectedNode = node;
            }
            refreshSelectionUi();
            return;
        }

        if (selectedNodes.contains(node)) {
            primarySelectedNode = node;
            primarySelectedWire = null;
            refreshSelectionUi();
            return;
        }
        selectOnlyNode(node);
    }

    private void handleWireSelectionRequest(WireConnection wire, boolean toggleSelection) {
        if (toggleSelection) {
            if (selectedWires.contains(wire)) {
                selectedWires.remove(wire);
                wire.setSelectedVisual(false);
                if (primarySelectedWire == wire) {
                    primarySelectedWire = selectedWires.stream()
                            .reduce((first, second) -> second)
                            .orElse(null);
                }
            } else {
                selectedWires.add(wire);
                wire.setSelectedVisual(true);
                primarySelectedWire = wire;
            }
            refreshSelectionUi();
            return;
        }

        clearSelectionVisuals();
        selectedWires.add(wire);
        wire.setSelectedVisual(true);
        primarySelectedWire = wire;
        primarySelectedNode = null;
        refreshSelectionUi();
    }

    private void selectOnlyNode(CircuitNode node) {
        clearSelectionVisuals();
        if (node != null) {
            selectedNodes.add(node);
            node.setSelectedVisual(true);
        }
        primarySelectedNode = node;
        primarySelectedWire = null;
        refreshSelectionUi();
    }

    private void addNodeToSelection(CircuitNode node) {
        if (selectedNodes.add(node)) {
            node.setSelectedVisual(true);
        }
        primarySelectedNode = node;
        primarySelectedWire = null;
    }

    private void clearSelection() {
        clearSelectionVisuals();
        primarySelectedNode = null;
        primarySelectedWire = null;
        refreshSelectionUi();
    }

    private void clearSelectionVisuals() {
        cancelSegmentDrag();
        selectedNodes.forEach(node -> node.setSelectedVisual(false));
        selectedWires.forEach(wire -> wire.setSelectedVisual(false));
        selectedNodes.clear();
        selectedWires.clear();
    }

    private void refreshSelectionUi() {
        deleteButton.setDisable(
                simulationRunning || (selectedNodes.isEmpty() && selectedWires.isEmpty()));
        updateEditorActionButtons();
        updatePropertiesPanel();
        updateDashboard();
    }

    private static double snapWireSegment(double value, double maximum) {
        double snapped = Math.round(value / WIRE_SEGMENT_GRID_SIZE) * WIRE_SEGMENT_GRID_SIZE;
        return clamp(snapped, 0, maximum);
    }

    private boolean isWireEditPointBlocked(Point2D point) {
        final double clearance = 12;
        return nodes.values().stream().anyMatch(node ->
                point.getX() > node.getLayoutX() - clearance
                        && point.getX() < node.getLayoutX() + node.getNodeWidth() + clearance
                        && point.getY() > node.getLayoutY() - clearance
                        && point.getY() < node.getLayoutY() + node.getNodeHeight() + clearance);
    }

    private void cancelSegmentDrag() {
        segmentDragWire = null;
        segmentDragCandidate = null;
        activeSegmentEdit = null;
        segmentDragStartPoint = null;
        segmentDragStartSnapshot = null;
        if (componentLayer != null) {
            componentLayer.setCursor(Cursor.DEFAULT);
        }
    }

    private void beginGroupMove(CircuitNode anchor, double sceneX, double sceneY) {
        if (simulationRunning) {
            return;
        }
        if (!selectedNodes.contains(anchor)) {
            selectOnlyNode(anchor);
        }

        groupDragAnchor = anchor;
        pendingGroupMoveSnapshot = captureSnapshot();
        groupDragStartPointer = componentLayer.sceneToLocal(sceneX, sceneY);
        groupDragStartPositions.clear();
        for (CircuitNode node : selectedNodes) {
            groupDragStartPositions.put(node, new Point2D(node.getLayoutX(), node.getLayoutY()));
            node.toFront();
        }
    }

    private void updateGroupMove(CircuitNode anchor, double sceneX, double sceneY) {
        if (simulationRunning
                || groupDragAnchor != anchor
                || groupDragStartPointer == null
                || groupDragStartPositions.isEmpty()) {
            return;
        }

        Point2D pointer = componentLayer.sceneToLocal(sceneX, sceneY);
        double rawDeltaX = pointer.getX() - groupDragStartPointer.getX();
        double rawDeltaY = pointer.getY() - groupDragStartPointer.getY();
        Point2D anchorStart = groupDragStartPositions.get(anchor);
        if (anchorStart == null) {
            return;
        }

        double snappedAnchorX = Math.round(
                (anchorStart.getX() + rawDeltaX) / GRID_SIZE) * GRID_SIZE;
        double snappedAnchorY = Math.round(
                (anchorStart.getY() + rawDeltaY) / GRID_SIZE) * GRID_SIZE;
        double deltaX = snappedAnchorX - anchorStart.getX();
        double deltaY = snappedAnchorY - anchorStart.getY();

        double minimumStartX = Double.POSITIVE_INFINITY;
        double minimumStartY = Double.POSITIVE_INFINITY;
        double maximumRight = Double.NEGATIVE_INFINITY;
        double maximumBottom = Double.NEGATIVE_INFINITY;
        for (Map.Entry<CircuitNode, Point2D> entry : groupDragStartPositions.entrySet()) {
            CircuitNode node = entry.getKey();
            Point2D start = entry.getValue();
            minimumStartX = Math.min(minimumStartX, start.getX());
            minimumStartY = Math.min(minimumStartY, start.getY());
            maximumRight = Math.max(maximumRight, start.getX() + node.getNodeWidth());
            maximumBottom = Math.max(maximumBottom, start.getY() + node.getNodeHeight());
        }

        deltaX = clamp(deltaX, -minimumStartX, WORKSPACE_WIDTH - maximumRight);
        deltaY = clamp(deltaY, -minimumStartY, WORKSPACE_HEIGHT - maximumBottom);

        for (Map.Entry<CircuitNode, Point2D> entry : groupDragStartPositions.entrySet()) {
            Point2D start = entry.getValue();
            entry.getKey().relocate(start.getX() + deltaX, start.getY() + deltaY);
        }
        anchor.notifyPositionChanged();
    }

    private void finishGroupMove(CircuitNode anchor) {
        if (groupDragAnchor != anchor) {
            return;
        }
        CircuitSnapshot beforeMove = pendingGroupMoveSnapshot;
        groupDragStartPositions.clear();
        groupDragStartPointer = null;
        groupDragAnchor = null;
        pendingGroupMoveSnapshot = null;
        updateAllWireGeometry();
        if (beforeMove != null && !beforeMove.equals(captureSnapshot())) {
            pushUndoSnapshot(beforeMove);
            refreshDocumentDirty();
        }
        updatePropertiesPanel();
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private void beginWireDrag(
            CircuitNode source,
            int sourceOutputIndex,
            double sceneX,
            double sceneY) {

        if (simulationRunning) {
            return;
        }
        cancelWirePreview();
        wireDragSource = source;
        wireDragSourceOutputIndex = sourceOutputIndex;
        selectOnlyNode(source);

        previewWire = new Path();
        previewWire.setFill(null);
        previewWire.setMouseTransparent(true);
        previewWire.getStyleClass().addAll("signal-wire", "wire-preview");
        previewWire.pseudoClassStateChanged(
                ACTIVE,
                source.getOutputState(sourceOutputIndex));
        wireLayer.getChildren().add(previewWire);
        updateWireDrag(sceneX, sceneY);
    }

    private void updateWireDrag(double sceneX, double sceneY) {
        if (previewWire == null || wireDragSource == null) {
            return;
        }

        Point2D end = wireLayer.sceneToLocal(sceneX, sceneY);
        TargetPin nearbyTarget = findNearestInputPin(wireDragSource, sceneX, sceneY);
        Point2D previewTarget = nearbyTarget == null
                ? end
                : new Point2D(
                        nearbyTarget.node().inputAnchorX(nearbyTarget.inputIndex()),
                        nearbyTarget.node().inputAnchorY(nearbyTarget.inputIndex()));
        double previewDepartureOffset = suggestedPreviewDepartureOffset(
                wireDragSource, wireDragSourceOutputIndex, previewTarget);
        List<Point2D> previewPoints;
        if (nearbyTarget != null
                && validateConnection(
                        wireDragSource,
                        wireDragSourceOutputIndex,
                        nearbyTarget.node(),
                        nearbyTarget.inputIndex()) == null) {
            previewPoints = OrthogonalWireRouter.route(
                    wireDragSource,
                    wireDragSourceOutputIndex,
                    nearbyTarget.node(),
                    nearbyTarget.inputIndex(),
                    nodes.values(),
                    List.of(),
                    previewDepartureOffset,
                    collectOccupiedSegments(),
                    netId(wireDragSource, wireDragSourceOutputIndex),
                    WORKSPACE_WIDTH,
                    WORKSPACE_HEIGHT);
        } else {
            previewPoints = OrthogonalWireRouter.preview(
                    wireDragSource,
                    wireDragSourceOutputIndex,
                    end,
                    previewDepartureOffset);
        }
        OrthogonalWireRouter.applyRoundedPath(previewWire, previewPoints);
    }

    private void finishWireDrag(
            CircuitNode source,
            int sourceOutputIndex,
            double sceneX,
            double sceneY) {

        if (simulationRunning) {
            cancelWirePreview();
            return;
        }
        TargetPin targetPin = findNearestInputPin(source, sceneX, sceneY);
        cancelWirePreview();

        if (targetPin == null
                || validateConnection(
                        source,
                        sourceOutputIndex,
                        targetPin.node(),
                        targetPin.inputIndex()) != null) {
            return;
        }

        rememberCurrentState();
        createWire(source, sourceOutputIndex, targetPin.node(), targetPin.inputIndex());
        evaluatePausedNetwork();
    }

    private TargetPin findNearestInputPin(CircuitNode source, double sceneX, double sceneY) {
        TargetPin best = null;
        double bestDistance = Double.POSITIVE_INFINITY;

        for (CircuitNode candidate : nodes.values()) {
            if (candidate == source || candidate.getInputCount() == 0) {
                continue;
            }
            for (int inputIndex = 0; inputIndex < candidate.getInputCount(); inputIndex++) {
                double distance = candidate.distanceToInputPinScene(
                        inputIndex, sceneX, sceneY);
                if (distance <= PIN_DROP_TOLERANCE && distance < bestDistance) {
                    best = new TargetPin(candidate, inputIndex);
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    private String validateConnection(
            CircuitNode source,
            int sourceOutputIndex,
            CircuitNode target,
            int targetInputIndex) {

        if (source == target) {
            return "self-feedback";
        }
        if (sourceOutputIndex < 0 || sourceOutputIndex >= source.getOutputCount()) {
            return "invalid-output";
        }
        if (targetInputIndex < 0 || targetInputIndex >= target.getInputCount()) {
            return "invalid-input";
        }
        if (wires.stream().anyMatch(wire ->
                wire.getTarget() == target
                        && wire.getTargetInputIndex() == targetInputIndex)) {
            return "occupied-input";
        }
        return null;
    }

    private WireConnection createWire(
            CircuitNode source,
            int sourceOutputIndex,
            CircuitNode target,
            int targetInputIndex) {

        return createWire(source, sourceOutputIndex, target, targetInputIndex, true);
    }

    private WireConnection createWire(
            CircuitNode source,
            int sourceOutputIndex,
            CircuitNode target,
            int targetInputIndex,
            boolean rerouteImmediately) {

        WireConnection wire = new WireConnection(
                nextWireId++, source, sourceOutputIndex, target, targetInputIndex);
        wires.add(wire);
        wireLayer.getChildren().add(wire.getPath());
        wire.getPath().toBack();
        if (rerouteImmediately) {
            updateAllWireGeometry();
        }
        return wire;
    }

    private void cancelWirePreview() {
        if (previewWire != null) {
            wireLayer.getChildren().remove(previewWire);
            previewWire = null;
        }
        if (wireDragSource != null) {
            wireDragSource.setPendingConnectionVisual(false);
            wireDragSource = null;
        }
        wireDragSourceOutputIndex = 0;
    }

    private int countIncoming(CircuitNode node) {
        return (int) wires.stream().filter(wire -> wire.getTarget() == node).count();
    }

    private int countOutgoing(CircuitNode node) {
        return (int) wires.stream().filter(wire -> wire.getSource() == node).count();
    }

    @FXML
    private void handleRunToggle() {
        simulationRunning = runButton.isSelected();
        runButton.setText(simulationRunning ? "Pause" : "Play");

        if (simulationRunning) {
            synchronizeFlipFlopClockMemory();
            evaluateCircuit();
            simulationTimeline.play();
        } else {
            simulationTimeline.stop();
        }
        applySimulationMode();
        updatePropertiesPanel();
        updateDashboard();
    }

    private void applySimulationMode() {
        nodes.values().forEach(node -> node.setEditingLocked(simulationRunning));
        componentPalettePanel.setDisable(simulationRunning);
        homeButton.setDisable(simulationRunning);
        newButton.setDisable(simulationRunning);
        openButton.setDisable(simulationRunning);
        clearButton.setDisable(simulationRunning);
        demoMenuButton.setDisable(simulationRunning);
        copyButton.setDisable(simulationRunning || selectedNodes.isEmpty());
        pasteButton.setDisable(simulationRunning || clipboard == null);
        rotateButton.setDisable(simulationRunning || selectedNodes.isEmpty());
        abstractButton.setDisable(simulationRunning || selectedNodes.size() < 2);
        deleteButton.setDisable(
                simulationRunning || (selectedNodes.isEmpty() && selectedWires.isEmpty()));
        updateEditorActionButtons();
        if (simulationRunning) {
            cancelSegmentDrag();
        }
    }

    private void buildSimulationTimeline() {
        simulationTimeline = new Timeline(new KeyFrame(
                Duration.seconds(SIMULATION_TICK_SECONDS),
                event -> advanceClockSources()));
        simulationTimeline.setCycleCount(Timeline.INDEFINITE);
    }

    private void advanceClockSources() {
        if (!simulationRunning) {
            return;
        }

        boolean clockChanged = false;
        for (CircuitNode node : nodes.values()) {
            if (node.getType() == ComponentType.CUSTOM) {
                clockChanged |= node.advanceCustomClocks(SIMULATION_TICK_SECONDS);
                continue;
            }
            if (node.getType() != ComponentType.CLOCK) {
                continue;
            }

            double elapsed = clockAccumulators.getOrDefault(node, 0.0)
                    + SIMULATION_TICK_SECONDS;
            double halfPeriod = 0.5 / node.getConfig().clockFrequencyHz();
            while (elapsed + 1e-9 >= halfPeriod) {
                elapsed -= halfPeriod;
                node.setOutputState(0, !node.getOutputState(0));
                clockChanged = true;
            }
            clockAccumulators.put(node, elapsed);
        }

        if (clockChanged) {
            evaluateCircuit();
        }
    }

    private void synchronizeFlipFlopClockMemory() {
        for (CircuitNode node : nodes.values()) {
            if (node.getType() == ComponentType.CUSTOM) {
                node.synchronizeCustomClockMemory(readInputs(node));
                continue;
            }
            if (node.getType() == ComponentType.INTEGRATED_CIRCUIT) {
                configureIcPowerConnections(node);
                node.synchronizeIcClockMemory(readInputs(node));
                continue;
            }
            if (!node.getType().isFlipFlop() && !node.getType().isClockedStateBlock()) {
                continue;
            }
            boolean[] inputs = readInputs(node);
            int clockIndex = node.getType().clockInputIndex(node.getConfig());
            node.setPreviousClockState(clockIndex >= 0 && inputs[clockIndex]);
        }
    }

    private void evaluatePausedNetwork() {
        evaluateCombinationalNetwork();
        for (int pass = 0; pass < MAX_SEQUENTIAL_CASCADE_PASSES; pass++) {
            boolean asyncChanged = applyAsynchronousControlsOnce();
            if (!asyncChanged) {
                break;
            }
            evaluateCombinationalNetwork();
        }
        updateWireSignals();
        updatePropertiesPanel();
        updateDashboard();
    }

    private boolean applyAsynchronousControlsOnce() {
        boolean changed = false;
        for (CircuitNode node : nodes.values()) {
            if (node.getType() == ComponentType.CUSTOM) {
                changed |= node.applyCustomAsynchronousControls(readInputs(node));
                unstableFeedbackDetected |= node.hasUnstableCustomFeedback();
                continue;
            }
            if (node.getType() == ComponentType.INTEGRATED_CIRCUIT) {
                configureIcPowerConnections(node);
                changed |= node.applyIcAsynchronousControls(readInputs(node));
                continue;
            }
            if (node.getType().isClockedStateBlock()) {
                boolean[] inputs = readInputs(node);
                int resetIndex = node.getType().resetInputIndex(node.getConfig());
                if (resetIndex >= 0 && inputs[resetIndex]) {
                    boolean[] cleared = new boolean[node.getOutputCount()];
                    if (!Arrays.equals(cleared, node.getOutputStatesCopy())) {
                        node.setOutputStates(cleared);
                        changed = true;
                    }
                }
                continue;
            }
            if (!node.getType().isFlipFlop()) {
                continue;
            }
            boolean[] inputs = readInputs(node);
            int presetIndex = node.getType().presetInputIndex(node.getConfig());
            int clearIndex = node.getType().clearInputIndex(node.getConfig());
            boolean presetAsserted = presetIndex >= 0 && !inputs[presetIndex];
            boolean clearAsserted = clearIndex >= 0 && !inputs[clearIndex];
            node.setInvalidAsyncControls(presetAsserted && clearAsserted);
            if (presetAsserted && clearAsserted) {
                continue;
            }
            boolean next = node.getStoredState();
            if (clearAsserted) {
                next = false;
            } else if (presetAsserted) {
                next = true;
            } else {
                continue;
            }
            if (next != node.getStoredState()) {
                node.setStoredState(next);
                changed = true;
            }
        }
        return changed;
    }

    private void evaluateCircuit() {
        evaluateCombinationalNetwork();
        for (int pass = 0; pass < MAX_SEQUENTIAL_CASCADE_PASSES; pass++) {
            boolean changed = updateSequentialElementsOnce();
            if (!changed) {
                break;
            }
            evaluateCombinationalNetwork();
        }
        updateWireSignals();
        updatePropertiesPanel();
        updateDashboard();
    }

    private void evaluateCombinationalNetwork() {
        unstableFeedbackDetected = false;
        List<CircuitNode> order = topologicalOrder();
        for (CircuitNode node : order) {
            evaluateCombinationalNode(node);
        }

        if (order.size() == nodes.size()) {
            unstableFeedbackDetected |= nodes.values().stream()
                    .anyMatch(CircuitNode::hasUnstableCustomFeedback);
            return;
        }

        Set<CircuitNode> orderedNodes = new HashSet<>(order);
        List<CircuitNode> feedbackRegion = nodes.values().stream()
                .filter(node -> !orderedNodes.contains(node))
                .sorted(Comparator.comparingInt(CircuitNode::getComponentId))
                .toList();

        // Feedback regions deliberately have no topological ordering. Reuse current
        // outputs as memory, then relax the region until it settles. A repeated state
        // means the zero-delay Boolean model is oscillating rather than converging.
        Set<String> seenStates = new HashSet<>();
        seenStates.add(feedbackStateSignature(feedbackRegion));
        for (int pass = 0; pass < MAX_COMBINATIONAL_SETTLE_PASSES; pass++) {
            boolean changed = false;
            for (CircuitNode node : feedbackRegion) {
                changed |= evaluateCombinationalNode(node);
            }
            if (!changed) {
                unstableFeedbackDetected |= nodes.values().stream()
                        .anyMatch(CircuitNode::hasUnstableCustomFeedback);
                return;
            }
            if (!seenStates.add(feedbackStateSignature(feedbackRegion))) {
                unstableFeedbackDetected = true;
                return;
            }
        }
        unstableFeedbackDetected = true;
    }

    private static String feedbackStateSignature(List<CircuitNode> feedbackRegion) {
        StringBuilder signature = new StringBuilder();
        for (CircuitNode node : feedbackRegion) {
            signature.append(node.getComponentId()).append(':');
            for (boolean output : node.getOutputStatesCopy()) {
                signature.append(output ? '1' : '0');
            }
            signature.append('/').append(node.getDisplayState() ? '1' : '0').append('/');
            for (boolean segment : node.getSevenSegmentStatesCopy()) {
                signature.append(segment ? '1' : '0');
            }
            signature.append(';');
        }
        return signature.toString();
    }

    private boolean evaluateCombinationalNode(CircuitNode node) {
        ComponentType type = node.getType();
        if (type.isToggleSource()
                || type.isClock()
                || type.isFlipFlop()
                || type.isClockedStateBlock()) {
            return false;
        }

        boolean[] previousOutputs = node.getOutputStatesCopy();
        boolean previousDisplay = node.getDisplayState();
        boolean[] previousSegments = node.getSevenSegmentStatesCopy();

        boolean[] inputs = readInputs(node);
        if (type == ComponentType.CUSTOM) {
            node.evaluateCustomCombinational(inputs);
        } else if (type == ComponentType.INTEGRATED_CIRCUIT) {
            configureIcPowerConnections(node);
            node.evaluateIcCombinational(inputs);
        } else {
            NativeComponentLogic.Evaluation evaluation =
                    NativeComponentLogic.evaluate(type, node.getConfig(), inputs);
            node.setOutputStates(evaluation.outputs());
            if (type == ComponentType.SEVEN_SEGMENT_DISPLAY) {
                node.setSevenSegmentStates(evaluation.sevenSegmentStates());
            } else {
                node.setDisplayState(evaluation.displayState());
            }
        }

        return previousDisplay != node.getDisplayState()
                || !Arrays.equals(previousOutputs, node.getOutputStatesCopy())
                || !Arrays.equals(previousSegments, node.getSevenSegmentStatesCopy());
    }

    private boolean[] readInputs(CircuitNode node) {
        boolean[] inputs = new boolean[node.getInputCount()];
        if (node.getType().isFlipFlop()) {
            // Active-low asynchronous pins behave as pulled-up/inactive when left
            // unconnected so ordinary D/JK/T/SR use does not require two extra wires.
            int presetIndex = node.getType().presetInputIndex(node.getConfig());
            int clearIndex = node.getType().clearInputIndex(node.getConfig());
            if (presetIndex >= 0) {
                inputs[presetIndex] = true;
            }
            if (clearIndex >= 0) {
                inputs[clearIndex] = true;
            }
        }
        for (WireConnection wire : wires) {
            if (wire.getTarget() == node) {
                inputs[wire.getTargetInputIndex()] = wire.getSignalState();
            }
        }
        return inputs;
    }

    private void configureIcPowerConnections(CircuitNode node) {
        if (node.getType() != ComponentType.INTEGRATED_CIRCUIT || node.getIcDefinition() == null) {
            return;
        }
        int vccIndex = node.getIcDefinition().inputIndex("VCC");
        int gndIndex = node.getIcDefinition().inputIndex("GND");
        boolean vccConnected = wires.stream().anyMatch(wire ->
                wire.getTarget() == node && wire.getTargetInputIndex() == vccIndex);
        boolean gndConnected = wires.stream().anyMatch(wire ->
                wire.getTarget() == node && wire.getTargetInputIndex() == gndIndex);
        node.setIcPowerConnections(vccConnected, gndConnected);
    }

    private boolean updateSequentialElementsOnce() {
        Map<CircuitNode, Boolean> pendingStates = new LinkedHashMap<>();
        Map<CircuitNode, boolean[]> pendingOutputStates = new LinkedHashMap<>();
        boolean changed = false;

        for (CircuitNode node : nodes.values()) {
            if (node.getType() == ComponentType.CUSTOM) {
                boolean customChanged = node.updateCustomSequential(readInputs(node));
                unstableFeedbackDetected |= node.hasUnstableCustomFeedback();
                changed |= customChanged;
                continue;
            }
            if (node.getType() == ComponentType.INTEGRATED_CIRCUIT) {
                configureIcPowerConnections(node);
                changed |= node.updateIcSequential(readInputs(node));
                continue;
            }
            ComponentType type = node.getType();
            if (type.isClockedStateBlock()) {
                boolean[] inputs = readInputs(node);
                int clockIndex = type.clockInputIndex(node.getConfig());
                int resetIndex = type.resetInputIndex(node.getConfig());
                boolean clock = clockIndex >= 0 && inputs[clockIndex];
                boolean risingEdge = !node.getPreviousClockState() && clock;
                node.setPreviousClockState(clock);
                if (resetIndex >= 0 && inputs[resetIndex]) {
                    pendingOutputStates.put(node, new boolean[node.getOutputCount()]);
                    continue;
                }
                if (!risingEdge) {
                    continue;
                }

                pendingOutputStates.put(node,
                        SequentialComponentLogic.nextClockedBlockOutputs(
                                type, node.getOutputStatesCopy(), inputs));
                continue;
            }
            if (!type.isFlipFlop()) {
                continue;
            }

            boolean[] inputs = readInputs(node);
            int clockIndex = type.clockInputIndex(node.getConfig());

            boolean clock = clockIndex >= 0 && inputs[clockIndex];
            boolean risingEdge = !node.getPreviousClockState() && clock;
            // Track the physical clock even while an async control is asserted so
            // releasing PRE/CLR while CLK is already high cannot create a fake edge.
            node.setPreviousClockState(clock);
            SequentialComponentLogic.AsyncControl async =
                    SequentialComponentLogic.asynchronousControl(
                            type, node.getConfig(), inputs, node.getStoredState());
            node.setInvalidAsyncControls(async.invalid());

            // Asynchronous controls override the clock and data inputs. Both low is
            // an invalid hardware condition; retain the stored state rather than
            // inventing a deterministic Q value in this two-state simulator.
            if (async.invalid()) {
                continue;
            }
            if (async.asserted()) {
                pendingStates.put(node, async.nextState());
                continue;
            }

            if (!risingEdge) {
                continue;
            }

            pendingStates.put(node, SequentialComponentLogic.nextFlipFlopState(
                    type, node.getStoredState(), inputs));
        }

        for (Map.Entry<CircuitNode, Boolean> entry : pendingStates.entrySet()) {
            CircuitNode node = entry.getKey();
            boolean next = entry.getValue();
            if (next != node.getStoredState()) {
                node.setStoredState(next);
                changed = true;
            }
        }
        for (Map.Entry<CircuitNode, boolean[]> entry : pendingOutputStates.entrySet()) {
            CircuitNode node = entry.getKey();
            if (!Arrays.equals(node.getOutputStatesCopy(), entry.getValue())) {
                node.setOutputStates(entry.getValue());
                changed = true;
            }
        }
        return changed;
    }

    private List<CircuitNode> topologicalOrder() {
        Map<CircuitNode, Integer> indegree = new HashMap<>();
        Map<CircuitNode, List<CircuitNode>> outgoing = new HashMap<>();
        nodes.values().forEach(node -> indegree.put(node, 0));

        for (WireConnection wire : wires) {
            if (!wire.getTarget().isSequentialBoundary()) {
                indegree.computeIfPresent(
                        wire.getTarget(),
                        (ignored, value) -> value + 1);
                outgoing.computeIfAbsent(wire.getSource(), ignored -> new ArrayList<>())
                        .add(wire.getTarget());
            }
        }

        Queue<CircuitNode> queue = new ArrayDeque<>();
        indegree.entrySet().stream()
                .filter(entry -> entry.getValue() == 0)
                .map(Map.Entry::getKey)
                .sorted(Comparator.comparingInt(CircuitNode::getComponentId))
                .forEach(queue::add);

        List<CircuitNode> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            CircuitNode node = queue.remove();
            order.add(node);

            for (CircuitNode target : outgoing.getOrDefault(node, List.of())) {
                int newDegree = indegree.computeIfPresent(
                        target, (ignored, value) -> value - 1);
                if (newDegree == 0) {
                    queue.add(target);
                }
            }
        }
        return order;
    }

    @FXML
    private void handleLoadAdderMuxDemo() {
        if (!beginDemoLoad()) {
            return;
        }

        CircuitNode carry = addComponent(ComponentType.LOGIC_INPUT, 300, 1120);
        carry.setUserLabel("Carry In");
        carry.setOutputState(0, false);

        boolean[] aValues = { true, false, false, true };
        boolean[] bValues = { false, true, true, false };
        CircuitNode[] sums = new CircuitNode[4];
        CircuitNode firstInput = null;

        for (int bit = 0; bit < 4; bit++) {
            double rowY = 100 + bit * 270;
            CircuitNode a = addComponent(ComponentType.LOGIC_INPUT, 80, rowY);
            CircuitNode b = addComponent(ComponentType.LOGIC_INPUT, 280, rowY);
            a.setUserLabel("A" + bit);
            b.setUserLabel("B" + bit);
            a.setOutputState(0, aValues[bit]);
            b.setOutputState(0, bValues[bit]);
            if (firstInput == null) {
                firstInput = a;
            }

            FullAdderStage stage = buildFullAdderStage(bit, a, b, carry, rowY);
            sums[bit] = stage.sum();
            carry = stage.carry();
        }

        CircuitNode halfSelect = addComponent(ComponentType.LOGIC_INPUT, 1500, 160);
        halfSelect.setUserLabel("Show High Half");
        halfSelect.setOutputState(0, false);

        CircuitNode displayBit0 = addComponent(
                ComponentType.MULTIPLEXER, ComponentConfig.bits(1), 1700, 300);
        CircuitNode displayBit1 = addComponent(
                ComponentType.MULTIPLEXER, ComponentConfig.bits(1), 1700, 610);
        displayBit0.setUserLabel("SUM MUX 0");
        displayBit1.setUserLabel("SUM MUX 1");
        createWire(sums[0], 0, displayBit0, 0, false);
        createWire(sums[2], 0, displayBit0, 1, false);
        createWire(halfSelect, 0, displayBit0, 2, false);
        createWire(sums[1], 0, displayBit1, 0, false);
        createWire(sums[3], 0, displayBit1, 1, false);
        createWire(halfSelect, 0, displayBit1, 2, false);

        CircuitNode shown0 = addComponent(ComponentType.LOGIC_OUTPUT, 2050, 340);
        CircuitNode shown1 = addComponent(ComponentType.LOGIC_OUTPUT, 2050, 650);
        CircuitNode carryOut = addComponent(ComponentType.LOGIC_OUTPUT, 2050, 960);
        shown0.setUserLabel("Displayed Sum 0");
        shown1.setUserLabel("Displayed Sum 1");
        carryOut.setUserLabel("Carry Out");
        createWire(displayBit0, 0, shown0, 0, false);
        createWire(displayBit1, 0, shown1, 0, false);
        createWire(carry, 0, carryOut, 0, false);

        finishDemoLoad(firstInput);
    }

    private FullAdderStage buildFullAdderStage(
            int bit,
            CircuitNode a,
            CircuitNode b,
            CircuitNode carryIn,
            double rowY) {

        ComponentConfig twoInputs = ComponentConfig.bits(2);
        CircuitNode inputXor = addComponent(ComponentType.XOR, twoInputs, 520, rowY);
        CircuitNode sumXor = addComponent(ComponentType.XOR, twoInputs, 820, rowY - 40);
        CircuitNode directCarry = addComponent(ComponentType.AND, twoInputs, 520, rowY + 105);
        CircuitNode rippleCarry = addComponent(ComponentType.AND, twoInputs, 820, rowY + 105);
        CircuitNode carryOr = addComponent(ComponentType.OR, twoInputs, 1120, rowY + 105);
        inputXor.setUserLabel("Bit " + bit + " A XOR B");
        sumXor.setUserLabel("Sum " + bit);
        directCarry.setUserLabel("Bit " + bit + " Generate");
        rippleCarry.setUserLabel("Bit " + bit + " Propagate");
        carryOr.setUserLabel(bit == 1 ? "Low 2-bit Carry" : bit == 3 ? "High 2-bit Carry" : "Carry " + (bit + 1));

        createWire(a, 0, inputXor, 0, false);
        createWire(b, 0, inputXor, 1, false);
        createWire(inputXor, 0, sumXor, 0, false);
        createWire(carryIn, 0, sumXor, 1, false);
        createWire(a, 0, directCarry, 0, false);
        createWire(b, 0, directCarry, 1, false);
        createWire(inputXor, 0, rippleCarry, 0, false);
        createWire(carryIn, 0, rippleCarry, 1, false);
        createWire(directCarry, 0, carryOr, 0, false);
        createWire(rippleCarry, 0, carryOr, 1, false);
        return new FullAdderStage(sumXor, carryOr);
    }

    @FXML
    private void handleLoadMuxPlaygroundDemo() {
        if (!beginDemoLoad()) {
            return;
        }
        CircuitNode[] data = new CircuitNode[4];
        for (int index = 0; index < data.length; index++) {
            data[index] = addComponent(ComponentType.LOGIC_INPUT, 100, 120 + index * 150);
            data[index].setUserLabel("Data " + index);
            data[index].setOutputState(0, index == 1 || index == 2);
        }
        CircuitNode select0 = addComponent(ComponentType.LOGIC_INPUT, 520, 760);
        CircuitNode select1 = addComponent(ComponentType.LOGIC_INPUT, 760, 760);
        select0.setUserLabel("Select S0");
        select1.setUserLabel("Select S1");
        CircuitNode mux = addComponent(
                ComponentType.MULTIPLEXER, ComponentConfig.bits(2), 620, 180);
        mux.setUserLabel("4-Way Signal Selector");
        for (int index = 0; index < data.length; index++) {
            createWire(data[index], 0, mux, index, false);
        }
        createWire(select0, 0, mux, 4, false);
        createWire(select1, 0, mux, 5, false);
        CircuitNode output = addComponent(ComponentType.BULB, 1100, 350);
        output.setUserLabel("Selected Signal");
        createWire(mux, 0, output, 0, false);
        finishDemoLoad(data[0]);
    }

    @FXML
    private void handleLoadParityDemo() {
        if (!beginDemoLoad()) {
            return;
        }
        CircuitNode parity = addComponent(
                ComponentType.XOR, ComponentConfig.bits(4), 620, 300);
        parity.setUserLabel("4-Bit Odd Parity");
        CircuitNode firstInput = null;
        for (int index = 0; index < 4; index++) {
            CircuitNode input = addComponent(ComponentType.LOGIC_INPUT, 120, 120 + index * 170);
            input.setUserLabel("Bit " + index);
            input.setOutputState(0, index < 3);
            createWire(input, 0, parity, index, false);
            if (firstInput == null) {
                firstInput = input;
            }
        }
        CircuitNode odd = addComponent(ComponentType.LOGIC_OUTPUT, 980, 300);
        odd.setUserLabel("Odd Number of Ones");
        createWire(parity, 0, odd, 0, false);
        finishDemoLoad(firstInput);
    }

    @FXML
    private void handleLoadLightChaserDemo() {
        if (!beginDemoLoad()) {
            return;
        }

        CircuitNode init = addComponent(ComponentType.LOGIC_INPUT, 100, 110);
        init.setUserLabel("INIT_n • 0 seeds 0001, then set to 1");
        init.setOutputState(0, false);

        CircuitNode clock = addComponent(
                ComponentType.CLOCK, ComponentConfig.clock(1.0), 100, 520);
        clock.setUserLabel("Ring Clock");

        CircuitNode[] stages = new CircuitNode[4];
        double[] stageX = {360, 680, 1000, 1320};
        for (int index = 0; index < stages.length; index++) {
            stages[index] = addComponent(ComponentType.D_FLIP_FLOP, stageX[index], 240);
            stages[index].setUserLabel("Ring FF " + index);
            stages[index].setStoredState(false);
            createWire(clock, 0, stages[index], 1, false);
        }

        // Q circulates back into D: Q0→D1→D2→D3→D0.
        for (int index = 0; index < stages.length; index++) {
            CircuitNode source = stages[index];
            CircuitNode target = stages[(index + 1) % stages.length];
            createWire(source, 0, target, 0, false);
        }

        // Active-low initialization creates the one-hot seed 0001 without a clock:
        // FF0 is preset while FF1..FF3 are cleared. Unconnected async pins are
        // treated as pulled high/inactive.
        createWire(init, 0, stages[0], 2, false); // /PRE
        for (int index = 1; index < stages.length; index++) {
            createWire(init, 0, stages[index], 3, false); // /CLR
        }

        for (int index = 0; index < stages.length; index++) {
            CircuitNode lamp = addComponent(ComponentType.BULB, stageX[index] + 30, 610);
            lamp.setUserLabel("Ring Lamp " + index);
            createWire(stages[index], 0, lamp, 0, false);
        }

        finishDemoLoad(init);
    }

    @FXML
    private void handleLoadSevenSegmentDemo() {
        if (!beginDemoLoad()) {
            return;
        }

        CircuitNode[] bits = new CircuitNode[4];
        // Present the switches visually as B3 B2 B1 B0 while keeping B0 as the
        // decoder's least-significant input. All switches off therefore means 0000.
        for (int bit = 3; bit >= 0; bit--) {
            int row = 3 - bit;
            CircuitNode input = addComponent(ComponentType.SWITCH, 100, 100 + row * 150);
            input.setUserLabel("B" + bit + " (" + (1 << bit) + ")");
            input.setOutputState(0, false);
            bits[bit] = input;
        }

        CircuitNode decoder = addComponent(ComponentType.BCD_TO_7SEGMENT, 560, 210);
        decoder.setUserLabel("BCD → 7-Segment Decoder");
        for (int bit = 0; bit < 4; bit++) {
            createWire(bits[bit], 0, decoder, bit, false);
        }

        CircuitNode display = addComponent(ComponentType.SEVEN_SEGMENT_DISPLAY, 1050, 185);
        display.setUserLabel("Decimal Display");
        for (int segment = 0; segment < 7; segment++) {
            createWire(decoder, segment, display, segment, false);
        }

        // 0000 starts at decimal zero. For example B3=1, B0=1 (1001) shows 9.
        finishDemoLoad(bits[0]);
    }

    @FXML
    private void handleLoadMasterSlaveJkDemo() {
        if (!beginDemoLoad()) {
            return;
        }

        ComponentConfig nand2 = ComponentConfig.bits(2);
        ComponentConfig nand3 = ComponentConfig.bits(3);

        CircuitNode j = addComponent(ComponentType.LOGIC_INPUT, 100, 120);
        CircuitNode k = addComponent(ComponentType.LOGIC_INPUT, 100, 480);
        CircuitNode clock = addComponent(
                ComponentType.CLOCK, ComponentConfig.clock(1.0), 100, 800);
        j.setUserLabel("J • set when K=0");
        k.setUserLabel("K • reset when J=0");
        clock.setUserLabel("Master-Slave Clock");

        CircuitNode jGate = addComponent(ComponentType.NAND, nand3, 400, 120);
        CircuitNode kGate = addComponent(ComponentType.NAND, nand3, 400, 480);
        CircuitNode clockBar = addComponent(ComponentType.NAND, nand2, 400, 800);
        jGate.setUserLabel("NAND • J · CLK · Q̅");
        kGate.setUserLabel("NAND • K · CLK · Q");
        clockBar.setUserLabel("NAND inverter • CLK̅");

        CircuitNode masterQ = addComponent(ComponentType.NAND, nand2, 740, 180);
        CircuitNode masterQBar = addComponent(ComponentType.NAND, nand2, 740, 470);
        masterQ.setUserLabel("MASTER latch • M");
        masterQBar.setUserLabel("MASTER latch • M̅");

        CircuitNode slaveSetGate = addComponent(ComponentType.NAND, nand2, 1060, 180);
        CircuitNode slaveResetGate = addComponent(ComponentType.NAND, nand2, 1060, 470);
        slaveSetGate.setUserLabel("SLAVE set gate");
        slaveResetGate.setUserLabel("SLAVE reset gate");

        CircuitNode slaveQ = addComponent(ComponentType.NAND, nand2, 1380, 180);
        CircuitNode slaveQBar = addComponent(ComponentType.NAND, nand2, 1380, 470);
        slaveQ.setUserLabel("SLAVE latch • Q");
        slaveQBar.setUserLabel("SLAVE latch • Q̅");

        // Seed the two cross-coupled NAND latches into complementary stable states.
        masterQ.setOutputState(0, false);
        masterQBar.setOutputState(0, true);
        slaveQ.setOutputState(0, false);
        slaveQBar.setOutputState(0, true);
        jGate.setOutputState(0, true);
        kGate.setOutputState(0, true);
        clockBar.setOutputState(0, true);
        slaveSetGate.setOutputState(0, true);
        slaveResetGate.setOutputState(0, false);

        createWire(j, 0, jGate, 0, false);
        createWire(clock, 0, jGate, 1, false);
        createWire(slaveQBar, 0, jGate, 2, false);
        createWire(k, 0, kGate, 0, false);
        createWire(clock, 0, kGate, 1, false);
        createWire(slaveQ, 0, kGate, 2, false);
        createWire(clock, 0, clockBar, 0, false);
        createWire(clock, 0, clockBar, 1, false);

        createWire(jGate, 0, masterQ, 0, false);
        createWire(masterQBar, 0, masterQ, 1, false);
        createWire(kGate, 0, masterQBar, 0, false);
        createWire(masterQ, 0, masterQBar, 1, false);

        createWire(masterQ, 0, slaveSetGate, 0, false);
        createWire(clockBar, 0, slaveSetGate, 1, false);
        createWire(masterQBar, 0, slaveResetGate, 0, false);
        createWire(clockBar, 0, slaveResetGate, 1, false);

        createWire(slaveSetGate, 0, slaveQ, 0, false);
        createWire(slaveQBar, 0, slaveQ, 1, false);
        createWire(slaveResetGate, 0, slaveQBar, 0, false);
        createWire(slaveQ, 0, slaveQBar, 1, false);

        CircuitNode q = addComponent(ComponentType.BULB, 1720, 180);
        CircuitNode qBar = addComponent(ComponentType.BULB, 1720, 470);
        q.setUserLabel("Q • falling-edge output");
        qBar.setUserLabel("Q̅ • complement");
        createWire(slaveQ, 0, q, 0, false);
        createWire(slaveQBar, 0, qBar, 0, false);
        finishDemoLoad(j);
    }

    @FXML
    private void handleLoadMuxLockboxDemo() {
        if (!beginDemoLoad()) {
            return;
        }

        CircuitNode[] codeBits = new CircuitNode[4];
        for (int bit = 3; bit >= 0; bit--) {
            int row = 3 - bit;
            codeBits[bit] = addComponent(ComponentType.LOGIC_INPUT, 100, 100 + row * 230);
            codeBits[bit].setUserLabel("Code D" + bit);
            codeBits[bit].setOutputState(0, false);
        }

        CircuitNode vcc = addComponent(ComponentType.VCC, 340, 1020);
        CircuitNode ground = addComponent(ComponentType.GROUND, 580, 1020);
        vcc.setUserLabel("Logic 1 • expected bit");
        ground.setUserLabel("Logic 0 • expected bit");

        // D3 D2 D1 D0 = 1 0 1 1. Each visible 2:1 MUX acts as one equality detector.
        boolean[] expected = { true, true, false, true };
        CircuitNode[] matches = new CircuitNode[4];
        for (int bit = 0; bit < 4; bit++) {
            int row = 3 - bit;
            CircuitNode mux = addComponent(
                    ComponentType.MULTIPLEXER,
                    ComponentConfig.bits(1),
                    700,
                    80 + row * 230);
            mux.setUserLabel("D" + bit + " must equal " + (expected[bit] ? "1" : "0"));
            createWire(expected[bit] ? ground : vcc, 0, mux, 0, false);
            createWire(expected[bit] ? vcc : ground, 0, mux, 1, false);
            createWire(codeBits[bit], 0, mux, 2, false);
            matches[bit] = mux;
        }

        CircuitNode allMatch = addComponent(
                ComponentType.AND, ComponentConfig.bits(4), 1160, 390);
        allMatch.setUserLabel("All four MUX matches");
        for (int bit = 0; bit < 4; bit++) {
            createWire(matches[bit], 0, allMatch, bit, false);
        }

        CircuitNode unlocked = addComponent(ComponentType.BULB, 1540, 430);
        unlocked.setUserLabel("UNLOCKED • D3D2D1D0 = 1011");
        createWire(allMatch, 0, unlocked, 0, false);
        finishDemoLoad(codeBits[0]);
    }

    @FXML
    private void handleLoadTwosComplementSubtractorDemo() {
        if (!beginDemoLoad()) {
            return;
        }

        CircuitNode[] a = new CircuitNode[4];
        CircuitNode[] b = new CircuitNode[4];
        CircuitNode[] invertedB = new CircuitNode[4];
        for (int bit = 0; bit < 4; bit++) {
            double rowY = 100 + bit * 190;
            a[bit] = addComponent(ComponentType.LOGIC_INPUT, 80, rowY);
            b[bit] = addComponent(ComponentType.LOGIC_INPUT, 300, rowY);
            invertedB[bit] = addComponent(ComponentType.NOT, 560, rowY);
            a[bit].setUserLabel("A" + bit);
            b[bit].setUserLabel("B" + bit);
            invertedB[bit].setUserLabel("NOT B" + bit);
            createWire(b[bit], 0, invertedB[bit], 0, false);
        }

        CircuitNode carryOne = addComponent(ComponentType.VCC, 680, 900);
        carryOne.setUserLabel("+1 carry-in");
        CircuitNode adder = addComponent(
                ComponentType.FULL_ADDER, ComponentConfig.bits(4), 900, 180);
        adder.setUserLabel("A + NOT(B) + 1");
        for (int bit = 0; bit < 4; bit++) {
            createWire(a[bit], 0, adder, bit, false);
            createWire(invertedB[bit], 0, adder, 4 + bit, false);
        }
        createWire(carryOne, 0, adder, 8, false);

        for (int bit = 0; bit < 4; bit++) {
            CircuitNode difference = addComponent(ComponentType.LOGIC_OUTPUT, 1360, 100 + bit * 190);
            difference.setUserLabel("Difference D" + bit);
            createWire(adder, bit, difference, 0, false);
        }
        CircuitNode noBorrow = addComponent(ComponentType.LOGIC_OUTPUT, 1360, 900);
        noBorrow.setUserLabel("COUT • 1 means no borrow");
        createWire(adder, 4, noBorrow, 0, false);
        finishDemoLoad(a[0]);
    }

    private boolean beginDemoLoad() {
        if (simulationRunning) {
            return false;
        }
        rememberCurrentState();
        clearWorkspace(true);
        currentCircuitFile = null;
        return true;
    }

    private void finishDemoLoad(CircuitNode focus) {
        clockAccumulators.replaceAll((node, value) -> 0.0);
        updateAllWireGeometry();
        evaluatePausedNetwork();
        synchronizeFlipFlopClockMemory();
        updateWireSignals();
        applySimulationMode();
        selectOnlyNode(focus);
        updateEditorActionButtons();
        updateDashboard();
        Platform.runLater(this::centerViewOnCircuit);
    }

    /** Opens a fresh canvas at its geometric centre instead of the top-left corner. */
    private void centerWorkspaceView() {
        workspaceScroll.setHvalue(0.5);
        workspaceScroll.setVvalue(0.5);
    }

    /** Centres the viewport around the loaded circuit while keeping empty projects centred. */
    private void centerViewOnCircuit() {
        if (nodes.isEmpty()) {
            centerWorkspaceView();
            return;
        }
        double minimumX = nodes.values().stream()
                .mapToDouble(CircuitNode::getLayoutX)
                .min().orElse(WORKSPACE_WIDTH / 2.0);
        double maximumX = nodes.values().stream()
                .mapToDouble(node -> node.getLayoutX() + node.getNodeWidth())
                .max().orElse(WORKSPACE_WIDTH / 2.0);
        double minimumY = nodes.values().stream()
                .mapToDouble(CircuitNode::getLayoutY)
                .min().orElse(WORKSPACE_HEIGHT / 2.0);
        double maximumY = nodes.values().stream()
                .mapToDouble(node -> node.getLayoutY() + node.getNodeHeight())
                .max().orElse(WORKSPACE_HEIGHT / 2.0);
        Bounds viewport = workspaceScroll.getViewportBounds();
        workspaceScroll.setHvalue(scrollValueForCenter(
                ((minimumX + maximumX) / 2.0) * currentZoom,
                WORKSPACE_WIDTH * currentZoom,
                viewport.getWidth()));
        workspaceScroll.setVvalue(scrollValueForCenter(
                ((minimumY + maximumY) / 2.0) * currentZoom,
                WORKSPACE_HEIGHT * currentZoom,
                viewport.getHeight()));
    }

    private void updateAllWireGeometry() {
        assignFanoutDepartureOffsets();
        List<OccupiedSegment> occupiedSegments = new ArrayList<>();
        wires.stream()
                .sorted(Comparator.comparingInt(WireConnection::getConnectionId))
                .forEach(wire -> {
                    wire.updateGeometry(
                            nodes.values(),
                            occupiedSegments,
                            WORKSPACE_WIDTH,
                            WORKSPACE_HEIGHT);
                    occupiedSegments.addAll(wire.getOccupiedSegments());
                });
        renderWireCrossings();
    }

    private void updateConnectedWireGeometry(Set<CircuitNode> movedNodes) {
        assignFanoutDepartureOffsets();
        List<WireConnection> affected = wires.stream()
                .filter(wire -> movedNodes.contains(wire.getSource())
                        || movedNodes.contains(wire.getTarget()))
                .sorted(Comparator.comparingInt(WireConnection::getConnectionId))
                .toList();
        Set<WireConnection> affectedSet = new HashSet<>(affected);
        List<OccupiedSegment> occupiedSegments = new ArrayList<>();
        wires.stream()
                .filter(wire -> !affectedSet.contains(wire))
                .forEach(wire -> occupiedSegments.addAll(wire.getOccupiedSegments()));

        for (WireConnection wire : affected) {
            wire.updateGeometry(
                    nodes.values(),
                    occupiedSegments,
                    WORKSPACE_WIDTH,
                    WORKSPACE_HEIGHT);
            occupiedSegments.addAll(wire.getOccupiedSegments());
        }
        renderWireCrossings();
    }

    private void assignFanoutDepartureOffsets() {
        Map<OutputPinKey, List<WireConnection>> groups = new LinkedHashMap<>();
        for (WireConnection wire : wires) {
            OutputPinKey key = new OutputPinKey(wire.getSource(), wire.getSourceOutputIndex());
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(wire);
        }

        for (Map.Entry<OutputPinKey, List<WireConnection>> entry : groups.entrySet()) {
            OutputPinKey key = entry.getKey();
            List<WireConnection> group = entry.getValue();
            group.forEach(wire -> wire.setSourceDepartureOffset(0));
            if (group.size() <= 1) {
                continue;
            }

            CircuitNode.PinSide side = key.source().outputPinSide(key.outputIndex());
            Point2D sourcePoint = new Point2D(
                    key.source().outputAnchorX(key.outputIndex()),
                    key.source().outputAnchorY(key.outputIndex()));

            WireConnection straightWire = group.stream()
                    .min(Comparator
                            .comparingDouble((WireConnection wire) -> Math.abs(
                                    perpendicularDelta(
                                            side,
                                            sourcePoint,
                                            new Point2D(
                                                    wire.getTarget().inputAnchorX(wire.getTargetInputIndex()),
                                                    wire.getTarget().inputAnchorY(wire.getTargetInputIndex())))))
                            .thenComparingInt(WireConnection::getConnectionId))
                    .orElse(group.get(0));

            int negativeLane = 0;
            int positiveLane = 0;
            for (WireConnection wire : group.stream()
                    .sorted(Comparator.comparingInt(WireConnection::getConnectionId))
                    .toList()) {
                if (wire == straightWire) {
                    continue;
                }
                Point2D targetPoint = new Point2D(
                        wire.getTarget().inputAnchorX(wire.getTargetInputIndex()),
                        wire.getTarget().inputAnchorY(wire.getTargetInputIndex()));
                double delta = perpendicularDelta(side, sourcePoint, targetPoint);
                boolean useNegative = Math.abs(delta) < GRID_SIZE
                        ? negativeLane <= positiveLane
                        : delta < 0;
                int laneIndex = useNegative ? negativeLane++ : positiveLane++;
                double magnitude = FANOUT_FIRST_OFFSET + laneIndex * FANOUT_LANE_STEP;
                wire.setSourceDepartureOffset(useNegative ? -magnitude : magnitude);
            }
        }
    }

    private double suggestedPreviewDepartureOffset(
            CircuitNode source,
            int sourceOutputIndex,
            Point2D targetPoint) {

        List<WireConnection> outgoing = wires.stream()
                .filter(wire -> wire.getSource() == source
                        && wire.getSourceOutputIndex() == sourceOutputIndex)
                .toList();
        if (outgoing.isEmpty()) {
            return 0;
        }

        CircuitNode.PinSide side = source.outputPinSide(sourceOutputIndex);
        Point2D sourcePoint = new Point2D(
                source.outputAnchorX(sourceOutputIndex),
                source.outputAnchorY(sourceOutputIndex));
        double delta = perpendicularDelta(side, sourcePoint, targetPoint);
        boolean useNegative;
        if (Math.abs(delta) < GRID_SIZE) {
            long negativeCount = outgoing.stream()
                    .filter(wire -> wire.getSourceDepartureOffset() < 0)
                    .count();
            long positiveCount = outgoing.stream()
                    .filter(wire -> wire.getSourceDepartureOffset() > 0)
                    .count();
            useNegative = negativeCount <= positiveCount;
        } else {
            useNegative = delta < 0;
        }

        long laneCount = outgoing.stream()
                .filter(wire -> useNegative
                        ? wire.getSourceDepartureOffset() < 0
                        : wire.getSourceDepartureOffset() > 0)
                .count();
        double magnitude = FANOUT_FIRST_OFFSET + laneCount * FANOUT_LANE_STEP;
        return useNegative ? -magnitude : magnitude;
    }

    private static double perpendicularDelta(
            CircuitNode.PinSide side, Point2D source, Point2D target) {
        return switch (side) {
            case LEFT, RIGHT -> target.getY() - source.getY();
            case TOP, BOTTOM -> target.getX() - source.getX();
        };
    }

    private List<OccupiedSegment> collectOccupiedSegments() {
        return wires.stream()
                .flatMap(wire -> wire.getOccupiedSegments().stream())
                .toList();
    }

    private static long netId(CircuitNode source, int sourceOutputIndex) {
        return ((long) source.getComponentId() << 32)
                | (sourceOutputIndex & 0xffffffffL);
    }

    private void renderWireCrossings() {
        Map<WireConnection, List<Point2D>> bridges =
                WireCrossingDetector.findHorizontalBridges(wires);
        for (WireConnection wire : wires) {
            wire.renderWithBridges(bridges.getOrDefault(wire, List.of()));
        }
    }

    private void updateWireSignals() {
        wires.forEach(WireConnection::updateSignalVisual);
    }

    private CircuitSnapshot captureSnapshot() {
        List<ComponentState> componentStates = nodes.values().stream()
                .sorted(Comparator.comparingInt(CircuitNode::getComponentId))
                .map(this::captureComponentState)
                .toList();
        List<WireState> wireStates = wires.stream()
                .sorted(Comparator.comparingInt(WireConnection::getConnectionId))
                .map(MainController::captureWireState)
                .toList();
        return new CircuitSnapshot(
                componentStates,
                wireStates,
                nextId,
                nextWireId,
                Map.copyOf(componentLabelCounters));
    }

    private ComponentState captureComponentState(CircuitNode node) {
        List<Boolean> outputs = new ArrayList<>();
        for (boolean state : node.getOutputStatesCopy()) {
            outputs.add(state);
        }
        List<Boolean> segments = new ArrayList<>();
        for (boolean state : node.getSevenSegmentStatesCopy()) {
            segments.add(state);
        }
        return new ComponentState(
                node.getComponentId(),
                node.getType(),
                node.getConfig(),
                node.getLayoutX(),
                node.getLayoutY(),
                node.getOrientation(),
                node.getUserLabel(),
                List.copyOf(outputs),
                node.getDisplayState(),
                List.copyOf(segments),
                node.getStoredState(),
                node.getPreviousClockState(),
                node.getCustomDefinitionId(),
                node.exportCustomState(),
                node.getIcDefinitionId(),
                node.exportIcState());
    }

    private static WireState captureWireState(WireConnection wire) {
        return new WireState(
                wire.getConnectionId(),
                wire.getSource().getComponentId(),
                wire.getSourceOutputIndex(),
                wire.getTarget().getComponentId(),
                wire.getTargetInputIndex(),
                wire.getBendAnchors());
    }

    private LogicForgeProjectFile toProjectFile(CircuitSnapshot snapshot) {
        List<ComponentData> components = snapshot.components().stream()
                .map(state -> new ComponentData(
                        state.componentId(),
                        state.type().name(),
                        state.config().bitWidth(),
                        state.config().clockFrequencyHz(),
                        state.x(),
                        state.y(),
                        state.orientation().name(),
                        state.label(),
                        state.outputStates(),
                        state.displayState(),
                        state.sevenSegmentStates(),
                        state.storedState(),
                        state.previousClockState(),
                        state.customDefinitionId(),
                        state.customState(),
                        state.icDefinitionId(),
                        state.icState()))
                .toList();

        List<WireData> wires = snapshot.wires().stream()
                .map(state -> new WireData(
                        state.connectionId(),
                        state.sourceComponentId(),
                        state.sourceOutputIndex(),
                        state.targetComponentId(),
                        state.targetInputIndex(),
                        state.bendAnchors().stream()
                                .map(point -> new PointData(point.getX(), point.getY()))
                                .toList()))
                .toList();

        Map<String, Integer> counters = new LinkedHashMap<>();
        snapshot.labelCounters().forEach((type, value) -> counters.put(type.name(), value));

        return new LogicForgeProjectFile(
                LogicForgeProjectFile.CURRENT_FORMAT_VERSION,
                components,
                wires,
                snapshot.nextComponentId(),
                snapshot.nextWireId(),
                counters,
                reachableCustomDefinitions(snapshot));
    }

    private List<CustomComponentDefinition> reachableCustomDefinitions(CircuitSnapshot snapshot) {
        Set<String> required = new LinkedHashSet<>();
        snapshot.components().stream()
                .filter(state -> state.type() == ComponentType.CUSTOM)
                .map(ComponentState::customDefinitionId)
                .forEach(required::add);

        List<CustomComponentDefinition> result = new ArrayList<>();
        Queue<String> queue = new ArrayDeque<>(required);
        while (!queue.isEmpty()) {
            String id = queue.remove();
            CustomComponentDefinition definition = customDefinitions.get(id);
            if (definition == null) {
                throw new IllegalArgumentException("Missing custom-component definition " + id);
            }
            result.add(definition);
            for (InternalComponent component : definition.components()) {
                if (component.type() == ComponentType.CUSTOM
                        && required.add(component.customDefinitionId())) {
                    queue.add(component.customDefinitionId());
                }
            }
        }
        return List.copyOf(result);
    }

    private static CircuitSnapshot fromProjectFile(LogicForgeProjectFile project) {
        if (project.formatVersion() < 1
                || project.formatVersion() > LogicForgeProjectFile.CURRENT_FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported .lgf format version");
        }
        if (!"SCHEMATIC".equals(project.workspaceMode())) {
            throw new IllegalArgumentException(
                    "This project belongs to the breadboard workspace");
        }

        Map<String, CustomComponentDefinition> projectDefinitions = new LinkedHashMap<>();
        for (CustomComponentDefinition definition : project.customDefinitions()) {
            if (projectDefinitions.putIfAbsent(definition.id(), definition) != null) {
                throw new IllegalArgumentException("Duplicate custom-component definition ID");
            }
        }
        for (CustomComponentDefinition definition : projectDefinitions.values()) {
            new CustomComponentRuntime(definition, projectDefinitions::get);
        }

        List<ComponentState> components = new ArrayList<>();
        Map<Integer, ComponentState> componentsById = new LinkedHashMap<>();
        int maximumComponentId = 0;

        for (ComponentData data : project.components()) {
            if (data.componentId() <= 0 || componentsById.containsKey(data.componentId())) {
                throw new IllegalArgumentException("Component IDs must be unique positive integers");
            }

            ComponentType type;
            ComponentOrientation orientation;
            try {
                type = ComponentType.valueOf(data.type());
                orientation = ComponentOrientation.valueOf(data.orientation());
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "Unknown component type or orientation in .lgf file", exception);
            }

            ComponentConfig config = new ComponentConfig(
                    data.bitWidth(), data.clockFrequencyHz());
            CustomComponentDefinition customDefinition = type == ComponentType.CUSTOM
                    ? projectDefinitions.get(data.customDefinitionId())
                    : null;
            IcDefinition icDefinition = type == ComponentType.INTEGRATED_CIRCUIT
                    ? IcCatalog.find(data.icDefinitionId())
                    : null;
            if (type == ComponentType.CUSTOM && customDefinition == null) {
                throw new IllegalArgumentException(
                        "Component " + data.componentId() + " references a missing custom definition");
            }
            if (type != ComponentType.CUSTOM && !data.customDefinitionId().isBlank()) {
                throw new IllegalArgumentException(
                        "Ordinary component cannot reference a custom definition");
            }
            if (type == ComponentType.INTEGRATED_CIRCUIT && icDefinition == null) {
                throw new IllegalArgumentException(
                        "Component " + data.componentId() + " references an unknown IC definition");
            }
            if (type != ComponentType.INTEGRATED_CIRCUIT && !data.icDefinitionId().isBlank()) {
                throw new IllegalArgumentException(
                        "Ordinary component cannot reference an IC definition");
            }
            int expectedOutputCount = type == ComponentType.CUSTOM
                    ? customDefinition.outputCount()
                    : type == ComponentType.INTEGRATED_CIRCUIT
                            ? icDefinition.outputCount()
                            : type.outputCount(config);
            if (data.outputStates().size() != expectedOutputCount) {
                throw new IllegalArgumentException(
                        "Component " + data.componentId() + " has an invalid output-state count");
            }
            int expectedSegmentStates = type == ComponentType.SEVEN_SEGMENT_DISPLAY ? 7 : 0;
            if (data.sevenSegmentStates().size() != expectedSegmentStates) {
                throw new IllegalArgumentException(
                        "Component " + data.componentId() + " has an invalid seven-segment state");
            }

            ComponentState state = new ComponentState(
                    data.componentId(),
                    type,
                    config,
                    data.x(),
                    data.y(),
                    orientation,
                    data.label(),
                    data.outputStates(),
                    data.displayState(),
                    data.sevenSegmentStates(),
                    data.storedState(),
                    data.previousClockState(),
                    data.customDefinitionId(),
                    data.customState(),
                    data.icDefinitionId(),
                    data.icState());

            if (type == ComponentType.CUSTOM && !data.customState().isBlank()) {
                CustomComponentRuntime runtime = new CustomComponentRuntime(
                        customDefinition, projectDefinitions::get);
                runtime.importState(data.customState());
            }
            if (type == ComponentType.INTEGRATED_CIRCUIT && !data.icState().isBlank()) {
                com.logicforge.model.IcRuntime runtime = new com.logicforge.model.IcRuntime(icDefinition);
                runtime.importState(data.icState());
            }
            components.add(state);
            componentsById.put(data.componentId(), state);
            maximumComponentId = Math.max(maximumComponentId, data.componentId());
        }

        List<WireState> wires = new ArrayList<>();
        Set<Integer> wireIds = new HashSet<>();
        Set<String> occupiedInputs = new HashSet<>();
        int maximumWireId = 0;
        for (WireData data : project.wires()) {
            if (data.connectionId() <= 0 || !wireIds.add(data.connectionId())) {
                throw new IllegalArgumentException("Wire IDs must be unique positive integers");
            }
            ComponentState source = componentsById.get(data.sourceComponentId());
            ComponentState target = componentsById.get(data.targetComponentId());
            if (source == null || target == null) {
                throw new IllegalArgumentException(
                        "Wire " + data.connectionId() + " references a missing component");
            }
            if (source.componentId() == target.componentId()) {
                throw new IllegalArgumentException(
                        "Wire " + data.connectionId() + " uses unsupported direct self-feedback");
            }
            if (data.sourceOutputIndex() < 0
                    || data.sourceOutputIndex() >= outputCount(source, projectDefinitions)) {
                throw new IllegalArgumentException(
                        "Wire " + data.connectionId() + " references an invalid source pin");
            }
            if (data.targetInputIndex() < 0
                    || data.targetInputIndex() >= inputCount(target, projectDefinitions)) {
                throw new IllegalArgumentException(
                        "Wire " + data.connectionId() + " references an invalid target pin");
            }
            String targetKey = data.targetComponentId() + ":" + data.targetInputIndex();
            if (!occupiedInputs.add(targetKey)) {
                throw new IllegalArgumentException(
                        "Multiple wires drive the same input pin in the .lgf file");
            }

            wires.add(new WireState(
                    data.connectionId(),
                    data.sourceComponentId(),
                    data.sourceOutputIndex(),
                    data.targetComponentId(),
                    data.targetInputIndex(),
                    data.bendAnchors().stream()
                            .map(point -> new Point2D(point.x(), point.y()))
                            .toList()));
            maximumWireId = Math.max(maximumWireId, data.connectionId());
        }

        if (project.nextComponentId() <= maximumComponentId) {
            throw new IllegalArgumentException("nextComponentId is inconsistent with the saved circuit");
        }
        if (project.nextWireId() <= maximumWireId) {
            throw new IllegalArgumentException("nextWireId is inconsistent with the saved circuit");
        }

        Map<ComponentType, Integer> counters = new EnumMap<>(ComponentType.class);
        for (Map.Entry<String, Integer> entry : project.labelCounters().entrySet()) {
            ComponentType type;
            try {
                type = ComponentType.valueOf(entry.getKey());
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "Unknown component type in label counters: " + entry.getKey(), exception);
            }
            if (entry.getValue() < 0) {
                throw new IllegalArgumentException("Label counters cannot be negative");
            }
            counters.put(type, entry.getValue());
        }

        return new CircuitSnapshot(
                List.copyOf(components),
                List.copyOf(wires),
                project.nextComponentId(),
                project.nextWireId(),
                Map.copyOf(counters));
    }

    private static int inputCount(
            ComponentState state,
            Map<String, CustomComponentDefinition> definitions) {

        if (state.type() == ComponentType.INTEGRATED_CIRCUIT) {
            IcDefinition definition = IcCatalog.find(state.icDefinitionId());
            return definition == null ? -1 : definition.inputCount();
        }
        if (state.type() != ComponentType.CUSTOM) {
            return state.type().inputCount(state.config());
        }
        CustomComponentDefinition definition = definitions.get(state.customDefinitionId());
        return definition == null ? -1 : definition.inputCount();
    }

    private static int outputCount(
            ComponentState state,
            Map<String, CustomComponentDefinition> definitions) {

        if (state.type() == ComponentType.INTEGRATED_CIRCUIT) {
            IcDefinition definition = IcCatalog.find(state.icDefinitionId());
            return definition == null ? -1 : definition.outputCount();
        }
        if (state.type() != ComponentType.CUSTOM) {
            return state.type().outputCount(state.config());
        }
        CustomComponentDefinition definition = definitions.get(state.customDefinitionId());
        return definition == null ? -1 : definition.outputCount();
    }

    private void rememberCurrentState() {
        if (!restoringSnapshot) {
            pushUndoSnapshot(captureSnapshot());
            markDocumentDirty();
        }
    }

    private void markDocumentDirty() {
        if (!documentDirty) {
            documentDirty = true;
            publishDocumentState();
        }
    }

    private void markDocumentClean() {
        cleanSnapshot = captureSnapshot();
        documentDirty = false;
        publishDocumentState();
    }

    private void refreshDocumentDirty() {
        documentDirty = cleanSnapshot == null || !cleanSnapshot.equals(captureSnapshot());
        publishDocumentState();
    }

    private void pushUndoSnapshot(CircuitSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        redoHistory.clear();
        if (!undoHistory.isEmpty() && undoHistory.peek().equals(snapshot)) {
            updateEditorActionButtons();
            return;
        }
        undoHistory.push(snapshot);
        while (undoHistory.size() > MAX_HISTORY_SIZE) {
            undoHistory.removeLast();
        }
        updateEditorActionButtons();
    }

    private void restoreSnapshot(CircuitSnapshot snapshot) {
        restoringSnapshot = true;
        try {
            clearWorkspace(false);
            componentLabelCounters.clear();

            for (ComponentState state : snapshot.components()) {
                nextId = state.componentId();
                CircuitNode node;
                if (state.type() == ComponentType.CUSTOM) {
                    CustomComponentDefinition definition = customDefinitions.get(
                            state.customDefinitionId());
                    if (definition == null) {
                        throw new IllegalArgumentException(
                                "Missing custom-component definition " + state.customDefinitionId());
                    }
                    node = addCustomComponent(definition, state.x(), state.y());
                } else if (state.type() == ComponentType.INTEGRATED_CIRCUIT) {
                    IcDefinition definition = IcCatalog.find(state.icDefinitionId());
                    if (definition == null) {
                        throw new IllegalArgumentException(
                                "Missing integrated-circuit definition " + state.icDefinitionId());
                    }
                    node = addIntegratedCircuit(definition, state.x(), state.y());
                } else {
                    node = addComponent(
                            state.type(), state.config(), state.x(), state.y());
                }
                node.setUserLabel(state.label());
                applyComponentState(node, state);
            }
            nextId = snapshot.nextComponentId();
            componentLabelCounters.clear();
            componentLabelCounters.putAll(snapshot.labelCounters());

            for (WireState state : snapshot.wires()) {
                CircuitNode source = nodes.get(state.sourceComponentId());
                CircuitNode target = nodes.get(state.targetComponentId());
                if (source == null || target == null) {
                    continue;
                }
                nextWireId = state.connectionId();
                WireConnection wire = createWire(
                        source,
                        state.sourceOutputIndex(),
                        target,
                        state.targetInputIndex(),
                        false);
                wire.setBendAnchors(state.bendAnchors());
            }
            nextWireId = snapshot.nextWireId();
            clearSelection();
            updateAllWireGeometry();
            for (CircuitNode node : nodes.values()) {
                if (node.getType() == ComponentType.INTEGRATED_CIRCUIT) {
                    configureIcPowerConnections(node);
                    node.evaluateIcCombinational(readInputs(node));
                }
            }
            updateWireSignals();
            applySimulationMode();
            updatePropertiesPanel();
            updateDashboard();
        } finally {
            restoringSnapshot = false;
            updateEditorActionButtons();
        }
    }

    private static void applyComponentState(CircuitNode node, ComponentState state) {
        node.setOrientation(state.orientation());
        node.importIcState(state.icState());
        for (int index = 0; index < state.outputStates().size(); index++) {
            node.setOutputState(index, state.outputStates().get(index));
        }
        node.setDisplayState(state.displayState());
        boolean[] segmentStates = new boolean[state.sevenSegmentStates().size()];
        for (int index = 0; index < segmentStates.length; index++) {
            segmentStates[index] = state.sevenSegmentStates().get(index);
        }
        node.setSevenSegmentStates(segmentStates);
        node.setStoredState(state.storedState());
        node.setPreviousClockState(state.previousClockState());
        node.importCustomState(state.customState());
        if (node.getType() == ComponentType.VCC) {
            node.setOutputState(0, true);
        } else if (node.getType() == ComponentType.GROUND) {
            node.setOutputState(0, false);
        }
    }

    @FXML
    private void handleUndo() {
        if (simulationRunning || undoHistory.isEmpty()) {
            return;
        }
        CircuitSnapshot current = captureSnapshot();
        CircuitSnapshot previous = undoHistory.pop();
        redoHistory.push(current);
        restoreSnapshot(previous);
        refreshDocumentDirty();
    }

    @FXML
    private void handleRedo() {
        if (simulationRunning || redoHistory.isEmpty()) {
            return;
        }
        CircuitSnapshot current = captureSnapshot();
        CircuitSnapshot next = redoHistory.pop();
        undoHistory.push(current);
        while (undoHistory.size() > MAX_HISTORY_SIZE) {
            undoHistory.removeLast();
        }
        restoreSnapshot(next);
        refreshDocumentDirty();
    }

    @FXML
    private void handleCreateAbstraction() {
        if (simulationRunning || selectedNodes.size() < 2) {
            return;
        }

        Set<CircuitNode> selection = new LinkedHashSet<>(selectedNodes);
        if (selection.stream().anyMatch(node ->
                node.getType() == ComponentType.INTEGRATED_CIRCUIT)) {
            showAbstractionError(
                    "Physical IC packages cannot be nested inside custom blocks.",
                    "Build the block from LogicForge gates and functional blocks. The physical 74HC package remains a top-level teaching component with explicit VCC and GND pins.");
            return;
        }
        List<WireConnection> boundaryWires = wires.stream()
                .filter(wire -> selection.contains(wire.getSource())
                        != selection.contains(wire.getTarget()))
                .toList();
        boolean invalidBoundary = boundaryWires.stream().anyMatch(wire -> {
            CircuitNode selectedEndpoint = selection.contains(wire.getSource())
                    ? wire.getSource()
                    : wire.getTarget();
            return selectedEndpoint.getType() != ComponentType.JUNCTION;
        });
        if (invalidBoundary) {
            showAbstractionError(
                    "A boundary connection does not pass through a junction.",
                    "Place a junction at each selected input or output boundary. LogicForge can then preserve connections outside the new block.");
            return;
        }

        Map<CircuitNode, JunctionRole> junctionRoles = new LinkedHashMap<>();
        for (CircuitNode junction : selection.stream()
                .filter(node -> node.getType() == ComponentType.JUNCTION)
                .sorted(portPositionComparator())
                .toList()) {

            boolean incomingBoundary = boundaryWires.stream().anyMatch(wire ->
                    wire.getTarget() == junction && !selection.contains(wire.getSource()));
            boolean outgoingBoundary = boundaryWires.stream().anyMatch(wire ->
                    wire.getSource() == junction && !selection.contains(wire.getTarget()));
            long internalIncoming = wires.stream().filter(wire ->
                    wire.getTarget() == junction && selection.contains(wire.getSource())).count();
            long internalOutgoing = wires.stream().filter(wire ->
                    wire.getSource() == junction && selection.contains(wire.getTarget())).count();

            boolean input = incomingBoundary
                    || (!outgoingBoundary && internalIncoming == 0 && internalOutgoing > 0);
            boolean output = outgoingBoundary
                    || (!incomingBoundary && internalOutgoing == 0 && internalIncoming > 0);
            if (input && output) {
                showAbstractionError(
                        "A junction has an ambiguous boundary role.",
                        "Use separate junctions for block input and output ports. A single junction cannot be both in the same block.");
                return;
            }
            junctionRoles.put(junction, input
                    ? JunctionRole.INPUT
                    : (output ? JunctionRole.OUTPUT : JunctionRole.INTERNAL));
        }

        List<CircuitNode> explicitInputNodes = selection.stream()
                .filter(node -> node.getType() == ComponentType.LOGIC_INPUT
                        || node.getType() == ComponentType.SWITCH
                        || junctionRoles.get(node) == JunctionRole.INPUT)
                .sorted(portPositionComparator())
                .toList();
        List<CircuitNode> explicitOutputNodes = selection.stream()
                .filter(node -> node.getType() == ComponentType.LOGIC_OUTPUT
                        || node.getType() == ComponentType.BULB
                        || junctionRoles.get(node) == JunctionRole.OUTPUT)
                .sorted(portPositionComparator())
                .toList();

        /*
         * Unwired ("naked") pins are useful block boundaries too. Represent each
         * one with a hidden synthetic marker inside the definition so the runtime
         * can keep using its proven junction/Logic Output port model. These marker
         * nodes never appear on the workspace and need no file-format migration.
         */
        int syntheticComponentId = selection.stream()
                .mapToInt(CircuitNode::getComponentId)
                .max().orElse(0) + 1;
        int syntheticWireId = wires.stream()
                .filter(wire -> selection.contains(wire.getSource())
                        && selection.contains(wire.getTarget()))
                .mapToInt(WireConnection::getConnectionId)
                .max().orElse(0) + 1;
        List<CircuitNode> syntheticBoundaryNodes = new ArrayList<>();
        List<CircuitNode> nakedInputNodes = new ArrayList<>();
        List<CircuitNode> nakedOutputNodes = new ArrayList<>();
        List<InternalWire> syntheticBoundaryWires = new ArrayList<>();
        Set<String> detectedInputNames = new HashSet<>();
        explicitInputNodes.stream()
                .map(CircuitNode::getUserLabel)
                .map(String::strip)
                .filter(name -> !name.isBlank())
                .map(String::toLowerCase)
                .forEach(detectedInputNames::add);
        Set<String> detectedOutputNames = new HashSet<>();
        explicitOutputNodes.stream()
                .map(CircuitNode::getUserLabel)
                .map(String::strip)
                .filter(name -> !name.isBlank())
                .map(String::toLowerCase)
                .forEach(detectedOutputNames::add);

        for (CircuitNode node : selection.stream().sorted(portPositionComparator()).toList()) {
            boolean explicitInputMarker = explicitInputNodes.contains(node);
            boolean explicitOutputMarker = explicitOutputNodes.contains(node);
            for (int inputIndex = 0; inputIndex < node.getInputCount(); inputIndex++) {
                int pinIndex = inputIndex;
                boolean connected = wires.stream().anyMatch(wire ->
                        wire.getTarget() == node && wire.getTargetInputIndex() == pinIndex);
                boolean implicitAsyncPullUp = node.getType().isFlipFlop()
                        && (inputIndex == node.getType().presetInputIndex(node.getConfig())
                                || inputIndex == node.getType().clearInputIndex(node.getConfig()));
                if (connected || implicitAsyncPullUp || explicitInputMarker || explicitOutputMarker) {
                    continue;
                }
                CircuitNode marker = new CircuitNode(
                        syntheticComponentId++, ComponentType.JUNCTION, ComponentConfig.defaults());
                marker.setUserLabel(uniqueDetectedPortName(
                        node.getInputName(inputIndex), "IN", detectedInputNames));
                marker.relocate(
                        node.inputAnchorX(inputIndex) - 80,
                        node.inputAnchorY(inputIndex) - marker.getNodeHeight() / 2.0);
                syntheticBoundaryNodes.add(marker);
                nakedInputNodes.add(marker);
                syntheticBoundaryWires.add(new InternalWire(
                        syntheticWireId++, marker.getComponentId(), 0,
                        node.getComponentId(), inputIndex));
            }

            for (int outputIndex = 0; outputIndex < node.getOutputCount(); outputIndex++) {
                int pinIndex = outputIndex;
                boolean connected = wires.stream().anyMatch(wire ->
                        wire.getSource() == node && wire.getSourceOutputIndex() == pinIndex);
                if (connected || explicitInputMarker || explicitOutputMarker) {
                    continue;
                }
                CircuitNode marker = new CircuitNode(
                        syntheticComponentId++, ComponentType.LOGIC_OUTPUT, ComponentConfig.defaults());
                marker.setUserLabel(uniqueDetectedPortName(
                        node.getOutputName(outputIndex), "OUT", detectedOutputNames));
                marker.relocate(
                        node.outputAnchorX(outputIndex) + 80,
                        node.outputAnchorY(outputIndex) - marker.getNodeHeight() / 2.0);
                syntheticBoundaryNodes.add(marker);
                nakedOutputNodes.add(marker);
                syntheticBoundaryWires.add(new InternalWire(
                        syntheticWireId++, node.getComponentId(), outputIndex,
                        marker.getComponentId(), 0));
            }
        }

        List<CircuitNode> inputNodes = new ArrayList<>(explicitInputNodes);
        inputNodes.addAll(nakedInputNodes);
        inputNodes.sort(portPositionComparator());
        List<CircuitNode> outputNodes = new ArrayList<>(explicitOutputNodes);
        outputNodes.addAll(nakedOutputNodes);
        outputNodes.sort(portPositionComparator());

        if (outputNodes.isEmpty()) {
            showAbstractionError(
                    "No output ports were found.",
                    "Add an output junction, Logic Output, or Bulb to the selected circuit.");
            return;
        }

        List<Port> detectedInputPorts = createPorts(inputNodes, "IN");
        List<Port> detectedOutputPorts = createPorts(outputNodes, "OUT");
        Optional<BlockCreationRequest> request = showBlockCreationDialog(
                selection.size(), detectedInputPorts, detectedOutputPorts);
        if (request.isEmpty()) {
            return;
        }
        String name = request.get().name();
        String description = request.get().description();
        List<Port> inputPorts = createPorts(inputNodes, request.get().inputNames());
        List<Port> outputPorts = createPorts(outputNodes, request.get().outputNames());

        double minimumX = selection.stream().mapToDouble(CircuitNode::getLayoutX).min().orElse(0);
        double minimumY = selection.stream().mapToDouble(CircuitNode::getLayoutY).min().orElse(0);
        double maximumX = selection.stream()
                .mapToDouble(node -> node.getLayoutX() + node.getNodeWidth())
                .max().orElse(minimumX);
        double maximumY = selection.stream()
                .mapToDouble(node -> node.getLayoutY() + node.getNodeHeight())
                .max().orElse(minimumY);

        List<InternalComponent> internalComponents = new ArrayList<>();
        selection.stream()
                .sorted(Comparator.comparingInt(CircuitNode::getComponentId))
                .map(node -> toInternalComponent(captureComponentState(node), minimumX, minimumY))
                .forEach(internalComponents::add);
        syntheticBoundaryNodes.stream()
                .sorted(Comparator.comparingInt(CircuitNode::getComponentId))
                .map(node -> toInternalComponent(captureComponentState(node), minimumX, minimumY))
                .forEach(internalComponents::add);
        List<InternalWire> internalWires = new ArrayList<>(wires.stream()
                .filter(wire -> selection.contains(wire.getSource())
                        && selection.contains(wire.getTarget()))
                .sorted(Comparator.comparingInt(WireConnection::getConnectionId))
                .map(wire -> new InternalWire(
                        wire.getConnectionId(),
                        wire.getSource().getComponentId(),
                        wire.getSourceOutputIndex(),
                        wire.getTarget().getComponentId(),
                        wire.getTargetInputIndex()))
                .toList());
        internalWires.addAll(syntheticBoundaryWires);

        CustomComponentDefinition definition;
        try {
            definition = new CustomComponentDefinition(
                    UUID.randomUUID().toString(),
                    name,
                    deriveCustomSymbol(name),
                    description,
                    inputPorts,
                    outputPorts,
                    List.copyOf(internalComponents),
                    List.copyOf(internalWires));
            new CustomComponentRuntime(definition, customDefinitions::get);
        } catch (IllegalArgumentException exception) {
            showAbstractionError("The selected circuit cannot be abstracted.", exception.getMessage());
            return;
        }

        customDefinitions.put(definition.id(), definition);
        if (!saveCustomComponentLibrary()) {
            customDefinitions.remove(definition.id());
            return;
        }

        rememberCurrentState();
        List<WireConnection> removedWires = wires.stream()
                .filter(wire -> selection.contains(wire.getSource())
                        || selection.contains(wire.getTarget()))
                .toList();
        removedWires.forEach(wire -> wireLayer.getChildren().remove(wire.getPath()));
        wires.removeAll(removedWires);
        componentLayer.getChildren().removeAll(selection);
        selection.forEach(node -> {
            nodes.remove(node.getComponentId());
            clockAccumulators.remove(node);
        });
        clearSelectionVisuals();

        CircuitNode customNode = addCustomComponent(
                definition,
                (minimumX + maximumX) / 2.0,
                (minimumY + maximumY) / 2.0);
        customNode.relocate(
                snapAndClamp(
                        (minimumX + maximumX - customNode.getNodeWidth()) / 2.0,
                        WORKSPACE_WIDTH - customNode.getNodeWidth()),
                snapAndClamp(
                        (minimumY + maximumY - customNode.getNodeHeight()) / 2.0,
                        WORKSPACE_HEIGHT - customNode.getNodeHeight()));

        Map<Integer, Integer> inputPortIndexes = new HashMap<>();
        for (int index = 0; index < inputPorts.size(); index++) {
            inputPortIndexes.put(inputPorts.get(index).componentId(), index);
        }
        Map<Integer, Integer> outputPortIndexes = new HashMap<>();
        for (int index = 0; index < outputPorts.size(); index++) {
            outputPortIndexes.put(outputPorts.get(index).componentId(), index);
        }
        for (WireConnection boundary : boundaryWires) {
            if (selection.contains(boundary.getTarget())) {
                Integer inputIndex = inputPortIndexes.get(boundary.getTarget().getComponentId());
                if (inputIndex != null) {
                    createWire(
                            boundary.getSource(),
                            boundary.getSourceOutputIndex(),
                            customNode,
                            inputIndex,
                            false);
                }
            } else {
                Integer outputIndex = outputPortIndexes.get(boundary.getSource().getComponentId());
                if (outputIndex != null) {
                    createWire(
                            customNode,
                            outputIndex,
                            boundary.getTarget(),
                            boundary.getTargetInputIndex(),
                            false);
                }
            }
        }
        selectOnlyNode(customNode);
        filterPalette(paletteSearchField.getText());
        updateAllWireGeometry();
        evaluatePausedNetwork();
        statusMessageLabel.setText(
                "Created reusable “" + definition.name() + "” block • "
                        + inputPorts.size() + " input"
                        + (inputPorts.size() == 1 ? "" : "s") + " • "
                        + outputPorts.size() + " output"
                        + (outputPorts.size() == 1 ? "" : "s"));
    }

    private static Comparator<CircuitNode> portPositionComparator() {
        return Comparator.comparingDouble(CircuitNode::getLayoutY)
                .thenComparingDouble(CircuitNode::getLayoutX)
                .thenComparingInt(CircuitNode::getComponentId);
    }

    private static String uniqueDetectedPortName(
            String preferredName,
            String fallbackPrefix,
            Set<String> usedNames) {

        String base = preferredName == null ? "" : preferredName.strip();
        if (base.isBlank()) {
            base = fallbackPrefix;
        }
        if (base.length() > 28) {
            base = base.substring(0, 28);
        }
        String candidate = base;
        int suffix = 2;
        while (!usedNames.add(candidate.toLowerCase())) {
            String suffixText = Integer.toString(suffix++);
            int maximumBaseLength = Math.max(1, 32 - suffixText.length());
            candidate = base.substring(0, Math.min(base.length(), maximumBaseLength)) + suffixText;
        }
        return candidate;
    }

    private static List<Port> createPorts(List<CircuitNode> nodes, String fallbackPrefix) {
        List<Port> ports = new ArrayList<>();
        for (int index = 0; index < nodes.size(); index++) {
            CircuitNode node = nodes.get(index);
            String name = node.getUserLabel().strip();
            if (name.isBlank()) {
                name = fallbackPrefix + (index + 1);
            }
            ports.add(new Port(name, node.getComponentId()));
        }
        return List.copyOf(ports);
    }

    private static List<Port> createPorts(List<CircuitNode> nodes, List<String> names) {
        if (nodes.size() != names.size()) {
            throw new IllegalArgumentException("Port-name count does not match the detected ports");
        }
        List<Port> ports = new ArrayList<>();
        for (int index = 0; index < nodes.size(); index++) {
            ports.add(new Port(names.get(index), nodes.get(index).getComponentId()));
        }
        return List.copyOf(ports);
    }

    private Optional<BlockCreationRequest> showBlockCreationDialog(
            int selectedComponentCount,
            List<Port> detectedInputs,
            List<Port> detectedOutputs) {

        Dialog<BlockCreationRequest> dialog = new Dialog<>();
        dialog.setTitle("Create Custom Component");
        dialog.setHeaderText(
                "Turn " + selectedComponentCount + " selected components into one reusable block");
        ButtonType createType = new ButtonType("Create Block", ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createType, ButtonType.CANCEL);

        TextField componentName = new TextField("Custom Block");
        componentName.setPromptText("Component name");
        componentName.setPrefColumnCount(28);

        TextArea componentDescription = new TextArea();
        componentDescription.setPromptText("Optional: explain what this block does (240 characters maximum)");
        componentDescription.setPrefRowCount(2);
        componentDescription.setWrapText(true);

        List<TextField> inputFields = detectedInputs.stream()
                .map(port -> new TextField(port.name()))
                .toList();
        List<TextField> outputFields = detectedOutputs.stream()
                .map(port -> new TextField(port.name()))
                .toList();

        GridPane inputGrid = createPortEditorGrid("Input", inputFields);
        GridPane outputGrid = createPortEditorGrid("Output", outputFields);
        Label inputHeading = new Label("INPUT PORTS • top-to-bottom order");
        inputHeading.getStyleClass().add("block-dialog-section-title");
        Label outputHeading = new Label("OUTPUT PORTS • top-to-bottom order");
        outputHeading.getStyleClass().add("block-dialog-section-title");

        VBox portEditors = new VBox(
                9,
                inputHeading,
                inputGrid,
                new Separator(),
                outputHeading,
                outputGrid);
        ScrollPane portScroll = new ScrollPane(portEditors);
        portScroll.setFitToWidth(true);
        portScroll.setPrefViewportWidth(355);
        portScroll.setPrefViewportHeight(420);
        portScroll.getStyleClass().add("panel-scroll");

        CustomBlockPreview preview = new CustomBlockPreview();
        HBox editorBody = new HBox(16, preview, portScroll);
        HBox.setHgrow(portScroll, Priority.ALWAYS);

        Label nameLabel = new Label("Component name");
        nameLabel.getStyleClass().add("block-dialog-section-title");
        HBox nameRow = new HBox(12, nameLabel, componentName);
        nameRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(componentName, Priority.ALWAYS);

        Label descriptionLabel = new Label("Description");
        descriptionLabel.getStyleClass().add("block-dialog-section-title");
        VBox descriptionRow = new VBox(6, descriptionLabel, componentDescription);

        Label validation = new Label();
        validation.getStyleClass().add("block-dialog-validation");
        validation.setWrapText(true);

        VBox content = new VBox(14, nameRow, descriptionRow, editorBody, validation);
        content.getStyleClass().add("block-creation-dialog");
        VBox.setVgrow(editorBody, Priority.ALWAYS);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefSize(930, 650);

        Node createButton = dialog.getDialogPane().lookupButton(createType);
        Runnable refresh = () -> {
            List<String> inputNames = fieldValues(inputFields);
            List<String> outputNames = fieldValues(outputFields);
            String rawName = componentName.getText() == null ? "" : componentName.getText();
            String rawDescription = componentDescription.getText() == null
                    ? ""
                    : componentDescription.getText();
            preview.update(rawName.strip(), deriveCustomSymbol(rawName), inputNames, outputNames);
            String error = blockCreationValidationError(
                    rawName, rawDescription, inputNames, outputNames);
            validation.setText(error == null
                    ? "Preview updates immediately. Port order follows the selected markers from top to bottom."
                    : error);
            validation.pseudoClassStateChanged(ACTIVE, error == null);
            createButton.setDisable(error != null);
        };
        componentName.textProperty().addListener((observable, oldValue, newValue) -> refresh.run());
        componentDescription.textProperty().addListener(
                (observable, oldValue, newValue) -> refresh.run());
        inputFields.forEach(field -> field.textProperty().addListener(
                (observable, oldValue, newValue) -> refresh.run()));
        outputFields.forEach(field -> field.textProperty().addListener(
                (observable, oldValue, newValue) -> refresh.run()));

        styleDialog(dialog);
        dialog.setResultConverter(button -> button == createType
                ? new BlockCreationRequest(
                        componentName.getText().strip(),
                        componentDescription.getText().strip(),
                        normalizedFieldValues(inputFields),
                        normalizedFieldValues(outputFields))
                : null);
        refresh.run();
        Platform.runLater(componentName::selectAll);
        return dialog.showAndWait();
    }

    private static GridPane createPortEditorGrid(String prefix, List<TextField> fields) {
        GridPane grid = new GridPane();
        grid.setHgap(9);
        grid.setVgap(7);
        for (int index = 0; index < fields.size(); index++) {
            Label number = new Label(prefix + " " + (index + 1));
            TextField field = fields.get(index);
            field.setPromptText(prefix.toUpperCase() + (index + 1));
            field.setPrefColumnCount(18);
            GridPane.setHgrow(field, Priority.ALWAYS);
            grid.add(number, 0, index);
            grid.add(field, 1, index);
        }
        if (fields.isEmpty()) {
            Label none = new Label("No external " + prefix.toLowerCase() + "s");
            none.setOpacity(0.72);
            grid.add(none, 0, 0, 2, 1);
        }
        return grid;
    }

    private String blockCreationValidationError(
            String rawName,
            String rawDescription,
            List<String> inputNames,
            List<String> outputNames) {

        String name = rawName == null ? "" : rawName.strip();
        if (name.isBlank()) {
            return "Enter a component name.";
        }
        if (name.length() > 48) {
            return "The component name must contain 48 characters or fewer.";
        }
        String description = rawDescription == null ? "" : rawDescription.strip();
        if (description.length() > 240) {
            return "The description must contain 240 characters or fewer.";
        }
        if (customDefinitions.values().stream()
                .anyMatch(definition -> definition.name().equalsIgnoreCase(name))) {
            return "A custom component named “" + name + "” already exists.";
        }
        String inputError = portNameValidationError("input", inputNames);
        if (inputError != null) {
            return inputError;
        }
        return portNameValidationError("output", outputNames);
    }

    private static String portNameValidationError(String kind, List<String> names) {
        Set<String> unique = new HashSet<>();
        for (String rawName : names) {
            String name = rawName == null ? "" : rawName.strip();
            if (name.isBlank()) {
                return "Every " + kind + " port needs a name.";
            }
            if (name.length() > 32) {
                return "Each " + kind + " port name must contain 32 characters or fewer.";
            }
            if (!unique.add(name.toLowerCase())) {
                return "The " + kind + " port name “" + name + "” is duplicated.";
            }
        }
        return null;
    }

    private static List<String> fieldValues(List<TextField> fields) {
        return fields.stream().map(TextField::getText).toList();
    }

    private static List<String> normalizedFieldValues(List<TextField> fields) {
        return fields.stream().map(TextField::getText).map(String::strip).toList();
    }

    private static String deriveCustomSymbol(String name) {
        List<String> words = Arrays.stream(name.strip().split("[^A-Za-z0-9]+"))
                .filter(word -> !word.isBlank())
                .toList();
        if (words.isEmpty()) {
            return "BLOCK";
        }
        String symbol;
        if (words.size() == 1) {
            symbol = words.get(0).toUpperCase();
        } else {
            StringBuilder initials = new StringBuilder();
            for (String word : words) {
                initials.append(Character.toUpperCase(word.charAt(0)));
            }
            symbol = initials.toString();
        }
        return symbol.substring(0, Math.min(10, symbol.length()));
    }

    private static InternalComponent toInternalComponent(
            ComponentState state,
            double originX,
            double originY) {

        return new InternalComponent(
                state.componentId(),
                state.type(),
                state.config(),
                state.customDefinitionId(),
                state.x() - originX,
                state.y() - originY,
                state.orientation(),
                state.label(),
                state.outputStates(),
                state.displayState(),
                state.sevenSegmentStates(),
                state.storedState(),
                state.previousClockState(),
                state.customState());
    }

    private void showAbstractionError(String header, String details) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Create Custom Component");
        alert.setHeaderText(header);
        alert.setContentText(details == null ? "" : details);
        styleDialog(alert);
        alert.showAndWait();
    }

    @FXML
    private void handleCopySelection() {
        if (simulationRunning || selectedNodes.isEmpty()) {
            return;
        }
        Set<Integer> selectedIds = selectedNodes.stream()
                .map(CircuitNode::getComponentId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<ComponentState> components = selectedNodes.stream()
                .sorted(Comparator.comparingInt(CircuitNode::getComponentId))
                .map(this::captureComponentState)
                .toList();
        List<WireState> internalWires = wires.stream()
                .filter(wire -> selectedIds.contains(wire.getSource().getComponentId())
                        && selectedIds.contains(wire.getTarget().getComponentId()))
                .sorted(Comparator.comparingInt(WireConnection::getConnectionId))
                .map(MainController::captureWireState)
                .toList();
        clipboard = new ClipboardSnapshot(components, internalWires);
        pasteGeneration = 0;
        updateEditorActionButtons();
        updateDashboard();
        statusMessageLabel.setText(
                "Copied " + components.size() + " component"
                        + (components.size() == 1 ? "" : "s")
                        + " and " + internalWires.size() + " internal wire"
                        + (internalWires.size() == 1 ? "" : "s"));
    }

    @FXML
    private void handlePasteSelection() {
        if (simulationRunning || clipboard == null || clipboard.components().isEmpty()) {
            return;
        }
        rememberCurrentState();
        pasteGeneration++;
        double offset = PASTE_OFFSET * pasteGeneration;
        Map<Integer, CircuitNode> pastedByOriginalId = new LinkedHashMap<>();

        clearSelectionVisuals();
        for (ComponentState state : clipboard.components()) {
            CircuitNode pasted;
            if (state.type() == ComponentType.CUSTOM) {
                CustomComponentDefinition definition = customDefinitions.get(
                        state.customDefinitionId());
                if (definition == null) {
                    showFileError(
                            "Could not paste custom component",
                            "Definition " + state.customDefinitionId() + " is unavailable.");
                    return;
                }
                pasted = addCustomComponent(
                        definition,
                        state.x() + offset,
                        state.y() + offset);
            } else if (state.type() == ComponentType.INTEGRATED_CIRCUIT) {
                IcDefinition definition = IcCatalog.find(state.icDefinitionId());
                if (definition == null) {
                    showFileError(
                            "Could not paste integrated circuit",
                            "Definition " + state.icDefinitionId() + " is unavailable.");
                    return;
                }
                pasted = addIntegratedCircuit(
                        definition,
                        state.x() + offset,
                        state.y() + offset);
            } else {
                pasted = addComponent(
                        state.type(),
                        state.config(),
                        state.x() + offset,
                        state.y() + offset);
            }
            // addComponent already assigns the next unique type label (e.g. DFF4).
            // Copy/paste must not duplicate the original component label.
            applyComponentState(pasted, state);
            pastedByOriginalId.put(state.componentId(), pasted);
        }

        for (WireState state : clipboard.wires()) {
            CircuitNode source = pastedByOriginalId.get(state.sourceComponentId());
            CircuitNode target = pastedByOriginalId.get(state.targetComponentId());
            if (source != null && target != null) {
                WireConnection pastedWire = createWire(
                        source,
                        state.sourceOutputIndex(),
                        target,
                        state.targetInputIndex(),
                        false);
                pastedWire.setBendAnchors(state.bendAnchors().stream()
                        .map(point -> point.add(offset, offset))
                        .toList());
            }
        }

        clearSelectionVisuals();
        pastedByOriginalId.values().forEach(this::addNodeToSelection);
        updateAllWireGeometry();
        evaluatePausedNetwork();
        refreshSelectionUi();
    }

    private void updateEditorActionButtons() {
        if (undoButton == null) {
            return;
        }
        undoButton.setDisable(simulationRunning || undoHistory.isEmpty());
        redoButton.setDisable(simulationRunning || redoHistory.isEmpty());
        copyButton.setDisable(simulationRunning || selectedNodes.isEmpty());
        pasteButton.setDisable(simulationRunning || clipboard == null);
        rotateButton.setDisable(simulationRunning || selectedNodes.isEmpty());
        abstractButton.setDisable(simulationRunning || selectedNodes.size() < 2);
    }

    @FXML
    private void handleRotateSelection() {
        if (simulationRunning || selectedNodes.isEmpty()) {
            return;
        }
        rememberCurrentState();
        for (CircuitNode node : selectedNodes) {
            node.rotateClockwise();
        }
        updateAllWireGeometry();
        refreshSelectionUi();
    }

    @FXML
    private void handleDeleteSelected() {
        if (simulationRunning || (selectedNodes.isEmpty() && selectedWires.isEmpty())) {
            return;
        }

        rememberCurrentState();
        Set<CircuitNode> nodesToDelete = new LinkedHashSet<>(selectedNodes);
        Set<WireConnection> wiresToDelete = new LinkedHashSet<>(selectedWires);
        wires.stream()
                .filter(wire -> nodesToDelete.contains(wire.getSource())
                        || nodesToDelete.contains(wire.getTarget()))
                .forEach(wiresToDelete::add);

        wiresToDelete.forEach(wire -> wireLayer.getChildren().remove(wire.getPath()));
        wires.removeAll(wiresToDelete);
        componentLayer.getChildren().removeAll(nodesToDelete);
        nodesToDelete.forEach(node -> {
            nodes.remove(node.getComponentId());
            clockAccumulators.remove(node);
        });

        clearSelection();
        cancelWirePreview();
        updateAllWireGeometry();
        evaluatePausedNetwork();
    }

    @FXML
    private void handleNewCircuit() {
        if (simulationRunning) {
            return;
        }
        if (!confirmDiscardChanges("creating a new project")) {
            return;
        }
        startNewProject();
    }

    @FXML
    private void handleNewBreadboard() {
        if (simulationRunning || !confirmDiscardChanges("creating a breadboard project")) {
            return;
        }
        breadboardAction.run();
    }

    @FXML
    private void handleClearCircuit() {
        if (!simulationRunning && (!nodes.isEmpty() || !wires.isEmpty())) {
            rememberCurrentState();
            clearWorkspace(true);
        }
    }

    private void clearWorkspace(boolean resetIds) {
        simulationTimeline.stop();
        simulationRunning = false;
        unstableFeedbackDetected = false;
        runButton.setSelected(false);
        runButton.setText("Play");
        cancelWirePreview();
        removeMarqueeRectangle();
        clearSelectionVisuals();
        primarySelectedNode = null;
        primarySelectedWire = null;
        groupDragStartPositions.clear();
        groupDragStartPointer = null;
        groupDragAnchor = null;
        pendingGroupMoveSnapshot = null;
        nodes.clear();
        wires.clear();
        clockAccumulators.clear();
        componentLayer.getChildren().clear();
        wireLayer.getChildren().clear();
        if (resetIds) {
            nextId = 1;
            nextWireId = 1;
            componentLabelCounters.clear();
        }
        applySimulationMode();
        updatePropertiesPanel();
        updateDashboard();
    }

    @FXML
    private void handleSaveCircuit() {
        saveCircuit();
    }

    private boolean saveCircuit() {
        java.nio.file.Path destination = currentCircuitFile;
        if (destination == null) {
            destination = chooseCircuitSavePath();
            if (destination == null) {
                return false;
            }
        }

        try {
            LogicForgeFileCodec.write(destination, toProjectFile(captureSnapshot()));
            currentCircuitFile = destination.toAbsolutePath().normalize();
            markDocumentClean();
            statusMessageLabel.setText("Saved " + destination.getFileName());
            return true;
        } catch (IOException | IllegalArgumentException exception) {
            showFileError("Could not save circuit", exception.getMessage());
            return false;
        }
    }

    @FXML
    private void handleOpenCircuit() {
        if (simulationRunning) {
            return;
        }

        FileChooser chooser = createCircuitFileChooser("Open LogicForge Circuit");
        File selected = chooser.showOpenDialog(componentLayer.getScene().getWindow());
        if (selected == null) {
            return;
        }
        if (!confirmDiscardChanges("opening another project")) {
            return;
        }

        if (projectOpenAction != null) {
            projectOpenAction.accept(selected.toPath());
        } else {
            try {
                openProject(selected.toPath());
            } catch (IOException | IllegalArgumentException exception) {
                showFileError("Could not open circuit", exception.getMessage());
            }
        }
    }

    @FXML
    private void handleReturnHome() {
        if (simulationRunning || !confirmDiscardChanges("returning to the welcome screen")) {
            return;
        }
        homeAction.run();
    }

    private boolean confirmDiscardChanges(String action) {
        if (!documentDirty) {
            return true;
        }

        ButtonType save = new ButtonType("Save", ButtonData.YES);
        ButtonType discard = new ButtonType("Discard", ButtonData.NO);
        ButtonType cancel = new ButtonType("Cancel", ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Unsaved LogicForge Project");
        alert.setHeaderText("Save changes before " + action + "?");
        String projectName = currentCircuitFile == null
                ? "Untitled Project"
                : currentCircuitFile.getFileName().toString();
        alert.setContentText(projectName + " contains changes that have not been saved.");
        alert.getButtonTypes().setAll(save, discard, cancel);
        styleDialog(alert);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isEmpty() || result.get() == cancel) {
            return false;
        }
        return result.get() == discard || saveCircuit();
    }

    private java.nio.file.Path chooseCircuitSavePath() {
        FileChooser chooser = createCircuitFileChooser("Save LogicForge Circuit");
        chooser.setInitialFileName(currentCircuitFile == null
                ? "circuit.lgf"
                : currentCircuitFile.getFileName().toString());
        File selected = chooser.showSaveDialog(componentLayer.getScene().getWindow());
        if (selected == null) {
            return null;
        }
        String path = selected.getAbsolutePath();
        if (!path.toLowerCase().endsWith(".lgf")) {
            path += ".lgf";
        }
        return java.nio.file.Path.of(path);
    }

    private static FileChooser createCircuitFileChooser(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("LogicForge Circuit (*.lgf)", "*.lgf"));
        return chooser;
    }

    private void showFileError(String header, String details) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("LogicForge File Error");
        alert.setHeaderText(header);
        alert.setContentText(details == null || details.isBlank()
                ? "The selected .lgf file could not be processed."
                : details);
        styleDialog(alert);
        alert.showAndWait();
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

    private void installWorkspaceZoomGesture() {
        workspaceScroll.addEventFilter(ScrollEvent.SCROLL, event -> {
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

        Bounds viewport = workspaceScroll.getViewportBounds();
        double oldContentWidth = WORKSPACE_WIDTH * currentZoom;
        double oldContentHeight = WORKSPACE_HEIGHT * currentZoom;
        double logicalCenterX = contentCenter(
                oldContentWidth, viewport.getWidth(), workspaceScroll.getHvalue()) / currentZoom;
        double logicalCenterY = contentCenter(
                oldContentHeight, viewport.getHeight(), workspaceScroll.getVvalue()) / currentZoom;

        currentZoom = newZoom;
        workspaceScale.setX(currentZoom);
        workspaceScale.setY(currentZoom);
        resizeZoomContainer();
        zoomLabel.setText(String.format("%.0f%%", currentZoom * 100));

        Platform.runLater(() -> {
            Bounds updatedViewport = workspaceScroll.getViewportBounds();
            double newContentWidth = WORKSPACE_WIDTH * currentZoom;
            double newContentHeight = WORKSPACE_HEIGHT * currentZoom;
            workspaceScroll.setHvalue(scrollValueForCenter(
                    logicalCenterX * currentZoom,
                    newContentWidth,
                    updatedViewport.getWidth()));
            workspaceScroll.setVvalue(scrollValueForCenter(
                    logicalCenterY * currentZoom,
                    newContentHeight,
                    updatedViewport.getHeight()));
        });
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
                0,
                1);
    }

    private void installKeyboardShortcuts() {
        if (keyboardShortcutsInstalled) {
            return;
        }

        Scene scene = componentLayer.getScene();
        if (scene == null) {
            Platform.runLater(this::installKeyboardShortcuts);
            return;
        }

        keyboardShortcutsInstalled = true;
        Map<KeyCombination, Runnable> accelerators = scene.getAccelerators();
        accelerators.put(shortcut(KeyCode.N), newButton::fire);
        accelerators.put(shortcut(KeyCode.O), openButton::fire);
        accelerators.put(shortcut(KeyCode.S), saveButton::fire);
        accelerators.put(shortcut(KeyCode.Z), undoButton::fire);
        accelerators.put(shortcut(KeyCode.Y), redoButton::fire);
        accelerators.put(shortcut(KeyCode.C), copyButton::fire);
        accelerators.put(shortcut(KeyCode.V), pasteButton::fire);
        accelerators.put(shortcut(KeyCode.B), abstractButton::fire);
        accelerators.put(new KeyCodeCombination(KeyCode.R), rotateButton::fire);
        accelerators.put(new KeyCodeCombination(
                KeyCode.Z, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN),
                redoButton::fire);
        accelerators.put(new KeyCodeCombination(KeyCode.DELETE), deleteButton::fire);
        accelerators.put(new KeyCodeCombination(KeyCode.BACK_SPACE), deleteButton::fire);
        accelerators.put(new KeyCodeCombination(
                KeyCode.DELETE, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN),
                clearButton::fire);
        accelerators.put(shortcut(KeyCode.D), demoMenuButton::show);
        accelerators.put(shortcut(KeyCode.F), paletteSearchField::requestFocus);
        accelerators.put(new KeyCodeCombination(KeyCode.F5), runButton::fire);
        accelerators.put(shortcut(KeyCode.Q), exitButton::fire);
        accelerators.put(new KeyCodeCombination(
                KeyCode.V, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN),
                this::toggleViewsMenu);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleGlobalKeyPressed);
    }

    private static KeyCodeCombination shortcut(KeyCode keyCode) {
        return new KeyCodeCombination(keyCode, KeyCombination.SHORTCUT_DOWN);
    }

    private void handleGlobalKeyPressed(KeyEvent event) {
        if (paletteSearchField.isFocused()) {
            if (event.getCode() == KeyCode.ESCAPE && !paletteSearchField.getText().isEmpty()) {
                paletteSearchField.clear();
                event.consume();
            }
            return;
        }
        if (event.isShortcutDown()
                && (event.getCode() == KeyCode.EQUALS
                        || event.getCode() == KeyCode.PLUS
                        || event.getCode() == KeyCode.ADD)) {
            zoomInButton.fire();
            event.consume();
            return;
        }
        if (event.isShortcutDown()
                && (event.getCode() == KeyCode.MINUS
                        || event.getCode() == KeyCode.SUBTRACT)) {
            zoomOutButton.fire();
            event.consume();
            return;
        }
        if (event.isShortcutDown() && event.getCode() == KeyCode.DIGIT0) {
            setZoom(1.0);
            event.consume();
            return;
        }
        if (event.isShortcutDown() && event.getCode() == KeyCode.A) {
            selectAllItems();
            event.consume();
            return;
        }
        if (event.getCode() == KeyCode.ESCAPE) {
            cancelWirePreview();
            removeMarqueeRectangle();
            clearSelection();
            event.consume();
        }
    }

    private void selectAllItems() {
        clearSelectionVisuals();
        primarySelectedNode = null;
        primarySelectedWire = null;

        for (CircuitNode node : nodes.values()) {
            selectedNodes.add(node);
            node.setSelectedVisual(true);
            primarySelectedNode = node;
        }
        for (WireConnection wire : wires) {
            selectedWires.add(wire);
            wire.setSelectedVisual(true);
            primarySelectedWire = wire;
        }
        refreshSelectionUi();
    }

    private void toggleViewsMenu() {
        if (viewsButton.isShowing()) {
            viewsButton.hide();
        } else {
            viewsButton.show();
        }
    }

    @FXML
    private void handleTogglePalette() {
        setRegionVisible(componentPalettePanel, paletteViewItem.isSelected());
    }

    @FXML
    private void handleToggleProperties() {
        setRegionVisible(propertiesPanel, propertiesViewItem.isSelected());
    }

    @FXML
    private void handleToggleStatusBar() {
        setRegionVisible(statusBar, statusBarViewItem.isSelected());
    }

    @FXML
    private void handleToggleGrid() {
        gridLayer.setVisible(gridViewItem.isSelected());
    }

    @FXML
    private void handleExportDesignImage() {
        if (nodes.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Export Design as PNG");
            alert.setHeaderText("There is no circuit to export.");
            alert.setContentText("Place a component or load a demo, then export again.");
            styleDialog(alert);
            alert.showAndWait();
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export LogicForge Design as PNG");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG Image", "*.png"));
        String baseName = currentCircuitFile == null
                ? "LogicForge-design"
                : currentCircuitFile.getFileName().toString().replaceFirst("(?i)\\.lgf$", "");
        chooser.setInitialFileName(baseName + ".png");
        File selected = chooser.showSaveDialog(componentLayer.getScene().getWindow());
        if (selected == null) {
            return;
        }
        if (!selected.getName().toLowerCase().endsWith(".png")) {
            selected = new File(selected.getParentFile(), selected.getName() + ".png");
        }

        Rectangle2D viewport = circuitExportViewport();
        double maximumDimension = Math.max(viewport.getWidth(), viewport.getHeight());
        double exportScale = Math.min(2.0, 8192.0 / Math.max(1.0, maximumDimension));
        int pixelWidth = Math.max(1, (int) Math.ceil(viewport.getWidth() * exportScale));
        int pixelHeight = Math.max(1, (int) Math.ceil(viewport.getHeight() * exportScale));

        selectedNodes.forEach(node -> node.setSelectedVisual(false));
        selectedWires.forEach(wire -> wire.setSelectedVisual(false));
        try {
            SnapshotParameters parameters = new SnapshotParameters();
            parameters.setViewport(viewport);
            parameters.setTransform(Transform.scale(exportScale, exportScale));
            parameters.setFill(javafx.scene.paint.Color.TRANSPARENT);
            WritableImage image = new WritableImage(pixelWidth, pixelHeight);
            workspaceStack.snapshot(parameters, image);
            ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", selected);
            statusMessageLabel.setText(
                    "Exported PNG • " + pixelWidth + " × " + pixelHeight + " px • " + selected.getName());
        } catch (IOException | RuntimeException exception) {
            showFileError("Could not export design image", exception.getMessage());
        } finally {
            selectedNodes.forEach(node -> node.setSelectedVisual(true));
            selectedWires.forEach(wire -> wire.setSelectedVisual(true));
        }
    }

    private Rectangle2D circuitExportViewport() {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (CircuitNode node : nodes.values()) {
            Bounds bounds = node.getBoundsInParent();
            minX = Math.min(minX, bounds.getMinX());
            minY = Math.min(minY, bounds.getMinY());
            maxX = Math.max(maxX, bounds.getMaxX());
            maxY = Math.max(maxY, bounds.getMaxY());
        }
        for (WireConnection wire : wires) {
            for (Point2D point : wire.getRoutePoints()) {
                minX = Math.min(minX, point.getX());
                minY = Math.min(minY, point.getY());
                maxX = Math.max(maxX, point.getX());
                maxY = Math.max(maxY, point.getY());
            }
        }
        double padding = 70;
        double x = Math.max(0, minX - padding);
        double y = Math.max(0, minY - padding);
        double right = Math.min(WORKSPACE_WIDTH, maxX + padding);
        double bottom = Math.min(WORKSPACE_HEIGHT, maxY + padding);
        return new Rectangle2D(x, y, Math.max(1, right - x), Math.max(1, bottom - y));
    }

    @FXML
    private void handleToggleLightTheme() {
        ThemeManager.setLightTheme(lightThemeItem.isSelected());
        ThemeManager.apply(componentLayer.getScene());
        statusMessageLabel.setText(lightThemeItem.isSelected()
                ? "Light theme enabled"
                : "Dark theme enabled");
    }

    private static void setRegionVisible(Node region, boolean visible) {
        region.setVisible(visible);
        region.setManaged(visible);
    }

    @FXML
    private void handleExit() {
        if (confirmCloseRequest()) {
            Platform.exit();
        }
    }

    private void updatePropertiesPanel() {
        int selectedItemCount = selectedNodes.size() + selectedWires.size();
        boolean noSelection = selectedItemCount == 0;
        boolean singleSelection = selectedItemCount == 1;
        boolean multipleSelection = selectedItemCount > 1;

        setRegionVisible(overviewSection, noSelection);
        setRegionVisible(singleSelectionSection, singleSelection);
        setRegionVisible(multiSelectionSection, multipleSelection);

        if (noSelection) {
            multiSelectionList.getChildren().clear();
            return;
        }

        if (multipleSelection) {
            populateMultipleSelectionProperties();
            return;
        }

        multiSelectionList.getChildren().clear();
        if (!selectedWires.isEmpty() && primarySelectedWire != null) {
            showSingleWireProperties(primarySelectedWire);
            return;
        }

        if (primarySelectedNode != null) {
            showSingleNodeProperties(primarySelectedNode);
        }
    }

    private void showSingleNodeProperties(CircuitNode node) {
        selectedNameLabel.setText(node.getType() == ComponentType.CUSTOM
                ? node.getCustomDefinition().name()
                : node.getType() == ComponentType.INTEGRATED_CIRCUIT
                        ? node.getIcDefinition().partNumber()
                        : node.getType().getDisplayName());
        selectedDescriptionLabel.setText(node.getType() == ComponentType.CUSTOM
                ? customComponentDescription(node.getCustomDefinition())
                : node.getType() == ComponentType.INTEGRATED_CIRCUIT
                        ? node.getIcDefinition().description()
                        : node.getType().getDescription());
        selectedCustomLabelLabel.setText(displayLabel(node.getUserLabel()));
        selectedIdLabel.setText("C" + node.getComponentId());
        selectedStateLabel.setText(formatNodeState(node));
        selectedConfigLabel.setText(configurationDetails(node));
        selectedPositionLabel.setText(String.format(
                "%.0f, %.0f • %s", node.getLayoutX(), node.getLayoutY(), node.getOrientation()));
        selectedInputsLabel.setText(inputConnectionDetails(node));
        selectedOutputsLabel.setText(outputConnectionDetails(node));
    }

    private void showSingleWireProperties(WireConnection wire) {
        selectedNameLabel.setText("Wire Connection");
        selectedDescriptionLabel.setText(
                "Connects one output pin to one input pin using clean automatic routing. Drag any editable straight section to reposition that run; no control points are displayed.");
        selectedCustomLabelLabel.setText("—");
        selectedIdLabel.setText("W" + wire.getConnectionId());
        selectedStateLabel.setText(wire.getSignalState() ? "TRUE / 1" : "FALSE / 0");
        selectedConfigLabel.setText(
                        "Source pin: " + wire.getSource().getOutputName(wire.getSourceOutputIndex())
                        + "\nTarget pin: " + wire.getTarget().getInputName(wire.getTargetInputIndex())
                        + "\nRouting: " + (wire.getAdjustedBendCount() == 0
                                ? "Automatic"
                                : "Manually adjusted • right-click to reset"));
        selectedPositionLabel.setText(
                "C" + wire.getSource().getComponentId() + " → C" + wire.getTarget().getComponentId());
        selectedInputsLabel.setText(
                displayComponentReference(wire.getSource())
                        + "." + wire.getSource().getOutputName(wire.getSourceOutputIndex()));
        selectedOutputsLabel.setText(
                displayComponentReference(wire.getTarget())
                        + "." + wire.getTarget().getInputName(wire.getTargetInputIndex()));
    }

    private void populateMultipleSelectionProperties() {
        multiSelectionList.getChildren().clear();
        int total = selectedNodes.size() + selectedWires.size();
        multiSelectionHeading.setText(total + (total == 1 ? " ITEM" : " ITEMS") + " SELECTED");

        selectedNodes.stream()
                .sorted(Comparator.comparingInt(CircuitNode::getComponentId))
                .map(this::createNodePropertyCard)
                .forEach(card -> multiSelectionList.getChildren().add(card));

        selectedWires.stream()
                .sorted(Comparator.comparingInt(WireConnection::getConnectionId))
                .map(this::createWirePropertyCard)
                .forEach(card -> multiSelectionList.getChildren().add(card));
    }

    private Node createNodePropertyCard(CircuitNode node) {
        VBox card = new VBox(4);
        card.getStyleClass().add("multi-property-card");

        Label title = new Label(node.getType() == ComponentType.CUSTOM
                ? node.getCustomDefinition().name()
                : node.getType().getDisplayName());
        title.setWrapText(true);
        title.getStyleClass().add("multi-property-title");

        Label label = new Label("Label: " + displayLabel(node.getUserLabel()));
        label.setWrapText(true);
        label.getStyleClass().add("multi-property-value");

        Label id = new Label("ID: C" + node.getComponentId());
        id.getStyleClass().add("multi-property-meta");

        Label state = new Label("State: " + formatNodeState(node));
        state.setWrapText(true);
        state.getStyleClass().add("multi-property-meta");

        Label config = new Label(configurationDetails(node));
        config.setWrapText(true);
        config.getStyleClass().add("multi-property-details");

        Label io = new Label(inputConnectionDetails(node) + "\n" + outputConnectionDetails(node));
        io.setWrapText(true);
        io.getStyleClass().add("multi-property-details");

        card.getChildren().addAll(title, label, id, state, config, io);
        return card;
    }

    private Node createWirePropertyCard(WireConnection wire) {
        VBox card = new VBox(4);
        card.getStyleClass().add("multi-property-card");

        Label title = new Label("Wire W" + wire.getConnectionId());
        title.getStyleClass().add("multi-property-title");

        Label state = new Label("State: " + (wire.getSignalState() ? "TRUE / 1" : "FALSE / 0"));
        state.getStyleClass().add("multi-property-meta");

        Label route = new Label(
                displayComponentReference(wire.getSource())
                        + "." + wire.getSource().getOutputName(wire.getSourceOutputIndex())
                        + "\n→ "
                        + displayComponentReference(wire.getTarget())
                        + "." + wire.getTarget().getInputName(wire.getTargetInputIndex()));
        route.setWrapText(true);
        route.getStyleClass().add("multi-property-details");

        card.getChildren().addAll(title, state, route);
        return card;
    }

    private String displayComponentReference(CircuitNode node) {
        String label = node.getUserLabel();
        return label == null || label.isBlank()
                ? "C" + node.getComponentId()
                : label + " (C" + node.getComponentId() + ")";
    }

    private static String displayLabel(String label) {
        return label == null || label.isBlank() ? "(empty)" : label;
    }

    private static String customComponentDescription(CustomComponentDefinition definition) {
        if (!definition.description().isBlank()) {
            return definition.description();
        }
        return "Reusable custom component abstracted from "
                + definition.components().size() + " hidden components.";
    }

    private String configurationDetails(CircuitNode node) {
        ComponentType type = node.getType();
        int bits = node.getConfig().bitWidth();
        return switch (type) {
            case MULTIPLEXER ->
                    "Data inputs: " + (1 << bits) + " (X0–X" + ((1 << bits) - 1) + ")"
                            + "\nSelect inputs: " + bits + " (S0 = LSB)"
                            + "\nOutput: Y";
            case DEMULTIPLEXER ->
                    "Data input: X"
                            + "\nSelect inputs: " + bits + " (S0 = LSB)"
                            + "\nOutputs: Y0–Y" + ((1 << bits) - 1);
            case DECODER ->
                    "Input bits: " + bits + " (X0–X" + (bits - 1) + ")"
                            + "\nDecoded outputs: " + (1 << bits)
                            + " (Y0–Y" + ((1 << bits) - 1) + ")";
            case ENCODER ->
                    "Input lines: " + (1 << bits) + " (X0–X" + ((1 << bits) - 1) + ")"
                            + "\nEncoded outputs: " + bits + " (Y0–Y" + (bits - 1) + ")"
                            + "\nPriority: highest active input";
            case BCD_TO_7SEGMENT ->
                    "Inputs: B0 (LSB), B1, B2, B3 (MSB)"
                            + "\nOutputs: A–G active-high"
                            + "\nBCD 0–9 displayed; 10–15 blank";
            case HALF_ADDER ->
                    "Width: " + bits + " bits"
                            + "\nInputs: A0–A" + (bits - 1) + ", B0–B" + (bits - 1)
                            + "\nOutputs: S0–S" + (bits - 1) + ", COUT";
            case FULL_ADDER ->
                    "Width: " + bits + " bits"
                            + "\nInputs: A0–A" + (bits - 1) + ", B0–B" + (bits - 1) + ", CIN"
                            + "\nOutputs: S0–S" + (bits - 1) + ", COUT";
            case COMPARATOR ->
                    "Width: " + bits + " bits unsigned"
                            + "\nOutputs: A>B, A=B, A<B";
            case SEVEN_SEGMENT_DISPLAY ->
                    "Inputs: A, B, C, D, E, F, G"
                            + "\nDrive type: active-high"
                            + "\nOutput: red LED segments";
            case AND, NAND, OR, NOR, XOR ->
                    "Gate inputs: " + bits + " (A–" + (char) ('A' + bits - 1) + ")"
                            + "\nOutput: Y";
            case CLOCK ->
                    "Frequency: " + node.getType().configurationSummary(node.getConfig())
                            + "\nOutput: square wave";
            case VCC -> "Fixed output: TRUE / 1\nCannot be toggled";
            case GROUND -> "Fixed output: FALSE / 0\nCannot be toggled";
            case JUNCTION -> "Pass-through branch point"
                    + "\nInput: one signal"
                    + "\nOutput: unlimited fan-out";
            case CUSTOM ->
                    "Custom block: " + node.getCustomDefinition().name()
                            + (node.getCustomDefinition().description().isBlank()
                                    ? ""
                                    : "\nDescription: " + node.getCustomDefinition().description())
                            + "\nHidden components: " + node.getCustomDefinition().components().size()
                            + "\nExternal inputs: " + node.getInputCount()
                            + "\nExternal outputs: " + node.getOutputCount()
                            + (node.isSequentialBoundary() ? "\nContains sequential state" : "\nCombinational");
            case INTEGRATED_CIRCUIT ->
                    "Part: " + node.getIcDefinition().partNumber()
                            + "\nFunction: " + node.getIcDefinition().name()
                            + "\nPackage: " + node.getIcDefinition().packageType().label()
                            + "\nPower: " + (node.isIcPowered()
                                    ? "Powered"
                                    : "UNPOWERED — connect VCC to 1 and GND to 0")
                            + "\nPhysical pins: " + node.getIcDefinition().packageType().pinCount()
                            + (node.getIcDefinition().isSequential()
                                    ? "\nContains sequential state"
                                    : "\nCombinational");
            case SR_FLIP_FLOP ->
                    "Trigger: rising edge"
                            + "\nInputs: S, R, CLK, /PRE, /CLR"
                            + "\nOutputs: Q, Q̅";
            case JK_FLIP_FLOP ->
                    "Trigger: rising edge"
                            + "\nInputs: J, K, CLK, /PRE, /CLR"
                            + "\nOutputs: Q, Q̅";
            case D_FLIP_FLOP ->
                    "Trigger: rising edge"
                            + "\nInputs: D, CLK, /PRE, /CLR"
                            + "\nOutputs: Q, Q̅";
            case T_FLIP_FLOP ->
                    "Trigger: rising edge"
                            + "\nInputs: T, CLK, /PRE, /CLR"
                            + "\nOutputs: Q, Q̅";
            case COUNTER ->
                    "Width: " + bits + " bits"
                            + "\nTrigger: rising CLK edge"
                            + "\nActive-high RESET • modulo " + (1 << bits);
            case SHIFT_REGISTER ->
                    "Width: " + bits + " bits"
                            + "\nSerial DATA enters Q0 on each rising CLK edge"
                            + "\nActive-high RESET • parallel Q outputs";
            default ->
                    "Inputs: " + node.getInputCount()
                            + "\nOutputs: " + node.getOutputCount();
        };
    }

    private String inputConnectionDetails(CircuitNode node) {
        if (node.getInputCount() == 0) {
            return "Inputs: none";
        }
        return "Inputs connected: " + countIncoming(node) + " / " + node.getInputCount();
    }

    private String outputConnectionDetails(CircuitNode node) {
        if (node.getOutputCount() == 0) {
            return "Outputs: none";
        }
        int outgoing = countOutgoing(node);
        return "Output pins: " + node.getOutputCount()
                + "\nOutgoing wires: " + outgoing;
    }

    private String formatNodeState(CircuitNode node) {
        if (node.getType() == ComponentType.SEVEN_SEGMENT_DISPLAY) {
            StringBuilder segments = new StringBuilder();
            boolean[] states = node.getSevenSegmentStatesCopy();
            for (int index = 0; index < states.length; index++) {
                if (index > 0) {
                    segments.append("  ");
                }
                segments.append((char) ('A' + index))
                        .append('=')
                        .append(states[index] ? '1' : '0');
            }
            return segments.toString();
        }
        if (node.getOutputCount() == 0) {
            return node.getDisplayState() ? "TRUE / 1" : "FALSE / 0";
        }
        if (node.getOutputCount() == 1) {
            return node.getOutputState(0) ? "TRUE / 1" : "FALSE / 0";
        }

        StringBuilder builder = new StringBuilder();
        int maximumShown = Math.min(node.getOutputCount(), 8);
        for (int index = 0; index < maximumShown; index++) {
            if (index > 0) {
                builder.append("  ");
            }
            builder.append(node.getOutputName(index))
                    .append('=')
                    .append(node.getOutputState(index) ? '1' : '0');
        }
        if (node.getOutputCount() > maximumShown) {
            builder.append(" …");
        }
        return builder.toString();
    }

    private String selectionStateSummary() {
        Set<Boolean> states = new HashSet<>();
        selectedNodes.stream().map(CircuitNode::getOutputState).forEach(states::add);
        selectedWires.stream().map(WireConnection::getSignalState).forEach(states::add);
        if (states.size() != 1) {
            return "MIXED";
        }
        return states.iterator().next() ? "TRUE / 1" : "FALSE / 0";
    }

    private void updateDashboard() {
        componentCountLabel.setText(Integer.toString(nodes.size()));
        wireCountLabel.setText(Integer.toString(wires.size()));
        boolean invalidAsyncControls = nodes.values().stream()
                .anyMatch(CircuitNode::hasInvalidAsyncControls);
        long unpoweredIcCount = nodes.values().stream()
                .filter(node -> node.getType() == ComponentType.INTEGRATED_CIRCUIT)
                .filter(node -> !node.isIcPowered())
                .count();
        String simulationState = simulationRunning
                ? (unstableFeedbackDetected
                        ? "UNSTABLE FEEDBACK"
                        : (invalidAsyncControls
                                ? "INVALID PRE/CLR"
                                : (unpoweredIcCount > 0 ? "RUNNING • UNPOWERED IC" : "RUNNING")))
                : (invalidAsyncControls
                        ? "PAUSED • INVALID PRE/CLR"
                        : (unpoweredIcCount > 0 ? "PAUSED • UNPOWERED IC" : "PAUSED"));
        simulationStatusLabel.setText(simulationState);
        simulationStatusLabel.getStyleClass().removeAll(
                "status-running", "status-paused", "status-unstable");
        simulationStatusLabel.getStyleClass().add(
                unstableFeedbackDetected || invalidAsyncControls || unpoweredIcCount > 0
                        ? "status-unstable"
                        : (simulationRunning ? "status-running" : "status-paused"));

        int selectedItemCount = selectedNodes.size() + selectedWires.size();
        if (invalidAsyncControls) {
            statusMessageLabel.setText(
                    "Invalid flip-flop control state • /PRE and /CLR cannot both be 0");
        } else if (unpoweredIcCount > 0) {
            statusMessageLabel.setText(
                    unpoweredIcCount + " unpowered IC"
                            + (unpoweredIcCount == 1 ? "" : "s")
                            + " • connect each VCC pin to logic 1 and GND pin to logic 0");
        } else if (simulationRunning && unstableFeedbackDetected) {
            statusMessageLabel.setText(
                    "Unstable zero-delay feedback detected • check for an oscillator or invalid latch state");
        } else if (simulationRunning) {
            statusMessageLabel.setText(
                    "Simulation running • editing locked • "
                            + nodes.size() + " components • " + wires.size() + " wires");
        } else if (nodes.isEmpty()) {
            statusMessageLabel.setText("Paused — build a circuit or choose one from the Demos menu");
        } else if (selectedItemCount > 0) {
            statusMessageLabel.setText(
                    "Paused • " + nodes.size() + " components • " + wires.size()
                            + " wires • " + selectedItemCount + " selected");
        } else {
            statusMessageLabel.setText(
                    "Paused • " + nodes.size() + " components • " + wires.size() + " wires");
        }
    }

    private record OutputPinKey(CircuitNode source, int outputIndex) { }

    private record TargetPin(CircuitNode node, int inputIndex) { }

    private record PaletteSelection(
            ComponentType type,
            String customDefinitionId,
            String icDefinitionId) { }

    private enum JunctionRole {
        INPUT,
        OUTPUT,
        INTERNAL
    }

    private record BlockCreationRequest(
            String name,
            String description,
            List<String> inputNames,
            List<String> outputNames) { }

    private record FullAdderStage(CircuitNode sum, CircuitNode carry) { }

    private record ComponentState(
            int componentId,
            ComponentType type,
            ComponentConfig config,
            double x,
            double y,
            ComponentOrientation orientation,
            String label,
            List<Boolean> outputStates,
            boolean displayState,
            List<Boolean> sevenSegmentStates,
            boolean storedState,
            boolean previousClockState,
            String customDefinitionId,
            String customState,
            String icDefinitionId,
            String icState) { }

    private record WireState(
            int connectionId,
            int sourceComponentId,
            int sourceOutputIndex,
            int targetComponentId,
            int targetInputIndex,
            List<Point2D> bendAnchors) { }

    private record CircuitSnapshot(
            List<ComponentState> components,
            List<WireState> wires,
            int nextComponentId,
            int nextWireId,
            Map<ComponentType, Integer> labelCounters) { }

    private record ClipboardSnapshot(
            List<ComponentState> components,
            List<WireState> wires) { }
}
