package com.logicforge.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class IcRuntimeTest {

    @Test
    void catalogueDefinitionsContainEveryPhysicalPin() {
        assertEquals(12, IcCatalog.all().size());
        for (IcDefinition definition : IcCatalog.all()) {
            assertEquals(definition.packageType().pinCount(), definition.pins().size());
            assertTrue(definition.inputIndex("VCC") >= 0);
            assertTrue(definition.inputIndex("GND") >= 0);
        }
    }

    @Test
    void quadNandRequiresPowerAndExposesIndependentGates() {
        IcDefinition definition = IcCatalog.require("74HC00");
        IcRuntime runtime = poweredRuntime(definition);
        boolean[] inputs = poweredInputs(definition);

        setInput(definition, inputs, "1A", true);
        setInput(definition, inputs, "1B", true);
        setInput(definition, inputs, "2A", true);
        setInput(definition, inputs, "2B", false);
        boolean[] outputs = runtime.evaluateCombinational(inputs);
        assertFalse(output(definition, outputs, "1Y"));
        assertTrue(output(definition, outputs, "2Y"));

        runtime.setPowerConnections(false, true);
        outputs = runtime.evaluateCombinational(inputs);
        assertFalse(runtime.isPowered());
        for (boolean state : outputs) {
            assertFalse(state);
        }
    }

    @Test
    void fourBitAdderUsesThePhysicalA1ThroughA4PinNames() {
        IcDefinition definition = IcCatalog.require("74HC283");
        IcRuntime runtime = poweredRuntime(definition);
        boolean[] inputs = poweredInputs(definition);
        writeNibble(definition, inputs, "A", 9);
        writeNibble(definition, inputs, "B", 7);
        setInput(definition, inputs, "C0", true);

        boolean[] outputs = runtime.evaluateCombinational(inputs);
        assertTrue(output(definition, outputs, "S1"));
        assertFalse(output(definition, outputs, "S2"));
        assertFalse(output(definition, outputs, "S3"));
        assertFalse(output(definition, outputs, "S4"));
        assertTrue(output(definition, outputs, "C4"));
    }

    @Test
    void dualDFlipFlopCapturesOnlyOnRisingEdgesAndRoundTripsState() {
        IcDefinition definition = IcCatalog.require("74HC74");
        IcRuntime runtime = poweredRuntime(definition);
        boolean[] inputs = poweredInputs(definition);
        setInput(definition, inputs, "1/CLR", true);
        setInput(definition, inputs, "1/PRE", true);
        setInput(definition, inputs, "2/CLR", true);
        setInput(definition, inputs, "2/PRE", true);
        setInput(definition, inputs, "1D", true);

        runtime.synchronizeClockMemory(inputs);
        setInput(definition, inputs, "1CLK", true);
        runtime.updateSequential(inputs);
        assertTrue(output(definition, runtime.outputs(), "1Q"));
        assertFalse(output(definition, runtime.outputs(), "1/Q"));

        String saved = runtime.exportState();
        IcRuntime restored = poweredRuntime(definition);
        restored.evaluateCombinational(inputs);
        restored.importState(saved);
        assertEquals(saved, restored.exportState());
        assertTrue(output(definition, restored.outputs(), "1Q"));
    }

    @Test
    void comparatorImplementsTheDatasheetCascadeCornerCases() {
        IcDefinition definition = IcCatalog.require("74HC85");
        IcRuntime runtime = poweredRuntime(definition);
        boolean[] inputs = poweredInputs(definition);

        boolean[] outputs = runtime.evaluateCombinational(inputs);
        assertTrue(output(definition, outputs, "QA>B"));
        assertTrue(output(definition, outputs, "QA<B"));
        assertFalse(output(definition, outputs, "QA=B"));

        setInput(definition, inputs, "IA=B", true);
        outputs = runtime.evaluateCombinational(inputs);
        assertFalse(output(definition, outputs, "QA>B"));
        assertFalse(output(definition, outputs, "QA<B"));
        assertTrue(output(definition, outputs, "QA=B"));
    }

    private static IcRuntime poweredRuntime(IcDefinition definition) {
        IcRuntime runtime = new IcRuntime(definition);
        runtime.setPowerConnections(true, true);
        return runtime;
    }

    private static boolean[] poweredInputs(IcDefinition definition) {
        boolean[] inputs = new boolean[definition.inputCount()];
        setInput(definition, inputs, "VCC", true);
        setInput(definition, inputs, "GND", false);
        return inputs;
    }

    private static void writeNibble(
            IcDefinition definition,
            boolean[] inputs,
            String prefix,
            int value) {

        for (int bit = 0; bit < 4; bit++) {
            setInput(definition, inputs, prefix + (bit + 1), (value & (1 << bit)) != 0);
        }
    }

    private static void setInput(
            IcDefinition definition,
            boolean[] inputs,
            String name,
            boolean value) {

        int index = definition.inputIndex(name);
        if (index < 0) {
            throw new IllegalArgumentException("Unknown input " + name);
        }
        inputs[index] = value;
    }

    private static boolean output(
            IcDefinition definition,
            boolean[] outputs,
            String name) {

        return outputs[definition.outputIndex(name)];
    }
}
