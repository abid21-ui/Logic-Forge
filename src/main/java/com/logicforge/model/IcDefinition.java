package com.logicforge.model;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable physical pinout and behavior descriptor for one supported IC. */
public record IcDefinition(
        String id,
        String partNumber,
        String name,
        String family,
        IcPackage packageType,
        IcFunction function,
        List<IcPin> pins) {

    public IcDefinition {
        id = requireText(id, "id");
        partNumber = requireText(partNumber, "part number");
        name = requireText(name, "name");
        family = requireText(family, "family");
        packageType = Objects.requireNonNull(packageType, "packageType");
        function = Objects.requireNonNull(function, "function");
        pins = pins == null ? List.of() : pins.stream()
                .sorted(Comparator.comparingInt(IcPin::number))
                .toList();

        if (pins.size() != packageType.pinCount()) {
            throw new IllegalArgumentException(
                    partNumber + " must define all " + packageType.pinCount() + " package pins");
        }
        Set<Integer> numbers = new HashSet<>();
        for (IcPin pin : pins) {
            if (!numbers.add(pin.number())
                    || pin.number() < 1
                    || pin.number() > packageType.pinCount()) {
                throw new IllegalArgumentException(partNumber + " has an invalid or duplicate pin number");
            }
        }
        if (pins.stream().noneMatch(pin -> pin.role() == IcPinRole.VCC)
                || pins.stream().noneMatch(pin -> pin.role() == IcPinRole.GND)) {
            throw new IllegalArgumentException(partNumber + " requires VCC and GND pins");
        }
    }

    public List<IcPin> inputPins() {
        return pins.stream().filter(IcPin::isInput).toList();
    }

    public List<IcPin> outputPins() {
        return pins.stream().filter(pin -> pin.role() == IcPinRole.OUTPUT).toList();
    }

    public int inputCount() {
        return inputPins().size();
    }

    public int outputCount() {
        return outputPins().size();
    }

    public List<String> inputNames() {
        return inputPins().stream().map(IcPin::displayName).toList();
    }

    public List<String> outputNames() {
        return outputPins().stream().map(IcPin::displayName).toList();
    }

    public int inputIndex(String pinName) {
        List<IcPin> inputs = inputPins();
        for (int index = 0; index < inputs.size(); index++) {
            if (inputs.get(index).name().equalsIgnoreCase(pinName)) {
                return index;
            }
        }
        return -1;
    }

    public int outputIndex(String pinName) {
        List<IcPin> outputs = outputPins();
        for (int index = 0; index < outputs.size(); index++) {
            if (outputs.get(index).name().equalsIgnoreCase(pinName)) {
                return index;
            }
        }
        return -1;
    }

    public boolean isSequential() {
        return function == IcFunction.DUAL_D_FLIP_FLOP;
    }

    public String description() {
        return partNumber + " — " + name + " • " + packageType.label();
    }

    private static String requireText(String value, String field) {
        String normalized = value == null ? "" : value.strip();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("IC " + field + " is required");
        }
        return normalized;
    }

    public enum IcPinRole {
        INPUT,
        OUTPUT,
        VCC,
        GND,
        NOT_CONNECTED
    }

    public enum IcPackage {
        DIP_14(14, "DIP-14"),
        DIP_16(16, "DIP-16");

        private final int pinCount;
        private final String label;

        IcPackage(int pinCount, String label) {
            this.pinCount = pinCount;
            this.label = label;
        }

        public int pinCount() {
            return pinCount;
        }

        public String label() {
            return label;
        }
    }

    public enum IcFunction {
        QUAD_NAND,
        QUAD_NOR,
        HEX_INVERTER,
        QUAD_AND,
        QUAD_OR,
        QUAD_XOR,
        DECODER_3_TO_8,
        MULTIPLEXER_8_TO_1,
        QUAD_MULTIPLEXER_2_TO_1,
        ADDER_4_BIT,
        COMPARATOR_4_BIT,
        DUAL_D_FLIP_FLOP
    }

    public record IcPin(int number, String name, IcPinRole role) {
        public IcPin {
            if (number <= 0) {
                throw new IllegalArgumentException("IC pin number must be positive");
            }
            name = requireText(name, "pin name");
            role = Objects.requireNonNull(role, "role");
        }

        public boolean isInput() {
            return role == IcPinRole.INPUT || role == IcPinRole.VCC || role == IcPinRole.GND;
        }

        public String displayName() {
            return number + " · " + name;
        }
    }
}
