package com.logicforge.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class ComponentTypeJunctionTest {

    @Test
    void junctionIsAOneInputOneOutputCombinationalPassThrough() {
        ComponentType junction = ComponentType.JUNCTION;
        ComponentConfig config = ComponentConfig.defaults();

        assertEquals(1, junction.inputCount(config));
        assertEquals(1, junction.outputCount(config));
        assertEquals("IN", junction.inputNames(config).get(0));
        assertEquals("Y", junction.outputNames(config).get(0));
        assertFalse(junction.isSequentialBoundary());
        assertFalse(junction.isToggleSource());
        assertFalse(junction.isConstantSource());
    }
}
