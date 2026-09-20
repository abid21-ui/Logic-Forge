package com.logicforge.model;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable definition of a reusable component made from a LogicForge circuit.
 * Logic-input/switch nodes or boundary junctions act as external input ports;
 * logic-output/bulb nodes or boundary junctions act as external output ports.
 * Existing internal fan-out is preserved.
 */
public record CustomComponentDefinition(
        String id,
        String name,
        String symbol,
        String description,
        List<Port> inputPorts,
        List<Port> outputPorts,
        List<InternalComponent> components,
        List<InternalWire> wires) {

    public CustomComponentDefinition {
        id = requireText(id, "id");
        name = requireText(name, "name");
        symbol = requireText(symbol, "symbol");
        description = description == null ? "" : description.strip();
        if (description.length() > 240) {
            throw new IllegalArgumentException(
                    "Custom component description must contain 240 characters or fewer");
        }
        inputPorts = List.copyOf(inputPorts == null ? List.of() : inputPorts);
        outputPorts = List.copyOf(outputPorts == null ? List.of() : outputPorts);
        components = List.copyOf(components == null ? List.of() : components);
        wires = List.copyOf(wires == null ? List.of() : wires);

        if (components.isEmpty()) {
            throw new IllegalArgumentException("A custom component cannot be empty");
        }
        if (outputPorts.isEmpty()) {
            throw new IllegalArgumentException("A custom component needs at least one output port");
        }

        Set<Integer> componentIds = new HashSet<>();
        for (InternalComponent component : components) {
            if (!componentIds.add(component.componentId())) {
                throw new IllegalArgumentException("Duplicate internal component ID");
            }
        }
        validatePorts(inputPorts, componentIds, "input");
        validatePorts(outputPorts, componentIds, "output");

        Set<Integer> wireIds = new HashSet<>();
        Set<String> occupiedInputs = new HashSet<>();
        for (InternalWire wire : wires) {
            if (!wireIds.add(wire.connectionId())) {
                throw new IllegalArgumentException("Duplicate internal wire ID");
            }
            if (!componentIds.contains(wire.sourceComponentId())
                    || !componentIds.contains(wire.targetComponentId())) {
                throw new IllegalArgumentException("Internal wire references a missing component");
            }
            String target = wire.targetComponentId() + ":" + wire.targetInputIndex();
            if (!occupiedInputs.add(target)) {
                throw new IllegalArgumentException("Multiple internal wires drive one input");
            }
        }
    }

    /** Backward-compatible constructor for callers that do not supply a description. */
    public CustomComponentDefinition(
            String id,
            String name,
            String symbol,
            List<Port> inputPorts,
            List<Port> outputPorts,
            List<InternalComponent> components,
            List<InternalWire> wires) {

        this(id, name, symbol, "", inputPorts, outputPorts, components, wires);
    }

    public boolean hasDirectSequentialLogic() {
        return components.stream().anyMatch(component ->
                component.type().isFlipFlop()
                        || component.type().isClockedStateBlock()
                        || component.type().isClock());
    }

    public int inputCount() {
        return inputPorts.size();
    }

    public int outputCount() {
        return outputPorts.size();
    }

    public List<String> inputNames() {
        return inputPorts.stream().map(Port::name).toList();
    }

    public List<String> outputNames() {
        return outputPorts.stream().map(Port::name).toList();
    }

    private static void validatePorts(List<Port> ports, Set<Integer> ids, String kind) {
        Set<Integer> componentIds = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (Port port : ports) {
            if (!ids.contains(port.componentId())) {
                throw new IllegalArgumentException("Custom " + kind + " port references a missing component");
            }
            if (!componentIds.add(port.componentId())) {
                throw new IllegalArgumentException("An internal component cannot define two " + kind + " ports");
            }
            if (!names.add(port.name().toLowerCase())) {
                throw new IllegalArgumentException("Custom " + kind + " port names must be unique");
            }
        }
    }

    private static String requireText(String value, String field) {
        String normalized = value == null ? "" : value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Custom component " + field + " is required");
        }
        return normalized;
    }

    public record Port(String name, int componentId) {
        public Port {
            name = requireText(name, "port name");
            if (componentId <= 0) {
                throw new IllegalArgumentException("Port component ID must be positive");
            }
        }
    }

    public record InternalComponent(
            int componentId,
            ComponentType type,
            ComponentConfig config,
            String customDefinitionId,
            double x,
            double y,
            ComponentOrientation orientation,
            String label,
            List<Boolean> outputStates,
            boolean displayState,
            List<Boolean> sevenSegmentStates,
            boolean storedState,
            boolean previousClockState,
            String customState) {

        public InternalComponent {
            if (componentId <= 0) {
                throw new IllegalArgumentException("Internal component ID must be positive");
            }
            type = Objects.requireNonNull(type, "type");
            config = Objects.requireNonNull(config, "config");
            customDefinitionId = customDefinitionId == null ? "" : customDefinitionId.strip();
            if (type == ComponentType.CUSTOM && customDefinitionId.isEmpty()) {
                throw new IllegalArgumentException("Nested custom component needs a definition ID");
            }
            if (type != ComponentType.CUSTOM && !customDefinitionId.isEmpty()) {
                throw new IllegalArgumentException("Only custom components may reference a definition");
            }
            if (!Double.isFinite(x) || !Double.isFinite(y)) {
                throw new IllegalArgumentException("Internal component position must be finite");
            }
            orientation = orientation == null ? ComponentOrientation.EAST : orientation;
            label = label == null ? "" : label;
            outputStates = List.copyOf(outputStates == null ? List.of() : outputStates);
            sevenSegmentStates = List.copyOf(
                    sevenSegmentStates == null ? List.of() : sevenSegmentStates);
            customState = customState == null ? "" : customState;
        }
    }

    public record InternalWire(
            int connectionId,
            int sourceComponentId,
            int sourceOutputIndex,
            int targetComponentId,
            int targetInputIndex) {

        public InternalWire {
            if (connectionId <= 0
                    || sourceComponentId <= 0
                    || targetComponentId <= 0
                    || sourceOutputIndex < 0
                    || targetInputIndex < 0) {
                throw new IllegalArgumentException("Invalid internal wire endpoint");
            }
            if (sourceComponentId == targetComponentId) {
                throw new IllegalArgumentException("Direct internal self-feedback is unsupported");
            }
        }
    }
}
