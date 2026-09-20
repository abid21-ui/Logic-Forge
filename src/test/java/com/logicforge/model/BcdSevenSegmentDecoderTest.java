package com.logicforge.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

class BcdSevenSegmentDecoderTest {

    @Test
    void decodesZeroAndNineAsActiveHighSegments() {
        assertArrayEquals(
                new boolean[] {true, true, true, true, true, true, false},
                BcdSevenSegmentDecoder.decode(0));
        assertArrayEquals(
                new boolean[] {true, true, true, true, false, true, true},
                BcdSevenSegmentDecoder.decode(9));
    }

    @Test
    void blanksValuesOutsideBcdRange() {
        boolean[] blank = new boolean[7];
        assertArrayEquals(blank, BcdSevenSegmentDecoder.decode(-1));
        assertArrayEquals(blank, BcdSevenSegmentDecoder.decode(10));
        assertArrayEquals(blank, BcdSevenSegmentDecoder.decode(15));
    }
}
