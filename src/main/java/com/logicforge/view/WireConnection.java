package com.logicforge.view;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javafx.css.PseudoClass;
import javafx.geometry.Point2D;
import javafx.scene.shape.Path;

/** Directed visual connection from one output pin to a specific input pin. */
public final class WireConnection {

    private static final PseudoClass ACTIVE = PseudoClass.getPseudoClass("active");
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private static final double BEND_MERGE_DISTANCE = 20;
    private static final double MIN_EDITABLE_SEGMENT_LENGTH = 52;
    private static final double EPSILON = 0.001;

    private final int connectionId;
    private final CircuitNode source;
    private final int sourceOutputIndex;
    private final CircuitNode target;
    private final int targetInputIndex;
    private final Path path = new Path();
    private final List<Point2D> bendAnchors = new ArrayList<>();
    private List<Point2D> routePoints = List.of();
    private double sourceDepartureOffset;

    public WireConnection(
            int connectionId,
            CircuitNode source,
            int sourceOutputIndex,
            CircuitNode target,
            int targetInputIndex) {

        this.connectionId = connectionId;
        this.source = source;
        this.sourceOutputIndex = sourceOutputIndex;
        this.target = target;
        this.targetInputIndex = targetInputIndex;

        path.getStyleClass().add("signal-wire");
        path.setFill(null);
        path.setMouseTransparent(true);
        routePoints = List.of(sourcePinPoint(), targetPinPoint());
        WirePathRenderer.apply(path, routePoints, List.of());
        updateSignalVisual();
    }

    public int getConnectionId() {
        return connectionId;
    }

    public CircuitNode getSource() {
        return source;
    }

    public int getSourceOutputIndex() {
        return sourceOutputIndex;
    }

    public CircuitNode getTarget() {
        return target;
    }

    public int getTargetInputIndex() {
        return targetInputIndex;
    }

    public long getNetId() {
        return ((long) source.getComponentId() << 32)
                | (sourceOutputIndex & 0xffffffffL);
    }

    public Path getPath() {
        return path;
    }

    public List<Point2D> getRoutePoints() {
        return routePoints;
    }

    public List<OrthogonalWireRouter.OccupiedSegment> getOccupiedSegments() {
        return OrthogonalWireRouter.segmentsOf(routePoints, getNetId());
    }

    public boolean getSignalState() {
        return source.getOutputState(sourceOutputIndex);
    }

    public void setSelectedVisual(boolean selected) {
        path.pseudoClassStateChanged(SELECTED, selected);
    }

    public void setSourceDepartureOffset(double sourceDepartureOffset) {
        this.sourceDepartureOffset = sourceDepartureOffset;
    }

    public double getSourceDepartureOffset() {
        return sourceDepartureOffset;
    }

    /** Recomputes this connection around components and previously routed wires. */
    public void updateGeometry(
            Collection<CircuitNode> components,
            Collection<OrthogonalWireRouter.OccupiedSegment> occupiedSegments,
            double workspaceWidth,
            double workspaceHeight) {

        routePoints = OrthogonalWireRouter.route(
                source,
                sourceOutputIndex,
                target,
                targetInputIndex,
                components,
                bendAnchors,
                sourceDepartureOffset,
                occupiedSegments,
                getNetId(),
                workspaceWidth,
                workspaceHeight);
        WirePathRenderer.apply(path, routePoints, List.of());
    }

    public void renderWithBridges(List<Point2D> bridgeCrossings) {
        WirePathRenderer.apply(path, routePoints, bridgeCrossings);
    }

    public void updateSignalVisual() {
        path.pseudoClassStateChanged(ACTIVE, getSignalState());
    }

    public int getAdjustedBendCount() {
        return bendAnchors.size();
    }

    public List<Point2D> getBendAnchors() {
        return List.copyOf(bendAnchors);
    }

    public void setBendAnchors(List<Point2D> anchors) {
        bendAnchors.clear();
        if (anchors != null) {
            anchors.stream()
                    .filter(java.util.Objects::nonNull)
                    .forEach(this::addDistinctBendAnchor);
        }
    }

    /** Removes every manual route constraint and returns to automatic routing. */
    public void resetBendAnchors() {
        bendAnchors.clear();
    }

    /**
     * Finds the nearest movable straight section. Short terminal runs attached
     * directly to component pins are excluded so editing cannot detach a wire.
     */
    public EditableSegment findEditableSegment(Point2D query, double tolerance) {
        if (query == null || routePoints.size() < 2) {
            return null;
        }

        EditableSegment nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        int lastSegmentIndex = routePoints.size() - 2;
        for (int index = 0; index <= lastSegmentIndex; index++) {
            Point2D start = routePoints.get(index);
            Point2D end = routePoints.get(index + 1);
            boolean horizontal = Math.abs(start.getY() - end.getY()) <= EPSILON;
            boolean vertical = Math.abs(start.getX() - end.getX()) <= EPSILON;
            if (!horizontal && !vertical) {
                continue;
            }

            // Keep a fixed lead beside each physical pin. On a completely straight
            // wire this creates the two terminal corners only after the user drags.
            if (index == 0) {
                start = moveToward(start, end, Math.min(52, start.distance(end) / 3.0));
            }
            if (index == lastSegmentIndex) {
                end = moveToward(end, start, Math.min(52, start.distance(end) / 3.0));
            }
            if (start.distance(end) < MIN_EDITABLE_SEGMENT_LENGTH) {
                continue;
            }

            Point2D projection = nearestPointOnSegment(query, start, end);
            double distance = query.distance(projection);
            if (distance <= tolerance && distance < nearestDistance) {
                nearest = new EditableSegment(start, end, horizontal);
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private static Point2D moveToward(Point2D origin, Point2D destination, double distance) {
        Point2D direction = destination.subtract(origin);
        double magnitude = direction.magnitude();
        if (magnitude <= EPSILON) {
            return origin;
        }
        return origin.add(direction.multiply(Math.min(1.0, distance / magnitude)));
    }

    /** Locks both ends of a routed section so it can move as one line. */
    public SegmentEdit beginSegmentEdit(EditableSegment segment) {
        if (segment == null) {
            return null;
        }
        int firstIndex = lockBendAt(segment.start());
        int secondIndex = lockBendAt(segment.end());
        if (firstIndex == secondIndex) {
            return null;
        }
        if (secondIndex < firstIndex) {
            int swap = firstIndex;
            firstIndex = secondIndex;
            secondIndex = swap;
        }
        Point2D first = bendAnchors.get(firstIndex);
        Point2D second = bendAnchors.get(secondIndex);
        return new SegmentEdit(
                firstIndex,
                secondIndex,
                segment.horizontal(),
                segment.horizontal() ? first.getX() : first.getY(),
                segment.horizontal() ? second.getX() : second.getY());
    }

    /** Moves a complete line section perpendicular to its current direction. */
    public void moveSegment(SegmentEdit edit, double coordinate) {
        if (edit == null
                || edit.firstAnchorIndex() < 0
                || edit.secondAnchorIndex() >= bendAnchors.size()) {
            return;
        }
        if (edit.horizontal()) {
            bendAnchors.set(edit.firstAnchorIndex(), new Point2D(edit.firstFixed(), coordinate));
            bendAnchors.set(edit.secondAnchorIndex(), new Point2D(edit.secondFixed(), coordinate));
        } else {
            bendAnchors.set(edit.firstAnchorIndex(), new Point2D(coordinate, edit.firstFixed()));
            bendAnchors.set(edit.secondAnchorIndex(), new Point2D(coordinate, edit.secondFixed()));
        }
    }

    public int lockBendAt(Point2D point) {
        Point2D anchor = point == null ? nearestPoint(sourcePinPoint()) : point;
        int nearbyAnchor = findBendAnchorNear(anchor, BEND_MERGE_DISTANCE);
        if (nearbyAnchor >= 0) {
            return nearbyAnchor;
        }
        double newDistance = distanceAlongRoute(anchor);
        int insertionIndex = 0;
        while (insertionIndex < bendAnchors.size()
                && distanceAlongRoute(bendAnchors.get(insertionIndex)) <= newDistance) {
            insertionIndex++;
        }
        bendAnchors.add(insertionIndex, anchor);
        return insertionIndex;
    }

    public int findBendAnchorNear(Point2D point, double tolerance) {
        for (int index = 0; index < bendAnchors.size(); index++) {
            if (bendAnchors.get(index).distance(point) <= tolerance) {
                return index;
            }
        }
        return -1;
    }

    /** Returns the nearest point on the sharp routed polyline. */
    public Point2D nearestPoint(Point2D query) {
        Point2D nearest = routePoints.isEmpty() ? query : routePoints.get(0);
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (int index = 1; index < routePoints.size(); index++) {
            Point2D candidate = nearestPointOnSegment(
                    query,
                    routePoints.get(index - 1),
                    routePoints.get(index));
            double distance = query.distance(candidate);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = candidate;
            }
        }
        return nearest;
    }

    /** Returns the shortest distance from a workspace point to the routed wire. */
    public double distanceTo(double x, double y) {
        return new Point2D(x, y).distance(nearestPoint(new Point2D(x, y)));
    }

    private double distanceAlongRoute(Point2D query) {
        double traversed = 0;
        double bestDistance = Double.POSITIVE_INFINITY;
        double bestPosition = 0;
        for (int index = 1; index < routePoints.size(); index++) {
            Point2D start = routePoints.get(index - 1);
            Point2D end = routePoints.get(index);
            Point2D projection = nearestPointOnSegment(query, start, end);
            double distance = query.distance(projection);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestPosition = traversed + start.distance(projection);
            }
            traversed += start.distance(end);
        }
        return bestPosition;
    }

    private Point2D sourcePinPoint() {
        return new Point2D(
                source.outputAnchorX(sourceOutputIndex),
                source.outputAnchorY(sourceOutputIndex));
    }

    private Point2D targetPinPoint() {
        return new Point2D(
                target.inputAnchorX(targetInputIndex),
                target.inputAnchorY(targetInputIndex));
    }

    private static Point2D nearestPointOnSegment(
            Point2D point,
            Point2D start,
            Point2D end) {

        double deltaX = end.getX() - start.getX();
        double deltaY = end.getY() - start.getY();
        double lengthSquared = deltaX * deltaX + deltaY * deltaY;
        if (lengthSquared <= EPSILON) {
            return start;
        }

        double projection = ((point.getX() - start.getX()) * deltaX
                + (point.getY() - start.getY()) * deltaY) / lengthSquared;
        double clamped = Math.max(0, Math.min(1, projection));
        return new Point2D(
                start.getX() + clamped * deltaX,
                start.getY() + clamped * deltaY);
    }

    private void addDistinctBendAnchor(Point2D candidate) {
        if (findBendAnchorNear(candidate, BEND_MERGE_DISTANCE) < 0) {
            bendAnchors.add(candidate);
        }
    }

    /** A visible straight section that can be dragged without control-point UI. */
    public record EditableSegment(Point2D start, Point2D end, boolean horizontal) { }

    /** Stable anchor information retained throughout one segment drag. */
    public record SegmentEdit(
            int firstAnchorIndex,
            int secondAnchorIndex,
            boolean horizontal,
            double firstFixed,
            double secondFixed) { }

}
