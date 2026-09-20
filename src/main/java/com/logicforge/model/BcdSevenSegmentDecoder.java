package com.logicforge.model;

import java.util.Arrays;

/** Active-high BCD (0-9) to seven-segment A-G decoding. */
public final class BcdSevenSegmentDecoder {
    private static final boolean[][] DIGITS = {
            {true,  true,  true,  true,  true,  true,  false}, // 0
            {false, true,  true,  false, false, false, false}, // 1
            {true,  true,  false, true,  true,  false, true }, // 2
            {true,  true,  true,  true,  false, false, true }, // 3
            {false, true,  true,  false, false, true,  true }, // 4
            {true,  false, true,  true,  false, true,  true }, // 5
            {true,  false, true,  true,  true,  true,  true }, // 6
            {true,  true,  true,  false, false, false, false}, // 7
            {true,  true,  true,  true,  true,  true,  true }, // 8
            {true,  true,  true,  true,  false, true,  true }  // 9
    };

    private BcdSevenSegmentDecoder() { }

    /** Returns A..G active-high states; invalid BCD values 10-15 are blanked. */
    public static boolean[] decode(int value) {
        if (value < 0 || value >= DIGITS.length) {
            return new boolean[7];
        }
        return Arrays.copyOf(DIGITS[value], 7);
    }
}
