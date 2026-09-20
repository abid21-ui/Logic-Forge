package com.logicforge.view;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import com.logicforge.model.ComponentConfig;
import com.logicforge.model.ComponentOrientation;
import com.logicforge.model.ComponentType;
import com.logicforge.model.CustomComponentDefinition;
import com.logicforge.model.CustomComponentRuntime;
import com.logicforge.model.IcDefinition;
import com.logicforge.model.IcDefinition.IcPin;
import com.logicforge.model.IcRuntime;

import javafx.css.PseudoClass;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcTo;
import javafx.scene.shape.Circle;
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.CubicCurveTo;
import javafx.scene.shape.Line;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.text.TextAlignment;

/**
 * Visual and stateful representation of one circuit component.
 * Supports variable input/output counts and conventional gate symbols.
 */
public final class CircuitNode extends Pane {

    /** Side of a component from which a pin's wire should approach. */
    public enum PinSide {
        LEFT,
        RIGHT,
        TOP,
        BOTTOM
    }

    public static final double NODE_WIDTH = 160;
    public static final double NODE_HEIGHT = 90;

    private static final double BASIC_INPUT_PIN_X = 8;
    private static final double BASIC_OUTPUT_PIN_X = 152;
    private static final double BASIC_CENTER_Y = 45;
    private static final double TOP_INPUT_Y = 31;
    private static final double BOTTOM_INPUT_Y = 59;

    private static final double BLOCK_WIDTH = 210;
    private static final double BLOCK_PIN_MARGIN = 8;
    private static final double BLOCK_BODY_LEFT = 38;
    private static final double BLOCK_BODY_RIGHT_MARGIN = 38;
    private static final double BLOCK_PIN_SPACING = 18;
    private static final double BLOCK_VERTICAL_PADDING = 30;
    private static final double LABEL_AREA_HEIGHT = 20;

    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private static final PseudoClass PENDING = PseudoClass.getPseudoClass("pending");
    private static final PseudoClass ACTIVE = PseudoClass.getPseudoClass("active");
    private static final PseudoClass LOCKED = PseudoClass.getPseudoClass("locked");
    private static final PseudoClass UNPOWERED = PseudoClass.getPseudoClass("unpowered");

    private final int componentId;
    private final ComponentType type;
    private final ComponentConfig config;
    private final CustomComponentDefinition customDefinition;
    private final CustomComponentRuntime customRuntime;
    private final IcDefinition icDefinition;
    private final IcRuntime icRuntime;
    private final int inputCount;
    private final int outputCount;
    private final double nodeWidth;
    private final double nodeHeight;

    private final List<Circle> inputPins = new ArrayList<>();
    private final List<Double> inputPinXs = new ArrayList<>();
    private final List<Double> inputPinYs = new ArrayList<>();
    private final List<PinSide> inputPinSides = new ArrayList<>();
    private final List<Circle> outputPins = new ArrayList<>();
    private final List<Line> outputLines = new ArrayList<>();
    private final List<Double> outputPinXs = new ArrayList<>();
    private final List<Double> outputPinYs = new ArrayList<>();
    private final List<PinSide> outputPinSides = new ArrayList<>();
    private final List<Label> pinLabels = new ArrayList<>();
    private final List<Label> orientationStableLabels = new ArrayList<>();
    private final boolean[] outputStates;
    private final boolean[] sevenSegmentStates = new boolean[7];
    private final List<Shape> sevenSegmentShapes = new ArrayList<>();

    private Line switchArm;
    private Label logicStateLabel;
    private Label componentLabel;
    private String userLabel;
    private boolean displayState;
    private boolean storedState;
    private boolean previousClockState;
    private boolean invalidAsyncControls;
    private ComponentOrientation orientation = ComponentOrientation.EAST;
    private boolean editingLocked;

    private double pressedSceneX;
    private double pressedSceneY;
    private boolean bodyWasDragged;
    private boolean bodyPressedWithControl;

    private SelectionHandler selectionHandler = (ignored, toggleSelection) -> { };
    private MoveHandler moveHandler = MoveHandler.NO_OP;
    private Consumer<CircuitNode> positionChangedHandler = ignored -> { };
    private Consumer<CircuitNode> sourceChangeStartedHandler = ignored -> { };
    private Consumer<CircuitNode> sourceChangedHandler = ignored -> { };
    private Consumer<CircuitNode> labelEditHandler = ignored -> { };
    private WireDragHandler wireDragHandler = WireDragHandler.NO_OP;

    public CircuitNode(int componentId, ComponentType type) {
        this(componentId, type, ComponentConfig.defaults());
    }

    public CircuitNode(int componentId, ComponentType type, ComponentConfig config) {
        this(componentId, type, config, null, ignored -> null, null);
    }

    public CircuitNode(
            int componentId,
            ComponentType type,
            ComponentConfig config,
            CustomComponentDefinition customDefinition,
            Function<String, CustomComponentDefinition> customDefinitionResolver) {

        this(componentId, type, config, customDefinition, customDefinitionResolver, null);
    }

    public CircuitNode(
            int componentId,
            ComponentType type,
            ComponentConfig config,
            CustomComponentDefinition customDefinition,
            Function<String, CustomComponentDefinition> customDefinitionResolver,
            IcDefinition icDefinition) {

        this.componentId = componentId;
        this.type = Objects.requireNonNull(type, "type");
        this.config = Objects.requireNonNull(config, "config");
        this.customDefinition = customDefinition;
        this.icDefinition = icDefinition;
        if (type == ComponentType.CUSTOM && customDefinition == null) {
            throw new IllegalArgumentException("Custom components require a definition");
        }
        if (type != ComponentType.CUSTOM && customDefinition != null) {
            throw new IllegalArgumentException("Only custom components may have a custom definition");
        }
        if (type == ComponentType.INTEGRATED_CIRCUIT && icDefinition == null) {
            throw new IllegalArgumentException("Integrated circuits require a definition");
        }
        if (type != ComponentType.INTEGRATED_CIRCUIT && icDefinition != null) {
            throw new IllegalArgumentException("Only integrated circuits may have an IC definition");
        }
        this.inputCount = type == ComponentType.CUSTOM
                ? customDefinition.inputCount()
                : type == ComponentType.INTEGRATED_CIRCUIT
                        ? icDefinition.inputCount()
                        : type.inputCount(config);
        this.outputCount = type == ComponentType.CUSTOM
                ? customDefinition.outputCount()
                : type == ComponentType.INTEGRATED_CIRCUIT
                        ? icDefinition.outputCount()
                        : type.outputCount(config);
        this.outputStates = new boolean[outputCount];
        this.customRuntime = type == ComponentType.CUSTOM
                ? new CustomComponentRuntime(customDefinition, customDefinitionResolver)
                : null;
        this.icRuntime = type == ComponentType.INTEGRATED_CIRCUIT
                ? new IcRuntime(icDefinition)
                : null;

        if (type == ComponentType.JUNCTION) {
            nodeWidth = 40;
            nodeHeight = 40;
        } else if (usesStandardSymbol(type)) {
            nodeWidth = NODE_WIDTH;
            nodeHeight = NODE_HEIGHT;
        } else if (type == ComponentType.CLOCK) {
            nodeWidth = NODE_WIDTH;
            nodeHeight = 105;
        } else if (type == ComponentType.MULTIPLEXER) {
            int selectBits = config.bitWidth();
            int dataInputs = 1 << selectBits;

            /*
             * Scale MUXes in BOTH dimensions.  A 16:1 MUX needs much more vertical
             * room than a 2:1 MUX, but keeping the same width makes the larger
             * variants look unnaturally thin.  The width therefore grows with both
             * select width and body height while preserving the classic tall profile.
             */
            double bodyHeight = Math.max(130.0, 72.0 + (dataInputs - 1) * 26.0);
            double bodyWidth = Math.max(
                    88.0 + (selectBits - 1) * 26.0,
                    bodyHeight * 0.48);
            nodeWidth = roundUpToTen(bodyWidth + 96.0);
            nodeHeight = roundUpToTen(28.0 + bodyHeight + 58.0);
        } else if (type == ComponentType.DECODER
                || type == ComponentType.ENCODER
                || type == ComponentType.DEMULTIPLEXER) {
            int bits = config.bitWidth();
            int maximumPins = Math.max(inputCount, outputCount);

            /*
             * Decoder/encoder blocks scale proportionally instead of becoming tall,
             * fixed-width towers at 8/16 lines.  Pin spacing stays readable while the
             * body grows horizontally with its vertical demand.
             */
            double bodyHeight = Math.max(116.0, 68.0 + (maximumPins - 1) * 24.0);
            double bodyWidth = Math.max(
                    142.0 + (bits - 1) * 14.0,
                    bodyHeight * 0.72);
            nodeWidth = roundUpToTen(bodyWidth + BLOCK_BODY_LEFT + BLOCK_BODY_RIGHT_MARGIN);
            nodeHeight = roundUpToTen(28.0 + bodyHeight + 18.0);
        } else if (type == ComponentType.SEVEN_SEGMENT_DISPLAY) {
            nodeWidth = 250;
            nodeHeight = 250;
        } else if (type.isFlipFlop()) {
            // Keep all four flip-flop symbols on one consistent footprint.
            nodeWidth = 220;
            nodeHeight = 170;
        } else if (type == ComponentType.INTEGRATED_CIRCUIT) {
            nodeWidth = 360;
            nodeHeight = Math.max(300, 86 + icDefinition.packageType().pinCount() / 2 * 34.0);
        } else if (type == ComponentType.CUSTOM) {
            int maximumPins = Math.max(inputCount, outputCount);
            nodeWidth = Math.max(230, roundUpToTen(150 + customDefinition.symbol().length() * 7.0));
            nodeHeight = Math.max(140,
                    BLOCK_VERTICAL_PADDING * 2 + maximumPins * BLOCK_PIN_SPACING + LABEL_AREA_HEIGHT);
        } else {
            int maximumPins = Math.max(inputCount, outputCount);
            nodeWidth = BLOCK_WIDTH;
            nodeHeight = Math.max(125,
                    BLOCK_VERTICAL_PADDING * 2 + maximumPins * BLOCK_PIN_SPACING + LABEL_AREA_HEIGHT)
                    + OperandPinLayout.extraHeight(type);
        }

        setPrefSize(nodeWidth, nodeHeight);
        setMinSize(nodeWidth, nodeHeight);
        setMaxSize(nodeWidth, nodeHeight);
        setPickOnBounds(false);
        getStyleClass().addAll("circuit-node", "component-" + type.name().toLowerCase());

        buildSymbol();
        userLabel = componentId < 0 ? "" : type.defaultLabelPrefix() + componentId;
        buildInstanceLabel();
        if (type == ComponentType.CUSTOM) {
            setOutputStates(customRuntime.outputs());
        } else if (type == ComponentType.INTEGRATED_CIRCUIT) {
            setOutputStates(icRuntime.outputs());
            updateIcPowerVisual();
        } else if (type.isFlipFlop()) {
            setStoredState(false);
        } else if (type == ComponentType.VCC) {
            setOutputState(0, true);
        } else if (type == ComponentType.GROUND) {
            setOutputState(0, false);
        } else {
            updateStateVisual();
        }
    }

    private static double roundUpToTen(double value) {
        return Math.ceil(value / 10.0) * 10.0;
    }

    private static boolean usesStandardSymbol(ComponentType type) {
        return switch (type) {
            case SWITCH, LOGIC_INPUT, VCC, GROUND,
                    AND, NAND, OR, NOR, NOT, XOR, BULB, LOGIC_OUTPUT -> true;
            default -> false;
        };
    }

    private static boolean isBasicLogicGate(ComponentType type) {
        return switch (type) {
            case AND, NAND, OR, NOR, NOT, XOR -> true;
            default -> false;
        };
    }

    private void buildSymbol() {
        switch (type) {
            case AND -> buildAndGate(false);
            case NAND -> buildAndGate(true);
            case OR -> buildOrGate(false, false);
            case NOR -> buildOrGate(false, true);
            case XOR -> buildOrGate(true, false);
            case NOT -> buildNotGate();
            case SWITCH -> buildSwitch();
            case LOGIC_INPUT -> buildLogicStateDisplay(true);
            case VCC -> buildPowerSource(true);
            case GROUND -> buildPowerSource(false);
            case JUNCTION -> buildJunction();
            case BULB -> buildBulb();
            case LOGIC_OUTPUT -> buildLogicStateDisplay(false);
            case SEVEN_SEGMENT_DISPLAY -> buildSevenSegmentDisplay();
            case CLOCK -> buildClock();
            case MULTIPLEXER -> buildMultiplexer();
            case DECODER, ENCODER, BCD_TO_7SEGMENT,
                    HALF_ADDER, FULL_ADDER, COMPARATOR, DEMULTIPLEXER,
                    COUNTER, SHIFT_REGISTER -> buildFunctionalBlock(type.getSymbol());
            case SR_FLIP_FLOP, JK_FLIP_FLOP, D_FLIP_FLOP, T_FLIP_FLOP -> buildFlipFlop();
            case INTEGRATED_CIRCUIT -> buildIntegratedCircuit();
            case CUSTOM -> buildCustomComponent();
        }

        Rectangle bodyHitBox = createBodyHitBox();
        getChildren().addFirst(bodyHitBox);
    }

    private Rectangle createBodyHitBox() {
        double x = type == ComponentType.JUNCTION ? 4 : (usesStandardSymbol(type) ? 26 : BLOCK_BODY_LEFT);
        double y = type == ComponentType.JUNCTION ? 4 : (usesStandardSymbol(type) ? 10 : 12);
        double width = type == ComponentType.JUNCTION
                ? 32
                : (usesStandardSymbol(type) ? 104 : nodeWidth - BLOCK_BODY_LEFT - BLOCK_BODY_RIGHT_MARGIN);
        double height = type == ComponentType.JUNCTION
                ? 32
                : (usesStandardSymbol(type) ? 70 : nodeHeight - 24);

        Rectangle bodyHitBox = new Rectangle(x, y, width, height);
        bodyHitBox.setFill(Color.TRANSPARENT);
        bodyHitBox.setCursor(Cursor.MOVE);
        bodyHitBox.getStyleClass().add("node-hitbox");
        installBodyInteraction(bodyHitBox);
        return bodyHitBox;
    }

    private void buildAndGate(boolean inverted) {
        addVariableGateInputs(45);

        Path body = new Path(
                new MoveTo(45, 18),
                new LineTo(75, 18),
                new ArcTo(27, 27, 0, 75, 72, false, true),
                new LineTo(45, 72),
                new ClosePath());
        styleGateShape(body);
        getChildren().add(body);

        if (inverted) {
            addInversionBubble(108, BASIC_CENTER_Y);
            addBasicOutputTerminal(114, BASIC_CENTER_Y, "Y");
        } else {
            addBasicOutputTerminal(102, BASIC_CENTER_Y, "Y");
        }
    }

    private void buildOrGate(boolean xor, boolean inverted) {
        addVariableGateInputs(xor ? 43 : 47);

        Path body = new Path(
                new MoveTo(45, 18),
                new CubicCurveTo(68, 18, 92, 25, 108, BASIC_CENTER_Y),
                new CubicCurveTo(92, 65, 68, 72, 45, 72),
                new CubicCurveTo(58, 55, 58, 35, 45, 18),
                new ClosePath());
        styleGateShape(body);
        getChildren().add(body);

        if (xor) {
            Path extraCurve = new Path(
                    new MoveTo(38, 18),
                    new CubicCurveTo(51, 35, 51, 55, 38, 72));
            extraCurve.getStyleClass().addAll("gate-shape", "xor-extra-curve");
            extraCurve.setMouseTransparent(true);
            getChildren().add(extraCurve);
        }

        if (inverted) {
            addInversionBubble(114, BASIC_CENTER_Y);
            addBasicOutputTerminal(120, BASIC_CENTER_Y, "Y");
        } else {
            addBasicOutputTerminal(108, BASIC_CENTER_Y, "Y");
        }
    }

    private void buildNotGate() {
        addBasicInputTerminal(BASIC_CENTER_Y, 45, "IN");

        Path triangle = new Path(
                new MoveTo(45, 18),
                new LineTo(104, BASIC_CENTER_Y),
                new LineTo(45, 72),
                new ClosePath());
        styleGateShape(triangle);
        getChildren().add(triangle);

        addInversionBubble(111, BASIC_CENTER_Y);
        addBasicOutputTerminal(117, BASIC_CENTER_Y, "Y");
    }

    private void addInversionBubble(double centerX, double centerY) {
        Circle inversionBubble = new Circle(centerX, centerY, 6);
        inversionBubble.getStyleClass().addAll("gate-shape", "inversion-bubble");
        inversionBubble.setMouseTransparent(true);
        getChildren().add(inversionBubble);
    }

    private void addVariableGateInputs(double bodyX) {
        List<String> names = type.inputNames(config);
        double firstY = inputCount == 2 ? TOP_INPUT_Y : 24;
        double lastY = inputCount == 2 ? BOTTOM_INPUT_Y : 66;
        for (int index = 0; index < inputCount; index++) {
            addBasicInputTerminal(
                    distributedPosition(index, inputCount, firstY, lastY),
                    bodyX,
                    names.get(index));
        }
    }

    private void buildSwitch() {
        Circle leftContact = new Circle(53, BASIC_CENTER_Y, 5);
        Circle rightContact = new Circle(96, BASIC_CENTER_Y, 5);
        leftContact.getStyleClass().addAll("gate-shape", "switch-contact");
        rightContact.getStyleClass().addAll("gate-shape", "switch-contact");
        leftContact.setMouseTransparent(true);
        rightContact.setMouseTransparent(true);

        switchArm = new Line(57, BASIC_CENTER_Y, 91, 27);
        switchArm.getStyleClass().addAll("gate-shape", "switch-arm");
        switchArm.setMouseTransparent(true);

        getChildren().addAll(leftContact, rightContact, switchArm);
        addBasicOutputTerminal(101, BASIC_CENTER_Y, "Y");
    }

    /** Compact fixed-rail source used for permanent logic high and logic low. */
    private void buildPowerSource(boolean high) {
        double symbolX = NODE_WIDTH / 2.0;
        if (high) {
            Line stem = new Line(symbolX, 70, symbolX, 29);
            Polygon arrow = new Polygon(
                    symbolX, 19,
                    symbolX - 10, 32,
                    symbolX + 10, 32);
            stem.getStyleClass().addAll("gate-shape", "power-symbol-line");
            arrow.getStyleClass().addAll("gate-shape", "power-symbol-fill");
            stem.setMouseTransparent(true);
            arrow.setMouseTransparent(true);
            getChildren().addAll(stem, arrow);
            addVerticalOutputTerminal(symbolX, 70, PinSide.BOTTOM);
        } else {
            // Conventional earth ground: the connection descends into the widest
            // bar, followed by progressively shorter bars below it.
            Line stem = new Line(symbolX, 18, symbolX, 45);
            Line groundTop = new Line(symbolX - 16, 45, symbolX + 16, 45);
            Line groundMiddle = new Line(symbolX - 10, 53, symbolX + 10, 53);
            Line groundBottom = new Line(symbolX - 4, 61, symbolX + 4, 61);
            for (Line line : List.of(stem, groundTop, groundMiddle, groundBottom)) {
                line.getStyleClass().addAll("gate-shape", "power-symbol-line");
                line.setMouseTransparent(true);
            }
            getChildren().addAll(stem, groundTop, groundMiddle, groundBottom);
            addVerticalOutputTerminal(symbolX, 18, PinSide.TOP);
        }
    }

    /** A zero-delay pass-through point whose output may fan out to any number of wires. */
    private void buildJunction() {
        double center = 20;
        Circle dragArea = new Circle(center, center, 9, Color.TRANSPARENT);
        dragArea.setCursor(Cursor.CROSSHAIR);
        installOutputWireInteraction(dragArea, 0);

        Circle dot = new Circle(center, center, 6);
        dot.getStyleClass().addAll("pin-handle", "input-pin", "output-pin", "junction-dot");
        dot.setCursor(Cursor.CROSSHAIR);
        installOutputWireInteraction(dot, 0);

        inputPins.add(dot);
        inputPinXs.add(center);
        inputPinYs.add(center);
        inputPinSides.add(PinSide.LEFT);
        outputPins.add(dot);
        outputPinXs.add(center);
        outputPinYs.add(center);
        outputPinSides.add(PinSide.RIGHT);
        getChildren().addAll(dragArea, dot);
    }

    private void buildLogicStateDisplay(boolean inputSource) {
        double displayX = 53;
        double displayWidth = 54;

        if (inputSource) {
            addBasicOutputTerminal(displayX + displayWidth, BASIC_CENTER_Y, "Y");
        } else {
            addBasicInputTerminal(BASIC_CENTER_Y, displayX, "IN");
        }

        Rectangle display = new Rectangle(displayX, 20, displayWidth, 50);
        display.setArcWidth(12);
        display.setArcHeight(12);
        display.getStyleClass().addAll("gate-shape", "logic-state-display");
        display.setMouseTransparent(true);

        logicStateLabel = new Label("0");
        logicStateLabel.setLayoutX(displayX);
        logicStateLabel.setLayoutY(20);
        logicStateLabel.setPrefSize(displayWidth, 50);
        logicStateLabel.setMouseTransparent(true);
        logicStateLabel.getStyleClass().add("logic-state-text");
        orientationStableLabels.add(logicStateLabel);

        getChildren().addAll(display, logicStateLabel);
    }

    private void buildBulb() {
        addBasicInputTerminal(BASIC_CENTER_Y, 59, "IN");

        Circle bulb = new Circle(82, BASIC_CENTER_Y, 23);
        bulb.getStyleClass().addAll("gate-shape", "bulb-glass");
        bulb.setMouseTransparent(true);

        Line filamentA = new Line(68, 31, 96, 59);
        Line filamentB = new Line(96, 31, 68, 59);
        filamentA.getStyleClass().add("bulb-filament");
        filamentB.getStyleClass().add("bulb-filament");
        filamentA.setMouseTransparent(true);
        filamentB.setMouseTransparent(true);

        getChildren().addAll(bulb, filamentA, filamentB);
    }

    /** Active-high seven-segment LED display with independent A through G inputs. */
    private void buildSevenSegmentDisplay() {
        double bodyLeft = BLOCK_BODY_LEFT;
        double bodyTop = 24;
        double bodyRight = nodeWidth - 22;
        double bodyBottom = nodeHeight - 16;

        Rectangle body = new Rectangle(
                bodyLeft,
                bodyTop,
                bodyRight - bodyLeft,
                bodyBottom - bodyTop);
        body.setArcWidth(10);
        body.setArcHeight(10);
        body.getStyleClass().addAll("gate-shape", "block-body", "seven-segment-body");
        body.setMouseTransparent(true);
        getChildren().add(body);

        List<String> names = type.inputNames(config);
        for (int index = 0; index < inputCount; index++) {
            addInputTerminal(
                    BLOCK_PIN_MARGIN,
                    distributedPosition(index, inputCount, 42, 216),
                    bodyLeft,
                    names.get(index),
                    true);
        }

        Rectangle displayWell = new Rectangle(83, 34, 128, 190);
        displayWell.setArcWidth(12);
        displayWell.setArcHeight(12);
        displayWell.getStyleClass().add("seven-segment-well");
        displayWell.setMouseTransparent(true);
        getChildren().add(displayWell);

        addSevenSegment(horizontalSegment(101, 43, 91, 12));       // A
        addSevenSegment(verticalSegment(190, 50, 119, 12));        // B
        addSevenSegment(verticalSegment(190, 132, 201, 12));       // C
        addSevenSegment(horizontalSegment(101, 199, 91, 12));      // D
        addSevenSegment(verticalSegment(90, 132, 201, 12));        // E
        addSevenSegment(verticalSegment(90, 50, 119, 12));         // F
        addSevenSegment(horizontalSegment(101, 121, 91, 12));      // G
    }

    private void addSevenSegment(Polygon segment) {
        segment.getStyleClass().add("seven-segment");
        segment.setMouseTransparent(true);
        sevenSegmentShapes.add(segment);
        getChildren().add(segment);
    }

    private static Polygon horizontalSegment(double x, double y, double width, double height) {
        double bevel = height / 2.0;
        return new Polygon(
                x + bevel, y,
                x + width - bevel, y,
                x + width, y + bevel,
                x + width - bevel, y + height,
                x + bevel, y + height,
                x, y + bevel);
    }

    private static Polygon verticalSegment(double x, double top, double bottom, double width) {
        double bevel = width / 2.0;
        return new Polygon(
                x + bevel, top,
                x + width, top + bevel,
                x + width, bottom - bevel,
                x + bevel, bottom,
                x, bottom - bevel,
                x, top + bevel);
    }

    private void buildClock() {
        double bodyX = 42;
        double bodyY = 25;
        double bodyWidth = nodeWidth - 84;
        double bodyHeight = 60;
        double centerY = bodyY + bodyHeight / 2.0;

        Rectangle body = new Rectangle(bodyX, bodyY, bodyWidth, bodyHeight);
        body.setArcWidth(8);
        body.setArcHeight(8);
        body.getStyleClass().addAll("gate-shape", "block-body", "clock-body");
        body.setMouseTransparent(true);

        // The square wave and output terminal share the exact vertical centre of
        // the clock body.  This avoids the upward drift seen in v0.92.
        double left = bodyX + 16;
        double right = bodyX + bodyWidth - 16;
        double high = centerY - 12;
        double low = centerY + 12;
        double step = (right - left) / 5.0;
        Polyline waveform = new Polyline(
                left, low,
                left + step, low,
                left + step, high,
                left + 2 * step, high,
                left + 2 * step, low,
                left + 3 * step, low,
                left + 3 * step, high,
                left + 4 * step, high,
                left + 4 * step, low,
                right, low);
        waveform.getStyleClass().add("clock-waveform");
        waveform.setMouseTransparent(true);

        getChildren().addAll(body, waveform);
        addOutputTerminal(bodyX + bodyWidth, centerY, nodeWidth - BLOCK_PIN_MARGIN, "CLK", false);
    }

    /** Classic tall trapezoid multiplexer with data pins on the left and selectors on the bottom. */
    private void buildMultiplexer() {
        int selectBits = config.bitWidth();
        int dataInputs = 1 << selectBits;

        double bodyLeft = 48;
        double bodyRight = nodeWidth - 48;
        double bodyTop = 28;
        double bodyBottom = nodeHeight - 58;
        double taper = Math.min(18, Math.max(10, (bodyRight - bodyLeft) * 0.08));

        Path body = new Path(
                new MoveTo(bodyLeft, bodyTop),
                new LineTo(bodyRight, bodyTop + taper),
                new LineTo(bodyRight, bodyBottom - taper),
                new LineTo(bodyLeft, bodyBottom),
                new ClosePath());
        styleGateShape(body);
        body.getStyleClass().add("mux-body");
        getChildren().add(body);

        Label title = createCenteredTitle("MUX", bodyLeft, bodyRight, bodyTop, bodyBottom);
        getChildren().add(title);

        List<String> inputNames = type.inputNames(config);

        /*
         * Data pins are distributed over the usable height of the trapezoid.  The
         * constructor guarantees enough height to keep even X0..X15 legible.
         */
        double firstDataY = bodyTop + 20;
        double lastDataY = bodyBottom - 20;
        for (int index = 0; index < dataInputs; index++) {
            double y = distributedPosition(index, dataInputs, firstDataY, lastDataY);
            addInputTerminal(
                    BLOCK_PIN_MARGIN,
                    y,
                    bodyLeft,
                    inputNames.get(index),
                    true);
        }

        /*
         * S0 is the LSB and is intentionally the RIGHT-most selector.  Each lead
         * begins on the actual sloping lower edge of the trapezoid (not at a shared
         * Y coordinate), so all selector pins visibly connect to the body.
         */
        double selectorPinY = bodyBottom + 38;
        double selectorSpanLeft = bodyLeft + 14;
        double selectorSpanRight = bodyRight - 14;
        for (int bit = 0; bit < selectBits; bit++) {
            double x = selectBits == 1
                    ? (bodyLeft + bodyRight) / 2.0
                    : selectorSpanRight
                            - bit * ((selectorSpanRight - selectorSpanLeft) / (selectBits - 1.0));

            double bodyEdgeY = muxBottomEdgeY(
                    x,
                    bodyLeft,
                    bodyRight,
                    bodyBottom,
                    taper);

            addBottomInputTerminal(
                    x,
                    selectorPinY,
                    bodyEdgeY,
                    inputNames.get(dataInputs + bit),
                    true);
        }

        double outputY = (bodyTop + bodyBottom) / 2.0;
        addOutputTerminal(
                bodyRight,
                outputY,
                nodeWidth - BLOCK_PIN_MARGIN,
                type.outputNames(config).get(0),
                true);
    }

    private static double muxBottomEdgeY(
            double x,
            double bodyLeft,
            double bodyRight,
            double bodyBottom,
            double taper) {
        if (bodyRight <= bodyLeft) {
            return bodyBottom;
        }
        double fraction = (x - bodyLeft) / (bodyRight - bodyLeft);
        fraction = Math.max(0.0, Math.min(1.0, fraction));
        return bodyBottom - taper * fraction;
    }

    /** Clean rectangular symbol used for decoder and encoder blocks. */
    private void buildFunctionalBlock(String titleText) {
        double bodyTop = 28;
        double bodyBottom = nodeHeight - 18;
        double bodyLeft = BLOCK_BODY_LEFT;
        double bodyRight = nodeWidth - BLOCK_BODY_RIGHT_MARGIN;

        Rectangle body = new Rectangle(bodyLeft, bodyTop, bodyRight - bodyLeft, bodyBottom - bodyTop);
        body.setArcWidth(8);
        body.setArcHeight(8);
        body.getStyleClass().addAll("gate-shape", "block-body");
        body.setMouseTransparent(true);

        Label title = createCenteredTitle(titleText, bodyLeft, bodyRight, bodyTop, bodyBottom);
        getChildren().addAll(body, title);

        double pinTop = bodyTop + 18;
        double pinBottom = bodyBottom - 18;

        List<String> inputNames = type.inputNames(config);
        for (int index = 0; index < inputCount; index++) {
            double inputY = OperandPinLayout.supports(type)
                    ? OperandPinLayout.inputY(type, config.bitWidth(), index, pinTop)
                    : distributedPosition(index, inputCount, pinTop, pinBottom);
            addInputTerminal(
                    BLOCK_PIN_MARGIN,
                    inputY,
                    bodyLeft,
                    inputNames.get(index),
                    true);
        }

        List<String> outputNames = type.outputNames(config);
        for (int index = 0; index < outputCount; index++) {
            addOutputTerminal(
                    bodyRight,
                    distributedPosition(index, outputCount, pinTop, pinBottom),
                    nodeWidth - BLOCK_PIN_MARGIN,
                    outputNames.get(index),
                    true);
        }
    }

    /** Physical top-view DIP package. Pin-array order follows the logical IC definition. */
    private void buildIntegratedCircuit() {
        double bodyLeft = 64;
        double bodyRight = nodeWidth - 64;
        double bodyTop = 34;
        double bodyBottom = nodeHeight - 24;

        Rectangle body = new Rectangle(bodyLeft, bodyTop, bodyRight - bodyLeft, bodyBottom - bodyTop);
        body.setArcWidth(14);
        body.setArcHeight(14);
        body.getStyleClass().addAll("gate-shape", "ic-body");
        body.setMouseTransparent(true);

        Circle notch = new Circle((bodyLeft + bodyRight) / 2.0, bodyTop, 12);
        notch.getStyleClass().add("ic-notch");
        notch.setMouseTransparent(true);

        Label partNumber = new Label(icDefinition.partNumber());
        partNumber.setLayoutX(bodyLeft + 18);
        partNumber.setLayoutY((bodyTop + bodyBottom) / 2.0 - 24);
        partNumber.setPrefSize(bodyRight - bodyLeft - 36, 28);
        partNumber.setAlignment(Pos.CENTER);
        partNumber.getStyleClass().add("ic-part-number");
        partNumber.setMouseTransparent(true);
        orientationStableLabels.add(partNumber);

        Label packageLabel = new Label(icDefinition.packageType().label() + "  •  " + icDefinition.family());
        packageLabel.setLayoutX(bodyLeft + 18);
        packageLabel.setLayoutY((bodyTop + bodyBottom) / 2.0 + 6);
        packageLabel.setPrefSize(bodyRight - bodyLeft - 36, 20);
        packageLabel.setAlignment(Pos.CENTER);
        packageLabel.getStyleClass().add("ic-package-label");
        packageLabel.setMouseTransparent(true);
        orientationStableLabels.add(packageLabel);

        getChildren().addAll(body, notch, partNumber, packageLabel);
        for (IcPin pin : icDefinition.inputPins()) {
            addDipInputTerminal(pin, bodyLeft, bodyRight, bodyTop, bodyBottom);
        }
        for (IcPin pin : icDefinition.outputPins()) {
            addDipOutputTerminal(pin, bodyLeft, bodyRight, bodyTop, bodyBottom);
        }
    }

    private void addDipInputTerminal(
            IcPin definitionPin,
            double bodyLeft,
            double bodyRight,
            double bodyTop,
            double bodyBottom) {

        PinSide side = dipPinSide(definitionPin.number());
        double y = dipPinY(definitionPin.number(), bodyTop, bodyBottom);
        double pinX = side == PinSide.LEFT ? BLOCK_PIN_MARGIN : nodeWidth - BLOCK_PIN_MARGIN;
        double bodyX = side == PinSide.LEFT ? bodyLeft : bodyRight;
        Line line = new Line(pinX, y, bodyX, y);
        line.getStyleClass().addAll("terminal-line", "input-terminal", "ic-terminal");
        line.setMouseTransparent(true);

        Circle pin = new Circle(pinX, y, 5);
        pin.getStyleClass().addAll("pin-handle", "input-pin", "ic-pin");
        if (definitionPin.role() == IcDefinition.IcPinRole.VCC
                || definitionPin.role() == IcDefinition.IcPinRole.GND) {
            pin.getStyleClass().add("ic-power-pin");
        }
        pin.setCursor(Cursor.CROSSHAIR);
        inputPins.add(pin);
        inputPinXs.add(pinX);
        inputPinYs.add(y);
        inputPinSides.add(side);
        getChildren().addAll(line, pin);
        addDipPinLabel(definitionPin, bodyX, y, side);
    }

    private void addDipOutputTerminal(
            IcPin definitionPin,
            double bodyLeft,
            double bodyRight,
            double bodyTop,
            double bodyBottom) {

        int outputIndex = outputPins.size();
        PinSide side = dipPinSide(definitionPin.number());
        double y = dipPinY(definitionPin.number(), bodyTop, bodyBottom);
        double pinX = side == PinSide.LEFT ? BLOCK_PIN_MARGIN : nodeWidth - BLOCK_PIN_MARGIN;
        double bodyX = side == PinSide.LEFT ? bodyLeft : bodyRight;
        Line line = new Line(bodyX, y, pinX, y);
        line.getStyleClass().addAll("terminal-line", "output-terminal", "ic-terminal");
        line.setMouseTransparent(true);

        Line dragArea = new Line(bodyX, y, pinX, y);
        dragArea.setStroke(Color.TRANSPARENT);
        dragArea.setStrokeWidth(18);
        dragArea.setCursor(Cursor.CROSSHAIR);
        installOutputWireInteraction(dragArea, outputIndex);

        Circle pin = new Circle(pinX, y, 5);
        pin.getStyleClass().addAll("pin-handle", "output-pin", "ic-pin");
        pin.setCursor(Cursor.CROSSHAIR);
        installOutputWireInteraction(pin, outputIndex);

        outputLines.add(line);
        outputPins.add(pin);
        outputPinXs.add(pinX);
        outputPinYs.add(y);
        outputPinSides.add(side);
        getChildren().addAll(line, dragArea, pin);
        addDipPinLabel(definitionPin, bodyX, y, side);
    }

    private void addDipPinLabel(IcPin definitionPin, double bodyX, double y, PinSide side) {
        Label label = createPinLabel(definitionPin.number() + "  " + definitionPin.name());
        label.setLayoutY(y - 9);
        label.setPrefSize(94, 18);
        if (side == PinSide.LEFT) {
            label.setLayoutX(bodyX + 7);
            label.setAlignment(Pos.CENTER_LEFT);
            label.setTextAlignment(TextAlignment.LEFT);
        } else {
            label.setLayoutX(bodyX - 101);
            label.setAlignment(Pos.CENTER_RIGHT);
            label.setTextAlignment(TextAlignment.RIGHT);
        }
        label.getStyleClass().add("ic-pin-label");
        pinLabels.add(label);
        getChildren().add(label);
    }

    private PinSide dipPinSide(int physicalPinNumber) {
        return physicalPinNumber <= icDefinition.packageType().pinCount() / 2
                ? PinSide.LEFT
                : PinSide.RIGHT;
    }

    private double dipPinY(int physicalPinNumber, double bodyTop, double bodyBottom) {
        int pinCount = icDefinition.packageType().pinCount();
        int pinsPerSide = pinCount / 2;
        int indexFromTop = physicalPinNumber <= pinsPerSide
                ? physicalPinNumber - 1
                : pinCount - physicalPinNumber;
        return distributedPosition(indexFromTop, pinsPerSide, bodyTop + 24, bodyBottom - 24);
    }

    /** Compact black-box symbol whose port names come from an abstracted circuit. */
    private void buildCustomComponent() {
        double bodyTop = 28;
        double bodyBottom = nodeHeight - 18;
        double bodyLeft = BLOCK_BODY_LEFT;
        double bodyRight = nodeWidth - BLOCK_BODY_RIGHT_MARGIN;

        Rectangle body = new Rectangle(bodyLeft, bodyTop, bodyRight - bodyLeft, bodyBottom - bodyTop);
        body.setArcWidth(12);
        body.setArcHeight(12);
        body.getStyleClass().addAll("gate-shape", "block-body", "custom-component-body");
        body.setMouseTransparent(true);

        Label title = createCenteredTitle(
                customDefinition.symbol(), bodyLeft, bodyRight, bodyTop, bodyBottom);
        title.getStyleClass().add("custom-component-title");
        getChildren().addAll(body, title);

        double pinTop = bodyTop + 20;
        double pinBottom = bodyBottom - 20;
        for (int index = 0; index < inputCount; index++) {
            addInputTerminal(
                    BLOCK_PIN_MARGIN,
                    distributedPosition(index, inputCount, pinTop, pinBottom),
                    bodyLeft,
                    customDefinition.inputNames().get(index),
                    true);
        }
        for (int index = 0; index < outputCount; index++) {
            addOutputTerminal(
                    bodyRight,
                    distributedPosition(index, outputCount, pinTop, pinBottom),
                    nodeWidth - BLOCK_PIN_MARGIN,
                    customDefinition.outputNames().get(index),
                    true);
        }
    }

    /** Flip-flop block with edge clock input and asynchronous active-low PRE/CLR controls. */
    private void buildFlipFlop() {
        double bodyTop = 28;
        double bodyBottom = nodeHeight - 28;
        double bodyLeft = BLOCK_BODY_LEFT;
        double bodyRight = nodeWidth - BLOCK_BODY_RIGHT_MARGIN;

        Rectangle body = new Rectangle(bodyLeft, bodyTop, bodyRight - bodyLeft, bodyBottom - bodyTop);
        body.setArcWidth(6);
        body.setArcHeight(6);
        body.getStyleClass().addAll("gate-shape", "block-body", "flipflop-body");
        body.setMouseTransparent(true);

        Label title = createCenteredTitle(type.getSymbol(), bodyLeft, bodyRight, bodyTop, bodyBottom);
        getChildren().addAll(body, title);

        List<String> inputNames = type.inputNames(config);
        int clockIndex = type.clockInputIndex(config);
        int leftInputCount = clockIndex + 1;
        double inputTop = bodyTop + 25;
        double inputBottom = bodyBottom - 25;
        for (int index = 0; index < leftInputCount; index++) {
            double y = distributedPosition(index, leftInputCount, inputTop, inputBottom);
            boolean isClockInput = index == clockIndex;
            addInputTerminal(BLOCK_PIN_MARGIN, y, bodyLeft, inputNames.get(index), !isClockInput);

            if (isClockInput) {
                Path clockMarker = new Path(
                        new MoveTo(bodyLeft, y - 7),
                        new LineTo(bodyLeft + 10, y),
                        new LineTo(bodyLeft, y + 7));
                clockMarker.getStyleClass().addAll("gate-shape", "clock-edge-marker");
                clockMarker.setMouseTransparent(true);
                getChildren().add(clockMarker);
            }
        }

        // Both active-low asynchronous controls leave the lower edge. Their names
        // remain inside the body so the external wiring area stays uncluttered.
        double asyncSpacing = (bodyRight - bodyLeft) * 0.28;
        double centerX = (bodyLeft + bodyRight) / 2.0;
        double asyncPinY = nodeHeight - 8;
        addBottomAsyncInputTerminal(
                centerX - asyncSpacing,
                asyncPinY,
                bodyBottom,
                inputNames.get(type.presetInputIndex(config)),
                true);
        addBottomAsyncInputTerminal(
                centerX + asyncSpacing,
                asyncPinY,
                bodyBottom,
                inputNames.get(type.clearInputIndex(config)),
                true);

        double outputTop = bodyTop + 25;
        double outputBottom = bodyBottom - 25;
        List<String> outputNames = type.outputNames(config);
        for (int index = 0; index < outputCount; index++) {
            addOutputTerminal(
                    bodyRight,
                    distributedPosition(index, outputCount, outputTop, outputBottom),
                    nodeWidth - BLOCK_PIN_MARGIN,
                    outputNames.get(index),
                    true);
        }
    }

    private Label createCenteredTitle(
            String text,
            double left,
            double right,
            double top,
            double bottom) {

        Label label = new Label(text);
        label.setLayoutX(left + 4);
        label.setLayoutY((top + bottom) / 2.0 - 13);
        label.setPrefWidth(right - left - 8);
        label.setPrefHeight(26);
        label.setMouseTransparent(true);
        label.getStyleClass().add("block-title");
        orientationStableLabels.add(label);
        return label;
    }

    private static double distributedPosition(
            int index,
            int count,
            double first,
            double last) {
        if (count <= 1) {
            return (first + last) / 2.0;
        }
        return first + index * ((last - first) / (count - 1.0));
    }

    private double pinY(int index, int count) {
        double contentBottom = nodeHeight - LABEL_AREA_HEIGHT;
        if (count <= 1) {
            return contentBottom / 2.0;
        }
        double available = contentBottom - 2 * BLOCK_VERTICAL_PADDING;
        return BLOCK_VERTICAL_PADDING + index * (available / (count - 1.0));
    }

    private void addBasicInputTerminal(double y, double bodyX, String name) {
        addInputTerminal(BASIC_INPUT_PIN_X, y, bodyX, name, false);
    }

    private void addInputTerminal(double pinX, double y, double bodyX, String name, boolean showLabel) {
        Line line = new Line(pinX, y, bodyX, y);
        line.getStyleClass().addAll("terminal-line", "input-terminal");
        line.setMouseTransparent(true);

        Circle pin = new Circle(pinX, y, 5);
        pin.getStyleClass().addAll("pin-handle", "input-pin");
        pin.setCursor(Cursor.CROSSHAIR);
        inputPins.add(pin);
        inputPinXs.add(pinX);
        inputPinYs.add(y);
        inputPinSides.add(PinSide.LEFT);

        getChildren().addAll(line, pin);
        if (showLabel) {
            addLeftPinLabel(name, bodyX, y);
        }
    }

    private void addBottomInputTerminal(
            double x,
            double pinY,
            double bodyY,
            String name,
            boolean showLabel) {

        Line line = new Line(x, bodyY, x, pinY);
        line.getStyleClass().addAll("terminal-line", "input-terminal", "selector-terminal");
        line.setMouseTransparent(true);

        Circle pin = new Circle(x, pinY, 5);
        pin.getStyleClass().addAll("pin-handle", "input-pin", "selector-pin");
        pin.setCursor(Cursor.CROSSHAIR);
        inputPins.add(pin);
        inputPinXs.add(x);
        inputPinYs.add(pinY);
        inputPinSides.add(PinSide.BOTTOM);
        getChildren().addAll(line, pin);

        if (showLabel) {
            addBottomPinLabel(name, x, pinY);
        }
    }

    private void addTopInputTerminal(
            double x, double pinY, double bodyY, String name, boolean activeLow) {
        addVerticalInputTerminal(x, pinY, bodyY, name, true, activeLow);
    }

    private void addBottomAsyncInputTerminal(
            double x, double pinY, double bodyY, String name, boolean activeLow) {
        addVerticalInputTerminal(x, pinY, bodyY, name, false, activeLow);
    }

    private void addVerticalInputTerminal(
            double x,
            double pinY,
            double bodyY,
            String name,
            boolean top,
            boolean activeLow) {
        Line line = new Line(x, bodyY, x, pinY);
        line.getStyleClass().addAll("terminal-line", "input-terminal", "async-terminal");
        line.setMouseTransparent(true);

        Circle pin = new Circle(x, pinY, 5);
        pin.getStyleClass().addAll("pin-handle", "input-pin", "async-pin");
        pin.setCursor(Cursor.CROSSHAIR);
        inputPins.add(pin);
        inputPinXs.add(x);
        inputPinYs.add(pinY);
        inputPinSides.add(top ? PinSide.TOP : PinSide.BOTTOM);
        getChildren().addAll(line, pin);

        if (activeLow) {
            Circle bubble = new Circle(x, bodyY, 4);
            bubble.getStyleClass().addAll("gate-shape", "active-low-bubble");
            bubble.setMouseTransparent(true);
            getChildren().add(bubble);
        }

        Label label = createPinLabel(name);
        if (top) {
            label.setLayoutX(x + 8);
            label.setLayoutY(bodyY - 23);
            label.setPrefSize(44, 16);
            label.setAlignment(Pos.CENTER_LEFT);
            label.setTextAlignment(TextAlignment.LEFT);
        } else {
            label.setLayoutX(x - 25);
            label.setLayoutY(bodyY - 22);
            label.setPrefSize(50, 16);
            label.setAlignment(Pos.CENTER);
            label.setTextAlignment(TextAlignment.CENTER);
        }
        pinLabels.add(label);
        getChildren().add(label);
    }

    private void addBasicOutputTerminal(double bodyX, double y, String name) {
        addOutputTerminal(bodyX, y, BASIC_OUTPUT_PIN_X, name, false);
    }

    private void addVerticalOutputTerminal(double x, double y, PinSide side) {
        int outputIndex = outputPins.size();
        Circle dragArea = new Circle(x, y, 12, Color.TRANSPARENT);
        dragArea.setCursor(Cursor.CROSSHAIR);
        installOutputWireInteraction(dragArea, outputIndex);

        Circle pin = new Circle(x, y, 5);
        pin.getStyleClass().addAll("pin-handle", "output-pin", "power-output-pin");
        pin.setCursor(Cursor.CROSSHAIR);
        installOutputWireInteraction(pin, outputIndex);

        outputPins.add(pin);
        outputPinXs.add(x);
        outputPinYs.add(y);
        outputPinSides.add(side);
        getChildren().addAll(dragArea, pin);
    }

    private void addOutputTerminal(
            double bodyX,
            double y,
            double pinX,
            String name,
            boolean showLabel) {

        int outputIndex = outputPins.size();
        Line line = new Line(bodyX, y, pinX, y);
        line.getStyleClass().addAll("terminal-line", "output-terminal");
        line.setMouseTransparent(true);

        Line dragArea = new Line(bodyX, y, pinX, y);
        dragArea.setStroke(Color.TRANSPARENT);
        dragArea.setStrokeWidth(18);
        dragArea.setCursor(Cursor.CROSSHAIR);
        installOutputWireInteraction(dragArea, outputIndex);

        Circle pin = new Circle(pinX, y, 5);
        pin.getStyleClass().addAll("pin-handle", "output-pin");
        pin.setCursor(Cursor.CROSSHAIR);
        installOutputWireInteraction(pin, outputIndex);

        outputLines.add(line);
        outputPins.add(pin);
        outputPinXs.add(pinX);
        outputPinYs.add(y);
        outputPinSides.add(PinSide.RIGHT);
        getChildren().addAll(line, dragArea, pin);

        if (showLabel) {
            addRightPinLabel(name, bodyX, y);
        }
    }

    private void addLeftPinLabel(String text, double bodyX, double y) {
        Label label = createPinLabel(text);
        label.setLayoutX(bodyX + 5);
        label.setLayoutY(y - 8);
        label.setPrefSize(type == ComponentType.CUSTOM ? 68 : 34, 16);
        label.setAlignment(Pos.CENTER_LEFT);
        label.setTextAlignment(TextAlignment.LEFT);
        pinLabels.add(label);
        getChildren().add(label);
    }

    private void addRightPinLabel(String text, double bodyX, double y) {
        Label label = createPinLabel(text);
        if (type == ComponentType.MULTIPLEXER || type == ComponentType.CLOCK) {
            // Keep narrow symbols clean by placing the output name just above the external lead.
            label.setLayoutX(bodyX + 4);
            label.setLayoutY(y - 17);
            label.setPrefSize(32, 14);
            label.setAlignment(Pos.CENTER_LEFT);
            label.setTextAlignment(TextAlignment.LEFT);
        } else {
            double width = type == ComponentType.CUSTOM ? 68 : 34;
            label.setLayoutX(bodyX - width - 5);
            label.setLayoutY(y - 8);
            label.setPrefSize(width, 16);
            label.setAlignment(Pos.CENTER_RIGHT);
            label.setTextAlignment(TextAlignment.RIGHT);
        }
        pinLabels.add(label);
        getChildren().add(label);
    }

    private void addBottomPinLabel(String text, double x, double pinY) {
        Label label = createPinLabel(text);
        // Selector names sit to the LEFT of the external pin so a vertical wire can
        // leave the pin without running through S0/S1/S2/S3 text.
        label.setLayoutX(x - 38);
        label.setLayoutY(pinY - 8);
        label.setPrefSize(28, 16);
        label.setAlignment(Pos.CENTER_RIGHT);
        label.setTextAlignment(TextAlignment.RIGHT);
        pinLabels.add(label);
        getChildren().add(label);
    }

    private Label createPinLabel(String text) {
        Label label = new Label(text == null ? "" : text);
        label.setMouseTransparent(true);
        label.getStyleClass().add("pin-name");
        orientationStableLabels.add(label);
        return label;
    }

    private void buildInstanceLabel() {
        componentLabel = new Label();
        double labelOffsetX = isBasicLogicGate(type) ? -10.0 : 0.0;
        componentLabel.setLayoutX(labelOffsetX);
        componentLabel.setLayoutY(0);
        componentLabel.setPrefWidth(nodeWidth);
        componentLabel.setMinWidth(nodeWidth);
        componentLabel.setMaxWidth(nodeWidth);
        componentLabel.setPrefHeight(16);
        componentLabel.setAlignment(Pos.CENTER);
        componentLabel.setTextAlignment(TextAlignment.CENTER);
        componentLabel.getStyleClass().add("component-label");
        componentLabel.setVisible(type != ComponentType.JUNCTION);
        componentLabel.setManaged(type != ComponentType.JUNCTION);
        componentLabel.setText(userLabel == null ? "" : userLabel);
        componentLabel.setCursor(Cursor.TEXT);
        componentLabel.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getButton() == MouseButton.PRIMARY && !editingLocked) {
                selectionHandler.onSelectionRequested(this, false);
                labelEditHandler.accept(this);
                event.consume();
            }
        });
        getChildren().add(componentLabel);
        updateInstanceLabelOrientation();
    }

    private void styleGateShape(Shape shape) {
        shape.getStyleClass().add("gate-shape");
        shape.setMouseTransparent(true);
    }

    private void installBodyInteraction(Rectangle hitBox) {
        hitBox.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() != MouseButton.PRIMARY) {
                return;
            }

            bodyPressedWithControl = event.isControlDown();
            selectionHandler.onSelectionRequested(this, bodyPressedWithControl);
            pressedSceneX = event.getSceneX();
            pressedSceneY = event.getSceneY();
            bodyWasDragged = false;
            if (!bodyPressedWithControl && !editingLocked) {
                moveHandler.onStarted(this, event.getSceneX(), event.getSceneY());
            }
            event.consume();
        });

        hitBox.addEventHandler(MouseEvent.MOUSE_DRAGGED, event -> {
            if (!event.isPrimaryButtonDown()) {
                return;
            }

            double distance = Math.hypot(
                    event.getSceneX() - pressedSceneX,
                    event.getSceneY() - pressedSceneY);
            if (distance > 4) {
                bodyWasDragged = true;
            }

            if (bodyWasDragged && !bodyPressedWithControl && !editingLocked) {
                moveHandler.onDragged(this, event.getSceneX(), event.getSceneY());
            }
            event.consume();
        });

        hitBox.addEventHandler(MouseEvent.MOUSE_RELEASED, event -> {
            if (event.getButton() == MouseButton.PRIMARY
                    && !bodyPressedWithControl
                    && !editingLocked) {
                moveHandler.onFinished(this);
            }

            if (event.getButton() == MouseButton.PRIMARY
                    && type.isToggleSource()
                    && !bodyWasDragged
                    && !bodyPressedWithControl) {
                sourceChangeStartedHandler.accept(this);
                setOutputState(0, !getOutputState(0));
                sourceChangedHandler.accept(this);
            }
            event.consume();
        });

        hitBox.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getButton() == MouseButton.PRIMARY
                    && event.getClickCount() == 2
                    && !bodyWasDragged
                    && !editingLocked) {
                labelEditHandler.accept(this);
                event.consume();
            }
        });
    }

    private void installOutputWireInteraction(Node dragTarget, int outputIndex) {
        dragTarget.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() != MouseButton.PRIMARY) {
                return;
            }
            selectionHandler.onSelectionRequested(this, event.isControlDown());
            if (!editingLocked) {
                setPendingConnectionVisual(true);
                wireDragHandler.onStarted(this, outputIndex, event.getSceneX(), event.getSceneY());
            }
            event.consume();
        });

        dragTarget.addEventHandler(MouseEvent.MOUSE_DRAGGED, event -> {
            if (!event.isPrimaryButtonDown() || editingLocked) {
                return;
            }
            wireDragHandler.onDragged(this, outputIndex, event.getSceneX(), event.getSceneY());
            event.consume();
        });

        dragTarget.addEventHandler(MouseEvent.MOUSE_RELEASED, event -> {
            if (event.getButton() != MouseButton.PRIMARY) {
                return;
            }
            setPendingConnectionVisual(false);
            if (!editingLocked) {
                wireDragHandler.onReleased(this, outputIndex, event.getSceneX(), event.getSceneY());
            }
            event.consume();
        });
    }

    public int getComponentId() {
        return componentId;
    }

    public ComponentType getType() {
        return type;
    }

    public ComponentConfig getConfig() {
        return config;
    }

    public CustomComponentDefinition getCustomDefinition() {
        return customDefinition;
    }

    public String getCustomDefinitionId() {
        return customDefinition == null ? "" : customDefinition.id();
    }

    public IcDefinition getIcDefinition() {
        return icDefinition;
    }

    public String getIcDefinitionId() {
        return icDefinition == null ? "" : icDefinition.id();
    }

    public boolean isSequentialBoundary() {
        return type.isSequentialBoundary()
                || (customRuntime != null && customRuntime.isSequential())
                || (icRuntime != null && icRuntime.isSequential());
    }

    public String getUserLabel() {
        return userLabel == null ? "" : userLabel;
    }

    public void setUserLabel(String label) {
        userLabel = label == null ? "" : label.strip();
        if (componentLabel != null) {
            componentLabel.setText(userLabel);
        }
    }

    /** Controls pin-name text independently of the actual pins (used to keep palette icons clean). */
    public void setPinLabelsVisible(boolean visible) {
        pinLabels.forEach(label -> label.setVisible(visible));
    }

    public int getInputCount() {
        return inputCount;
    }

    public int getOutputCount() {
        return outputCount;
    }

    public double getNodeWidth() {
        return nodeWidth;
    }

    public double getNodeHeight() {
        return nodeHeight;
    }

    public ComponentOrientation getOrientation() {
        return orientation;
    }

    public void setOrientation(ComponentOrientation orientation) {
        this.orientation = orientation == null ? ComponentOrientation.EAST : orientation;
        setRotate(this.orientation.degrees());
        // Text belongs to the symbol and therefore rotates with it.
        orientationStableLabels.forEach(label -> label.setRotate(0));
        updateInstanceLabelOrientation();
    }

    /** Keeps the instance label attached to the symbol's local top edge. */
    private void updateInstanceLabelOrientation() {
        if (componentLabel == null) {
            return;
        }
        componentLabel.setRotate(0);
        componentLabel.setPrefWidth(nodeWidth);
        componentLabel.setMinWidth(nodeWidth);
        componentLabel.setMaxWidth(nodeWidth);
        componentLabel.setLayoutX(isBasicLogicGate(type) ? -10.0 : 0.0);
        componentLabel.setLayoutY(type.isFlipFlop() ? -14.0 : 0.0);
    }

    public void rotateClockwise() {
        setOrientation(orientation.clockwise());
    }

    public String getInputName(int index) {
        return type == ComponentType.CUSTOM
                ? customDefinition.inputNames().get(index)
                : type == ComponentType.INTEGRATED_CIRCUIT
                        ? icDefinition.inputNames().get(index)
                        : type.inputNames(config).get(index);
    }

    public String getOutputName(int index) {
        return type == ComponentType.CUSTOM
                ? customDefinition.outputNames().get(index)
                : type == ComponentType.INTEGRATED_CIRCUIT
                        ? icDefinition.outputNames().get(index)
                        : type.outputNames(config).get(index);
    }

    public boolean getOutputState() {
        return outputCount > 0 ? outputStates[0] : displayState;
    }

    public boolean getOutputState(int outputIndex) {
        if (outputIndex < 0 || outputIndex >= outputCount) {
            return false;
        }
        return outputStates[outputIndex];
    }

    public boolean[] getOutputStatesCopy() {
        return Arrays.copyOf(outputStates, outputStates.length);
    }

    public void setOutputState(boolean state) {
        if (outputCount > 0) {
            setOutputState(0, state);
        } else {
            setDisplayState(state);
        }
    }

    public void setOutputState(int outputIndex, boolean state) {
        if (outputIndex < 0 || outputIndex >= outputCount) {
            return;
        }
        outputStates[outputIndex] = state;
        updateOutputTerminalVisual(outputIndex);
        if (outputIndex == 0) {
            displayState = state;
            if (switchArm != null) {
                switchArm.setEndX(91);
                switchArm.setEndY(state ? BASIC_CENTER_Y : 27);
            }
            if (logicStateLabel != null) {
                logicStateLabel.setText(state ? "1" : "0");
            }
            updateStateVisual();
        }
    }

    public void setOutputStates(boolean[] states) {
        int limit = Math.min(states.length, outputStates.length);
        for (int index = 0; index < limit; index++) {
            setOutputState(index, states[index]);
        }
    }

    public boolean getDisplayState() {
        return displayState;
    }

    public void setDisplayState(boolean state) {
        displayState = state;
        if (logicStateLabel != null) {
            logicStateLabel.setText(state ? "1" : "0");
        }
        updateStateVisual();
    }

    public boolean[] getSevenSegmentStatesCopy() {
        return type == ComponentType.SEVEN_SEGMENT_DISPLAY
                ? Arrays.copyOf(sevenSegmentStates, sevenSegmentStates.length)
                : new boolean[0];
    }

    public void setSevenSegmentStates(boolean[] states) {
        if (type != ComponentType.SEVEN_SEGMENT_DISPLAY) {
            return;
        }
        Arrays.fill(sevenSegmentStates, false);
        int limit = Math.min(sevenSegmentStates.length, states == null ? 0 : states.length);
        boolean anyActive = false;
        for (int index = 0; index < sevenSegmentStates.length; index++) {
            boolean active = index < limit && states[index];
            sevenSegmentStates[index] = active;
            anyActive |= active;
            if (index < sevenSegmentShapes.size()) {
                sevenSegmentShapes.get(index).pseudoClassStateChanged(ACTIVE, active);
            }
        }
        displayState = anyActive;
        updateStateVisual();
    }

    public boolean getStoredState() {
        return storedState;
    }

    public void setStoredState(boolean state) {
        storedState = state;
        if (type.isFlipFlop() && outputCount >= 2) {
            setOutputState(0, state);
            setOutputState(1, !state);
        }
    }

    public boolean getPreviousClockState() {
        return previousClockState;
    }

    public void setPreviousClockState(boolean previousClockState) {
        this.previousClockState = previousClockState;
    }

    public boolean hasInvalidAsyncControls() {
        return invalidAsyncControls
                || (customRuntime != null && customRuntime.hasInvalidAsyncControls())
                || (icRuntime != null && icRuntime.hasInvalidAsyncControls());
    }

    public void setInvalidAsyncControls(boolean invalidAsyncControls) {
        this.invalidAsyncControls = invalidAsyncControls;
    }

    public boolean evaluateCustomCombinational(boolean[] inputs) {
        if (customRuntime == null) {
            return false;
        }
        boolean[] previous = getOutputStatesCopy();
        setOutputStates(customRuntime.evaluateCombinational(inputs));
        return !Arrays.equals(previous, outputStates);
    }

    public boolean applyCustomAsynchronousControls(boolean[] inputs) {
        if (customRuntime == null) {
            return false;
        }
        boolean changed = customRuntime.applyAsynchronousControls(inputs);
        setOutputStates(customRuntime.outputs());
        return changed;
    }

    public boolean updateCustomSequential(boolean[] inputs) {
        if (customRuntime == null) {
            return false;
        }
        boolean changed = customRuntime.updateSequential(inputs);
        setOutputStates(customRuntime.outputs());
        return changed;
    }

    public boolean advanceCustomClocks(double elapsedSeconds) {
        return customRuntime != null && customRuntime.advanceClocks(elapsedSeconds);
    }

    public void synchronizeCustomClockMemory(boolean[] inputs) {
        if (customRuntime != null) {
            customRuntime.synchronizeClockMemory(inputs);
        }
    }

    public boolean hasUnstableCustomFeedback() {
        return customRuntime != null && customRuntime.isUnstableFeedback();
    }

    public String exportCustomState() {
        return customRuntime == null ? "" : customRuntime.exportState();
    }

    public void importCustomState(String state) {
        if (customRuntime == null || state == null || state.isBlank()) {
            return;
        }
        customRuntime.importState(state);
        setOutputStates(customRuntime.outputs());
    }

    public void setIcPowerConnections(boolean vccConnected, boolean gndConnected) {
        if (icRuntime != null) {
            icRuntime.setPowerConnections(vccConnected, gndConnected);
        }
    }

    public boolean evaluateIcCombinational(boolean[] inputs) {
        if (icRuntime == null) {
            return false;
        }
        boolean[] previous = getOutputStatesCopy();
        setOutputStates(icRuntime.evaluateCombinational(inputs));
        updateIcPowerVisual();
        return !Arrays.equals(previous, outputStates);
    }

    public boolean applyIcAsynchronousControls(boolean[] inputs) {
        if (icRuntime == null) {
            return false;
        }
        boolean changed = icRuntime.applyAsynchronousControls(inputs);
        setOutputStates(icRuntime.outputs());
        updateIcPowerVisual();
        return changed;
    }

    public boolean updateIcSequential(boolean[] inputs) {
        if (icRuntime == null) {
            return false;
        }
        boolean changed = icRuntime.updateSequential(inputs);
        setOutputStates(icRuntime.outputs());
        updateIcPowerVisual();
        return changed;
    }

    public void synchronizeIcClockMemory(boolean[] inputs) {
        if (icRuntime != null) {
            icRuntime.synchronizeClockMemory(inputs);
        }
    }

    public boolean isIcPowered() {
        return icRuntime != null && icRuntime.isPowered();
    }

    public String exportIcState() {
        return icRuntime == null ? "" : icRuntime.exportState();
    }

    public void importIcState(String state) {
        if (icRuntime == null || state == null || state.isBlank()) {
            return;
        }
        icRuntime.importState(state);
        setOutputStates(icRuntime.outputs());
        updateIcPowerVisual();
    }

    private void updateIcPowerVisual() {
        pseudoClassStateChanged(UNPOWERED, icRuntime != null && !icRuntime.isPowered());
    }

    /** Kept for compatibility; descriptions remain in the Properties panel. */
    public void setSubtitle(String subtitle) {
        // Intentionally no-op to keep workspace symbols uncluttered.
    }

    public void setSelectedVisual(boolean selected) {
        pseudoClassStateChanged(SELECTED, selected);
    }

    public void setPendingConnectionVisual(boolean pending) {
        pseudoClassStateChanged(PENDING, pending);
    }

    public void setEditingLocked(boolean locked) {
        editingLocked = locked;
        pseudoClassStateChanged(LOCKED, locked);
        if (componentLabel != null) {
            componentLabel.setCursor(locked ? Cursor.DEFAULT : Cursor.TEXT);
        }
    }

    public boolean isEditingLocked() {
        return editingLocked;
    }

    public double outputAnchorX(int outputIndex) {
        return outputAnchorPoint(outputIndex).getX();
    }

    public double outputAnchorY(int outputIndex) {
        return outputAnchorPoint(outputIndex).getY();
    }

    private Point2D outputAnchorPoint(int outputIndex) {
        double localX = outputIndex >= 0 && outputIndex < outputPinXs.size()
                ? outputPinXs.get(outputIndex)
                : outputLocalX();
        double localY = outputIndex >= 0 && outputIndex < outputPinYs.size()
                ? outputPinYs.get(outputIndex)
                : nodeHeight / 2.0;
        return localToParent(localX, localY);
    }

    public double inputAnchorX(int inputIndex) {
        return inputAnchorPoint(inputIndex).getX();
    }

    /** Compatibility helper for callers that do not specify an input index. */
    public double inputAnchorX() {
        return inputAnchorX(0);
    }

    public double inputAnchorY(int inputIndex) {
        return inputAnchorPoint(inputIndex).getY();
    }

    private Point2D inputAnchorPoint(int inputIndex) {
        double localX = inputIndex >= 0 && inputIndex < inputPinXs.size()
                ? inputPinXs.get(inputIndex)
                : (usesStandardSymbol(type) ? BASIC_INPUT_PIN_X : BLOCK_PIN_MARGIN);
        double localY = inputIndex >= 0 && inputIndex < inputPinYs.size()
                ? inputPinYs.get(inputIndex)
                : nodeHeight / 2.0;
        return localToParent(localX, localY);
    }

    /** Returns the routed approach direction after applying component rotation. */
    public PinSide inputPinSide(int inputIndex) {
        PinSide base = inputIndex >= 0 && inputIndex < inputPinSides.size()
                ? inputPinSides.get(inputIndex)
                : PinSide.LEFT;
        return rotatePinSide(base);
    }

    public PinSide outputPinSide(int outputIndex) {
        PinSide side = outputIndex >= 0 && outputIndex < outputPinSides.size()
                ? outputPinSides.get(outputIndex)
                : PinSide.RIGHT;
        return rotatePinSide(side);
    }

    private PinSide rotatePinSide(PinSide base) {
        int quarterTurns = ((int) Math.round(orientation.degrees() / 90.0)) & 3;
        PinSide result = base;
        for (int turn = 0; turn < quarterTurns; turn++) {
            result = switch (result) {
                case TOP -> PinSide.RIGHT;
                case RIGHT -> PinSide.BOTTOM;
                case BOTTOM -> PinSide.LEFT;
                case LEFT -> PinSide.TOP;
            };
        }
        return result;
    }

    private double outputLocalX() {
        return usesStandardSymbol(type) ? BASIC_OUTPUT_PIN_X : nodeWidth - BLOCK_PIN_MARGIN;
    }

    public double distanceToInputPinScene(int inputIndex, double sceneX, double sceneY) {
        if (inputIndex < 0 || inputIndex >= inputPins.size()) {
            return Double.POSITIVE_INFINITY;
        }
        Circle pin = inputPins.get(inputIndex);
        Point2D pinPoint = pin.localToScene(pin.getCenterX(), pin.getCenterY());
        return pinPoint == null ? Double.POSITIVE_INFINITY : pinPoint.distance(sceneX, sceneY);
    }

    public void setSelectionHandler(SelectionHandler selectionHandler) {
        this.selectionHandler = Objects.requireNonNull(selectionHandler);
    }

    public void setMoveHandler(MoveHandler moveHandler) {
        this.moveHandler = Objects.requireNonNull(moveHandler);
    }

    public void setPositionChangedHandler(Consumer<CircuitNode> positionChangedHandler) {
        this.positionChangedHandler = Objects.requireNonNull(positionChangedHandler);
    }

    public void notifyPositionChanged() {
        positionChangedHandler.accept(this);
    }

    public void setSwitchChangedHandler(Consumer<CircuitNode> sourceChangedHandler) {
        this.sourceChangedHandler = Objects.requireNonNull(sourceChangedHandler);
    }

    public void setSwitchChangeStartedHandler(Consumer<CircuitNode> sourceChangeStartedHandler) {
        this.sourceChangeStartedHandler = Objects.requireNonNull(sourceChangeStartedHandler);
    }

    public void setLabelEditHandler(Consumer<CircuitNode> labelEditHandler) {
        this.labelEditHandler = Objects.requireNonNull(labelEditHandler);
    }

    public void setWireDragHandler(WireDragHandler wireDragHandler) {
        this.wireDragHandler = Objects.requireNonNull(wireDragHandler);
    }

    private void updateStateVisual() {
        pseudoClassStateChanged(ACTIVE, displayState);
    }

    private void updateOutputTerminalVisual(int index) {
        boolean active = outputStates[index];
        if (index < outputLines.size()) {
            outputLines.get(index).pseudoClassStateChanged(ACTIVE, active);
        }
        if (index < outputPins.size()) {
            outputPins.get(index).pseudoClassStateChanged(ACTIVE, active);
        }
    }

    @FunctionalInterface
    public interface SelectionHandler {
        void onSelectionRequested(CircuitNode node, boolean toggleSelection);
    }

    public interface MoveHandler {
        MoveHandler NO_OP = new MoveHandler() {
            @Override public void onStarted(CircuitNode node, double sceneX, double sceneY) { }
            @Override public void onDragged(CircuitNode node, double sceneX, double sceneY) { }
            @Override public void onFinished(CircuitNode node) { }
        };

        void onStarted(CircuitNode node, double sceneX, double sceneY);
        void onDragged(CircuitNode node, double sceneX, double sceneY);
        void onFinished(CircuitNode node);
    }

    public interface WireDragHandler {
        WireDragHandler NO_OP = new WireDragHandler() {
            @Override
            public void onStarted(CircuitNode source, int outputIndex, double sceneX, double sceneY) { }
            @Override
            public void onDragged(CircuitNode source, int outputIndex, double sceneX, double sceneY) { }
            @Override
            public void onReleased(CircuitNode source, int outputIndex, double sceneX, double sceneY) { }
        };

        void onStarted(CircuitNode source, int outputIndex, double sceneX, double sceneY);
        void onDragged(CircuitNode source, int outputIndex, double sceneX, double sceneY);
        void onReleased(CircuitNode source, int outputIndex, double sceneX, double sceneY);
    }
}
