package com.logicforge.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.logicforge.model.CustomComponentDefinition.InternalComponent;
import com.logicforge.model.CustomComponentDefinition.InternalWire;
import com.logicforge.model.CustomComponentDefinition.Port;

class ExpandedLogicBlocksTest {

    @Test
    void configurableArithmeticAndRoutingBlocksExposeExpectedPins() {
        ComponentConfig twoBits = ComponentConfig.bits(2);
        assertEquals(4, ComponentType.HALF_ADDER.inputCount(twoBits));
        assertEquals(3, ComponentType.HALF_ADDER.outputCount(twoBits));
        assertEquals(5, ComponentType.FULL_ADDER.inputCount(twoBits));
        assertEquals(3, ComponentType.FULL_ADDER.outputCount(twoBits));
        assertEquals(List.of("A>B", "A=B", "A<B"),
                ComponentType.COMPARATOR.outputNames(twoBits));
        assertEquals(4, ComponentType.DEMULTIPLEXER.outputCount(twoBits));
        assertTrue(ComponentType.COUNTER.isSequentialBoundary());
        assertTrue(ComponentType.SHIFT_REGISTER.isSequentialBoundary());
    }

    @Test
    void everyNativeComponentKeepsPinNamesAndCountsInSync() {
        ComponentConfig config = ComponentConfig.bits(4);
        for (ComponentType type : ComponentType.values()) {
            if (type == ComponentType.CUSTOM) {
                continue;
            }
            assertEquals(type.inputCount(config), type.inputNames(config).size(),
                    type + " input pin names");
            assertEquals(type.outputCount(config), type.outputNames(config).size(),
                    type + " output pin names");
        }
    }

    @Test
    void nativeTwoBitFullAdderRunsInsideACustomBlock() {
        CustomComponentDefinition definition = wrappedBlock(
                "native-adder",
                ComponentType.FULL_ADDER,
                ComponentConfig.bits(2));
        CustomComponentRuntime runtime = new CustomComponentRuntime(definition, ignored -> null);

        // A=3, B=1, CIN=0 -> binary 100: S0=0, S1=0, COUT=1.
        assertArrayEquals(new boolean[] { false, false, true },
                runtime.evaluateCombinational(
                        new boolean[] { true, true, true, false, false }));
    }

    @Test
    void nativeCounterKeepsMultiBitStateInsideACustomBlock() {
        CustomComponentDefinition definition = wrappedBlock(
                "native-counter",
                ComponentType.COUNTER,
                ComponentConfig.bits(2));
        CustomComponentRuntime runtime = new CustomComponentRuntime(definition, ignored -> null);

        runtime.synchronizeClockMemory(new boolean[] { false, false });
        runtime.updateSequential(new boolean[] { true, false });
        assertArrayEquals(new boolean[] { true, false }, runtime.outputs());
        runtime.updateSequential(new boolean[] { false, false });
        runtime.updateSequential(new boolean[] { true, false });
        assertArrayEquals(new boolean[] { false, true }, runtime.outputs());
        runtime.applyAsynchronousControls(new boolean[] { false, true });
        assertArrayEquals(new boolean[] { false, false }, runtime.outputs());
    }

    @Test
    void nativeShiftRegisterShiftsTowardHigherBitIndexes() {
        CustomComponentDefinition definition = wrappedBlock(
                "native-shift-register",
                ComponentType.SHIFT_REGISTER,
                ComponentConfig.bits(3));
        CustomComponentRuntime runtime = new CustomComponentRuntime(definition, ignored -> null);

        runtime.synchronizeClockMemory(new boolean[] { true, false, false });
        runtime.updateSequential(new boolean[] { true, true, false });
        assertArrayEquals(new boolean[] { true, false, false }, runtime.outputs());
        runtime.updateSequential(new boolean[] { false, false, false });
        runtime.updateSequential(new boolean[] { false, true, false });
        assertArrayEquals(new boolean[] { false, true, false }, runtime.outputs());
    }

    private static CustomComponentDefinition wrappedBlock(
            String id,
            ComponentType blockType,
            ComponentConfig config) {

        int inputCount = blockType.inputCount(config);
        int outputCount = blockType.outputCount(config);
        int blockId = inputCount + 1;
        List<InternalComponent> components = new ArrayList<>();
        List<Port> inputs = new ArrayList<>();
        List<Port> outputs = new ArrayList<>();
        List<InternalWire> wires = new ArrayList<>();
        int wireId = 1;

        for (int index = 0; index < inputCount; index++) {
            int componentId = index + 1;
            components.add(component(componentId, ComponentType.JUNCTION,
                    ComponentConfig.defaults()));
            inputs.add(new Port("IN" + index, componentId));
            wires.add(new InternalWire(wireId++, componentId, 0, blockId, index));
        }
        components.add(component(blockId, blockType, config));
        for (int index = 0; index < outputCount; index++) {
            int componentId = blockId + index + 1;
            components.add(component(componentId, ComponentType.LOGIC_OUTPUT,
                    ComponentConfig.defaults()));
            outputs.add(new Port("OUT" + index, componentId));
            wires.add(new InternalWire(wireId++, blockId, index, componentId, 0));
        }
        return new CustomComponentDefinition(
                id, id, "TEST", inputs, outputs, components, wires);
    }

    private static InternalComponent component(
            int id,
            ComponentType type,
            ComponentConfig config) {

        List<Boolean> states = java.util.stream.IntStream
                .range(0, type.outputCount(config))
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
                states,
                false,
                List.of(),
                false,
                false,
                "");
    }
}
