package com.logicforge.view;

import java.util.Objects;

import com.logicforge.model.ComponentType;

/** Pure layout policy for visually separating arithmetic A/B input groups. */
final class OperandPinLayout {

    static final double PIN_SPACING = 18;
    static final double OPERAND_GROUP_GAP = 18;
    static final double CARRY_INPUT_GAP = 12;

    private OperandPinLayout() { }

    static boolean supports(ComponentType type) {
        return type == ComponentType.HALF_ADDER
                || type == ComponentType.FULL_ADDER
                || type == ComponentType.COMPARATOR;
    }

    static double extraHeight(ComponentType type) {
        if (!supports(type)) {
            return 0;
        }
        return OPERAND_GROUP_GAP
                + (type == ComponentType.FULL_ADDER ? CARRY_INPUT_GAP : 0);
    }

    static double inputY(ComponentType type, int bits, int index, double firstY) {
        Objects.requireNonNull(type, "type");
        if (!supports(type) || bits < 1 || index < 0) {
            throw new IllegalArgumentException("Unsupported grouped operand pin");
        }
        if (index < bits) {
            return firstY + index * PIN_SPACING;
        }
        if (index < bits * 2) {
            return firstY
                    + bits * PIN_SPACING
                    + OPERAND_GROUP_GAP
                    + (index - bits) * PIN_SPACING;
        }
        return firstY
                + bits * 2 * PIN_SPACING
                + OPERAND_GROUP_GAP
                + CARRY_INPUT_GAP
                + (index - bits * 2) * PIN_SPACING;
    }
}
