package com.logicforge.view;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.logicforge.model.ComponentType;

class OperandPinLayoutTest {

    @Test
    void separatesFourBitAAndBGroupsByOneEmptyPinLane() {
        double firstY = 46;
        double lastA = OperandPinLayout.inputY(ComponentType.FULL_ADDER, 4, 3, firstY);
        double firstB = OperandPinLayout.inputY(ComponentType.FULL_ADDER, 4, 4, firstY);

        assertEquals(OperandPinLayout.PIN_SPACING * 2, firstB - lastA);
    }

    @Test
    void reservesASecondGapBeforeFullAdderCarryIn() {
        double firstY = 46;
        double lastB = OperandPinLayout.inputY(ComponentType.FULL_ADDER, 4, 7, firstY);
        double carry = OperandPinLayout.inputY(ComponentType.FULL_ADDER, 4, 8, firstY);

        assertEquals(
                OperandPinLayout.PIN_SPACING + OperandPinLayout.CARRY_INPUT_GAP,
                carry - lastB);
    }
}
