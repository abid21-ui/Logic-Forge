package com.logicforge.model;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.function.Function;

import com.logicforge.model.CustomComponentDefinition.InternalComponent;
import com.logicforge.model.CustomComponentDefinition.InternalWire;
import com.logicforge.model.CustomComponentDefinition.Port;

/** Independent hidden simulation state for one placed custom-component instance. */
public final class CustomComponentRuntime {

    private static final int STATE_FORMAT_VERSION = 1;
    private static final int MAX_SETTLE_PASSES = 128;
    private static final int MAX_SEQUENTIAL_PASSES = 12;

    private final CustomComponentDefinition definition;
    private final Function<String, CustomComponentDefinition> resolver;
    private final Map<Integer, RuntimeNode> nodes = new LinkedHashMap<>();
    private final Map<Integer, List<InternalWire>> incomingWiresByNode = new HashMap<>();
    private final Map<Integer, List<InternalWire>> outgoingWiresByNode = new HashMap<>();
    private final Set<Integer> externalInputNodeIds;
    private boolean unstableFeedback;

    public CustomComponentRuntime(
            CustomComponentDefinition definition,
            Function<String, CustomComponentDefinition> resolver) {

        this(definition, resolver, new HashSet<>());
    }

    private CustomComponentRuntime(
            CustomComponentDefinition definition,
            Function<String, CustomComponentDefinition> resolver,
            Set<String> constructionStack) {

        this.definition = java.util.Objects.requireNonNull(definition, "definition");
        this.resolver = java.util.Objects.requireNonNull(resolver, "resolver");
        this.externalInputNodeIds = definition.inputPorts().stream()
                .map(Port::componentId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!constructionStack.add(definition.id())) {
            throw new IllegalArgumentException("Recursive custom-component definition: " + definition.name());
        }

        try {
            for (InternalComponent component : definition.components()) {
                nodes.put(component.componentId(), createRuntimeNode(component, constructionStack));
            }
            indexWires();
            validateDefinitionPins();
            evaluateCombinational(new boolean[definition.inputCount()]);
        } finally {
            constructionStack.remove(definition.id());
        }
    }

    private void indexWires() {
        for (InternalWire wire : definition.wires()) {
            incomingWiresByNode
                    .computeIfAbsent(wire.targetComponentId(), ignored -> new ArrayList<>())
                    .add(wire);
            outgoingWiresByNode
                    .computeIfAbsent(wire.sourceComponentId(), ignored -> new ArrayList<>())
                    .add(wire);
        }
    }

    private RuntimeNode createRuntimeNode(
            InternalComponent component,
            Set<String> constructionStack) {

        CustomComponentRuntime nested = null;
        int outputCount;
        int inputCount;
        if (component.type() == ComponentType.CUSTOM) {
            CustomComponentDefinition nestedDefinition = resolver.apply(component.customDefinitionId());
            if (nestedDefinition == null) {
                throw new IllegalArgumentException(
                        "Missing nested custom-component definition " + component.customDefinitionId());
            }
            nested = new CustomComponentRuntime(nestedDefinition, resolver, constructionStack);
            if (!component.customState().isBlank()) {
                nested.importState(component.customState());
            }
            inputCount = nestedDefinition.inputCount();
            outputCount = nestedDefinition.outputCount();
        } else {
            inputCount = component.type().inputCount(component.config());
            outputCount = component.type().outputCount(component.config());
        }

        RuntimeNode runtime = new RuntimeNode(component, inputCount, outputCount, nested);
        int outputLimit = Math.min(outputCount, component.outputStates().size());
        for (int index = 0; index < outputLimit; index++) {
            runtime.outputs[index] = component.outputStates().get(index);
        }
        runtime.displayState = component.displayState();
        int segmentLimit = Math.min(runtime.sevenSegments.length, component.sevenSegmentStates().size());
        for (int index = 0; index < segmentLimit; index++) {
            runtime.sevenSegments[index] = component.sevenSegmentStates().get(index);
        }
        runtime.storedState = component.storedState();
        runtime.previousClockState = component.previousClockState();
        if (component.type().isFlipFlop() && runtime.outputs.length >= 2) {
            runtime.outputs[0] = runtime.storedState;
            runtime.outputs[1] = !runtime.storedState;
        } else if (component.type() == ComponentType.VCC && runtime.outputs.length > 0) {
            runtime.outputs[0] = true;
        } else if (component.type() == ComponentType.GROUND && runtime.outputs.length > 0) {
            runtime.outputs[0] = false;
        }
        return runtime;
    }

    private void validateDefinitionPins() {
        for (Port port : definition.inputPorts()) {
            RuntimeNode node = nodes.get(port.componentId());
            if (node == null || (node.component.type() != ComponentType.LOGIC_INPUT
                    && node.component.type() != ComponentType.SWITCH
                    && node.component.type() != ComponentType.JUNCTION)) {
                throw new IllegalArgumentException(
                        "Custom input ports must reference Logic Input, Switch, or Junction components");
            }
        }
        for (Port port : definition.outputPorts()) {
            RuntimeNode node = nodes.get(port.componentId());
            if (node == null || (node.component.type() != ComponentType.LOGIC_OUTPUT
                    && node.component.type() != ComponentType.BULB
                    && node.component.type() != ComponentType.JUNCTION)) {
                throw new IllegalArgumentException(
                        "Custom output ports must reference Logic Output, Bulb, or Junction components");
            }
        }
        for (InternalWire wire : definition.wires()) {
            RuntimeNode source = nodes.get(wire.sourceComponentId());
            RuntimeNode target = nodes.get(wire.targetComponentId());
            if (wire.sourceOutputIndex() >= source.outputCount
                    || wire.targetInputIndex() >= target.inputCount) {
                throw new IllegalArgumentException("Custom definition contains an invalid internal pin index");
            }
        }
    }

    public CustomComponentDefinition definition() {
        return definition;
    }

    public boolean isSequential() {
        return nodes.values().stream().anyMatch(this::isSequentialBoundary);
    }

    public boolean isUnstableFeedback() {
        return unstableFeedback
                || nodes.values().stream()
                        .filter(node -> node.nested != null)
                        .anyMatch(node -> node.nested.isUnstableFeedback());
    }

    public boolean hasInvalidAsyncControls() {
        return nodes.values().stream().anyMatch(node ->
                node.invalidAsyncControls
                        || (node.nested != null && node.nested.hasInvalidAsyncControls()));
    }

    public boolean[] evaluateCombinational(boolean[] externalInputs) {
        applyExternalInputs(externalInputs);
        settleCombinationalNetwork();
        return readExternalOutputs();
    }

    public boolean applyAsynchronousControls(boolean[] externalInputs) {
        applyExternalInputs(externalInputs);
        settleCombinationalNetwork();
        boolean changed = false;
        for (int pass = 0; pass < MAX_SEQUENTIAL_PASSES; pass++) {
            boolean passChanged = applyAsynchronousControlsOnce();
            changed |= passChanged;
            if (!passChanged) {
                break;
            }
            settleCombinationalNetwork();
        }
        return changed;
    }

    public boolean updateSequential(boolean[] externalInputs) {
        applyExternalInputs(externalInputs);
        settleCombinationalNetwork();
        boolean changed = false;
        for (int pass = 0; pass < MAX_SEQUENTIAL_PASSES; pass++) {
            boolean passChanged = updateSequentialOnce();
            changed |= passChanged;
            if (!passChanged) {
                break;
            }
            settleCombinationalNetwork();
        }
        return changed;
    }

    public boolean advanceClocks(double elapsedSeconds) {
        boolean changed = false;
        for (RuntimeNode node : nodes.values()) {
            if (node.component.type() == ComponentType.CLOCK) {
                node.clockAccumulator += elapsedSeconds;
                double halfPeriod = 0.5 / node.component.config().clockFrequencyHz();
                while (node.clockAccumulator + 1e-9 >= halfPeriod) {
                    node.clockAccumulator -= halfPeriod;
                    node.outputs[0] = !node.outputs[0];
                    node.displayState = node.outputs[0];
                    changed = true;
                }
            }
            if (node.nested != null) {
                changed |= node.nested.advanceClocks(elapsedSeconds);
            }
        }
        return changed;
    }

    public void synchronizeClockMemory(boolean[] externalInputs) {
        applyExternalInputs(externalInputs);
        settleCombinationalNetwork();
        for (RuntimeNode node : nodes.values()) {
            boolean[] inputs = readInputs(node);
            if (node.component.type().isFlipFlop()
                    || node.component.type().isClockedStateBlock()) {
                int clockIndex = node.component.type().clockInputIndex(node.component.config());
                node.previousClockState = clockIndex >= 0 && inputs[clockIndex];
            }
            if (node.nested != null) {
                node.nested.synchronizeClockMemory(inputs);
            }
        }
    }

    public boolean[] outputs() {
        return readExternalOutputs();
    }

    private void applyExternalInputs(boolean[] values) {
        for (int index = 0; index < definition.inputPorts().size(); index++) {
            RuntimeNode port = nodes.get(definition.inputPorts().get(index).componentId());
            boolean value = values != null && index < values.length && values[index];
            if (port.outputs.length > 0) {
                port.outputs[0] = value;
                port.displayState = value;
            }
        }
    }

    private boolean[] readExternalOutputs() {
        boolean[] outputs = new boolean[definition.outputPorts().size()];
        for (int index = 0; index < outputs.length; index++) {
            RuntimeNode port = nodes.get(definition.outputPorts().get(index).componentId());
            outputs[index] = port.displayState;
        }
        return outputs;
    }

    private void settleCombinationalNetwork() {
        unstableFeedback = false;
        List<RuntimeNode> order = topologicalOrder();
        for (RuntimeNode node : order) {
            evaluateNode(node);
        }
        if (order.size() == nodes.size()) {
            return;
        }

        Set<RuntimeNode> ordered = new HashSet<>(order);
        List<RuntimeNode> feedbackRegion = nodes.values().stream()
                .filter(node -> !ordered.contains(node))
                .sorted(Comparator.comparingInt(node -> node.component.componentId()))
                .toList();
        Set<String> seen = new HashSet<>();
        seen.add(stateSignature(feedbackRegion));
        for (int pass = 0; pass < MAX_SETTLE_PASSES; pass++) {
            boolean changed = false;
            for (RuntimeNode node : feedbackRegion) {
                changed |= evaluateNode(node);
            }
            if (!changed) {
                return;
            }
            if (!seen.add(stateSignature(feedbackRegion))) {
                unstableFeedback = true;
                return;
            }
        }
        unstableFeedback = true;
    }

    private List<RuntimeNode> topologicalOrder() {
        Map<RuntimeNode, Integer> indegree = new HashMap<>();
        nodes.values().forEach(node -> indegree.put(node, 0));
        for (InternalWire wire : definition.wires()) {
            RuntimeNode target = nodes.get(wire.targetComponentId());
            if (!isSequentialBoundary(target)) {
                indegree.computeIfPresent(target, (ignored, value) -> value + 1);
            }
        }

        Queue<RuntimeNode> queue = new ArrayDeque<>();
        indegree.entrySet().stream()
                .filter(entry -> entry.getValue() == 0)
                .map(Map.Entry::getKey)
                .sorted(Comparator.comparingInt(node -> node.component.componentId()))
                .forEach(queue::add);

        List<RuntimeNode> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            RuntimeNode node = queue.remove();
            order.add(node);
            for (InternalWire wire : outgoingWiresByNode.getOrDefault(
                    node.component.componentId(), List.of())) {
                RuntimeNode target = nodes.get(wire.targetComponentId());
                if (isSequentialBoundary(target)) {
                    continue;
                }
                int newDegree = indegree.computeIfPresent(target, (ignored, value) -> value - 1);
                if (newDegree == 0) {
                    queue.add(target);
                }
            }
        }
        return order;
    }

    private boolean isSequentialBoundary(RuntimeNode node) {
        return node.component.type().isFlipFlop()
                || node.component.type().isClockedStateBlock()
                || node.component.type().isClock()
                || (node.nested != null && node.nested.isSequential());
    }

    private boolean evaluateNode(RuntimeNode node) {
        ComponentType type = node.component.type();
        if (type.isToggleSource()
                || type.isClock()
                || type.isFlipFlop()
                || type.isClockedStateBlock()
                || (type == ComponentType.JUNCTION
                        && externalInputNodeIds.contains(node.component.componentId()))) {
            return false;
        }

        boolean[] previousOutputs = Arrays.copyOf(node.outputs, node.outputs.length);
        boolean previousDisplay = node.displayState;
        boolean[] previousSegments = Arrays.copyOf(node.sevenSegments, node.sevenSegments.length);
        boolean[] inputs = readInputs(node);

        if (type == ComponentType.CUSTOM) {
            boolean[] nestedOutputs = node.nested.evaluateCombinational(inputs);
            Arrays.fill(node.outputs, false);
            System.arraycopy(nestedOutputs, 0, node.outputs, 0,
                    Math.min(nestedOutputs.length, node.outputs.length));
            node.displayState = node.outputs.length > 0 && node.outputs[0];
        } else {
            NativeComponentLogic.Evaluation evaluation =
                    NativeComponentLogic.evaluate(type, node.component.config(), inputs);
            boolean[] evaluatedOutputs = evaluation.outputs();
            Arrays.fill(node.outputs, false);
            System.arraycopy(evaluatedOutputs, 0, node.outputs, 0,
                    Math.min(evaluatedOutputs.length, node.outputs.length));
            node.displayState = evaluation.displayState();
            Arrays.fill(node.sevenSegments, false);
            boolean[] evaluatedSegments = evaluation.sevenSegmentStates();
            System.arraycopy(evaluatedSegments, 0, node.sevenSegments, 0,
                    Math.min(evaluatedSegments.length, node.sevenSegments.length));
        }

        return previousDisplay != node.displayState
                || !Arrays.equals(previousOutputs, node.outputs)
                || !Arrays.equals(previousSegments, node.sevenSegments);
    }

    private boolean applyAsynchronousControlsOnce() {
        boolean changed = false;
        for (RuntimeNode node : nodes.values()) {
            if (node.nested != null) {
                boolean nestedChanged = node.nested.applyAsynchronousControls(readInputs(node));
                copyNestedOutputs(node);
                changed |= nestedChanged;
                continue;
            }
            ComponentType type = node.component.type();
            if (type.isClockedStateBlock()) {
                boolean[] inputs = readInputs(node);
                int resetIndex = type.resetInputIndex(node.component.config());
                if (resetIndex >= 0 && inputs[resetIndex]) {
                    boolean anySet = anyTrue(node.outputs);
                    Arrays.fill(node.outputs, false);
                    node.displayState = false;
                    changed |= anySet;
                }
                continue;
            }
            if (!type.isFlipFlop()) {
                continue;
            }
            boolean[] inputs = readInputs(node);
            SequentialComponentLogic.AsyncControl async =
                    SequentialComponentLogic.asynchronousControl(
                            type, node.component.config(), inputs, node.storedState);
            node.invalidAsyncControls = async.invalid();
            if (async.invalid()) {
                continue;
            }
            if (!async.asserted()) {
                continue;
            }
            changed |= setStoredState(node, async.nextState());
        }
        return changed;
    }

    private boolean updateSequentialOnce() {
        Map<RuntimeNode, Boolean> pending = new LinkedHashMap<>();
        Map<RuntimeNode, boolean[]> pendingOutputs = new LinkedHashMap<>();
        boolean changed = false;

        for (RuntimeNode node : nodes.values()) {
            if (node.nested != null) {
                boolean nestedChanged = node.nested.updateSequential(readInputs(node));
                copyNestedOutputs(node);
                changed |= nestedChanged;
                continue;
            }
            ComponentType type = node.component.type();
            if (type.isClockedStateBlock()) {
                boolean[] inputs = readInputs(node);
                int clockIndex = type.clockInputIndex(node.component.config());
                int resetIndex = type.resetInputIndex(node.component.config());
                boolean clock = clockIndex >= 0 && inputs[clockIndex];
                boolean risingEdge = !node.previousClockState && clock;
                node.previousClockState = clock;
                if (resetIndex >= 0 && inputs[resetIndex]) {
                    pendingOutputs.put(node, new boolean[node.outputs.length]);
                    continue;
                }
                if (!risingEdge) {
                    continue;
                }
                pendingOutputs.put(node,
                        SequentialComponentLogic.nextClockedBlockOutputs(
                                type, node.outputs, inputs));
                continue;
            }
            if (!type.isFlipFlop()) {
                continue;
            }

            boolean[] inputs = readInputs(node);
            int clockIndex = type.clockInputIndex(node.component.config());
            boolean clock = clockIndex >= 0 && inputs[clockIndex];
            boolean risingEdge = !node.previousClockState && clock;
            node.previousClockState = clock;
            SequentialComponentLogic.AsyncControl async =
                    SequentialComponentLogic.asynchronousControl(
                            type, node.component.config(), inputs, node.storedState);
            node.invalidAsyncControls = async.invalid();

            if (async.invalid()) {
                continue;
            }
            if (async.asserted()) {
                pending.put(node, async.nextState());
                continue;
            }
            if (!risingEdge) {
                continue;
            }

            pending.put(node, SequentialComponentLogic.nextFlipFlopState(
                    type, node.storedState, inputs));
        }

        for (Map.Entry<RuntimeNode, Boolean> entry : pending.entrySet()) {
            changed |= setStoredState(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<RuntimeNode, boolean[]> entry : pendingOutputs.entrySet()) {
            RuntimeNode node = entry.getKey();
            if (!Arrays.equals(node.outputs, entry.getValue())) {
                System.arraycopy(entry.getValue(), 0, node.outputs, 0, node.outputs.length);
                node.displayState = node.outputs.length > 0 && node.outputs[0];
                changed = true;
            }
        }
        return changed;
    }

    private void copyNestedOutputs(RuntimeNode node) {
        boolean[] nestedOutputs = node.nested.outputs();
        System.arraycopy(nestedOutputs, 0, node.outputs, 0,
                Math.min(nestedOutputs.length, node.outputs.length));
        node.displayState = node.outputs.length > 0 && node.outputs[0];
    }

    private boolean setStoredState(RuntimeNode node, boolean state) {
        if (node.storedState == state) {
            return false;
        }
        node.storedState = state;
        if (node.outputs.length >= 2) {
            node.outputs[0] = state;
            node.outputs[1] = !state;
            node.displayState = state;
        }
        return true;
    }

    private boolean[] readInputs(RuntimeNode node) {
        boolean[] inputs = new boolean[node.inputCount];
        ComponentType type = node.component.type();
        if (type.isFlipFlop()) {
            int preset = type.presetInputIndex(node.component.config());
            int clear = type.clearInputIndex(node.component.config());
            if (preset >= 0) {
                inputs[preset] = true;
            }
            if (clear >= 0) {
                inputs[clear] = true;
            }
        }
        for (InternalWire wire : incomingWiresByNode.getOrDefault(
                node.component.componentId(), List.of())) {
            RuntimeNode source = nodes.get(wire.sourceComponentId());
            inputs[wire.targetInputIndex()] = source.outputs[wire.sourceOutputIndex()];
        }
        return inputs;
    }

    private static boolean anyTrue(boolean[] values) {
        for (boolean value : values) {
            if (value) {
                return true;
            }
        }
        return false;
    }


    private static String stateSignature(List<RuntimeNode> region) {
        StringBuilder signature = new StringBuilder();
        for (RuntimeNode node : region) {
            signature.append(node.component.componentId()).append(':');
            for (boolean output : node.outputs) {
                signature.append(output ? '1' : '0');
            }
            signature.append(node.displayState ? '1' : '0').append(';');
        }
        return signature.toString();
    }

    public String exportState() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeInt(STATE_FORMAT_VERSION);
                out.writeInt(nodes.size());
                for (RuntimeNode node : nodes.values()) {
                    out.writeInt(node.component.componentId());
                    out.writeInt(node.outputs.length);
                    for (boolean output : node.outputs) {
                        out.writeBoolean(output);
                    }
                    out.writeBoolean(node.displayState);
                    out.writeInt(node.sevenSegments.length);
                    for (boolean segment : node.sevenSegments) {
                        out.writeBoolean(segment);
                    }
                    out.writeBoolean(node.storedState);
                    out.writeBoolean(node.previousClockState);
                    out.writeBoolean(node.invalidAsyncControls);
                    out.writeDouble(node.clockAccumulator);
                    writeString(out, node.nested == null ? "" : node.nested.exportState());
                }
            }
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException impossible) {
            throw new IllegalStateException("Could not serialize custom-component state", impossible);
        }
    }

    public void importState(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
                int version = in.readInt();
                if (version != STATE_FORMAT_VERSION) {
                    throw new IllegalArgumentException("Unsupported custom-component state version");
                }
                int count = in.readInt();
                if (count != nodes.size()) {
                    throw new IllegalArgumentException("Custom-component state does not match its definition");
                }
                for (int position = 0; position < count; position++) {
                    int componentId = in.readInt();
                    RuntimeNode node = nodes.get(componentId);
                    if (node == null) {
                        throw new IllegalArgumentException("Custom-component state references a missing node");
                    }
                    int outputCount = in.readInt();
                    if (outputCount != node.outputs.length) {
                        throw new IllegalArgumentException("Custom-component output state is incompatible");
                    }
                    for (int index = 0; index < outputCount; index++) {
                        node.outputs[index] = in.readBoolean();
                    }
                    node.displayState = in.readBoolean();
                    int segmentCount = in.readInt();
                    if (segmentCount != node.sevenSegments.length) {
                        throw new IllegalArgumentException("Custom-component display state is incompatible");
                    }
                    for (int index = 0; index < segmentCount; index++) {
                        node.sevenSegments[index] = in.readBoolean();
                    }
                    node.storedState = in.readBoolean();
                    node.previousClockState = in.readBoolean();
                    node.invalidAsyncControls = in.readBoolean();
                    node.clockAccumulator = in.readDouble();
                    String nestedState = readString(in);
                    if (node.nested == null && !nestedState.isEmpty()) {
                        throw new IllegalArgumentException("Unexpected nested custom-component state");
                    }
                    if (node.nested != null) {
                        node.nested.importState(nestedState);
                    }
                }
                if (in.available() != 0) {
                    throw new IllegalArgumentException("Custom-component state has trailing data");
                }
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid custom-component runtime state", exception);
        }
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > 64 * 1024 * 1024) {
            throw new IllegalArgumentException("Invalid nested state length");
        }
        byte[] bytes = in.readNBytes(length);
        if (bytes.length != length) {
            throw new IllegalArgumentException("Incomplete nested state");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static final class RuntimeNode {
        private final InternalComponent component;
        private final int inputCount;
        private final int outputCount;
        private final boolean[] outputs;
        private final boolean[] sevenSegments;
        private final CustomComponentRuntime nested;
        private boolean displayState;
        private boolean storedState;
        private boolean previousClockState;
        private boolean invalidAsyncControls;
        private double clockAccumulator;

        private RuntimeNode(
                InternalComponent component,
                int inputCount,
                int outputCount,
                CustomComponentRuntime nested) {

            this.component = component;
            this.inputCount = inputCount;
            this.outputCount = outputCount;
            this.outputs = new boolean[outputCount];
            this.sevenSegments = component.type() == ComponentType.SEVEN_SEGMENT_DISPLAY
                    ? new boolean[7]
                    : new boolean[0];
            this.nested = nested;
        }
    }
}
