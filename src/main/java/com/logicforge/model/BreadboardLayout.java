package com.logicforge.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Immutable geometry and conductive-strip map for the physical breadboard editor.
 * The board is rotated ninety degrees compared with a bench breadboard so DIP
 * packages run vertically and remain readable in the editor.
 */
public final class BreadboardLayout {

    public static final double HOLE_PITCH = 24.0;
    public static final double HOLE_RADIUS = 5.0;
    public static final double IC_TOP_PADDING = HOLE_PITCH;
    public static final int CONTROL_CHANNEL_COUNT = 16;

    // Keep the input controls visually detached from the physical board shell.
    private static final double TOP_MARGIN = 230.0;
    private static final double BOTTOM_MARGIN = 194.0;
    private static final double INPUT_BOARD_GAP = 82.0;
    private static final double CONTROL_PIN_LENGTH = 30.0;
    private static final double POWER_SOURCE_GAP = 32.0;
    private static final double SIDE_MARGIN = 42.0;
    private static final double MODULE_GAP = 72.0;
    private static final int MODULE_LAST_COLUMN = 18;
    private static final int LEFT_IC_COLUMN = 7;
    private static final int RIGHT_IC_COLUMN = 11;

    private final Size size;
    private final double width;
    private final double height;
    private final List<Hole> holes;
    private final Map<String, Hole> holesById;

    public BreadboardLayout(Size size) {
        this.size = size == null ? Size.HALF : size;
        this.width = Math.max(physicalBoardWidth(), controlRight() + SIDE_MARGIN);
        this.height = TOP_MARGIN + (this.size.rows() - 1) * HOLE_PITCH + BOTTOM_MARGIN;

        Map<String, Hole> generated = new LinkedHashMap<>();
        for (int module = 0; module < this.size.modules(); module++) {
            addModuleHoles(generated, module);
        }
        addControlPoints(generated);
        holesById = Map.copyOf(generated);
        holes = List.copyOf(generated.values());
    }

    public Size size() {
        return size;
    }

    public double width() {
        return width;
    }

    public double height() {
        return height;
    }

    public List<Hole> holes() {
        return holes;
    }

    public Hole hole(String id) {
        return id == null ? null : holesById.get(id);
    }

    public Hole nearestHole(double x, double y, double maximumDistance) {
        Hole nearest = null;
        double bestSquared = maximumDistance * maximumDistance;
        for (Hole hole : holes) {
            double deltaX = hole.x() - x;
            double deltaY = hole.y() - y;
            double distanceSquared = deltaX * deltaX + deltaY * deltaY;
            if (distanceSquared <= bestSquared) {
                bestSquared = distanceSquared;
                nearest = hole;
            }
        }
        return nearest;
    }

    public IcPlacement nearestIcPlacement(double centerX, double centerY, int physicalPinCount) {
        int pinsPerSide = physicalPinCount / 2;
        int module = nearestModule(centerX);
        int firstRow = (int) Math.round(
                (centerY - TOP_MARGIN) / HOLE_PITCH - (pinsPerSide - 1) / 2.0);
        firstRow = Math.max(0, Math.min(size.rows() - pinsPerSide, firstRow));
        return placement(module, firstRow, physicalPinCount);
    }

    public IcPlacement placement(int module, int firstRow, int physicalPinCount) {
        int pinsPerSide = physicalPinCount / 2;
        if (module < 0 || module >= size.modules()) {
            throw new IllegalArgumentException("Breadboard module is outside the selected board");
        }
        if (physicalPinCount < 2 || physicalPinCount % 2 != 0
                || firstRow < 0 || firstRow + pinsPerSide > size.rows()) {
            throw new IllegalArgumentException("IC does not fit on the selected breadboard");
        }
        Hole pinOne = hole(holeId(module, firstRow, LEFT_IC_COLUMN));
        List<String> occupied = new ArrayList<>(physicalPinCount);
        for (int pinNumber = 1; pinNumber <= physicalPinCount; pinNumber++) {
            occupied.add(icPinHoleId(module, firstRow, physicalPinCount, pinNumber));
        }
        return new IcPlacement(
                module,
                firstRow,
                pinOne.x(),
                pinOne.y() - IC_TOP_PADDING,
                (RIGHT_IC_COLUMN - LEFT_IC_COLUMN) * HOLE_PITCH,
                (pinsPerSide + 1) * HOLE_PITCH,
                occupied);
    }

    public String icPinHoleId(
            int module,
            int firstRow,
            int physicalPinCount,
            int pinNumber) {

        int pinsPerSide = physicalPinCount / 2;
        if (pinNumber < 1 || pinNumber > physicalPinCount) {
            throw new IllegalArgumentException("Invalid physical IC pin number");
        }
        boolean left = pinNumber <= pinsPerSide;
        int row = left
                ? firstRow + pinNumber - 1
                : firstRow + physicalPinCount - pinNumber;
        return holeId(module, row, left ? LEFT_IC_COLUMN : RIGHT_IC_COLUMN);
    }

    public double moduleLeft(int module) {
        return SIDE_MARGIN + module * (MODULE_LAST_COLUMN * HOLE_PITCH + MODULE_GAP);
    }

    public double trenchLeft(int module) {
        return moduleLeft(module) + 8 * HOLE_PITCH;
    }

    public double trenchWidth() {
        return 3 * HOLE_PITCH;
    }

    public double boardTop() {
        return TOP_MARGIN - 38;
    }

    public double boardBottom() {
        return TOP_MARGIN + (size.rows() - 1) * HOLE_PITCH + 38;
    }

    public double controlLeft() {
        // Centre the I/O panels over the complete physical board, including
        // every module in double/triple/quadruple board layouts.
        return (physicalBoardWidth() - switchPanelWidth()) / 2.0;
    }

    public double switchPanelTop() {
        return 20;
    }

    public double switchPanelWidth() {
        return CONTROL_CHANNEL_COUNT * HOLE_PITCH + 28;
    }

    public double switchPanelBottom() {
        return boardTop() - INPUT_BOARD_GAP;
    }

    public double switchPinY() {
        return switchPanelBottom() + CONTROL_PIN_LENGTH;
    }

    public double bulbPanelTop() {
        return boardBottom() + 48;
    }

    public double bulbPinY() {
        return bulbPanelTop() - CONTROL_PIN_LENGTH;
    }

    public double controlPinX(int index) {
        if (index < 0 || index >= CONTROL_CHANNEL_COUNT) {
            throw new IllegalArgumentException("Control channel must be between 0 and 15");
        }
        return controlLeft() + 26 + index * HOLE_PITCH;
    }

    public String switchHoleId(int index) {
        return "SW" + checkedChannel(index);
    }

    public String bulbHoleId(int index) {
        return "BULB" + checkedChannel(index);
    }

    public String vccHoleId() {
        return "VCC";
    }

    public String groundHoleId() {
        return "GND";
    }

    private int nearestModule(double centerX) {
        int nearest = 0;
        double best = Double.POSITIVE_INFINITY;
        for (int module = 0; module < size.modules(); module++) {
            double icCenter = moduleLeft(module)
                    + (LEFT_IC_COLUMN + RIGHT_IC_COLUMN) * HOLE_PITCH / 2.0;
            double distance = Math.abs(centerX - icCenter);
            if (distance < best) {
                best = distance;
                nearest = module;
            }
        }
        return nearest;
    }

    private void addModuleHoles(Map<String, Hole> generated, int module) {
        for (int row = 0; row < size.rows(); row++) {
            addHole(generated, module, row, 0, HoleKind.POSITIVE_RAIL,
                    "M" + module + ":LEFT:+");
            addHole(generated, module, row, 1, HoleKind.NEGATIVE_RAIL,
                    "M" + module + ":LEFT:-");
            for (int column = 3; column <= LEFT_IC_COLUMN; column++) {
                addHole(generated, module, row, column, HoleKind.TERMINAL,
                        "M" + module + ":ROW" + row + ":LEFT");
            }
            for (int column = RIGHT_IC_COLUMN; column <= 15; column++) {
                addHole(generated, module, row, column, HoleKind.TERMINAL,
                        "M" + module + ":ROW" + row + ":RIGHT");
            }
            addHole(generated, module, row, 17, HoleKind.POSITIVE_RAIL,
                    "M" + module + ":RIGHT:+");
            addHole(generated, module, row, 18, HoleKind.NEGATIVE_RAIL,
                    "M" + module + ":RIGHT:-");
        }
    }

    private void addControlPoints(Map<String, Hole> generated) {
        for (int channel = 0; channel < CONTROL_CHANNEL_COUNT; channel++) {
            String switchId = switchHoleId(channel);
            generated.put(switchId, new Hole(
                    switchId,
                    controlPinX(channel),
                    switchPinY(),
                    "CONTROL:SW" + channel,
                    HoleKind.SWITCH_OUTPUT,
                    -1,
                    channel,
                    -1));

            String bulbId = bulbHoleId(channel);
            generated.put(bulbId, new Hole(
                    bulbId,
                    controlPinX(channel),
                    bulbPinY(),
                    "CONTROL:BULB" + channel,
                    HoleKind.BULB_INPUT,
                    -1,
                    channel,
                    -1));
        }
        generated.put(vccHoleId(), new Hole(
                vccHoleId(), powerPinX(0), switchPinY(), "SOURCE:VCC",
                HoleKind.VCC_SOURCE, -1, -1, -1));
        generated.put(groundHoleId(), new Hole(
                groundHoleId(), powerPinX(1), switchPinY(), "SOURCE:GND",
                HoleKind.GND_SOURCE, -1, -1, -1));
    }

    private double powerPinX(int index) {
        // Keep both power sources the same distance from the panel edges.
        return index == 0
                ? controlLeft() - POWER_SOURCE_GAP
                : controlLeft() + switchPanelWidth() + POWER_SOURCE_GAP;
    }

    private double controlRight() {
        return controlLeft() + switchPanelWidth() + POWER_SOURCE_GAP + 28;
    }

    private double physicalBoardWidth() {
        return SIDE_MARGIN * 2
                + size.modules() * MODULE_LAST_COLUMN * HOLE_PITCH
                + Math.max(0, size.modules() - 1) * MODULE_GAP;
    }

    private static int checkedChannel(int index) {
        if (index < 0 || index >= CONTROL_CHANNEL_COUNT) {
            throw new IllegalArgumentException("Control channel must be between 0 and 15");
        }
        return index;
    }

    private void addHole(
            Map<String, Hole> generated,
            int module,
            int row,
            int column,
            HoleKind kind,
            String conductiveGroup) {

        String id = holeId(module, row, column);
        generated.put(id, new Hole(
                id,
                moduleLeft(module) + column * HOLE_PITCH,
                TOP_MARGIN + row * HOLE_PITCH,
                conductiveGroup,
                kind,
                module,
                row,
                column));
    }

    private static String holeId(int module, int row, int column) {
        return "M" + module + "R" + row + "C" + column;
    }

    public enum Size {
        MINI(17, 1, "Mini • 170 tie points"),
        HALF(30, 1, "Half • 300 tie points"),
        FULL(63, 1, "Full • 630 tie points"),
        DOUBLE(63, 2, "Double • two full boards"),
        TRIPLE(63, 3, "Triple • three full boards"),
        QUADRUPLE(63, 4, "Quadruple • four full boards"),
        QUINTUPLE(63, 5, "Quintuple • five full boards");

        private final int rows;
        private final int modules;
        private final String label;

        Size(int rows, int modules, String label) {
            this.rows = rows;
            this.modules = modules;
            this.label = label;
        }

        public int rows() {
            return rows;
        }

        public int modules() {
            return modules;
        }

        public String label() {
            return label;
        }

        public static Size parse(String value) {
            if (value == null || value.isBlank()) {
                return HALF;
            }
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        }
    }

    public enum HoleKind {
        TERMINAL,
        POSITIVE_RAIL,
        NEGATIVE_RAIL,
        SWITCH_OUTPUT,
        BULB_INPUT,
        VCC_SOURCE,
        GND_SOURCE;

        public boolean isPhysicalSocket() {
            return this == TERMINAL || this == POSITIVE_RAIL || this == NEGATIVE_RAIL;
        }
    }

    public record Hole(
            String id,
            double x,
            double y,
            String conductiveGroup,
            HoleKind kind,
            int module,
            int row,
            int column) { }

    public record IcPlacement(
            int module,
            int firstRow,
            double x,
            double y,
            double width,
            double height,
            List<String> pinHoleIds) {

        public IcPlacement {
            pinHoleIds = List.copyOf(pinHoleIds);
        }
    }
}
