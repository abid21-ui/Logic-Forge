package com.logicforge.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.logicforge.model.CustomComponentDefinition.InternalComponent;
import com.logicforge.model.CustomComponentDefinition.InternalWire;
import com.logicforge.model.CustomComponentDefinition.Port;

class CustomComponentRuntimeTest {

    @Test
    void preservesInternalFanOutAcrossAnAbstractedHalfAdder() {
        CustomComponentDefinition definition = halfAdder();
        Map<String, CustomComponentDefinition> definitions = Map.of(definition.id(), definition);
        CustomComponentRuntime runtime = new CustomComponentRuntime(definition, definitions::get);

        assertArrayEquals(new boolean[] { false, false },
                runtime.evaluateCombinational(new boolean[] { false, false }));
        assertArrayEquals(new boolean[] { true, false },
                runtime.evaluateCombinational(new boolean[] { true, false }));
        assertArrayEquals(new boolean[] { true, false },
                runtime.evaluateCombinational(new boolean[] { false, true }));
        assertArrayEquals(new boolean[] { false, true },
                runtime.evaluateCombinational(new boolean[] { true, true }));
    }

    @Test
    void customSequentialInstancesKeepIndependentStateAndRoundTripIt() {
        CustomComponentDefinition definition = dFlipFlopBlock();
        Map<String, CustomComponentDefinition> definitions = Map.of(definition.id(), definition);
        CustomComponentRuntime first = new CustomComponentRuntime(definition, definitions::get);
        CustomComponentRuntime second = new CustomComponentRuntime(definition, definitions::get);

        first.synchronizeClockMemory(new boolean[] { false, false });
        first.updateSequential(new boolean[] { true, false });
        first.updateSequential(new boolean[] { true, true });
        assertArrayEquals(new boolean[] { true }, first.outputs());
        assertArrayEquals(new boolean[] { false }, second.outputs());

        String savedState = first.exportState();
        CustomComponentRuntime restored = new CustomComponentRuntime(definition, definitions::get);
        restored.importState(savedState);
        assertArrayEquals(new boolean[] { true }, restored.outputs());
        assertFalse(restored.hasInvalidAsyncControls());
        assertFalse(restored.isUnstableFeedback());
    }

    @Test
    void junctionsActAsExternalPortsAndInternalPassThroughNodes() {
        List<InternalComponent> components = List.of(
                component(1, ComponentType.JUNCTION),
                component(2, ComponentType.NOT),
                component(3, ComponentType.JUNCTION));
        CustomComponentDefinition definition = new CustomComponentDefinition(
                "junction-inverter",
                "Junction Inverter",
                "JINV",
                List.of(new Port("A", 1)),
                List.of(new Port("Y", 3)),
                components,
                List.of(
                        wire(1, 1, 0, 2, 0),
                        wire(2, 2, 0, 3, 0)));

        CustomComponentRuntime runtime = new CustomComponentRuntime(definition, ignored -> null);
        assertArrayEquals(new boolean[] { true },
                runtime.evaluateCombinational(new boolean[] { false }));
        assertArrayEquals(new boolean[] { false },
                runtime.evaluateCombinational(new boolean[] { true }));
    }

    private static CustomComponentDefinition halfAdder() {
        List<InternalComponent> components = List.of(
                component(1, ComponentType.LOGIC_INPUT),
                component(2, ComponentType.LOGIC_INPUT),
                component(3, ComponentType.XOR),
                component(4, ComponentType.AND),
                component(5, ComponentType.LOGIC_OUTPUT),
                component(6, ComponentType.LOGIC_OUTPUT));
        List<InternalWire> wires = List.of(
                wire(1, 1, 0, 3, 0),
                wire(2, 2, 0, 3, 1),
                wire(3, 1, 0, 4, 0),
                wire(4, 2, 0, 4, 1),
                wire(5, 3, 0, 5, 0),
                wire(6, 4, 0, 6, 0));
        return new CustomComponentDefinition(
                "half-adder",
                "Half Adder",
                "HA",
                List.of(new Port("A", 1), new Port("B", 2)),
                List.of(new Port("SUM", 5), new Port("CARRY", 6)),
                components,
                wires);
    }

    private static CustomComponentDefinition dFlipFlopBlock() {
        List<InternalComponent> components = List.of(
                component(1, ComponentType.LOGIC_INPUT),
                component(2, ComponentType.LOGIC_INPUT),
                component(3, ComponentType.D_FLIP_FLOP),
                component(4, ComponentType.LOGIC_OUTPUT));
        List<InternalWire> wires = List.of(
                wire(1, 1, 0, 3, 0),
                wire(2, 2, 0, 3, 1),
                wire(3, 3, 0, 4, 0));
        return new CustomComponentDefinition(
                "dff-block",
                "D Storage",
                "DST",
                List.of(new Port("D", 1), new Port("CLK", 2)),
                List.of(new Port("Q", 4)),
                components,
                wires);
    }

    private static InternalComponent component(int id, ComponentType type) {
        ComponentConfig config = type.isVariableInputGate()
                ? ComponentConfig.bits(2)
                : ComponentConfig.defaults();
        int outputs = type.outputCount(config);
        List<Boolean> outputStates = java.util.stream.IntStream.range(0, outputs)
                .mapToObj(ignored -> false)
                .toList();
        return new InternalComponent(
                id,
                type,
                config,
                "",
                id * 100.0,
                100,
                ComponentOrientation.EAST,
                type.defaultLabelPrefix() + id,
                outputStates,
                false,
                List.of(),
                false,
                false,
                "");
    }

    private static InternalWire wire(
            int id,
            int source,
            int sourceOutput,
            int target,
            int targetInput) {

        return new InternalWire(id, source, sourceOutput, target, targetInput);
    }
}
