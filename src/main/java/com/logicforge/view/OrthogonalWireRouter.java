package com.logicforge.view;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.TreeSet;

import javafx.geometry.Point2D;
import javafx.scene.shape.Path;

/**
 * Routes wires through orthogonal visibility channels while avoiding component
 * bounds and discouraging congestion with previously routed wires.
 */
public final class OrthogonalWireRouter {

    public static final double WIRE_LANE_SPACING = 16;

    private static final double EPSILON = 0.001;
    private static final double COMPONENT_CLEARANCE = 16;
    private static final double PIN_LEAD_LENGTH = 52;
    /*
     * A direct two-point route must be truly axis-aligned. Earlier releases
     * accepted a 12 px mismatch and consequently drew a subtly diagonal wire.
     * Near-aligned pins now fall through to the orthogonal router instead.
     */
    private static final double DIRECT_ALIGNMENT_TOLERANCE = EPSILON;
    private static final double ROUTE_EDGE_OFFSET = 1;
    private static final double OUTER_CHANNEL_PADDING = 72;
    private static final double BEND_PENALTY = 30;
    private static final double CROSSING_PENALTY = 70;
    private static final double OVERLAP_PENALTY = 20_000;

    private OrthogonalWireRouter() {
    }

    /** A sharp orthogonal segment already occupied by a routed connection. */
    public record OccupiedSegment(Point2D start, Point2D end, long netId) {
        public OccupiedSegment {
            Objects.requireNonNull(start, "start");
            Objects.requireNonNull(end, "end");
        }

        public boolean isHorizontal() {
            return Math.abs(start.getY() - end.getY()) <= EPSILON;
        }

        public boolean isVertical() {
            return Math.abs(start.getX() - end.getX()) <= EPSILON;
        }
    }

    /**
     * Creates a complete route between two component pins.
     *
     * @param bendAnchors invisible constraints created by direct segment editing
     * @param sourceDepartureOffset zero for a straight departure, otherwise a
     *        vertical offset applied immediately at the source pin
     * @param occupiedSegments routes that should be crossed sparingly and never
     *        overlapped when another channel is available
     */
    public static List<Point2D> route(
            CircuitNode source,
            int sourceOutputIndex,
            CircuitNode target,
            int targetInputIndex,
            Collection<CircuitNode> components,
            List<Point2D> bendAnchors,
            double sourceDepartureOffset,
            Collection<OccupiedSegment> occupiedSegments,
            long netId,
            double workspaceWidth,
            double workspaceHeight) {

        Point2D sourcePin = new Point2D(
                source.outputAnchorX(sourceOutputIndex),
                source.outputAnchorY(sourceOutputIndex));
        Point2D targetPin = new Point2D(
                target.inputAnchorX(targetInputIndex),
                target.inputAnchorY(targetInputIndex));
        CircuitNode.PinSide sourceSide = source.outputPinSide(sourceOutputIndex);
        CircuitNode.PinSide targetSide = target.inputPinSide(targetInputIndex);

        // Only exactly aligned opposing pins receive a direct two-point route.
        // Even a small mismatch must be resolved with horizontal/vertical bends;
        // completed LogicForge wires never contain diagonal segments.
        if (bendAnchors.isEmpty()
                && isNearlyAligned(sourcePin, sourceSide, targetPin, targetSide)
                && directRunClear(sourcePin, targetPin, components, source, target)) {
            return List.of(sourcePin, targetPin);
        }
        List<Point2D> sourcePrefix = sourcePrefix(
                sourcePin, sourceSide, sourceDepartureOffset);
        Point2D sourceEscape = sourcePrefix.get(sourcePrefix.size() - 1);
        Point2D targetLead = leadPoint(targetPin, targetSide);

        List<Obstacle> obstacles = components.stream()
                .map(OrthogonalWireRouter::obstacleFor)
                .toList();
        List<OccupiedSegment> congestion = new ArrayList<>(occupiedSegments);

        List<Point2D> checkpoints = new ArrayList<>();
        checkpoints.add(sourceEscape);
        bendAnchors.stream().filter(Objects::nonNull).forEach(checkpoints::add);
        checkpoints.add(targetLead);

        List<Point2D> complete = new ArrayList<>(sourcePrefix);
        for (int index = 1; index < checkpoints.size(); index++) {
            Point2D sectionStart = checkpoints.get(index - 1);
            Point2D sectionEnd = checkpoints.get(index);
            List<Point2D> section = findRoute(
                    sectionStart,
                    sectionEnd,
                    obstacles,
                    congestion,
                    netId,
                    workspaceWidth,
                    workspaceHeight);
            if (section.isEmpty()) {
                section = fallbackRoute(sectionStart, sectionEnd, obstacles);
            }
            appendWithoutDuplicate(complete, section);
            congestion.addAll(segmentsOf(section, netId));
        }

        complete.add(targetPin);
        return simplify(complete);
    }

    /** Lightweight route preview used before the target pin is known. */
    public static List<Point2D> preview(
            CircuitNode source,
            int sourceOutputIndex,
            Point2D pointer,
            double sourceDepartureOffset) {

        Point2D sourcePin = new Point2D(
                source.outputAnchorX(sourceOutputIndex),
                source.outputAnchorY(sourceOutputIndex));
        List<Point2D> points = new ArrayList<>(sourcePrefix(
                sourcePin, source.outputPinSide(sourceOutputIndex), sourceDepartureOffset));
        Point2D sourceEscape = points.get(points.size() - 1);

        double horizontalDistance = Math.abs(pointer.getX() - sourceEscape.getX());
        double verticalDistance = Math.abs(pointer.getY() - sourceEscape.getY());
        if (horizontalDistance >= verticalDistance) {
            points.add(new Point2D(pointer.getX(), sourceEscape.getY()));
        } else {
            points.add(new Point2D(sourceEscape.getX(), pointer.getY()));
        }
        points.add(pointer);
        return simplify(points);
    }

    /** Retained as the common rendering entry point for live wire previews. */
    public static void applyRoundedPath(Path path, List<Point2D> routePoints) {
        WirePathRenderer.apply(path, routePoints, List.of());
    }

    public static List<OccupiedSegment> segmentsOf(List<Point2D> points, long netId) {
        List<OccupiedSegment> segments = new ArrayList<>();
        for (int index = 1; index < points.size(); index++) {
            Point2D start = points.get(index - 1);
            Point2D end = points.get(index);
            boolean orthogonal = Math.abs(start.getX() - end.getX()) <= EPSILON
                    || Math.abs(start.getY() - end.getY()) <= EPSILON;
            if (orthogonal && start.distance(end) > EPSILON) {
                segments.add(new OccupiedSegment(start, end, netId));
            }
        }
        return List.copyOf(segments);
    }

    static boolean isNearlyAligned(
            Point2D source,
            CircuitNode.PinSide sourceSide,
            Point2D target,
            CircuitNode.PinSide targetSide) {

        return switch (sourceSide) {
            case RIGHT -> targetSide == CircuitNode.PinSide.LEFT
                    && target.getX() > source.getX()
                    && Math.abs(target.getY() - source.getY()) <= DIRECT_ALIGNMENT_TOLERANCE;
            case LEFT -> targetSide == CircuitNode.PinSide.RIGHT
                    && target.getX() < source.getX()
                    && Math.abs(target.getY() - source.getY()) <= DIRECT_ALIGNMENT_TOLERANCE;
            case BOTTOM -> targetSide == CircuitNode.PinSide.TOP
                    && target.getY() > source.getY()
                    && Math.abs(target.getX() - source.getX()) <= DIRECT_ALIGNMENT_TOLERANCE;
            case TOP -> targetSide == CircuitNode.PinSide.BOTTOM
                    && target.getY() < source.getY()
                    && Math.abs(target.getX() - source.getX()) <= DIRECT_ALIGNMENT_TOLERANCE;
        };
    }

    private static boolean directRunClear(
            Point2D start,
            Point2D end,
            Collection<CircuitNode> components,
            CircuitNode source,
            CircuitNode target) {

        return components.stream()
                .filter(node -> node != source && node != target)
                .map(OrthogonalWireRouter::obstacleFor)
                .noneMatch(obstacle -> segmentIntersects(obstacle, start, end));
    }

    /** Parametric line/rectangle test used only for the short near-axis shortcut. */
    private static boolean segmentIntersects(Obstacle obstacle, Point2D start, Point2D end) {
        double deltaX = end.getX() - start.getX();
        double deltaY = end.getY() - start.getY();
        double[] interval = { 0.0, 1.0 };
        return clipAxis(start.getX(), deltaX, obstacle.left(), obstacle.right(), interval)
                && clipAxis(start.getY(), deltaY, obstacle.top(), obstacle.bottom(), interval)
                && interval[1] > EPSILON
                && interval[0] < 1.0 - EPSILON;
    }

    private static boolean clipAxis(
            double origin,
            double delta,
            double minimum,
            double maximum,
            double[] interval) {

        if (Math.abs(delta) <= EPSILON) {
            return origin >= minimum && origin <= maximum;
        }
        double first = (minimum - origin) / delta;
        double second = (maximum - origin) / delta;
        if (first > second) {
            double swap = first;
            first = second;
            second = swap;
        }
        interval[0] = Math.max(interval[0], first);
        interval[1] = Math.min(interval[1], second);
        return interval[0] <= interval[1];
    }

    private static List<Point2D> sourcePrefix(
            Point2D sourcePin, CircuitNode.PinSide side, double departureOffset) {
        List<Point2D> prefix = new ArrayList<>();
        prefix.add(sourcePin);
        Point2D perpendicular = switch (side) {
            case LEFT, RIGHT -> new Point2D(0, departureOffset);
            case TOP, BOTTOM -> new Point2D(departureOffset, 0);
        };
        Point2D departure = sourcePin.add(perpendicular);
        if (Math.abs(departureOffset) > EPSILON) {
            prefix.add(departure);
        }
        prefix.add(leadPoint(departure, side));
        return prefix;
    }

    private static List<Point2D> findRoute(
            Point2D start,
            Point2D end,
            List<Obstacle> obstacles,
            List<OccupiedSegment> occupiedSegments,
            long netId,
            double workspaceWidth,
            double workspaceHeight) {

        TreeSet<Double> xCoordinates = new TreeSet<>();
        TreeSet<Double> yCoordinates = new TreeSet<>();
        xCoordinates.add(start.getX());
        xCoordinates.add(end.getX());
        yCoordinates.add(start.getY());
        yCoordinates.add(end.getY());

        double minimumX = Math.min(start.getX(), end.getX());
        double maximumX = Math.max(start.getX(), end.getX());
        double minimumY = Math.min(start.getY(), end.getY());
        double maximumY = Math.max(start.getY(), end.getY());

        for (Obstacle obstacle : obstacles) {
            xCoordinates.add(obstacle.left() - ROUTE_EDGE_OFFSET);
            xCoordinates.add(obstacle.right() + ROUTE_EDGE_OFFSET);
            yCoordinates.add(obstacle.top() - ROUTE_EDGE_OFFSET);
            yCoordinates.add(obstacle.bottom() + ROUTE_EDGE_OFFSET);
            minimumX = Math.min(minimumX, obstacle.left());
            maximumX = Math.max(maximumX, obstacle.right());
            minimumY = Math.min(minimumY, obstacle.top());
            maximumY = Math.max(maximumY, obstacle.bottom());
        }

        for (OccupiedSegment segment : occupiedSegments) {
            if (segment.isHorizontal()) {
                yCoordinates.add(segment.start().getY() - WIRE_LANE_SPACING);
                yCoordinates.add(segment.start().getY() + WIRE_LANE_SPACING);
            } else if (segment.isVertical()) {
                xCoordinates.add(segment.start().getX() - WIRE_LANE_SPACING);
                xCoordinates.add(segment.start().getX() + WIRE_LANE_SPACING);
            }
        }

        xCoordinates.add(clamp(
                minimumX - OUTER_CHANNEL_PADDING,
                -OUTER_CHANNEL_PADDING,
                workspaceWidth + OUTER_CHANNEL_PADDING));
        xCoordinates.add(clamp(
                maximumX + OUTER_CHANNEL_PADDING,
                -OUTER_CHANNEL_PADDING,
                workspaceWidth + OUTER_CHANNEL_PADDING));
        yCoordinates.add(clamp(
                minimumY - OUTER_CHANNEL_PADDING,
                -OUTER_CHANNEL_PADDING,
                workspaceHeight + OUTER_CHANNEL_PADDING));
        yCoordinates.add(clamp(
                maximumY + OUTER_CHANNEL_PADDING,
                -OUTER_CHANNEL_PADDING,
                workspaceHeight + OUTER_CHANNEL_PADDING));

        List<Double> xs = new ArrayList<>(xCoordinates);
        List<Double> ys = new ArrayList<>(yCoordinates);
        int xCount = xs.size();
        int yCount = ys.size();
        boolean[][] valid = new boolean[yCount][xCount];

        for (int yIndex = 0; yIndex < yCount; yIndex++) {
            for (int xIndex = 0; xIndex < xCount; xIndex++) {
                Point2D point = new Point2D(xs.get(xIndex), ys.get(yIndex));
                valid[yIndex][xIndex] = point.equals(start)
                        || point.equals(end)
                        || obstacles.stream().noneMatch(obstacle -> obstacle.containsInterior(point));
            }
        }

        int startX = xs.indexOf(start.getX());
        int startY = ys.indexOf(start.getY());
        int endX = xs.indexOf(end.getX());
        int endY = ys.indexOf(end.getY());
        if (startX < 0 || startY < 0 || endX < 0 || endY < 0) {
            return List.of();
        }

        return search(
                xs,
                ys,
                valid,
                obstacles,
                occupiedSegments,
                netId,
                startX,
                startY,
                endX,
                endY);
    }

    private static List<Point2D> search(
            List<Double> xs,
            List<Double> ys,
            boolean[][] valid,
            List<Obstacle> obstacles,
            List<OccupiedSegment> occupiedSegments,
            long netId,
            int startX,
            int startY,
            int endX,
            int endY) {

        int xCount = xs.size();
        int pointCount = xCount * ys.size();
        int stateCount = pointCount * 3;
        double[] costs = new double[stateCount];
        int[] previous = new int[stateCount];
        Arrays.fill(costs, Double.POSITIVE_INFINITY);
        Arrays.fill(previous, -1);

        int startPoint = pointId(startX, startY, xCount);
        int endPoint = pointId(endX, endY, xCount);
        int startState = stateId(startPoint, Direction.NONE);
        costs[startState] = 0;

        PriorityQueue<SearchEntry> open = new PriorityQueue<>(
                Comparator.comparingDouble(SearchEntry::priority));
        open.add(new SearchEntry(
                startState,
                0,
                heuristic(xs.get(startX), ys.get(startY), xs.get(endX), ys.get(endY))));

        int finalState = -1;
        while (!open.isEmpty()) {
            SearchEntry current = open.remove();
            if (current.cost() > costs[current.stateId()] + EPSILON) {
                continue;
            }

            int currentPoint = current.stateId() / 3;
            Direction incomingDirection = Direction.fromIndex(current.stateId() % 3);
            if (currentPoint == endPoint) {
                finalState = current.stateId();
                break;
            }

            int currentX = currentPoint % xCount;
            int currentY = currentPoint / xCount;
            Point2D currentValue = new Point2D(xs.get(currentX), ys.get(currentY));

            for (GridIndex neighbor : visibleNeighbors(
                    currentX, currentY, xs, ys, valid, obstacles)) {
                Point2D neighborValue = new Point2D(
                        xs.get(neighbor.x()), ys.get(neighbor.y()));
                Direction direction = neighbor.x() == currentX
                        ? Direction.VERTICAL
                        : Direction.HORIZONTAL;
                double bendCost = incomingDirection == Direction.NONE
                        || incomingDirection == direction
                                ? 0
                                : BEND_PENALTY;
                double candidateCost = current.cost()
                        + currentValue.distance(neighborValue)
                        + bendCost
                        + congestionPenalty(currentValue, neighborValue, occupiedSegments, netId);
                int neighborPoint = pointId(neighbor.x(), neighbor.y(), xCount);
                int neighborState = stateId(neighborPoint, direction);

                if (candidateCost + EPSILON >= costs[neighborState]) {
                    continue;
                }
                costs[neighborState] = candidateCost;
                previous[neighborState] = current.stateId();
                double priority = candidateCost + heuristic(
                        neighborValue.getX(),
                        neighborValue.getY(),
                        xs.get(endX),
                        ys.get(endY));
                open.add(new SearchEntry(neighborState, candidateCost, priority));
            }
        }

        if (finalState < 0) {
            return List.of();
        }

        List<Point2D> reversed = new ArrayList<>();
        int cursor = finalState;
        while (cursor >= 0) {
            int point = cursor / 3;
            int xIndex = point % xCount;
            int yIndex = point / xCount;
            Point2D value = new Point2D(xs.get(xIndex), ys.get(yIndex));
            if (reversed.isEmpty() || !reversed.get(reversed.size() - 1).equals(value)) {
                reversed.add(value);
            }
            cursor = previous[cursor];
        }
        Collections.reverse(reversed);
        return simplify(reversed);
    }

    private static double congestionPenalty(
            Point2D start,
            Point2D end,
            List<OccupiedSegment> occupiedSegments,
            long netId) {

        boolean horizontal = Math.abs(start.getY() - end.getY()) <= EPSILON;
        double penalty = 0;
        for (OccupiedSegment occupied : occupiedSegments) {
            if (horizontal && occupied.isHorizontal()) {
                double overlap = intervalOverlap(
                        start.getX(), end.getX(), occupied.start().getX(), occupied.end().getX());
                if (overlap <= EPSILON) {
                    continue;
                }
                double separation = Math.abs(start.getY() - occupied.start().getY());
                if (separation <= EPSILON) {
                    penalty += OVERLAP_PENALTY + overlap * 20;
                } else if (separation < WIRE_LANE_SPACING) {
                    penalty += (WIRE_LANE_SPACING - separation) * 10;
                }
            } else if (!horizontal && occupied.isVertical()) {
                double overlap = intervalOverlap(
                        start.getY(), end.getY(), occupied.start().getY(), occupied.end().getY());
                if (overlap <= EPSILON) {
                    continue;
                }
                double separation = Math.abs(start.getX() - occupied.start().getX());
                if (separation <= EPSILON) {
                    penalty += OVERLAP_PENALTY + overlap * 20;
                } else if (separation < WIRE_LANE_SPACING) {
                    penalty += (WIRE_LANE_SPACING - separation) * 10;
                }
            } else if (perpendicularIntersection(start, end, occupied.start(), occupied.end()) != null
                    && occupied.netId() != netId) {
                penalty += CROSSING_PENALTY;
            }
        }
        return penalty;
    }

    private static Point2D perpendicularIntersection(
            Point2D firstStart,
            Point2D firstEnd,
            Point2D secondStart,
            Point2D secondEnd) {

        boolean firstHorizontal = Math.abs(firstStart.getY() - firstEnd.getY()) <= EPSILON;
        boolean secondHorizontal = Math.abs(secondStart.getY() - secondEnd.getY()) <= EPSILON;
        if (firstHorizontal == secondHorizontal) {
            return null;
        }

        Point2D horizontalStart = firstHorizontal ? firstStart : secondStart;
        Point2D horizontalEnd = firstHorizontal ? firstEnd : secondEnd;
        Point2D verticalStart = firstHorizontal ? secondStart : firstStart;
        Point2D verticalEnd = firstHorizontal ? secondEnd : firstEnd;
        double x = verticalStart.getX();
        double y = horizontalStart.getY();
        if (strictlyBetween(x, horizontalStart.getX(), horizontalEnd.getX())
                && strictlyBetween(y, verticalStart.getY(), verticalEnd.getY())) {
            return new Point2D(x, y);
        }
        return null;
    }

    private static List<GridIndex> visibleNeighbors(
            int x,
            int y,
            List<Double> xs,
            List<Double> ys,
            boolean[][] valid,
            List<Obstacle> obstacles) {

        List<GridIndex> neighbors = new ArrayList<>(4);
        addVisibleHorizontalNeighbor(neighbors, x, y, -1, xs, ys, valid, obstacles);
        addVisibleHorizontalNeighbor(neighbors, x, y, 1, xs, ys, valid, obstacles);
        addVisibleVerticalNeighbor(neighbors, x, y, -1, xs, ys, valid, obstacles);
        addVisibleVerticalNeighbor(neighbors, x, y, 1, xs, ys, valid, obstacles);
        return neighbors;
    }

    private static void addVisibleHorizontalNeighbor(
            List<GridIndex> neighbors,
            int x,
            int y,
            int step,
            List<Double> xs,
            List<Double> ys,
            boolean[][] valid,
            List<Obstacle> obstacles) {

        Point2D start = new Point2D(xs.get(x), ys.get(y));
        for (int candidateX = x + step;
                candidateX >= 0 && candidateX < xs.size();
                candidateX += step) {
            if (!valid[y][candidateX]) {
                continue;
            }
            Point2D candidate = new Point2D(xs.get(candidateX), ys.get(y));
            if (segmentClear(start, candidate, obstacles)) {
                neighbors.add(new GridIndex(candidateX, y));
            }
            return;
        }
    }

    private static void addVisibleVerticalNeighbor(
            List<GridIndex> neighbors,
            int x,
            int y,
            int step,
            List<Double> xs,
            List<Double> ys,
            boolean[][] valid,
            List<Obstacle> obstacles) {

        Point2D start = new Point2D(xs.get(x), ys.get(y));
        for (int candidateY = y + step;
                candidateY >= 0 && candidateY < ys.size();
                candidateY += step) {
            if (!valid[candidateY][x]) {
                continue;
            }
            Point2D candidate = new Point2D(xs.get(x), ys.get(candidateY));
            if (segmentClear(start, candidate, obstacles)) {
                neighbors.add(new GridIndex(x, candidateY));
            }
            return;
        }
    }

    private static List<Point2D> fallbackRoute(
            Point2D start,
            Point2D end,
            List<Obstacle> obstacles) {

        Point2D horizontalCorner = new Point2D(end.getX(), start.getY());
        if (segmentClear(start, horizontalCorner, obstacles)
                && segmentClear(horizontalCorner, end, obstacles)) {
            return simplify(List.of(start, horizontalCorner, end));
        }

        Point2D verticalCorner = new Point2D(start.getX(), end.getY());
        if (segmentClear(start, verticalCorner, obstacles)
                && segmentClear(verticalCorner, end, obstacles)) {
            return simplify(List.of(start, verticalCorner, end));
        }

        double upperChannel = obstacles.stream()
                .mapToDouble(Obstacle::top)
                .min()
                .orElse(Math.min(start.getY(), end.getY())) - OUTER_CHANNEL_PADDING;
        double lowerChannel = obstacles.stream()
                .mapToDouble(Obstacle::bottom)
                .max()
                .orElse(Math.max(start.getY(), end.getY())) + OUTER_CHANNEL_PADDING;
        double selectedChannel = routeLengthThroughY(start, end, upperChannel)
                <= routeLengthThroughY(start, end, lowerChannel)
                        ? upperChannel
                        : lowerChannel;
        return simplify(List.of(
                start,
                new Point2D(start.getX(), selectedChannel),
                new Point2D(end.getX(), selectedChannel),
                end));
    }

    private static double routeLengthThroughY(Point2D start, Point2D end, double y) {
        return Math.abs(start.getY() - y)
                + Math.abs(start.getX() - end.getX())
                + Math.abs(end.getY() - y);
    }

    private static boolean segmentClear(
            Point2D start,
            Point2D end,
            List<Obstacle> obstacles) {

        if (Math.abs(start.getY() - end.getY()) <= EPSILON) {
            double y = start.getY();
            double minimumX = Math.min(start.getX(), end.getX());
            double maximumX = Math.max(start.getX(), end.getX());
            return obstacles.stream().noneMatch(obstacle ->
                    y > obstacle.top() + EPSILON
                            && y < obstacle.bottom() - EPSILON
                            && maximumX > obstacle.left() + EPSILON
                            && minimumX < obstacle.right() - EPSILON);
        }

        if (Math.abs(start.getX() - end.getX()) <= EPSILON) {
            double x = start.getX();
            double minimumY = Math.min(start.getY(), end.getY());
            double maximumY = Math.max(start.getY(), end.getY());
            return obstacles.stream().noneMatch(obstacle ->
                    x > obstacle.left() + EPSILON
                            && x < obstacle.right() - EPSILON
                            && maximumY > obstacle.top() + EPSILON
                            && minimumY < obstacle.bottom() - EPSILON);
        }
        return false;
    }

    private static Obstacle obstacleFor(CircuitNode node) {
        javafx.geometry.Bounds bounds = node.getBoundsInParent();
        return new Obstacle(
                bounds.getMinX() - COMPONENT_CLEARANCE,
                bounds.getMinY() - COMPONENT_CLEARANCE,
                bounds.getMaxX() + COMPONENT_CLEARANCE,
                bounds.getMaxY() + COMPONENT_CLEARANCE);
    }

    private static Point2D leadPoint(Point2D pin, CircuitNode.PinSide side) {
        return switch (side) {
            case LEFT -> pin.add(-PIN_LEAD_LENGTH, 0);
            case RIGHT -> pin.add(PIN_LEAD_LENGTH, 0);
            case TOP -> pin.add(0, -PIN_LEAD_LENGTH);
            case BOTTOM -> pin.add(0, PIN_LEAD_LENGTH);
        };
    }

    private static void appendWithoutDuplicate(List<Point2D> target, List<Point2D> addition) {
        for (Point2D point : addition) {
            if (target.isEmpty() || target.get(target.size() - 1).distance(point) > EPSILON) {
                target.add(point);
            }
        }
    }

    public static List<Point2D> simplify(List<Point2D> rawPoints) {
        List<Point2D> points = new ArrayList<>();
        for (Point2D point : rawPoints) {
            if (point == null) {
                continue;
            }
            if (!points.isEmpty() && points.get(points.size() - 1).distance(point) <= EPSILON) {
                continue;
            }
            while (points.size() >= 2
                    && isCollinear(
                            points.get(points.size() - 2),
                            points.get(points.size() - 1),
                            point)) {
                points.remove(points.size() - 1);
            }
            points.add(point);
        }
        return List.copyOf(points);
    }

    private static boolean isCollinear(Point2D first, Point2D middle, Point2D last) {
        boolean sameX = Math.abs(first.getX() - middle.getX()) <= EPSILON
                && Math.abs(middle.getX() - last.getX()) <= EPSILON;
        boolean sameY = Math.abs(first.getY() - middle.getY()) <= EPSILON
                && Math.abs(middle.getY() - last.getY()) <= EPSILON;
        return sameX || sameY;
    }

    private static double intervalOverlap(
            double firstStart,
            double firstEnd,
            double secondStart,
            double secondEnd) {

        double firstMinimum = Math.min(firstStart, firstEnd);
        double firstMaximum = Math.max(firstStart, firstEnd);
        double secondMinimum = Math.min(secondStart, secondEnd);
        double secondMaximum = Math.max(secondStart, secondEnd);
        return Math.max(0, Math.min(firstMaximum, secondMaximum)
                - Math.max(firstMinimum, secondMinimum));
    }

    private static boolean strictlyBetween(double value, double first, double second) {
        double minimum = Math.min(first, second) + EPSILON;
        double maximum = Math.max(first, second) - EPSILON;
        return value > minimum && value < maximum;
    }

    private static int pointId(int x, int y, int xCount) {
        return y * xCount + x;
    }

    private static int stateId(int pointId, Direction direction) {
        return pointId * 3 + direction.index();
    }

    private static double heuristic(double x, double y, double endX, double endY) {
        return Math.abs(endX - x) + Math.abs(endY - y);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private enum Direction {
        NONE(0),
        HORIZONTAL(1),
        VERTICAL(2);

        private final int index;

        Direction(int index) {
            this.index = index;
        }

        int index() {
            return index;
        }

        static Direction fromIndex(int index) {
            return switch (index) {
                case 1 -> HORIZONTAL;
                case 2 -> VERTICAL;
                default -> NONE;
            };
        }
    }

    private record Obstacle(double left, double top, double right, double bottom) {
        boolean containsInterior(Point2D point) {
            return point.getX() > left + EPSILON
                    && point.getX() < right - EPSILON
                    && point.getY() > top + EPSILON
                    && point.getY() < bottom - EPSILON;
        }
    }

    private record GridIndex(int x, int y) {
    }

    private record SearchEntry(int stateId, double cost, double priority) {
    }
}
