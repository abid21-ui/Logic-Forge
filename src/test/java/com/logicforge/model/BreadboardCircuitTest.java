package com.logicforge.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class BreadboardCircuitTest {

    @Test
    void eachSocketAcceptsOnlyOneLeadOrJumperEndpoint() {
        BreadboardCircuit circuit = new BreadboardCircuit(BreadboardLayout.Size.HALF);
        assertTrue(circuit.addJumper("M0R0C7", "M0R0C0").succeeded());

        double icCenterX = circuit.layout().moduleLeft(0) + 9 * BreadboardLayout.HOLE_PITCH;
        double icCenterY = circuit.layout().hole("M0R3C7").y();
        BreadboardCircuit.PlacementResult placement = circuit.placeIc(
                IcCatalog.require("74HC00"), icCenterX, icCenterY);

        assertFalse(placement.succeeded());
        assertEquals("M0R0C7", placement.occupiedHoleId());
    }

    @Test
    void jumperCannotDuplicateAnInternalTerminalConnection() {
        BreadboardCircuit circuit = new BreadboardCircuit(BreadboardLayout.Size.HALF);
        BreadboardCircuit.JumperResult result = circuit.addJumper("M0R4C3", "M0R4C7");

        assertFalse(result.succeeded());
        assertTrue(result.error().contains("already connected"));
    }

    @Test
    void poweredNandIcEvaluatesThroughPhysicalStripsAndJumpers() {
        BreadboardCircuit circuit = new BreadboardCircuit(BreadboardLayout.Size.HALF);
        double icCenterX = circuit.layout().moduleLeft(0) + 9 * BreadboardLayout.HOLE_PITCH;
        double icCenterY = circuit.layout().hole("M0R3C7").y();
        BreadboardCircuit.PlacementResult result = circuit.placeIc(
                IcCatalog.require("74HC00"), icCenterX, icCenterY);
        assertTrue(result.succeeded());

        // VCC pin 14, GND pin 7, and both first-gate inputs are wired via
        // free sockets in their five-hole terminal strips.
        assertTrue(circuit.addJumper("M0R0C12", "VCC").succeeded());
        assertTrue(circuit.addJumper("M0R6C6", "GND").succeeded());
        assertTrue(circuit.addJumper("M0R0C6", "SW0").succeeded());
        assertTrue(circuit.addJumper("M0R1C6", "SW1").succeeded());
        circuit.setSwitchState(0, true);
        circuit.setSwitchState(1, true);

        assertTrue(result.placedIc().isPowered());
        assertEquals(BreadboardCircuit.Signal.LOW, circuit.signalAt("M0R2C6"));
        assertEquals(0, circuit.conflictCount());
    }

    @Test
    void freeSocketsInPowerRowsCanDaisyChainVccAndGroundAcrossIcs() {
        BreadboardCircuit circuit = new BreadboardCircuit(BreadboardLayout.Size.HALF);
        double centerX = circuit.layout().moduleLeft(0) + 9 * BreadboardLayout.HOLE_PITCH;
        BreadboardCircuit.PlacementResult first = circuit.placeIc(
                IcCatalog.require("74HC00"),
                centerX, circuit.layout().hole("M0R3C7").y());
        BreadboardCircuit.PlacementResult second = circuit.placeIc(
                IcCatalog.require("74HC04"),
                centerX, circuit.layout().hole("M0R18C7").y());
        assertTrue(first.succeeded());
        assertTrue(second.succeeded());

        // Pin 14 shares its right-side row with C12–C15. Pin 7 shares its
        // left-side row with C3–C6, so one free socket can feed another IC.
        assertTrue(circuit.addJumper("VCC", "M0R0C12").succeeded());
        assertTrue(circuit.addJumper("M0R0C13", "M0R15C12").succeeded());
        assertTrue(circuit.addJumper("GND", "M0R6C6").succeeded());
        assertTrue(circuit.addJumper("M0R6C5", "M0R21C6").succeeded());

        assertTrue(first.placedIc().isPowered());
        assertTrue(second.placedIc().isPowered());
        assertEquals(BreadboardCircuit.Signal.HIGH, circuit.signalAt("M0R15C15"));
        assertEquals(BreadboardCircuit.Signal.LOW, circuit.signalAt("M0R21C3"));
    }

    @Test
    void visualPowerRailsFloatUntilConnectedToDedicatedSources() {
        BreadboardCircuit circuit = new BreadboardCircuit(BreadboardLayout.Size.HALF);

        assertEquals(BreadboardCircuit.Signal.FLOATING, circuit.signalAt("M0R0C0"));
        assertEquals(BreadboardCircuit.Signal.FLOATING, circuit.signalAt("M0R0C1"));

        assertTrue(circuit.addJumper("VCC", "M0R0C0").succeeded());
        assertTrue(circuit.addJumper("GND", "M0R0C1").succeeded());

        assertEquals(BreadboardCircuit.Signal.HIGH, circuit.signalAt("M0R8C0"));
        assertEquals(BreadboardCircuit.Signal.LOW, circuit.signalAt("M0R8C1"));
    }

    @Test
    void switchOutputsDriveTheSixteenBulbInputs() {
        BreadboardCircuit circuit = new BreadboardCircuit(BreadboardLayout.Size.HALF);
        assertTrue(circuit.addJumper("SW15", "BULB15").succeeded());

        assertEquals(BreadboardCircuit.Signal.LOW, circuit.bulbSignal(15));
        circuit.setSwitchState(15, true);
        assertEquals(BreadboardCircuit.Signal.HIGH, circuit.bulbSignal(15));
    }

    @Test
    void breadboardDataRoundTripsWithOccupancyIntact() {
        BreadboardCircuit original = new BreadboardCircuit(BreadboardLayout.Size.MINI);
        assertTrue(original.addJumper("M0R0C3", "M0R1C0").succeeded());
        original.setSwitchState(5, true);

        BreadboardCircuit restored = BreadboardCircuit.fromData(original.toData());

        assertEquals(original.toData(), restored.toData());
        assertTrue(restored.isOccupied("M0R0C3"));
        assertTrue(restored.isOccupied("M0R1C0"));
        assertTrue(restored.switchState(5));
    }
}
