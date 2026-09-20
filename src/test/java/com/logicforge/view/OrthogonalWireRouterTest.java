package com.logicforge.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import javafx.geometry.Point2D;

class OrthogonalWireRouterTest {

    @Test
    void onlyRecognizesTrulyAlignedOpposingPinsAsOneStraightRun() {
        assertTrue(OrthogonalWireRouter.isNearlyAligned(
                new Point2D(100, 100),
                CircuitNode.PinSide.RIGHT,
                new Point2D(400, 100),
                CircuitNode.PinSide.LEFT));

        assertFalse(OrthogonalWireRouter.isNearlyAligned(
                new Point2D(100, 100),
                CircuitNode.PinSide.RIGHT,
                new Point2D(400, 110),
                CircuitNode.PinSide.LEFT));
    }

    @Test
    void congestionModelOnlyPublishesOrthogonalSegments() {
        assertEquals(1, OrthogonalWireRouter.segmentsOf(List.of(
                new Point2D(0, 0),
                new Point2D(100, 0)), 1).size());
        assertTrue(OrthogonalWireRouter.segmentsOf(List.of(
                new Point2D(0, 0),
                new Point2D(100, 8)), 1).isEmpty());
    }
}
