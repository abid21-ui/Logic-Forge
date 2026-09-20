package com.logicforge.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

final class BreadboardDemosTest {

    @Test
    void everyDemoLoadsTwoPoweredIcsWithValidSocketOccupancy() {
        List<BreadboardCircuit> demos = List.of(
                BreadboardDemos.halfAdder(),
                BreadboardDemos.nandInverterChain(),
                BreadboardDemos.andOrChain());

        for (BreadboardCircuit demo : demos) {
            assertEquals(2, demo.integratedCircuits().size());
            assertTrue(demo.integratedCircuits().stream()
                    .allMatch(BreadboardCircuit.PlacedIc::isPowered));
            assertTrue(demo.jumpers().size() >= 8);
            assertEquals(0, demo.conflictCount());
            assertEquals(demo.toData(), BreadboardCircuit.fromData(demo.toData()).toData());
        }
    }

    @Test
    void newVersionDemosLoadPoweredAndConflictFree() {
        List<BreadboardCircuit> demos = List.of(
                BreadboardDemos.fullAdder(),
                BreadboardDemos.twosComplementSubtractor());

        assertEquals(3, demos.get(0).integratedCircuits().size());
        assertEquals(2, demos.get(1).integratedCircuits().size());
        for (BreadboardCircuit demo : demos) {
            assertTrue(demo.integratedCircuits().stream()
                    .allMatch(BreadboardCircuit.PlacedIc::isPowered));
            assertEquals(0, demo.conflictCount());
            assertEquals(demo.toData(), BreadboardCircuit.fromData(demo.toData()).toData());
        }
    }

    @Test
    void fullAdderStartsAtOnePlusZeroPlusOne() {
        BreadboardCircuit demo = BreadboardDemos.fullAdder();

        assertEquals(BreadboardCircuit.Signal.LOW, demo.bulbSignal(0));
        assertEquals(BreadboardCircuit.Signal.HIGH, demo.bulbSignal(1));
    }

    @Test
    void subtractorStartsAtFiveMinusThree() {
        BreadboardCircuit demo = BreadboardDemos.twosComplementSubtractor();

        assertEquals(BreadboardCircuit.Signal.LOW, demo.bulbSignal(0));
        assertEquals(BreadboardCircuit.Signal.HIGH, demo.bulbSignal(1));
        assertEquals(BreadboardCircuit.Signal.LOW, demo.bulbSignal(2));
        assertEquals(BreadboardCircuit.Signal.LOW, demo.bulbSignal(3));
        assertEquals(BreadboardCircuit.Signal.HIGH, demo.bulbSignal(4));
    }

    @Test
    void halfAdderDrivesSeparateSumAndCarryOutputs() {
        BreadboardCircuit demo = BreadboardDemos.halfAdder();

        assertEquals(BreadboardCircuit.Signal.HIGH, demo.bulbSignal(0));
        assertEquals(BreadboardCircuit.Signal.LOW, demo.bulbSignal(1));

        demo.setSwitchState(1, true);
        assertEquals(BreadboardCircuit.Signal.LOW, demo.bulbSignal(0));
        assertEquals(BreadboardCircuit.Signal.HIGH, demo.bulbSignal(1));
    }
}
