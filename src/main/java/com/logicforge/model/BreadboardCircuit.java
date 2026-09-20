package com.logicforge.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.logicforge.model.BreadboardLayout.Hole;
import com.logicforge.model.BreadboardLayout.HoleKind;
import com.logicforge.model.BreadboardLayout.IcPlacement;
import com.logicforge.persistence.LogicForgeProjectFile.BreadboardData;
import com.logicforge.persistence.LogicForgeProjectFile.BreadboardIcData;
import com.logicforge.persistence.LogicForgeProjectFile.BreadboardJumperData;

/**
 * Socket-level breadboard state and live two-state IC simulation. Terminal
 * strips conduct internally, jumpers join strips, and every physical socket is
 * exclusively occupied by either one component lead or one jumper endpoint.
 */
public final class BreadboardCircuit {

    private static final int MAX_SETTLE_PASSES = 64;

    private final BreadboardLayout layout;
    private final Map<Integer, PlacedIc> integratedCircuits = new LinkedHashMap<>();
    private final Map<Integer, Jumper> jumpers = new LinkedHashMap<>();
    private final Map<String, Occupant> occupancy = new HashMap<>();
    private final Map<String, Signal> signalsByGroup = new HashMap<>();
    private final Set<String> conflictingGroups = new LinkedHashSet<>();
    private final boolean[] switchStates =
            new boolean[BreadboardLayout.CONTROL_CHANNEL_COUNT];
    private int nextIcId = 1;
    private int nextJumperId = 1;

    public BreadboardCircuit(BreadboardLayout.Size size) {
        layout = new BreadboardLayout(size);
        evaluate();
    }

    public BreadboardLayout layout() {
        return layout;
    }

    public Collection<PlacedIc> integratedCircuits() {
        return List.copyOf(integratedCircuits.values());
    }

    public Collection<Jumper> jumpers() {
        return List.copyOf(jumpers.values());
    }

    public boolean isEmpty() {
        return integratedCircuits.isEmpty()
                && jumpers.isEmpty()
                && switchStates().stream().noneMatch(Boolean::booleanValue);
    }

    public boolean switchState(int channel) {
        return switchStates[checkedChannel(channel)];
    }

    public List<Boolean> switchStates() {
        List<Boolean> states = new ArrayList<>(switchStates.length);
        for (boolean state : switchStates) {
            states.add(state);
        }
        return List.copyOf(states);
    }

    public void setSwitchState(int channel, boolean high) {
        int checked = checkedChannel(channel);
        if (switchStates[checked] != high) {
            switchStates[checked] = high;
            evaluate();
        }
    }

    public Signal bulbSignal(int channel) {
        return signalAt(layout.bulbHoleId(checkedChannel(channel)));
    }

    public boolean isOccupied(String holeId) {
        return occupancy.containsKey(holeId);
    }

    public Occupant occupant(String holeId) {
        return occupancy.get(holeId);
    }

    public Signal signalAt(String holeId) {
        Hole hole = layout.hole(holeId);
        return hole == null
                ? Signal.FLOATING
                : signalsByGroup.getOrDefault(hole.conductiveGroup(), Signal.FLOATING);
    }

    public int conflictCount() {
        return conflictingGroups.size();
    }

    public Set<String> conflictingGroups() {
        return Set.copyOf(conflictingGroups);
    }

    public PlacementResult placeIc(IcDefinition definition, double centerX, double centerY) {
        Objects.requireNonNull(definition, "definition");
        IcPlacement placement = layout.nearestIcPlacement(
                centerX, centerY, definition.packageType().pinCount());
        String conflict = firstOccupiedHole(placement.pinHoleIds());
        if (conflict != null) {
            return PlacementResult.failure(conflict);
        }
        int id = nextIcId++;
        PlacedIc placed = new PlacedIc(
                id,
                definition,
                placement.module(),
                placement.firstRow(),
                definition.partNumber() + id,
                new IcRuntime(definition));
        integratedCircuits.put(id, placed);
        occupyIc(placed);
        evaluate();
        return PlacementResult.success(placed);
    }

    public boolean moveIc(int id, double centerX, double centerY) {
        PlacedIc placed = integratedCircuits.get(id);
        if (placed == null) {
            return false;
        }
        int oldModule = placed.moduleIndex();
        int oldFirstRow = placed.firstRow();
        releaseIc(placed);
        IcPlacement candidate = layout.nearestIcPlacement(
                centerX, centerY, placed.definition().packageType().pinCount());
        String conflict = firstOccupiedHole(candidate.pinHoleIds());
        if (conflict != null) {
            placed.moveTo(oldModule, oldFirstRow);
            occupyIc(placed);
            return false;
        }
        placed.moveTo(candidate.module(), candidate.firstRow());
        occupyIc(placed);
        evaluate();
        return true;
    }

    public boolean removeIc(int id) {
        PlacedIc removed = integratedCircuits.remove(id);
        if (removed == null) {
            return false;
        }
        releaseIc(removed);
        evaluate();
        return true;
    }

    public JumperResult addJumper(String startHoleId, String endHoleId) {
        Hole start = layout.hole(startHoleId);
        Hole end = layout.hole(endHoleId);
        if (start == null || end == null || start.id().equals(end.id())) {
            return JumperResult.failure("Choose two different breadboard sockets");
        }
        if (isOccupied(start.id()) || isOccupied(end.id())) {
            return JumperResult.failure("Each socket can hold only one lead or jumper end");
        }
        if (start.conductiveGroup().equals(end.conductiveGroup())) {
            return JumperResult.failure("Those sockets are already connected by the metal strip");
        }
        int id = nextJumperId++;
        Jumper jumper = new Jumper(id, start.id(), end.id(), jumperColor(id));
        jumpers.put(id, jumper);
        occupancy.put(start.id(), new Occupant(OccupantKind.JUMPER, id));
        occupancy.put(end.id(), new Occupant(OccupantKind.JUMPER, id));
        evaluate();
        return JumperResult.success(jumper);
    }

    public boolean removeJumper(int id) {
        Jumper jumper = jumpers.remove(id);
        if (jumper == null) {
            return false;
        }
        occupancy.remove(jumper.startHoleId());
        occupancy.remove(jumper.endHoleId());
        evaluate();
        return true;
    }

    public void clear() {
        integratedCircuits.clear();
        jumpers.clear();
        occupancy.clear();
        signalsByGroup.clear();
        conflictingGroups.clear();
        nextIcId = 1;
        nextJumperId = 1;
        Arrays.fill(switchStates, false);
        evaluate();
    }

    public void evaluate() {
        for (int pass = 0; pass < MAX_SETTLE_PASSES; pass++) {
            Map<String, Signal> currentSignals = resolveSignals();
            boolean changed = false;
            for (PlacedIc placed : integratedCircuits.values()) {
                IcDefinition definition = placed.definition();
                boolean[] inputs = new boolean[definition.inputCount()];
                boolean vccConnected = false;
                boolean gndConnected = false;
                List<IcDefinition.IcPin> inputPins = definition.inputPins();
                for (int index = 0; index < inputPins.size(); index++) {
                    IcDefinition.IcPin pin = inputPins.get(index);
                    Signal signal = signalForPhysicalPin(placed, pin.number(), currentSignals);
                    inputs[index] = signal == Signal.HIGH;
                    if (pin.role() == IcDefinition.IcPinRole.VCC) {
                        vccConnected = signal == Signal.HIGH;
                    } else if (pin.role() == IcDefinition.IcPinRole.GND) {
                        gndConnected = signal == Signal.LOW;
                    }
                }
                placed.runtime().setPowerConnections(vccConnected, gndConnected);
                boolean[] before = placed.runtime().outputs();
                placed.runtime().updateSequential(inputs);
                changed |= !Arrays.equals(before, placed.runtime().outputs());
            }
            if (!changed) {
                break;
            }
        }
        signalsByGroup.clear();
        signalsByGroup.putAll(resolveSignals());
    }

    public BreadboardData toData() {
        List<BreadboardIcData> icData = integratedCircuits.values().stream()
                .map(placed -> new BreadboardIcData(
                        placed.id(),
                        placed.definition().id(),
                        placed.moduleIndex(),
                        placed.firstRow(),
                        placed.label(),
                        placed.runtime().exportState()))
                .toList();
        List<BreadboardJumperData> jumperData = jumpers.values().stream()
                .map(jumper -> new BreadboardJumperData(
                        jumper.id(),
                        jumper.startHoleId(),
                        jumper.endHoleId(),
                        jumper.color()))
                .toList();
        return new BreadboardData(
                layout.size().name(), icData, jumperData,
                nextIcId, nextJumperId, switchStates());
    }

    public static BreadboardCircuit fromData(BreadboardData data) {
        BreadboardData safe = data == null ? BreadboardData.empty() : data;
        BreadboardCircuit circuit = new BreadboardCircuit(
                BreadboardLayout.Size.parse(safe.size()));
        for (int channel = 0; channel < BreadboardLayout.CONTROL_CHANNEL_COUNT; channel++) {
            circuit.switchStates[channel] = safe.switchStates().get(channel);
        }

        Set<Integer> icIds = new HashSet<>();
        for (BreadboardIcData saved : safe.integratedCircuits()) {
            if (saved.id() <= 0 || !icIds.add(saved.id())) {
                throw new IllegalArgumentException("Breadboard IC IDs must be unique and positive");
            }
            IcDefinition definition = IcCatalog.find(saved.definitionId());
            if (definition == null) {
                throw new IllegalArgumentException("Unknown breadboard IC " + saved.definitionId());
            }
            IcPlacement placement = circuit.layout.placement(
                    saved.moduleIndex(), saved.firstRow(), definition.packageType().pinCount());
            String conflict = circuit.firstOccupiedHole(placement.pinHoleIds());
            if (conflict != null) {
                throw new IllegalArgumentException("Two breadboard components occupy socket " + conflict);
            }
            IcRuntime runtime = new IcRuntime(definition);
            runtime.importState(saved.state());
            PlacedIc placed = new PlacedIc(
                    saved.id(), definition, saved.moduleIndex(), saved.firstRow(),
                    saved.label().isBlank() ? definition.partNumber() + saved.id() : saved.label(),
                    runtime);
            circuit.integratedCircuits.put(placed.id(), placed);
            circuit.occupyIc(placed);
        }

        Set<Integer> jumperIds = new HashSet<>();
        for (BreadboardJumperData saved : safe.jumpers()) {
            if (saved.id() <= 0 || !jumperIds.add(saved.id())) {
                throw new IllegalArgumentException("Breadboard jumper IDs must be unique and positive");
            }
            Hole start = circuit.layout.hole(saved.startHoleId());
            Hole end = circuit.layout.hole(saved.endHoleId());
            if (start == null || end == null || start.id().equals(end.id())) {
                throw new IllegalArgumentException("Breadboard jumper references an invalid socket");
            }
            if (start.conductiveGroup().equals(end.conductiveGroup())) {
                throw new IllegalArgumentException(
                        "Breadboard jumper duplicates an internal terminal connection");
            }
            if (circuit.isOccupied(start.id()) || circuit.isOccupied(end.id())) {
                throw new IllegalArgumentException("Breadboard socket has more than one occupant");
            }
            Jumper jumper = new Jumper(
                    saved.id(), start.id(), end.id(), saved.color());
            circuit.jumpers.put(jumper.id(), jumper);
            circuit.occupancy.put(start.id(), new Occupant(OccupantKind.JUMPER, jumper.id()));
            circuit.occupancy.put(end.id(), new Occupant(OccupantKind.JUMPER, jumper.id()));
        }
        circuit.nextIcId = Math.max(safe.nextIcId(), maxId(icIds) + 1);
        circuit.nextJumperId = Math.max(safe.nextJumperId(), maxId(jumperIds) + 1);
        circuit.evaluate();
        return circuit;
    }

    private Map<String, Signal> resolveSignals() {
        UnionFind groups = new UnionFind();
        for (Hole hole : layout.holes()) {
            groups.add(hole.conductiveGroup());
        }
        for (Jumper jumper : jumpers.values()) {
            Hole start = layout.hole(jumper.startHoleId());
            Hole end = layout.hole(jumper.endHoleId());
            groups.union(start.conductiveGroup(), end.conductiveGroup());
        }

        Map<String, Drivers> drivers = new HashMap<>();
        for (Hole hole : layout.holes()) {
            if (hole.kind() == HoleKind.VCC_SOURCE) {
                drivers.computeIfAbsent(groups.find(hole.conductiveGroup()), ignored -> new Drivers())
                        .high = true;
            } else if (hole.kind() == HoleKind.GND_SOURCE) {
                drivers.computeIfAbsent(groups.find(hole.conductiveGroup()), ignored -> new Drivers())
                        .low = true;
            } else if (hole.kind() == HoleKind.SWITCH_OUTPUT) {
                Drivers switchDriver = drivers.computeIfAbsent(
                        groups.find(hole.conductiveGroup()), ignored -> new Drivers());
                if (switchStates[hole.row()]) {
                    switchDriver.high = true;
                } else {
                    switchDriver.low = true;
                }
            }
        }
        for (PlacedIc placed : integratedCircuits.values()) {
            boolean[] outputs = placed.runtime().outputs();
            List<IcDefinition.IcPin> pins = placed.definition().outputPins();
            for (int index = 0; index < pins.size(); index++) {
                String holeId = layout.icPinHoleId(
                        placed.moduleIndex(), placed.firstRow(),
                        placed.definition().packageType().pinCount(), pins.get(index).number());
                Hole hole = layout.hole(holeId);
                Drivers netDrivers = drivers.computeIfAbsent(
                        groups.find(hole.conductiveGroup()), ignored -> new Drivers());
                if (outputs[index]) {
                    netDrivers.high = true;
                } else {
                    netDrivers.low = true;
                }
            }
        }

        conflictingGroups.clear();
        Map<String, Signal> resolved = new HashMap<>();
        for (Hole hole : layout.holes()) {
            String root = groups.find(hole.conductiveGroup());
            Drivers netDrivers = drivers.get(root);
            Signal signal = netDrivers == null
                    ? Signal.FLOATING
                    : netDrivers.high && netDrivers.low
                            ? Signal.CONFLICT
                            : netDrivers.high ? Signal.HIGH : Signal.LOW;
            resolved.put(hole.conductiveGroup(), signal);
            if (signal == Signal.CONFLICT) {
                conflictingGroups.add(root);
            }
        }
        return resolved;
    }

    private Signal signalForPhysicalPin(
            PlacedIc placed,
            int physicalPinNumber,
            Map<String, Signal> currentSignals) {

        String holeId = layout.icPinHoleId(
                placed.moduleIndex(), placed.firstRow(),
                placed.definition().packageType().pinCount(), physicalPinNumber);
        Hole hole = layout.hole(holeId);
        return currentSignals.getOrDefault(hole.conductiveGroup(), Signal.FLOATING);
    }

    private void occupyIc(PlacedIc placed) {
        int pinCount = placed.definition().packageType().pinCount();
        for (int pin = 1; pin <= pinCount; pin++) {
            occupancy.put(layout.icPinHoleId(
                    placed.moduleIndex(), placed.firstRow(), pinCount, pin),
                    new Occupant(OccupantKind.IC_PIN, placed.id()));
        }
    }

    private void releaseIc(PlacedIc placed) {
        int pinCount = placed.definition().packageType().pinCount();
        for (int pin = 1; pin <= pinCount; pin++) {
            occupancy.remove(layout.icPinHoleId(
                    placed.moduleIndex(), placed.firstRow(), pinCount, pin));
        }
    }

    private String firstOccupiedHole(List<String> holeIds) {
        for (String holeId : holeIds) {
            if (occupancy.containsKey(holeId)) {
                return holeId;
            }
        }
        return null;
    }

    private static int maxId(Set<Integer> ids) {
        return ids.stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    private static int checkedChannel(int channel) {
        if (channel < 0 || channel >= BreadboardLayout.CONTROL_CHANNEL_COUNT) {
            throw new IllegalArgumentException("Control channel must be between 0 and 15");
        }
        return channel;
    }

    private static String jumperColor(int id) {
        return switch (Math.floorMod(id, 10)) {
            case 0 -> "YELLOW";
            case 1 -> "CYAN";
            case 2 -> "MAGENTA";
            case 3 -> "ORANGE";
            case 4 -> "GREEN";
            case 5 -> "RED";
            case 6 -> "BLUE";
            case 7 -> "PURPLE";
            case 8 -> "TEAL";
            default -> "WHITE";
        };
    }

    public enum Signal {
        FLOATING,
        LOW,
        HIGH,
        CONFLICT
    }

    public enum OccupantKind {
        IC_PIN,
        JUMPER
    }

    public record Occupant(OccupantKind kind, int itemId) { }

    public record Jumper(
            int id,
            String startHoleId,
            String endHoleId,
            String color) { }

    public static final class PlacedIc {
        private final int id;
        private final IcDefinition definition;
        private final String label;
        private final IcRuntime runtime;
        private int moduleIndex;
        private int firstRow;

        private PlacedIc(
                int id,
                IcDefinition definition,
                int moduleIndex,
                int firstRow,
                String label,
                IcRuntime runtime) {

            this.id = id;
            this.definition = definition;
            this.moduleIndex = moduleIndex;
            this.firstRow = firstRow;
            this.label = label;
            this.runtime = runtime;
        }

        public int id() {
            return id;
        }

        public IcDefinition definition() {
            return definition;
        }

        public int moduleIndex() {
            return moduleIndex;
        }

        public int firstRow() {
            return firstRow;
        }

        public String label() {
            return label;
        }

        public IcRuntime runtime() {
            return runtime;
        }

        public boolean isPowered() {
            return runtime.isPowered();
        }

        private void moveTo(int moduleIndex, int firstRow) {
            this.moduleIndex = moduleIndex;
            this.firstRow = firstRow;
        }
    }

    public record PlacementResult(PlacedIc placedIc, String occupiedHoleId) {
        public static PlacementResult success(PlacedIc placedIc) {
            return new PlacementResult(placedIc, "");
        }

        public static PlacementResult failure(String occupiedHoleId) {
            return new PlacementResult(null, occupiedHoleId);
        }

        public boolean succeeded() {
            return placedIc != null;
        }
    }

    public record JumperResult(Jumper jumper, String error) {
        public static JumperResult success(Jumper jumper) {
            return new JumperResult(jumper, "");
        }

        public static JumperResult failure(String error) {
            return new JumperResult(null, error);
        }

        public boolean succeeded() {
            return jumper != null;
        }
    }

    private static final class Drivers {
        private boolean low;
        private boolean high;
    }

    private static final class UnionFind {
        private final Map<String, String> parent = new HashMap<>();

        void add(String value) {
            parent.putIfAbsent(value, value);
        }

        String find(String value) {
            String current = parent.get(value);
            if (current == null) {
                add(value);
                return value;
            }
            if (!current.equals(value)) {
                parent.put(value, find(current));
            }
            return parent.get(value);
        }

        void union(String first, String second) {
            String firstRoot = find(first);
            String secondRoot = find(second);
            if (!firstRoot.equals(secondRoot)) {
                parent.put(secondRoot, firstRoot);
            }
        }
    }
}
