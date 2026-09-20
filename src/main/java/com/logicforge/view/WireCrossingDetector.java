package com.logicforge.view;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import javafx.geometry.Point2D;

/** Finds unrelated perpendicular crossings and assigns the bridge to the horizontal wire. */
public final class WireCrossingDetector {

    private static final double ENDPOINT_CLEARANCE = 11;
    private static final double EPSILON = 0.001;

    private WireCrossingDetector() {
    }

    public static Map<WireConnection, List<Point2D>> findHorizontalBridges(
            List<WireConnection> wires) {

        Map<WireConnection, List<Point2D>> bridges = new IdentityHashMap<>();
        Map<WireConnection, List<OrthogonalWireRouter.OccupiedSegment>> routes =
                new IdentityHashMap<>();
        wires.forEach(wire -> {
            bridges.put(wire, new ArrayList<>());
            routes.put(wire, wire.getOccupiedSegments());
        });

        for (int firstIndex = 0; firstIndex < wires.size(); firstIndex++) {
            WireConnection first = wires.get(firstIndex);
            for (int secondIndex = firstIndex + 1; secondIndex < wires.size(); secondIndex++) {
                WireConnection second = wires.get(secondIndex);
                if (first.getNetId() == second.getNetId()) {
                    continue;
                }

                for (OrthogonalWireRouter.OccupiedSegment firstSegment
                        : routes.get(first)) {
                    for (OrthogonalWireRouter.OccupiedSegment secondSegment
                            : routes.get(second)) {
                        Crossing crossing = perpendicularCrossing(firstSegment, secondSegment);
                        if (crossing == null) {
                            continue;
                        }
                        List<Point2D> assigned = bridges.get(crossing.horizontalWireIsFirst()
                                ? first
                                : second);
                        if (assigned.stream().noneMatch(point -> point.distance(crossing.point()) < 1)) {
                            assigned.add(crossing.point());
                        }
                    }
                }
            }
        }
        return bridges;
    }

    private static Crossing perpendicularCrossing(
            OrthogonalWireRouter.OccupiedSegment first,
            OrthogonalWireRouter.OccupiedSegment second) {

        if (first.isHorizontal() == second.isHorizontal()) {
            return null;
        }

        OrthogonalWireRouter.OccupiedSegment horizontal = first.isHorizontal() ? first : second;
        OrthogonalWireRouter.OccupiedSegment vertical = first.isHorizontal() ? second : first;
        double x = vertical.start().getX();
        double y = horizontal.start().getY();
        Point2D crossing = new Point2D(x, y);

        if (!insideWithClearance(x, horizontal.start().getX(), horizontal.end().getX())
                || !insideWithClearance(y, vertical.start().getY(), vertical.end().getY())) {
            return null;
        }
        return new Crossing(crossing, first.isHorizontal());
    }

    private static boolean insideWithClearance(double value, double first, double second) {
        double minimum = Math.min(first, second) + ENDPOINT_CLEARANCE + EPSILON;
        double maximum = Math.max(first, second) - ENDPOINT_CLEARANCE - EPSILON;
        return value > minimum && value < maximum;
    }

    private record Crossing(Point2D point, boolean horizontalWireIsFirst) {
    }
}
