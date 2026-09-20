package com.logicforge.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class BreadboardLayoutTest {

    @Test
    void standardSizesExposeExpectedTerminalCountsAndMultiples() {
        assertEquals(17, BreadboardLayout.Size.MINI.rows());
        assertEquals(30, BreadboardLayout.Size.HALF.rows());
        assertEquals(63, BreadboardLayout.Size.FULL.rows());
        assertEquals(2, BreadboardLayout.Size.DOUBLE.modules());
        assertEquals(3, BreadboardLayout.Size.TRIPLE.modules());
        assertEquals(4, BreadboardLayout.Size.QUADRUPLE.modules());
        assertEquals(5, BreadboardLayout.Size.QUINTUPLE.modules());
    }

    @Test
    void dipFootprintUsesExactSocketPitchAndPhysicalPinNumbering() {
        BreadboardLayout layout = new BreadboardLayout(BreadboardLayout.Size.HALF);
        BreadboardLayout.IcPlacement placement = layout.placement(0, 3, 14);

        assertEquals(4 * BreadboardLayout.HOLE_PITCH, placement.width(), 0.0001);
        assertEquals(0.0, placement.width() % BreadboardLayout.HOLE_PITCH, 0.0001);
        assertEquals("M0R3C7", layout.icPinHoleId(0, 3, 14, 1));
        assertEquals("M0R9C7", layout.icPinHoleId(0, 3, 14, 7));
        assertEquals("M0R9C11", layout.icPinHoleId(0, 3, 14, 8));
        assertEquals("M0R3C11", layout.icPinHoleId(0, 3, 14, 14));
        assertEquals(14, placement.pinHoleIds().size());
        assertTrue(placement.pinHoleIds().stream().distinct().count() == 14);
    }

    @Test
    void fiveTerminalSocketsShareOneConductiveStrip() {
        BreadboardLayout layout = new BreadboardLayout(BreadboardLayout.Size.HALF);
        String group = layout.hole("M0R4C3").conductiveGroup();
        for (int column = 3; column <= 7; column++) {
            assertEquals(group, layout.hole("M0R4C" + column).conductiveGroup());
        }
    }

    @Test
    void exposesSixteenSwitchesBulbsAndDedicatedPowerSources() {
        BreadboardLayout layout = new BreadboardLayout(BreadboardLayout.Size.HALF);

        for (int channel = 0; channel < 16; channel++) {
            assertEquals(BreadboardLayout.HoleKind.SWITCH_OUTPUT,
                    layout.hole("SW" + channel).kind());
            assertEquals(BreadboardLayout.HoleKind.BULB_INPUT,
                    layout.hole("BULB" + channel).kind());
        }
        assertEquals(BreadboardLayout.HoleKind.VCC_SOURCE, layout.hole("VCC").kind());
        assertEquals(BreadboardLayout.HoleKind.GND_SOURCE, layout.hole("GND").kind());
    }

    @Test
    void controlConnectionPointsSitOutsidePanelsAndVccIsLeftOfSwitches() {
        BreadboardLayout layout = new BreadboardLayout(BreadboardLayout.Size.HALF);

        assertTrue(layout.hole(layout.switchHoleId(0)).y() > layout.switchPanelBottom());
        assertTrue(layout.hole(layout.bulbHoleId(0)).y() < layout.bulbPanelTop());
        assertEquals(30.0,
                layout.hole(layout.switchHoleId(0)).y() - layout.switchPanelBottom(),
                0.0001);
        assertEquals(30.0,
                layout.bulbPanelTop() - layout.hole(layout.bulbHoleId(0)).y(),
                0.0001);
        assertTrue(layout.hole(layout.vccHoleId()).x() < layout.controlLeft());
        assertTrue(layout.hole(layout.groundHoleId()).x()
                > layout.controlLeft() + layout.switchPanelWidth());
        assertEquals(
                layout.controlLeft() - layout.hole(layout.vccHoleId()).x(),
                layout.hole(layout.groundHoleId()).x()
                        - (layout.controlLeft() + layout.switchPanelWidth()),
                0.0001);
    }
}
