package com.logicforge.model;

import java.util.Arrays;

import com.logicforge.model.IcDefinition.IcFunction;

/** Deterministic two-state runtime for one physical 74HC package instance. */
public final class IcRuntime {

    private final IcDefinition definition;
    private final boolean[] outputs;
    private final boolean[] storedStates = new boolean[2];
    private final boolean[] previousClocks = new boolean[2];
    private boolean vccConnected;
    private boolean gndConnected;
    private boolean powered;
    private boolean invalidAsyncControls;

    public IcRuntime(IcDefinition definition) {
        this.definition = java.util.Objects.requireNonNull(definition, "definition");
        this.outputs = new boolean[definition.outputCount()];
        refreshSequentialOutputs();
    }

    public IcDefinition definition() {
        return definition;
    }

    public boolean isSequential() {
        return definition.isSequential();
    }

    public boolean isPowered() {
        return powered;
    }

    public boolean hasInvalidAsyncControls() {
        return invalidAsyncControls;
    }

    public void setPowerConnections(boolean vccConnected, boolean gndConnected) {
        this.vccConnected = vccConnected;
        this.gndConnected = gndConnected;
    }

    public boolean[] evaluateCombinational(boolean[] inputs) {
        boolean wasPowered = powered;
        powered = hasPower(inputs);
        invalidAsyncControls = false;
        if (!powered) {
            Arrays.fill(outputs, false);
            if (wasPowered && definition.isSequential()) {
                Arrays.fill(storedStates, false);
                Arrays.fill(previousClocks, false);
            }
            return outputs();
        }

        switch (definition.function()) {
            case QUAD_NAND -> evaluateQuadGate(inputs, Gate.NAND);
            case QUAD_NOR -> evaluateQuadGate(inputs, Gate.NOR);
            case HEX_INVERTER -> evaluateInverters(inputs);
            case QUAD_AND -> evaluateQuadGate(inputs, Gate.AND);
            case QUAD_OR -> evaluateQuadGate(inputs, Gate.OR);
            case QUAD_XOR -> evaluateQuadGate(inputs, Gate.XOR);
            case DECODER_3_TO_8 -> evaluateDecoder(inputs);
            case MULTIPLEXER_8_TO_1 -> evaluateMux8(inputs);
            case QUAD_MULTIPLEXER_2_TO_1 -> evaluateQuadMux(inputs);
            case ADDER_4_BIT -> evaluateAdder(inputs);
            case COMPARATOR_4_BIT -> evaluateComparator(inputs);
            case DUAL_D_FLIP_FLOP -> refreshSequentialOutputs();
        }
        return outputs();
    }

    public boolean applyAsynchronousControls(boolean[] inputs) {
        boolean[] before = outputs();
        evaluateCombinational(inputs);
        if (!powered || definition.function() != IcFunction.DUAL_D_FLIP_FLOP) {
            return !Arrays.equals(before, outputs);
        }

        invalidAsyncControls = false;
        for (int channel = 0; channel < 2; channel++) {
            String prefix = Integer.toString(channel + 1);
            boolean clear = !input(inputs, prefix + "/CLR");
            boolean preset = !input(inputs, prefix + "/PRE");
            if (clear && preset) {
                invalidAsyncControls = true;
            } else if (clear) {
                storedStates[channel] = false;
            } else if (preset) {
                storedStates[channel] = true;
            }
        }
        refreshSequentialOutputs();
        return !Arrays.equals(before, outputs);
    }

    public boolean updateSequential(boolean[] inputs) {
        boolean[] before = outputs();
        applyAsynchronousControls(inputs);
        if (!powered || definition.function() != IcFunction.DUAL_D_FLIP_FLOP) {
            return !Arrays.equals(before, outputs);
        }

        for (int channel = 0; channel < 2; channel++) {
            String prefix = Integer.toString(channel + 1);
            boolean clock = input(inputs, prefix + "CLK");
            boolean risingEdge = !previousClocks[channel] && clock;
            previousClocks[channel] = clock;
            boolean asyncAsserted = !input(inputs, prefix + "/CLR")
                    || !input(inputs, prefix + "/PRE");
            if (risingEdge && !asyncAsserted) {
                storedStates[channel] = input(inputs, prefix + "D");
            }
        }
        refreshSequentialOutputs();
        return !Arrays.equals(before, outputs);
    }

    public void synchronizeClockMemory(boolean[] inputs) {
        if (definition.function() != IcFunction.DUAL_D_FLIP_FLOP) {
            return;
        }
        previousClocks[0] = input(inputs, "1CLK");
        previousClocks[1] = input(inputs, "2CLK");
    }

    public boolean[] outputs() {
        return Arrays.copyOf(outputs, outputs.length);
    }

    public String exportState() {
        if (!definition.isSequential()) {
            return "";
        }
        return (storedStates[0] ? "1" : "0")
                + (storedStates[1] ? "1" : "0")
                + (previousClocks[0] ? "1" : "0")
                + (previousClocks[1] ? "1" : "0");
    }

    public void importState(String state) {
        if (!definition.isSequential() || state == null || state.isBlank()) {
            return;
        }
        if (state.length() != 4 || !state.matches("[01]{4}")) {
            throw new IllegalArgumentException("Invalid " + definition.partNumber() + " state");
        }
        storedStates[0] = state.charAt(0) == '1';
        storedStates[1] = state.charAt(1) == '1';
        previousClocks[0] = state.charAt(2) == '1';
        previousClocks[1] = state.charAt(3) == '1';
        refreshSequentialOutputs();
    }

    private boolean hasPower(boolean[] inputs) {
        return vccConnected
                && gndConnected
                && input(inputs, "VCC")
                && !input(inputs, "GND");
    }

    private void evaluateQuadGate(boolean[] inputs, Gate gate) {
        for (int channel = 1; channel <= 4; channel++) {
            boolean a = input(inputs, channel + "A");
            boolean b = input(inputs, channel + "B");
            boolean result = switch (gate) {
                case AND -> a && b;
                case NAND -> !(a && b);
                case OR -> a || b;
                case NOR -> !(a || b);
                case XOR -> a ^ b;
            };
            output(channel + "Y", result);
        }
    }

    private void evaluateInverters(boolean[] inputs) {
        for (int channel = 1; channel <= 6; channel++) {
            output(channel + "Y", !input(inputs, channel + "A"));
        }
    }

    private void evaluateDecoder(boolean[] inputs) {
        Arrays.fill(outputs, true);
        boolean enabled = input(inputs, "G1")
                && !input(inputs, "/G2A")
                && !input(inputs, "/G2B");
        if (!enabled) {
            return;
        }
        int selected = bit(inputs, "A", 0) | bit(inputs, "B", 1) | bit(inputs, "C", 2);
        output("/Y" + selected, false);
    }

    private void evaluateMux8(boolean[] inputs) {
        boolean enabled = !input(inputs, "/G");
        int selected = bit(inputs, "A", 0) | bit(inputs, "B", 1) | bit(inputs, "C", 2);
        boolean value = enabled && input(inputs, "D" + selected);
        output("Y", value);
        output("W", !value);
    }

    private void evaluateQuadMux(boolean[] inputs) {
        boolean enabled = !input(inputs, "/G");
        boolean selectB = input(inputs, "A/B");
        for (int channel = 1; channel <= 4; channel++) {
            boolean value = enabled && input(inputs, channel + (selectB ? "B" : "A"));
            output(channel + "Y", value);
        }
    }

    private void evaluateAdder(boolean[] inputs) {
        int a = 0;
        int b = 0;
        for (int bit = 0; bit < 4; bit++) {
            if (input(inputs, "A" + (bit + 1))) {
                a |= 1 << bit;
            }
            if (input(inputs, "B" + (bit + 1))) {
                b |= 1 << bit;
            }
        }
        int sum = a + b + (input(inputs, "C0") ? 1 : 0);
        for (int bit = 0; bit < 4; bit++) {
            output("S" + (bit + 1), ((sum >> bit) & 1) != 0);
        }
        output("C4", sum > 15);
    }

    private void evaluateComparator(boolean[] inputs) {
        int a = bit(inputs, "A0", 0) | bit(inputs, "A1", 1)
                | bit(inputs, "A2", 2) | bit(inputs, "A3", 3);
        int b = bit(inputs, "B0", 0) | bit(inputs, "B1", 1)
                | bit(inputs, "B2", 2) | bit(inputs, "B3", 3);
        boolean greater;
        boolean equal;
        boolean less;
        if (a > b) {
            greater = true;
            equal = false;
            less = false;
        } else if (a < b) {
            greater = false;
            equal = false;
            less = true;
        } else {
            boolean cascadeGreater = input(inputs, "IA>B");
            boolean cascadeEqual = input(inputs, "IA=B");
            boolean cascadeLess = input(inputs, "IA<B");
            if (cascadeEqual) {
                greater = false;
                equal = true;
                less = false;
            } else if (cascadeGreater == cascadeLess) {
                // The 74HC85 defines both-low as both magnitude outputs high,
                // while both-high drives all three outputs low.
                greater = !cascadeGreater;
                equal = false;
                less = !cascadeLess;
            } else {
                greater = cascadeGreater;
                equal = false;
                less = cascadeLess;
            }
        }
        output("QA>B", greater);
        output("QA=B", equal);
        output("QA<B", less);
    }

    private void refreshSequentialOutputs() {
        if (definition.function() != IcFunction.DUAL_D_FLIP_FLOP || !powered) {
            if (definition.function() == IcFunction.DUAL_D_FLIP_FLOP) {
                Arrays.fill(outputs, false);
            }
            return;
        }
        output("1Q", storedStates[0]);
        output("1/Q", !storedStates[0]);
        output("2Q", storedStates[1]);
        output("2/Q", !storedStates[1]);
    }

    private boolean input(boolean[] values, String pinName) {
        int index = definition.inputIndex(pinName);
        return index >= 0 && values != null && index < values.length && values[index];
    }

    private int bit(boolean[] values, String pinName, int shift) {
        return input(values, pinName) ? 1 << shift : 0;
    }

    private void output(String pinName, boolean value) {
        int index = definition.outputIndex(pinName);
        if (index >= 0) {
            outputs[index] = value;
        }
    }

    private enum Gate {
        AND,
        NAND,
        OR,
        NOR,
        XOR
    }
}
