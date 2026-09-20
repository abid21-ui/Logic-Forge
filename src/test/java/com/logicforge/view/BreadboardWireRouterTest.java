package com.logicforge.view;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.logicforge.model.BreadboardCircuit.Jumper;
import com.logicforge.model.BreadboardLayout;
import com.logicforge.model.BreadboardCircuit;
import com.logicforge.model.IcCatalog;

final class BreadboardWireRouterTest {

    @Test
    void everyCompletedBreadboardWireSegmentIsOrthogonal() {
        BreadboardLayout layout = new BreadboardLayout(BreadboardLayout.Size.HALF);
        List<BreadboardWireRouter.RoutedJumper> routes = BreadboardWireRouter.routeAll(
                List.of(new Jumper(1, "SW0", "M0R10C15", "CYAN")), layout);

        List<javafx.geometry.Point2D> points = routes.get(0).points();
        for (int index = 1; index < points.size(); index++) {
            javafx.geometry.Point2D first = points.get(index - 1);
            javafx.geometry.Point2D second = points.get(index);
            assertTrue(first.getX() == second.getX() || first.getY() == second.getY());
        }
    }

    @Test
    void switchWireTurnsOnlyAfterClearingTheUnusedPinBank() {
        BreadboardLayout layout = new BreadboardLayout(BreadboardLayout.Size.HALF);
        List<javafx.geometry.Point2D> points = BreadboardWireRouter.routeAll(
                List.of(new Jumper(1, "SW0", "M0R10C15", "CYAN")), layout)
                .get(0).points();

        assertTrue(points.size() >= 4);
        assertTrue(points.get(1).getY() > layout.switchPinY());
        assertTrue(points.get(1).getY() >= layout.boardTop() - 24);
    }

    @Test
    void perpendicularCrossingAssignsBridgeToHorizontalRoute() {
        BreadboardLayout layout = new BreadboardLayout(BreadboardLayout.Size.HALF);
        List<BreadboardWireRouter.RoutedJumper> routes = BreadboardWireRouter.routeAll(
                List.of(
                        new Jumper(1, "M0R10C3", "M0R10C15", "CYAN"),
                        new Jumper(2, "M0R5C5", "M0R15C5", "GREEN")),
                layout);

        assertFalse(routes.get(0).bridgeCrossings().isEmpty());
        assertTrue(routes.get(1).bridgeCrossings().isEmpty());
    }

    @Test
    void jumperDetoursAroundAnIcBody() {
        BreadboardCircuit circuit = new BreadboardCircuit(BreadboardLayout.Size.HALF);
        BreadboardLayout layout = circuit.layout();
        assertTrue(circuit.placeIc(
                IcCatalog.require("74HC00"),
                layout.moduleLeft(0) + 9 * BreadboardLayout.HOLE_PITCH,
                layout.hole("M0R3C7").y()).succeeded());

        List<BreadboardWireRouter.RoutedJumper> routes = BreadboardWireRouter.routeAll(
                List.of(new Jumper(1, "M0R3C6", "M0R3C12", "CYAN")),
                circuit.integratedCircuits(), layout);
        BreadboardLayout.IcPlacement placement = layout.placement(0, 0, 14);

        assertTrue(routes.get(0).points().size() >= 4);
        assertTrue(routes.get(0).points().stream().anyMatch(point ->
                point.getY() < placement.y() + 7 - 10
                        || point.getY() > placement.y() + placement.height() - 7 + 10));
    }
}
