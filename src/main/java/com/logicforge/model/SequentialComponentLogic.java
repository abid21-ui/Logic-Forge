package com.logicforge.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * Pure transition rules shared by the workspace and custom-component runtime.
 * Clock-edge detection remains with each runtime because previous-clock state is
 * instance data; the actual state transition is defined in one place here.
 */
public final class SequentialComponentLogic {

    private SequentialComponentLogic() { }

    public record AsyncControl(boolean asserted, boolean invalid, boolean nextState) { }

    public static AsyncControl asynchronousControl(
            ComponentType type,
            ComponentConfig config,
            boolean[] inputs,
            boolean currentState) {

        requireFlipFlop(type);
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(inputs, "inputs");
        int presetIndex = type.presetInputIndex(config);
        int clearIndex = type.clearInputIndex(config);
        boolean preset = presetIndex >= 0 && !inputs[presetIndex];
        boolean clear = clearIndex >= 0 && !inputs[clearIndex];
        boolean invalid = preset && clear;
        if (invalid || (!preset && !clear)) {
            return new AsyncControl(false, invalid, currentState);
        }
        return new AsyncControl(true, false, preset);
    }

    public static boolean nextFlipFlopState(
            ComponentType type,
            boolean currentState,
            boolean[] inputs) {

        requireFlipFlop(type);
        Objects.requireNonNull(inputs, "inputs");
        return switch (type) {
            case SR_FLIP_FLOP -> inputs[0] && !inputs[1]
                    ? true
                    : (!inputs[0] && inputs[1] ? false : currentState);
            case JK_FLIP_FLOP -> inputs[0] && inputs[1]
                    ? !currentState
                    : (inputs[0] ? true : (inputs[1] ? false : currentState));
            case D_FLIP_FLOP -> inputs[0];
            case T_FLIP_FLOP -> inputs[0] ? !currentState : currentState;
            default -> throw new IllegalArgumentException(type + " is not a flip-flop");
        };
    }

    public static boolean[] nextClockedBlockOutputs(
            ComponentType type,
            boolean[] currentOutputs,
            boolean[] inputs) {

        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(currentOutputs, "currentOutputs");
        Objects.requireNonNull(inputs, "inputs");
        if (!type.isClockedStateBlock()) {
            throw new IllegalArgumentException(type + " is not a clocked state block");
        }

        boolean[] next = Arrays.copyOf(currentOutputs, currentOutputs.length);
        if (type == ComponentType.COUNTER) {
            int value = bitsToUnsigned(next);
            value = (value + 1) & ((1 << next.length) - 1);
            for (int bit = 0; bit < next.length; bit++) {
                next[bit] = (value & (1 << bit)) != 0;
            }
            return next;
        }

        for (int bit = next.length - 1; bit >= 1; bit--) {
            next[bit] = next[bit - 1];
        }
        if (next.length > 0) {
            next[0] = inputs[0];
        }
        return next;
    }

    private static int bitsToUnsigned(boolean[] values) {
        int result = 0;
        for (int bit = 0; bit < values.length; bit++) {
            if (values[bit]) {
                result |= 1 << bit;
            }
        }
        return result;
    }

    private static void requireFlipFlop(ComponentType type) {
        Objects.requireNonNull(type, "type");
        if (!type.isFlipFlop()) {
            throw new IllegalArgumentException(type + " is not a flip-flop");
        }
    }
}
