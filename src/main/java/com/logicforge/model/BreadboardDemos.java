package com.logicforge.model;

import java.util.ArrayList;
import java.util.List;

import com.logicforge.model.BreadboardCircuit.PlacedIc;
import com.logicforge.model.BreadboardLayout.Hole;
import com.logicforge.model.IcDefinition.IcPinRole;

/** Factory for complete, socket-valid, multi-IC breadboard demonstrations. */
public final class BreadboardDemos {

    private BreadboardDemos() {
    }

    /** A one-bit full adder built from XOR, AND, and OR packages. */
    public static BreadboardCircuit fullAdder() {
        Builder demo = new Builder();
        PlacedIc xor = demo.place("74HC86", 3);
        PlacedIc and = demo.place("74HC08", 12);
        PlacedIc or = demo.place("74HC32", 21);
        demo.powerTogether(xor, and, or);

        demo.circuit.setSwitchState(0, true);  // A
        demo.circuit.setSwitchState(1, false); // B
        demo.circuit.setSwitchState(2, true);  // carry-in
        demo.distributeSwitch(0, 1, xor, 1, and, 1);
        demo.distributeSwitch(1, 10, xor, 2, and, 2);
        demo.distributeSwitch(2, 19, xor, 5, and, 5);
        demo.connectPins(xor, 3, xor, 4);
        demo.connectPins(and, 3, or, 1);
        demo.connectPins(and, 6, or, 2);
        // The first XOR output feeds both the second XOR input and the
        // carry-term AND gate.  The second XOR output is the sum output.
        demo.distributeToTarget(xor, 3, 28, and, 4);
        demo.connectPinToHole(xor, 6, demo.layout.bulbHoleId(0));
        demo.connectPinToHole(or, 3, demo.layout.bulbHoleId(1));
        return demo.finish();
    }

    /** A four-bit A-B subtractor using XOR inversion and a 74HC283 full adder. */
    public static BreadboardCircuit twosComplementSubtractor() {
        Builder demo = new Builder();
        PlacedIc inverter = demo.place("74HC04", 3);
        PlacedIc adder = demo.place("74HC283", 14);
        demo.powerTogether(inverter, adder);

        int[] inverterInputs = {1, 3, 5, 9};
        int[] inverterOutputs = {2, 4, 6, 8};
        int[] adderA = {5, 3, 14, 12};
        int[] adderB = {6, 2, 15, 11};
        int[] adderSum = {4, 1, 13, 10};
        for (int bit = 0; bit < 4; bit++) {
            demo.circuit.setSwitchState(bit, (bit == 0 || bit == 2));
            demo.circuit.setSwitchState(4 + bit, (bit == 0 || bit == 1));
            demo.connectHoleToPin(demo.layout.switchHoleId(4 + bit), inverter, inverterInputs[bit]);
            demo.connectPins(inverter, inverterOutputs[bit], adder, adderB[bit]);
            demo.connectHoleToPin(demo.layout.switchHoleId(bit), adder, adderA[bit]);
            demo.connectPinToHole(adder, adderSum[bit], demo.layout.bulbHoleId(bit));
        }
        // The ninth switch is the required +1 carry-in for A + NOT(B) + 1.
        demo.circuit.setSwitchState(8, true);
        demo.connectHoleToPin(demo.layout.switchHoleId(8), adder, 7);
        demo.connectPinToHole(adder, 9, demo.layout.bulbHoleId(4));
        return demo.finish();
    }

    public static BreadboardCircuit halfAdder() {
        Builder demo = new Builder();
        PlacedIc xor = demo.place("74HC86", 4);
        PlacedIc and = demo.place("74HC08", 15);
        demo.powerTogether(xor, and);

        demo.circuit.setSwitchState(0, true);
        demo.distributeSwitch(0, 26, xor, 1, and, 1);
        demo.distributeSwitch(1, 27, xor, 2, and, 2);
        demo.connectPinToHole(xor, 3, demo.layout.bulbHoleId(0));
        demo.connectPinToHole(and, 3, demo.layout.bulbHoleId(1));
        return demo.finish();
    }

    public static BreadboardCircuit nandInverterChain() {
        Builder demo = new Builder();
        PlacedIc nand = demo.place("74HC00", 4);
        PlacedIc inverter = demo.place("74HC04", 15);
        demo.powerTogether(nand, inverter);

        demo.circuit.setSwitchState(0, true);
        demo.circuit.setSwitchState(1, true);
        demo.connectHoleToPin(demo.layout.switchHoleId(0), nand, 1);
        demo.connectHoleToPin(demo.layout.switchHoleId(1), nand, 2);
        demo.connectPins(nand, 3, inverter, 1);
        demo.connectPinToHole(inverter, 2, demo.layout.bulbHoleId(0));
        return demo.finish();
    }

    public static BreadboardCircuit andOrChain() {
        Builder demo = new Builder();
        PlacedIc and = demo.place("74HC08", 4);
        PlacedIc or = demo.place("74HC32", 15);
        demo.powerTogether(and, or);

        demo.circuit.setSwitchState(0, true);
        demo.circuit.setSwitchState(1, true);
        demo.connectHoleToPin(demo.layout.switchHoleId(0), and, 1);
        demo.connectHoleToPin(demo.layout.switchHoleId(1), and, 2);
        demo.connectPins(and, 3, or, 1);
        demo.connectHoleToPin(demo.layout.switchHoleId(2), or, 2);
        demo.connectPinToHole(or, 3, demo.layout.bulbHoleId(0));
        return demo.finish();
    }

    private static final class Builder {
        private final BreadboardCircuit circuit =
                new BreadboardCircuit(BreadboardLayout.Size.HALF);
        private final BreadboardLayout layout = circuit.layout();

        private PlacedIc place(String definitionId, int firstRow) {
            IcDefinition definition = IcCatalog.require(definitionId);
            int pinsPerSide = definition.packageType().pinCount() / 2;
            double centerX = layout.trenchLeft(0) + layout.trenchWidth() / 2.0;
            double centerY = layout.hole("M0R" + firstRow + "C7").y()
                    + (pinsPerSide - 1) * BreadboardLayout.HOLE_PITCH / 2.0;
            BreadboardCircuit.PlacementResult result =
                    circuit.placeIc(definition, centerX, centerY);
            if (!result.succeeded() || result.placedIc().firstRow() != firstRow) {
                throw new IllegalStateException(
                        "Could not place demo IC " + definition.partNumber());
            }
            return result.placedIc();
        }

        private void powerTogether(PlacedIc first, PlacedIc... remaining) {
            int firstVcc = physicalPin(first, IcPinRole.VCC);
            int firstGround = physicalPin(first, IcPinRole.GND);
            connect(layout.vccHoleId(), freeSocketForPin(first, firstVcc));
            connect(layout.groundHoleId(), freeSocketForPin(first, firstGround));

            PlacedIc previous = first;
            for (PlacedIc next : remaining) {
                connect(
                        freeSocketForPin(previous, physicalPin(previous, IcPinRole.VCC)),
                        freeSocketForPin(next, physicalPin(next, IcPinRole.VCC)));
                connect(
                        freeSocketForPin(previous, physicalPin(previous, IcPinRole.GND)),
                        freeSocketForPin(next, physicalPin(next, IcPinRole.GND)));
                previous = next;
            }
        }

        private void distributeSwitch(
                int channel,
                int distributionRow,
                PlacedIc first,
                int firstPin,
                PlacedIc second,
                int secondPin) {

            String rowPrefix = "M0R" + distributionRow + "C";
            connect(layout.switchHoleId(channel), rowPrefix + "3");
            connect(rowPrefix + "4", freeSocketForPin(first, firstPin));
            connect(rowPrefix + "5", freeSocketForPin(second, secondPin));
        }

        private void distributeOutput(
                PlacedIc source,
                int sourcePin,
                int distributionRow,
                PlacedIc target,
                int targetPin,
                String extraHoleId) {

            String rowPrefix = "M0R" + distributionRow + "C";
            connect(freeSocketForPin(source, sourcePin), rowPrefix + "3");
            connect(rowPrefix + "4", freeSocketForPin(target, targetPin));
            connect(rowPrefix + "5", extraHoleId);
        }

        private void distributeToTarget(
                PlacedIc source,
                int sourcePin,
                int distributionRow,
                PlacedIc target,
                int targetPin) {
            String rowPrefix = "M0R" + distributionRow + "C";
            connect(freeSocketForPin(source, sourcePin), rowPrefix + "3");
            connect(rowPrefix + "4", freeSocketForPin(target, targetPin));
        }

        private void connectHoleToPin(String holeId, PlacedIc ic, int physicalPin) {
            connect(holeId, freeSocketForPin(ic, physicalPin));
        }

        private void connectPinToHole(PlacedIc ic, int physicalPin, String holeId) {
            connect(freeSocketForPin(ic, physicalPin), holeId);
        }

        private void connectPins(
                PlacedIc first,
                int firstPin,
                PlacedIc second,
                int secondPin) {

            connect(freeSocketForPin(first, firstPin), freeSocketForPin(second, secondPin));
        }

        private String freeSocketForPin(PlacedIc ic, int physicalPin) {
            String pinHoleId = layout.icPinHoleId(
                    ic.moduleIndex(), ic.firstRow(),
                    ic.definition().packageType().pinCount(), physicalPin);
            Hole pinHole = layout.hole(pinHoleId);
            int[] columns = pinHole.column() == 7
                    ? new int[] {6, 5, 4, 3}
                    : new int[] {12, 13, 14, 15};
            for (int column : columns) {
                String candidate = "M" + ic.moduleIndex()
                        + "R" + pinHole.row() + "C" + column;
                if (!circuit.isOccupied(candidate)) {
                    return candidate;
                }
            }
            throw new IllegalStateException(
                    "No free terminal socket beside " + ic.label() + " pin " + physicalPin);
        }

        private int physicalPin(PlacedIc ic, IcPinRole role) {
            return ic.definition().pins().stream()
                    .filter(pin -> pin.role() == role)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            ic.label() + " has no " + role + " pin"))
                    .number();
        }

        private void connect(String startHoleId, String endHoleId) {
            BreadboardCircuit.JumperResult result =
                    circuit.addJumper(startHoleId, endHoleId);
            if (!result.succeeded()) {
                throw new IllegalStateException(result.error()
                        + " (" + startHoleId + " → " + endHoleId + ")");
            }
        }

        private BreadboardCircuit finish() {
            circuit.evaluate();
            List<PlacedIc> unpowered = new ArrayList<>(circuit.integratedCircuits());
            unpowered.removeIf(PlacedIc::isPowered);
            if (!unpowered.isEmpty()) {
                throw new IllegalStateException("Demo contains an unpowered IC");
            }
            return circuit;
        }
    }
}
