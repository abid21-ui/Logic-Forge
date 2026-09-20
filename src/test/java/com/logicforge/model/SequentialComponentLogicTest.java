package com.logicforge.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SequentialComponentLogicTest {

    @Test
    void evaluatesJkAndAsynchronousControls() {
        ComponentConfig config = ComponentConfig.defaults();
        assertTrue(SequentialComponentLogic.nextFlipFlopState(
                ComponentType.JK_FLIP_FLOP, false,
                new boolean[] { true, false, false, true, true }));
        assertFalse(SequentialComponentLogic.nextFlipFlopState(
                ComponentType.JK_FLIP_FLOP, true,
                new boolean[] { false, true, false, true, true }));

        SequentialComponentLogic.AsyncControl preset =
                SequentialComponentLogic.asynchronousControl(
                        ComponentType.JK_FLIP_FLOP, config,
                        new boolean[] { false, false, false, false, true }, false);
        assertTrue(preset.asserted());
        assertTrue(preset.nextState());
        assertFalse(preset.invalid());
    }

    @Test
    void advancesCountersAndShiftRegisters() {
        assertArrayEquals(new boolean[] { false, false, true },
                SequentialComponentLogic.nextClockedBlockOutputs(
                        ComponentType.COUNTER,
                        new boolean[] { true, true, false },
                        new boolean[] { true, false }));
        assertArrayEquals(new boolean[] { true, true, false },
                SequentialComponentLogic.nextClockedBlockOutputs(
                        ComponentType.SHIFT_REGISTER,
                        new boolean[] { true, false, false },
                        new boolean[] { true, true, false }));
    }
}
