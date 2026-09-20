package com.logicforge.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * Pure, shared truth-table evaluation for every native combinational component.
 * Both the visible workspace and hidden custom-component runtimes delegate here,
 * preventing the two simulation paths from drifting apart.
 */
public final class NativeComponentLogic {

    private NativeComponentLogic() { }

    /** Immutable result of evaluating one native component. */
    public record Evaluation(
            boolean[] outputs,
            boolean displayState,
            boolean[] sevenSegmentStates) {

        public Evaluation {
            outputs = Arrays.copyOf(outputs, outputs.length);
            sevenSegmentStates = Arrays.copyOf(
                    sevenSegmentStates, sevenSegmentStates.length);
        }

        @Override
        public boolean[] outputs() {
            return Arrays.copyOf(outputs, outputs.length);
        }

        @Override
        public boolean[] sevenSegmentStates() {
            return Arrays.copyOf(sevenSegmentStates, sevenSegmentStates.length);
        }

    }

    public static boolean supports(ComponentType type) {
        Objects.requireNonNull(type, "type");
        return !type.isToggleSource()
                && !type.isClock()
                && !type.isFlipFlop()
                && !type.isClockedStateBlock()
                && type != ComponentType.INTEGRATED_CIRCUIT
                && type != ComponentType.CUSTOM;
    }

    public static Evaluation evaluate(
            ComponentType type,
            ComponentConfig config,
            boolean[] rawInputs) {

        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(config, "config");
        if (!supports(type)) {
            throw new IllegalArgumentException(type + " is not native combinational logic");
        }

        boolean[] inputs = rawInputs == null
                ? new boolean[type.inputCount(config)]
                : Arrays.copyOf(rawInputs, rawInputs.length);
        int expectedInputs = type.inputCount(config);
        if (inputs.length != expectedInputs) {
            throw new IllegalArgumentException(
                    type + " expected " + expectedInputs + " inputs but received " + inputs.length);
        }

        boolean[] outputs = new boolean[type.outputCount(config)];
        boolean[] segments = new boolean[type == ComponentType.SEVEN_SEGMENT_DISPLAY ? 7 : 0];
        boolean display;

        switch (type) {
            case VCC -> outputs[0] = true;
            case GROUND -> outputs[0] = false;
            case JUNCTION -> outputs[0] = inputs[0];
            case AND -> outputs[0] = allTrue(inputs);
            case NAND -> outputs[0] = !allTrue(inputs);
            case OR -> outputs[0] = anyTrue(inputs);
            case NOR -> outputs[0] = !anyTrue(inputs);
            case NOT -> outputs[0] = !inputs[0];
            case XOR -> outputs[0] = oddParity(inputs);
            case BULB, LOGIC_OUTPUT -> { }
            case SEVEN_SEGMENT_DISPLAY ->
                    System.arraycopy(inputs, 0, segments, 0, Math.min(inputs.length, 7));
            case MULTIPLEXER -> evaluateMultiplexer(outputs, inputs, config.bitWidth());
            case DECODER -> evaluateDecoder(outputs, inputs, config.bitWidth());
            case ENCODER -> evaluateEncoder(outputs, inputs);
            case BCD_TO_7SEGMENT -> {
                boolean[] decoded = BcdSevenSegmentDecoder.decode(bitsToUnsigned(inputs, 0, 4));
                System.arraycopy(decoded, 0, outputs, 0, Math.min(decoded.length, outputs.length));
            }
            case HALF_ADDER -> evaluateHalfAdder(outputs, inputs, config.bitWidth());
            case FULL_ADDER -> evaluateFullAdder(outputs, inputs, config.bitWidth());
            case COMPARATOR -> evaluateComparator(outputs, inputs, config.bitWidth());
            case DEMULTIPLEXER -> evaluateDemultiplexer(outputs, inputs, config.bitWidth());
            default -> throw new IllegalArgumentException(type + " is not native combinational logic");
        }

        if (type == ComponentType.BULB || type == ComponentType.LOGIC_OUTPUT) {
            display = inputs[0];
        } else if (type == ComponentType.SEVEN_SEGMENT_DISPLAY) {
            display = anyTrue(segments);
        } else {
            display = outputs.length > 0 && outputs[0];
        }
        return new Evaluation(outputs, display, segments);
    }

    private static void evaluateMultiplexer(boolean[] outputs, boolean[] inputs, int bits) {
        int dataCount = 1 << bits;
        int selected = bitsToUnsigned(inputs, dataCount, bits);
        outputs[0] = inputs[selected];
    }

    private static void evaluateDecoder(boolean[] outputs, boolean[] inputs, int bits) {
        outputs[bitsToUnsigned(inputs, 0, bits)] = true;
    }

    private static void evaluateEncoder(boolean[] outputs, boolean[] inputs) {
        int selected = 0;
        for (int index = 0; index < inputs.length; index++) {
            if (inputs[index]) {
                selected = index;
            }
        }
        writeBits(outputs, selected, outputs.length);
    }

    private static void evaluateHalfAdder(boolean[] outputs, boolean[] inputs, int bits) {
        int result = bitsToUnsigned(inputs, 0, bits)
                + bitsToUnsigned(inputs, bits, bits);
        writeBits(outputs, result, bits + 1);
    }

    private static void evaluateFullAdder(boolean[] outputs, boolean[] inputs, int bits) {
        int result = bitsToUnsigned(inputs, 0, bits)
                + bitsToUnsigned(inputs, bits, bits)
                + (inputs[bits * 2] ? 1 : 0);
        writeBits(outputs, result, bits + 1);
    }

    private static void evaluateComparator(boolean[] outputs, boolean[] inputs, int bits) {
        int a = bitsToUnsigned(inputs, 0, bits);
        int b = bitsToUnsigned(inputs, bits, bits);
        outputs[0] = a > b;
        outputs[1] = a == b;
        outputs[2] = a < b;
    }

    private static void evaluateDemultiplexer(boolean[] outputs, boolean[] inputs, int bits) {
        outputs[bitsToUnsigned(inputs, 1, bits)] = inputs[0];
    }

    private static void writeBits(boolean[] outputs, int value, int count) {
        for (int bit = 0; bit < count && bit < outputs.length; bit++) {
            outputs[bit] = (value & (1 << bit)) != 0;
        }
    }

    private static int bitsToUnsigned(boolean[] values, int offset, int count) {
        int result = 0;
        for (int bit = 0; bit < count && offset + bit < values.length; bit++) {
            if (values[offset + bit]) {
                result |= 1 << bit;
            }
        }
        return result;
    }

    private static boolean allTrue(boolean[] values) {
        for (boolean value : values) {
            if (!value) {
                return false;
            }
        }
        return true;
    }

    private static boolean anyTrue(boolean[] values) {
        for (boolean value : values) {
            if (value) {
                return true;
            }
        }
        return false;
    }

    private static boolean oddParity(boolean[] values) {
        boolean odd = false;
        for (boolean value : values) {
            odd ^= value;
        }
        return odd;
    }
}
