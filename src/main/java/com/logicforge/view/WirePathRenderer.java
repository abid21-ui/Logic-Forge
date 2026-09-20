package com.logicforge.view;

import java.util.Comparator;
import java.util.List;

import javafx.geometry.Point2D;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.QuadCurveTo;

/** Renders rounded orthogonal routes and small bridge arcs at wire crossings. */
public final class WirePathRenderer {

    private static final double CORNER_RADIUS = 12;
    private static final double BRIDGE_HALF_WIDTH = 8;
    private static final double BRIDGE_HEIGHT = 9;
    private static final double EPSILON = 0.001;

    private WirePathRenderer() {
    }

    public static void apply(
            Path path,
            List<Point2D> rawRoutePoints,
            List<Point2D> bridgeCrossings) {

        List<Point2D> points = OrthogonalWireRouter.simplify(rawRoutePoints);
        path.getElements().clear();
        if (points.isEmpty()) {
            return;
        }

        Point2D first = points.get(0);
        path.getElements().add(new MoveTo(first.getX(), first.getY()));
        if (points.size() == 1) {
            return;
        }

        for (int segmentIndex = 0; segmentIndex < points.size() - 1; segmentIndex++) {
            Point2D segmentStart = segmentIndex == 0
                    ? points.get(0)
                    : afterCorner(points, segmentIndex);
            Point2D segmentEnd = segmentIndex == points.size() - 2
                    ? points.get(points.size() - 1)
                    : beforeCorner(points, segmentIndex + 1);

            appendStraightRun(path, segmentStart, segmentEnd, bridgeCrossings);

            int cornerIndex = segmentIndex + 1;
            if (cornerIndex < points.size() - 1) {
                Point2D corner = points.get(cornerIndex);
                Point2D after = afterCorner(points, cornerIndex);
                path.getElements().add(new QuadCurveTo(
                        corner.getX(),
                        corner.getY(),
                        after.getX(),
                        after.getY()));
            }
        }
    }

    private static void appendStraightRun(
            Path path,
            Point2D start,
            Point2D end,
            List<Point2D> bridgeCrossings) {

        boolean horizontal = Math.abs(start.getY() - end.getY()) <= EPSILON;
        if (!horizontal || bridgeCrossings.isEmpty()) {
            path.getElements().add(new LineTo(end.getX(), end.getY()));
            return;
        }

        double direction = end.getX() >= start.getX() ? 1 : -1;
        List<Point2D> crossings = bridgeCrossings.stream()
                .filter(crossing -> Math.abs(crossing.getY() - start.getY()) <= EPSILON)
                .filter(crossing -> safelyInsideRun(crossing.getX(), start.getX(), end.getX()))
                .sorted(Comparator.comparingDouble(point -> direction * point.getX()))
                .toList();

        double lastBridgeX = Double.NaN;
        for (Point2D crossing : crossings) {
            if (!Double.isNaN(lastBridgeX)
                    && Math.abs(crossing.getX() - lastBridgeX) < BRIDGE_HALF_WIDTH * 2.5) {
                continue;
            }

            double beforeX = crossing.getX() - direction * BRIDGE_HALF_WIDTH;
            double afterX = crossing.getX() + direction * BRIDGE_HALF_WIDTH;
            path.getElements().add(new LineTo(beforeX, start.getY()));
            path.getElements().add(new QuadCurveTo(
                    crossing.getX(),
                    start.getY() - BRIDGE_HEIGHT * 2,
                    afterX,
                    start.getY()));
            lastBridgeX = crossing.getX();
        }
        path.getElements().add(new LineTo(end.getX(), end.getY()));
    }

    private static boolean safelyInsideRun(double value, double first, double second) {
        double minimum = Math.min(first, second) + BRIDGE_HALF_WIDTH + 2;
        double maximum = Math.max(first, second) - BRIDGE_HALF_WIDTH - 2;
        return value > minimum && value < maximum;
    }

    private static Point2D beforeCorner(List<Point2D> points, int cornerIndex) {
        Point2D previous = points.get(cornerIndex - 1);
        Point2D corner = points.get(cornerIndex);
        Point2D next = points.get(cornerIndex + 1);
        double radius = cornerRadius(previous, corner, next);
        return moveToward(corner, previous, radius);
    }

    private static Point2D afterCorner(List<Point2D> points, int cornerIndex) {
        Point2D previous = points.get(cornerIndex - 1);
        Point2D corner = points.get(cornerIndex);
        Point2D next = points.get(cornerIndex + 1);
        double radius = cornerRadius(previous, corner, next);
        return moveToward(corner, next, radius);
    }

    private static double cornerRadius(Point2D previous, Point2D corner, Point2D next) {
        double incomingLength = previous.distance(corner);
        double outgoingLength = corner.distance(next);
        return Math.min(CORNER_RADIUS, Math.min(incomingLength / 2.0, outgoingLength / 2.0));
    }

    private static Point2D moveToward(Point2D origin, Point2D destination, double distance) {
        Point2D direction = destination.subtract(origin);
        double magnitude = direction.magnitude();
        if (magnitude <= EPSILON) {
            return origin;
        }
        return origin.add(direction.multiply(distance / magnitude));
    }
}
