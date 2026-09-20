package com.logicforge.view;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.logicforge.model.BreadboardCircuit.Jumper;
import com.logicforge.model.BreadboardCircuit.PlacedIc;
import com.logicforge.model.BreadboardLayout;
import com.logicforge.model.BreadboardLayout.Hole;
import com.logicforge.model.BreadboardLayout.HoleKind;
import com.logicforge.model.BreadboardLayout.IcPlacement;

import javafx.geometry.Point2D;

/**
 * Lightweight orthogonal router for breadboard jumpers. It shares the
 * schematic renderer, so completed jumpers have rounded corners and bridge
 * arcs instead of independent diagonal line styling.
 */
public final class BreadboardWireRouter {

    private static final double EPSILON = 0.001;
    private static final double ENDPOINT_CLEARANCE = 11;
    private static final double LANE_STEP = 7;
    private static final double IC_CLEARANCE = 10;
    private static final double BEND_COST = 12;

    private BreadboardWireRouter() {
    }

    public static List<RoutedJumper> routeAll(
            Collection<Jumper> jumpers,
            BreadboardLayout layout) {

        return routeAll(jumpers, List.of(), layout);
    }

    public static List<RoutedJumper> routeAll(
            Collection<Jumper> jumpers,
            Collection<PlacedIc> integratedCircuits,
            BreadboardLayout layout) {

        List<Obstacle> obstacles = obstacles(integratedCircuits, layout);

        List<RoutedJumper> routed = jumpers.stream()
                .sorted(Comparator.comparingInt(Jumper::id))
                .map(jumper -> new RoutedJumper(
                        jumper,
                        route(jumper, layout, obstacles),
                        new ArrayList<>()))
                .toList();

        Map<Integer, List<Point2D>> bridges = new LinkedHashMap<>();
        routed.forEach(item -> bridges.put(item.jumper().id(), new ArrayList<>()));
        for (int firstIndex = 0; firstIndex < routed.size(); firstIndex++) {
            RoutedJumper first = routed.get(firstIndex);
            for (int secondIndex = firstIndex + 1; secondIndex < routed.size(); secondIndex++) {
                RoutedJumper second = routed.get(secondIndex);
                addCrossings(first, second, bridges);
            }
        }

        return routed.stream()
                .map(item -> new RoutedJumper(
                        item.jumper(),
                        item.points(),
                        List.copyOf(bridges.get(item.jumper().id()))))
                .toList();
    }

    public static List<Point2D> preview(Point2D start, Point2D pointer) {
        return routeBetween(start, pointer, List.of(), 0, Double.MAX_VALUE, Double.MAX_VALUE);
    }

    public static List<Point2D> preview(
            Point2D start,
            Point2D pointer,
            Collection<PlacedIc> integratedCircuits,
            BreadboardLayout layout) {

        return routeBetween(
                start, pointer, obstacles(integratedCircuits, layout),
                0, layout.width(), layout.height());
    }

    private static List<Point2D> route(
            Jumper jumper,
            BreadboardLayout layout,
            List<Obstacle> obstacles) {

        Hole startHole = layout.hole(jumper.startHoleId());
        Hole endHole = layout.hole(jumper.endHoleId());
        Point2D start = new Point2D(startHole.x(), startHole.y());
        Point2D end = new Point2D(endHole.x(), endHole.y());
        double laneOffset = (Math.floorMod(jumper.id(), 7) - 3) * LANE_STEP;

        // Control-panel jumpers must leave the complete pin bank before turning.
        // This prevents a horizontal wire from visually crossing every unused
        // switch or bulb lead, while still allowing a straight same-column wire.
        if (!aligned(start.getX(), end.getX())) {
            Double externalLane = externalControlLane(startHole, endHole, layout, laneOffset);
            if (externalLane != null) {
                List<Point2D> externalRoute = routeThroughExternalLane(
                        start, end, externalLane, obstacles);
                if (externalRoute != null) {
                    return externalRoute;
                }
            }
        }
        return routeBetween(
                start, end, obstacles, laneOffset, layout.width(), layout.height());
    }

    private static Double externalControlLane(
            Hole start,
            Hole end,
            BreadboardLayout layout,
            double laneOffset) {

        if (isUpperControl(start.kind()) || isUpperControl(end.kind())) {
            return layout.boardTop() - 12 - Math.abs(laneOffset) * 0.35;
        }
        if (start.kind() == HoleKind.BULB_INPUT || end.kind() == HoleKind.BULB_INPUT) {
            return layout.boardBottom() + 8 + Math.abs(laneOffset) * 0.20;
        }
        return null;
    }

    private static boolean isUpperControl(HoleKind kind) {
        return kind == HoleKind.SWITCH_OUTPUT
                || kind == HoleKind.VCC_SOURCE
                || kind == HoleKind.GND_SOURCE;
    }

    private static List<Point2D> routeThroughExternalLane(
            Point2D start,
            Point2D end,
            double laneY,
            List<Obstacle> obstacles) {

        List<Point2D> candidate = OrthogonalWireRouter.simplify(List.of(
                start,
                new Point2D(start.getX(), laneY),
                new Point2D(end.getX(), laneY),
                end));
        return routeIsClear(candidate, obstacles) ? candidate : null;
    }

    private static List<Point2D> routeBetween(
            Point2D start,
            Point2D end,
            List<Obstacle> obstacles,
            double laneOffset,
            double boardWidth,
            double boardHeight) {

        List<List<Point2D>> candidates = new ArrayList<>();
        if (aligned(start.getX(), end.getX()) || aligned(start.getY(), end.getY())) {
            candidates.add(List.of(start, end));
        }

        double midpointX = (start.getX() + end.getX()) / 2.0 + laneOffset;
        double midpointY = (start.getY() + end.getY()) / 2.0 + laneOffset;
        addVerticalLane(candidates, start, end, midpointX);
        addHorizontalLane(candidates, start, end, midpointY);

        double detour = LANE_STEP + Math.abs(laneOffset);
        for (Obstacle obstacle : obstacles) {
            addVerticalLane(candidates, start, end, obstacle.left() - detour);
            addVerticalLane(candidates, start, end, obstacle.right() + detour);
            addHorizontalLane(candidates, start, end, obstacle.top() - detour);
            addHorizontalLane(candidates, start, end, obstacle.bottom() + detour);
        }
        if (Double.isFinite(boardWidth) && Double.isFinite(boardHeight)) {
            addVerticalLane(candidates, start, end, 8 + Math.abs(laneOffset));
            addVerticalLane(candidates, start, end, boardWidth - 8 - Math.abs(laneOffset));
            addHorizontalLane(candidates, start, end, 8 + Math.abs(laneOffset));
            addHorizontalLane(candidates, start, end, boardHeight - 8 - Math.abs(laneOffset));
        }

        return candidates.stream()
                .map(OrthogonalWireRouter::simplify)
                .filter(candidate -> routeIsClear(candidate, obstacles))
                .min(Comparator.comparingDouble(BreadboardWireRouter::routeScore))
                .orElseGet(() -> OrthogonalWireRouter.simplify(List.of(
                        start,
                        new Point2D(start.getX(), midpointY),
                        new Point2D(end.getX(), midpointY),
                        end)));
    }

    private static void addVerticalLane(
            List<List<Point2D>> candidates,
            Point2D start,
            Point2D end,
            double laneX) {

        candidates.add(List.of(
                start,
                new Point2D(laneX, start.getY()),
                new Point2D(laneX, end.getY()),
                end));
    }

    private static void addHorizontalLane(
            List<List<Point2D>> candidates,
            Point2D start,
            Point2D end,
            double laneY) {

        candidates.add(List.of(
                start,
                new Point2D(start.getX(), laneY),
                new Point2D(end.getX(), laneY),
                end));
    }

    private static boolean routeIsClear(List<Point2D> points, List<Obstacle> obstacles) {
        for (int index = 1; index < points.size(); index++) {
            Point2D start = points.get(index - 1);
            Point2D end = points.get(index);
            if (obstacles.stream().anyMatch(obstacle -> obstacle.intersects(start, end))) {
                return false;
            }
        }
        return true;
    }

    private static double routeScore(List<Point2D> points) {
        double length = 0;
        for (int index = 1; index < points.size(); index++) {
            Point2D first = points.get(index - 1);
            Point2D second = points.get(index);
            length += Math.abs(second.getX() - first.getX())
                    + Math.abs(second.getY() - first.getY());
        }
        return length + Math.max(0, points.size() - 2) * BEND_COST;
    }

    private static List<Obstacle> obstacles(
            Collection<PlacedIc> integratedCircuits,
            BreadboardLayout layout) {

        return integratedCircuits.stream()
                .map(ic -> {
                    IcPlacement placement = layout.placement(
                            ic.moduleIndex(), ic.firstRow(),
                            ic.definition().packageType().pinCount());
                    return new Obstacle(
                            placement.x() + 13 - IC_CLEARANCE,
                            placement.y() + 7 - IC_CLEARANCE,
                            placement.x() + placement.width() - 13 + IC_CLEARANCE,
                            placement.y() + placement.height() - 7 + IC_CLEARANCE);
                })
                .toList();
    }

    private static void addCrossings(
            RoutedJumper first,
            RoutedJumper second,
            Map<Integer, List<Point2D>> bridges) {

        List<OrthogonalWireRouter.OccupiedSegment> firstSegments =
                OrthogonalWireRouter.segmentsOf(first.points(), first.jumper().id());
        List<OrthogonalWireRouter.OccupiedSegment> secondSegments =
                OrthogonalWireRouter.segmentsOf(second.points(), second.jumper().id());
        for (OrthogonalWireRouter.OccupiedSegment firstSegment : firstSegments) {
            for (OrthogonalWireRouter.OccupiedSegment secondSegment : secondSegments) {
                Crossing crossing = perpendicularCrossing(firstSegment, secondSegment);
                if (crossing == null) {
                    continue;
                }
                int horizontalId = crossing.firstIsHorizontal()
                        ? first.jumper().id()
                        : second.jumper().id();
                List<Point2D> assigned = bridges.get(horizontalId);
                if (assigned.stream().noneMatch(point -> point.distance(crossing.point()) < 1)) {
                    assigned.add(crossing.point());
                }
            }
        }
    }

    private static Crossing perpendicularCrossing(
            OrthogonalWireRouter.OccupiedSegment first,
            OrthogonalWireRouter.OccupiedSegment second) {

        if (first.isHorizontal() == second.isHorizontal()) {
            return null;
        }
        OrthogonalWireRouter.OccupiedSegment horizontal =
                first.isHorizontal() ? first : second;
        OrthogonalWireRouter.OccupiedSegment vertical =
                first.isHorizontal() ? second : first;
        double x = vertical.start().getX();
        double y = horizontal.start().getY();
        if (!insideWithClearance(x, horizontal.start().getX(), horizontal.end().getX())
                || !insideWithClearance(y, vertical.start().getY(), vertical.end().getY())) {
            return null;
        }
        return new Crossing(new Point2D(x, y), first.isHorizontal());
    }

    private static boolean insideWithClearance(double value, double first, double second) {
        double minimum = Math.min(first, second) + ENDPOINT_CLEARANCE + EPSILON;
        double maximum = Math.max(first, second) - ENDPOINT_CLEARANCE - EPSILON;
        return value > minimum && value < maximum;
    }

    private static boolean aligned(double first, double second) {
        return Math.abs(first - second) <= EPSILON;
    }

    public record RoutedJumper(
            Jumper jumper,
            List<Point2D> points,
            List<Point2D> bridgeCrossings) {

        public RoutedJumper {
            points = List.copyOf(points);
            bridgeCrossings = List.copyOf(bridgeCrossings);
        }
    }

    private record Crossing(Point2D point, boolean firstIsHorizontal) {
    }

    private record Obstacle(double left, double top, double right, double bottom) {
        private boolean intersects(Point2D first, Point2D second) {
            if (aligned(first.getY(), second.getY())) {
                double minimumX = Math.min(first.getX(), second.getX());
                double maximumX = Math.max(first.getX(), second.getX());
                return first.getY() >= top - EPSILON
                        && first.getY() <= bottom + EPSILON
                        && maximumX >= left - EPSILON
                        && minimumX <= right + EPSILON;
            }
            if (aligned(first.getX(), second.getX())) {
                double minimumY = Math.min(first.getY(), second.getY());
                double maximumY = Math.max(first.getY(), second.getY());
                return first.getX() >= left - EPSILON
                        && first.getX() <= right + EPSILON
                        && maximumY >= top - EPSILON
                        && minimumY <= bottom + EPSILON;
            }
            return true;
        }
    }
}
