package com.logicforge.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class NativeComponentLogicTest {

    @Test
    void evaluatesEveryTwoInputNandCombination() {
        ComponentConfig config = ComponentConfig.bits(2);
        assertArrayEquals(new boolean[] { true }, evaluate(ComponentType.NAND, config, false, false));
        assertArrayEquals(new boolean[] { true }, evaluate(ComponentType.NAND, config, false, true));
        assertArrayEquals(new boolean[] { true }, evaluate(ComponentType.NAND, config, true, false));
        assertArrayEquals(new boolean[] { false }, evaluate(ComponentType.NAND, config, true, true));
    }

    @Test
    void evaluatesFourBitArithmeticAndComparisonFromOneSharedImplementation() {
        ComponentConfig config = ComponentConfig.bits(4);
        boolean[] fullAdderInputs = {
                true, false, true, false,
                true, true, false, false,
                true
        };
        assertArrayEquals(new boolean[] { true, false, false, true, false },
                evaluate(ComponentType.FULL_ADDER, config, fullAdderInputs));
        assertArrayEquals(new boolean[] { true, false, false },
                evaluate(ComponentType.COMPARATOR, config,
                        true, false, true, false,
                        true, true, false, false));
    }

    @Test
    void keepsDisplayAndSevenSegmentStateSeparateFromOutputs() {
        NativeComponentLogic.Evaluation output = NativeComponentLogic.evaluate(
                ComponentType.LOGIC_OUTPUT, ComponentConfig.defaults(), new boolean[] { true });
        assertEquals(0, output.outputs().length);
        assertEquals(true, output.displayState());

        NativeComponentLogic.Evaluation display = NativeComponentLogic.evaluate(
                ComponentType.SEVEN_SEGMENT_DISPLAY,
                ComponentConfig.defaults(),
                new boolean[] { true, false, true, false, true, false, true });
        assertArrayEquals(new boolean[] { true, false, true, false, true, false, true },
                display.sevenSegmentStates());
    }

    @Test
    void rejectsSequentialComponents() {
        assertThrows(IllegalArgumentException.class, () -> NativeComponentLogic.evaluate(
                ComponentType.COUNTER, ComponentConfig.bits(4), new boolean[] { false, false }));
    }

    private static boolean[] evaluate(
            ComponentType type,
            ComponentConfig config,
            boolean... inputs) {

        return NativeComponentLogic.evaluate(type, config, inputs).outputs();
    }
}
