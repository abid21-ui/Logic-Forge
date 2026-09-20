package com.logicforge.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.logicforge.model.CustomComponentDefinition;

/** Serializable representation of a LogicForge circuit project. */
public record LogicForgeProjectFile(
        int formatVersion,
        List<ComponentData> components,
        List<WireData> wires,
        int nextComponentId,
        int nextWireId,
        Map<String, Integer> labelCounters,
        List<CustomComponentDefinition> customDefinitions,
        String workspaceMode,
        BreadboardData breadboard) {

    public static final int CURRENT_FORMAT_VERSION = 6;

    public LogicForgeProjectFile {
        components = List.copyOf(components == null ? List.of() : components);
        wires = List.copyOf(wires == null ? List.of() : wires);
        labelCounters = Map.copyOf(labelCounters == null ? Map.of() : labelCounters);
        customDefinitions = List.copyOf(customDefinitions == null ? List.of() : customDefinitions);
        workspaceMode = workspaceMode == null || workspaceMode.isBlank()
                ? "SCHEMATIC"
                : workspaceMode.strip().toUpperCase();
        breadboard = breadboard == null ? BreadboardData.empty() : breadboard;
        if (!workspaceMode.equals("SCHEMATIC") && !workspaceMode.equals("BREADBOARD")) {
            throw new IllegalArgumentException("Unknown LogicForge workspace mode " + workspaceMode);
        }
    }

    /** Compatibility constructor used by schematic projects created before v1.8. */
    public LogicForgeProjectFile(
            int formatVersion,
            List<ComponentData> components,
            List<WireData> wires,
            int nextComponentId,
            int nextWireId,
            Map<String, Integer> labelCounters,
            List<CustomComponentDefinition> customDefinitions) {

        this(formatVersion, components, wires, nextComponentId, nextWireId,
                labelCounters, customDefinitions, "SCHEMATIC", BreadboardData.empty());
    }

    /** Persistent state for one component instance. */
    public record ComponentData(
            int componentId,
            String type,
            int bitWidth,
            double clockFrequencyHz,
            double x,
            double y,
            String orientation,
            String label,
            List<Boolean> outputStates,
            boolean displayState,
            List<Boolean> sevenSegmentStates,
            boolean storedState,
            boolean previousClockState,
            String customDefinitionId,
            String customState,
            String icDefinitionId,
            String icState) {

        public ComponentData {
            type = type == null ? "" : type;
            orientation = orientation == null ? "EAST" : orientation;
            label = label == null ? "" : label;
            outputStates = List.copyOf(outputStates == null ? List.of() : outputStates);
            sevenSegmentStates = List.copyOf(
                    sevenSegmentStates == null ? List.of() : sevenSegmentStates);
            customDefinitionId = customDefinitionId == null ? "" : customDefinitionId;
            customState = customState == null ? "" : customState;
            icDefinitionId = icDefinitionId == null ? "" : icDefinitionId;
            icState = icState == null ? "" : icState;
        }

        /** Compatibility constructor for callers that create pre-v4 component data. */
        public ComponentData(
                int componentId,
                String type,
                int bitWidth,
                double clockFrequencyHz,
                double x,
                double y,
                String orientation,
                String label,
                List<Boolean> outputStates,
                boolean displayState,
                List<Boolean> sevenSegmentStates,
                boolean storedState,
                boolean previousClockState,
                String customDefinitionId,
                String customState) {

            this(componentId, type, bitWidth, clockFrequencyHz, x, y, orientation, label,
                    outputStates, displayState, sevenSegmentStates, storedState,
                    previousClockState, customDefinitionId, customState, "", "");
        }
    }

    /** Persistent state for one directed wire. */
    public record WireData(
            int connectionId,
            int sourceComponentId,
            int sourceOutputIndex,
            int targetComponentId,
            int targetInputIndex,
            List<PointData> bendAnchors) {

        public WireData {
            bendAnchors = List.copyOf(bendAnchors == null ? List.of() : bendAnchors);
        }
    }

    /** JSON-safe replacement for JavaFX Point2D. */
    public record PointData(double x, double y) { }

    /** Persistent data for the separate socket-level breadboard editor. */
    public record BreadboardData(
            String size,
            List<BreadboardIcData> integratedCircuits,
            List<BreadboardJumperData> jumpers,
            int nextIcId,
            int nextJumperId,
            List<Boolean> switchStates) {

        public BreadboardData {
            size = size == null || size.isBlank() ? "HALF" : size.strip().toUpperCase();
            integratedCircuits = List.copyOf(
                    integratedCircuits == null ? List.of() : integratedCircuits);
            jumpers = List.copyOf(jumpers == null ? List.of() : jumpers);
            nextIcId = Math.max(1, nextIcId);
            nextJumperId = Math.max(1, nextJumperId);
            if (switchStates != null && switchStates.size() > 16) {
                throw new IllegalArgumentException("Breadboard supports exactly 16 switches");
            }
            List<Boolean> normalizedSwitches = new ArrayList<>(16);
            if (switchStates != null) {
                normalizedSwitches.addAll(switchStates.stream()
                        .map(Boolean.TRUE::equals)
                        .toList());
            }
            while (normalizedSwitches.size() < 16) {
                normalizedSwitches.add(false);
            }
            switchStates = List.copyOf(normalizedSwitches);
        }

        /** Compatibility constructor for v5 breadboard projects. */
        public BreadboardData(
                String size,
                List<BreadboardIcData> integratedCircuits,
                List<BreadboardJumperData> jumpers,
                int nextIcId,
                int nextJumperId) {

            this(size, integratedCircuits, jumpers, nextIcId, nextJumperId, List.of());
        }

        public static BreadboardData empty() {
            return new BreadboardData("HALF", List.of(), List.of(), 1, 1, List.of());
        }
    }

    public record BreadboardIcData(
            int id,
            String definitionId,
            int moduleIndex,
            int firstRow,
            String label,
            String state) {

        public BreadboardIcData {
            definitionId = definitionId == null ? "" : definitionId.strip();
            label = label == null ? "" : label.strip();
            state = state == null ? "" : state.strip();
        }
    }

    public record BreadboardJumperData(
            int id,
            String startHoleId,
            String endHoleId,
            String color) {

        public BreadboardJumperData {
            startHoleId = startHoleId == null ? "" : startHoleId.strip();
            endHoleId = endHoleId == null ? "" : endHoleId.strip();
            color = color == null || color.isBlank() ? "CYAN" : color.strip().toUpperCase();
        }
    }
}
