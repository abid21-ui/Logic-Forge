package com.logicforge.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ComponentTypePowerSourceTest {

    private static final ComponentConfig CONFIG = ComponentConfig.defaults();

    @Test
    void vccAndGroundAreFixedSingleOutputSources() {
        for (ComponentType type : new ComponentType[] {
                ComponentType.VCC, ComponentType.GROUND }) {
            assertTrue(type.isConstantSource());
            assertTrue(type.isSequentialBoundary());
            assertFalse(type.isToggleSource());
            assertEquals(0, type.inputCount(CONFIG));
            assertEquals(1, type.outputCount(CONFIG));
        }
        assertEquals("VCC", ComponentType.VCC.outputNames(CONFIG).get(0));
        assertEquals("GND", ComponentType.GROUND.outputNames(CONFIG).get(0));
    }
}
