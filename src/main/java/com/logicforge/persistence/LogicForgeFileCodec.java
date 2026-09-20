package com.logicforge.persistence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.logicforge.model.ComponentConfig;
import com.logicforge.model.ComponentOrientation;
import com.logicforge.model.ComponentType;
import com.logicforge.model.CustomComponentDefinition;
import com.logicforge.model.CustomComponentDefinition.InternalComponent;
import com.logicforge.model.CustomComponentDefinition.InternalWire;
import com.logicforge.model.CustomComponentDefinition.Port;
import com.logicforge.persistence.LogicForgeProjectFile.ComponentData;
import com.logicforge.persistence.LogicForgeProjectFile.BreadboardData;
import com.logicforge.persistence.LogicForgeProjectFile.BreadboardIcData;
import com.logicforge.persistence.LogicForgeProjectFile.BreadboardJumperData;
import com.logicforge.persistence.LogicForgeProjectFile.PointData;
import com.logicforge.persistence.LogicForgeProjectFile.WireData;

/**
 * Reads and writes LogicForge .lgf files. The extension is custom, while the
 * on-disk representation remains ordinary UTF-8 JSON for robustness and
 * future migration.
 */
public final class LogicForgeFileCodec {

    private static final ObjectMapper JSON = new ObjectMapper(
            com.fasterxml.jackson.core.JsonFactory.builder()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .build())
            .enable(SerializationFeature.INDENT_OUTPUT);

    private LogicForgeFileCodec() { }

    public static void write(Path path, LogicForgeProjectFile project) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("path is required");
        }
        if (project == null) {
            throw new IllegalArgumentException("project is required");
        }
        Path absolute = path.toAbsolutePath();
        Path parent = absolute.getParent();
        if (parent == null) {
            parent = Path.of(".").toAbsolutePath();
        }
        Path temporary = Files.createTempFile(parent, ".logicforge-", ".tmp");
        try {
            Files.writeString(
                    temporary,
                    toJson(project) + System.lineSeparator(),
                    StandardCharsets.UTF_8);
            try {
                Files.move(
                        temporary,
                        absolute,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static LogicForgeProjectFile read(Path path) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("path is required");
        }
        String json = Files.readString(path, StandardCharsets.UTF_8);
        return fromJson(json);
    }

    public static String toJson(LogicForgeProjectFile project) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("format", "LogicForge Circuit");
        root.put("formatVersion", project.formatVersion());

        List<Object> components = new ArrayList<>();
        for (ComponentData component : project.components()) {
            Map<String, Object> object = new LinkedHashMap<>();
            object.put("id", component.componentId());
            object.put("type", component.type());

            Map<String, Object> config = new LinkedHashMap<>();
            config.put("bitWidth", component.bitWidth());
            config.put("clockFrequencyHz", component.clockFrequencyHz());
            object.put("config", config);

            object.put("x", component.x());
            object.put("y", component.y());
            object.put("orientation", component.orientation());
            object.put("label", component.label());
            object.put("outputStates", component.outputStates());
            object.put("displayState", component.displayState());
            object.put("sevenSegmentStates", component.sevenSegmentStates());
            object.put("storedState", component.storedState());
            object.put("previousClockState", component.previousClockState());
            object.put("customDefinitionId", component.customDefinitionId());
            object.put("customState", component.customState());
            object.put("icDefinitionId", component.icDefinitionId());
            object.put("icState", component.icState());
            components.add(object);
        }
        root.put("components", components);

        List<Object> wires = new ArrayList<>();
        for (WireData wire : project.wires()) {
            Map<String, Object> object = new LinkedHashMap<>();
            object.put("id", wire.connectionId());
            object.put("sourceComponentId", wire.sourceComponentId());
            object.put("sourceOutputIndex", wire.sourceOutputIndex());
            object.put("targetComponentId", wire.targetComponentId());
            object.put("targetInputIndex", wire.targetInputIndex());

            List<Object> anchors = new ArrayList<>();
            for (PointData point : wire.bendAnchors()) {
                Map<String, Object> anchor = new LinkedHashMap<>();
                anchor.put("x", point.x());
                anchor.put("y", point.y());
                anchors.add(anchor);
            }
            object.put("bendAnchors", anchors);
            wires.add(object);
        }
        root.put("wires", wires);
        root.put("nextComponentId", project.nextComponentId());
        root.put("nextWireId", project.nextWireId());
        root.put("labelCounters", new LinkedHashMap<>(project.labelCounters()));
        root.put("customDefinitions", customDefinitionsToValues(project.customDefinitions()));
        root.put("workspaceMode", project.workspaceMode());
        root.put("breadboard", breadboardToValue(project.breadboard()));
        return writeJson(root);
    }

    public static LogicForgeProjectFile fromJson(String json) {
        Map<String, Object> root = readJsonObject(json);

        String format = requireString(root.get("format"), "format");
        if (!"LogicForge Circuit".equals(format)) {
            throw new IllegalArgumentException("Not a LogicForge circuit file");
        }

        int version = requireInt(root.get("formatVersion"), "formatVersion");
        if (version < 1 || version > LogicForgeProjectFile.CURRENT_FORMAT_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported .lgf format version " + version);
        }

        List<ComponentData> components = new ArrayList<>();
        for (Object raw : requireArray(root.get("components"), "components")) {
            Map<String, Object> object = requireObject(raw, "component");
            Map<String, Object> config = requireObject(object.get("config"), "component.config");
            components.add(new ComponentData(
                    requireInt(object.get("id"), "component.id"),
                    requireString(object.get("type"), "component.type"),
                    requireInt(config.get("bitWidth"), "component.config.bitWidth"),
                    requireDouble(config.get("clockFrequencyHz"), "component.config.clockFrequencyHz"),
                    requireDouble(object.get("x"), "component.x"),
                    requireDouble(object.get("y"), "component.y"),
                    requireString(object.get("orientation"), "component.orientation"),
                    optionalString(object.get("label")),
                    requireBooleanList(object.get("outputStates"), "component.outputStates"),
                    requireBoolean(object.get("displayState"), "component.displayState"),
                    requireBooleanList(object.get("sevenSegmentStates"), "component.sevenSegmentStates"),
                    requireBoolean(object.get("storedState"), "component.storedState"),
                    requireBoolean(object.get("previousClockState"), "component.previousClockState"),
                    optionalStringField(object, "customDefinitionId"),
                    optionalStringField(object, "customState"),
                    optionalStringField(object, "icDefinitionId"),
                    optionalStringField(object, "icState")));
        }

        List<WireData> wires = new ArrayList<>();
        for (Object raw : requireArray(root.get("wires"), "wires")) {
            Map<String, Object> object = requireObject(raw, "wire");
            List<PointData> points = new ArrayList<>();
            for (Object rawPoint : requireArray(object.get("bendAnchors"), "wire.bendAnchors")) {
                Map<String, Object> point = requireObject(rawPoint, "wire.bendAnchor");
                points.add(new PointData(
                        requireDouble(point.get("x"), "wire.bendAnchor.x"),
                        requireDouble(point.get("y"), "wire.bendAnchor.y")));
            }
            wires.add(new WireData(
                    requireInt(object.get("id"), "wire.id"),
                    requireInt(object.get("sourceComponentId"), "wire.sourceComponentId"),
                    requireInt(object.get("sourceOutputIndex"), "wire.sourceOutputIndex"),
                    requireInt(object.get("targetComponentId"), "wire.targetComponentId"),
                    requireInt(object.get("targetInputIndex"), "wire.targetInputIndex"),
                    points));
        }

        Map<String, Integer> counters = new LinkedHashMap<>();
        Map<String, Object> rawCounters = requireObject(root.get("labelCounters"), "labelCounters");
        for (Map.Entry<String, Object> entry : rawCounters.entrySet()) {
            counters.put(entry.getKey(), requireInt(entry.getValue(), "labelCounters." + entry.getKey()));
        }

        return new LogicForgeProjectFile(
                version,
                components,
                wires,
                requireInt(root.get("nextComponentId"), "nextComponentId"),
                requireInt(root.get("nextWireId"), "nextWireId"),
                counters,
                customDefinitionsFromValues(
                        root.getOrDefault("customDefinitions", List.of()),
                        "customDefinitions"),
                optionalStringField(root, "workspaceMode"),
                breadboardFromValue(root.get("breadboard")));
    }

    /** JSON representation used by the persistent per-user custom-component palette. */
    public static String customComponentLibraryToJson(List<CustomComponentDefinition> definitions) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("format", "LogicForge Custom Component Library");
        root.put("formatVersion", 1);
        root.put("definitions", customDefinitionsToValues(definitions));
        return writeJson(root);
    }

    public static List<CustomComponentDefinition> customComponentLibraryFromJson(String json) {
        Map<String, Object> root = readJsonObject(json);
        if (!"LogicForge Custom Component Library".equals(
                requireString(root.get("format"), "format"))) {
            throw new IllegalArgumentException("Not a LogicForge custom-component library");
        }
        if (requireInt(root.get("formatVersion"), "formatVersion") != 1) {
            throw new IllegalArgumentException("Unsupported custom-component library version");
        }
        return customDefinitionsFromValues(root.get("definitions"), "definitions");
    }

    private static List<Object> customDefinitionsToValues(
            List<CustomComponentDefinition> definitions) {

        List<Object> values = new ArrayList<>();
        for (CustomComponentDefinition definition : definitions == null
                ? List.<CustomComponentDefinition>of()
                : definitions) {
            Map<String, Object> object = new LinkedHashMap<>();
            object.put("id", definition.id());
            object.put("name", definition.name());
            object.put("symbol", definition.symbol());
            object.put("description", definition.description());
            object.put("inputPorts", portsToValues(definition.inputPorts()));
            object.put("outputPorts", portsToValues(definition.outputPorts()));

            List<Object> components = new ArrayList<>();
            for (InternalComponent component : definition.components()) {
                Map<String, Object> componentObject = new LinkedHashMap<>();
                componentObject.put("id", component.componentId());
                componentObject.put("type", component.type().name());
                Map<String, Object> config = new LinkedHashMap<>();
                config.put("bitWidth", component.config().bitWidth());
                config.put("clockFrequencyHz", component.config().clockFrequencyHz());
                componentObject.put("config", config);
                componentObject.put("customDefinitionId", component.customDefinitionId());
                componentObject.put("x", component.x());
                componentObject.put("y", component.y());
                componentObject.put("orientation", component.orientation().name());
                componentObject.put("label", component.label());
                componentObject.put("outputStates", component.outputStates());
                componentObject.put("displayState", component.displayState());
                componentObject.put("sevenSegmentStates", component.sevenSegmentStates());
                componentObject.put("storedState", component.storedState());
                componentObject.put("previousClockState", component.previousClockState());
                componentObject.put("customState", component.customState());
                components.add(componentObject);
            }
            object.put("components", components);

            List<Object> wires = new ArrayList<>();
            for (InternalWire wire : definition.wires()) {
                Map<String, Object> wireObject = new LinkedHashMap<>();
                wireObject.put("id", wire.connectionId());
                wireObject.put("sourceComponentId", wire.sourceComponentId());
                wireObject.put("sourceOutputIndex", wire.sourceOutputIndex());
                wireObject.put("targetComponentId", wire.targetComponentId());
                wireObject.put("targetInputIndex", wire.targetInputIndex());
                wires.add(wireObject);
            }
            object.put("wires", wires);
            values.add(object);
        }
        return values;
    }

    private static List<Object> portsToValues(List<Port> ports) {
        List<Object> values = new ArrayList<>();
        for (Port port : ports) {
            Map<String, Object> object = new LinkedHashMap<>();
            object.put("name", port.name());
            object.put("componentId", port.componentId());
            values.add(object);
        }
        return values;
    }

    private static List<CustomComponentDefinition> customDefinitionsFromValues(
            Object rawDefinitions,
            String field) {

        List<CustomComponentDefinition> definitions = new ArrayList<>();
        for (Object rawDefinition : requireArray(rawDefinitions, field)) {
            Map<String, Object> object = requireObject(rawDefinition, field + "[]");
            List<InternalComponent> components = new ArrayList<>();
            for (Object rawComponent : requireArray(
                    object.get("components"), field + "[].components")) {
                Map<String, Object> component = requireObject(
                        rawComponent, field + "[].component");
                Map<String, Object> config = requireObject(
                        component.get("config"), field + "[].component.config");
                ComponentType type;
                ComponentOrientation orientation;
                try {
                    type = ComponentType.valueOf(
                            requireString(component.get("type"), field + "[].component.type"));
                    orientation = ComponentOrientation.valueOf(requireString(
                            component.get("orientation"), field + "[].component.orientation"));
                } catch (IllegalArgumentException exception) {
                    throw new IllegalArgumentException("Unknown custom-component type or orientation", exception);
                }
                components.add(new InternalComponent(
                        requireInt(component.get("id"), field + "[].component.id"),
                        type,
                        new ComponentConfig(
                                requireInt(config.get("bitWidth"), field + "[].component.config.bitWidth"),
                                requireDouble(config.get("clockFrequencyHz"),
                                        field + "[].component.config.clockFrequencyHz")),
                        optionalStringField(component, "customDefinitionId"),
                        requireDouble(component.get("x"), field + "[].component.x"),
                        requireDouble(component.get("y"), field + "[].component.y"),
                        orientation,
                        optionalString(component.get("label")),
                        requireBooleanList(component.get("outputStates"),
                                field + "[].component.outputStates"),
                        requireBoolean(component.get("displayState"),
                                field + "[].component.displayState"),
                        requireBooleanList(component.get("sevenSegmentStates"),
                                field + "[].component.sevenSegmentStates"),
                        requireBoolean(component.get("storedState"),
                                field + "[].component.storedState"),
                        requireBoolean(component.get("previousClockState"),
                                field + "[].component.previousClockState"),
                        optionalStringField(component, "customState")));
            }

            List<InternalWire> wires = new ArrayList<>();
            for (Object rawWire : requireArray(object.get("wires"), field + "[].wires")) {
                Map<String, Object> wire = requireObject(rawWire, field + "[].wire");
                wires.add(new InternalWire(
                        requireInt(wire.get("id"), field + "[].wire.id"),
                        requireInt(wire.get("sourceComponentId"),
                                field + "[].wire.sourceComponentId"),
                        requireInt(wire.get("sourceOutputIndex"),
                                field + "[].wire.sourceOutputIndex"),
                        requireInt(wire.get("targetComponentId"),
                                field + "[].wire.targetComponentId"),
                        requireInt(wire.get("targetInputIndex"),
                                field + "[].wire.targetInputIndex")));
            }

            definitions.add(new CustomComponentDefinition(
                    requireString(object.get("id"), field + "[].id"),
                    requireString(object.get("name"), field + "[].name"),
                    requireString(object.get("symbol"), field + "[].symbol"),
                    optionalStringField(object, "description"),
                    portsFromValues(object.get("inputPorts"), field + "[].inputPorts"),
                    portsFromValues(object.get("outputPorts"), field + "[].outputPorts"),
                    components,
                    wires));
        }
        return List.copyOf(definitions);
    }

    private static List<Port> portsFromValues(Object rawPorts, String field) {
        List<Port> ports = new ArrayList<>();
        for (Object rawPort : requireArray(rawPorts, field)) {
            Map<String, Object> port = requireObject(rawPort, field + "[]");
            ports.add(new Port(
                    requireString(port.get("name"), field + "[].name"),
                    requireInt(port.get("componentId"), field + "[].componentId")));
        }
        return List.copyOf(ports);
    }

    private static Object breadboardToValue(BreadboardData data) {
        BreadboardData safe = data == null ? BreadboardData.empty() : data;
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("size", safe.size());
        value.put("nextIcId", safe.nextIcId());
        value.put("nextJumperId", safe.nextJumperId());
        value.put("switchStates", safe.switchStates());

        List<Object> integratedCircuits = new ArrayList<>();
        for (BreadboardIcData ic : safe.integratedCircuits()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", ic.id());
            item.put("definitionId", ic.definitionId());
            item.put("moduleIndex", ic.moduleIndex());
            item.put("firstRow", ic.firstRow());
            item.put("label", ic.label());
            item.put("state", ic.state());
            integratedCircuits.add(item);
        }
        value.put("integratedCircuits", integratedCircuits);

        List<Object> jumpers = new ArrayList<>();
        for (BreadboardJumperData jumper : safe.jumpers()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", jumper.id());
            item.put("startHoleId", jumper.startHoleId());
            item.put("endHoleId", jumper.endHoleId());
            item.put("color", jumper.color());
            jumpers.add(item);
        }
        value.put("jumpers", jumpers);
        return value;
    }

    private static BreadboardData breadboardFromValue(Object raw) {
        if (raw == null) {
            return BreadboardData.empty();
        }
        Map<String, Object> value = requireObject(raw, "breadboard");
        List<BreadboardIcData> integratedCircuits = new ArrayList<>();
        for (Object rawIc : requireArray(
                value.getOrDefault("integratedCircuits", List.of()),
                "breadboard.integratedCircuits")) {
            Map<String, Object> ic = requireObject(rawIc, "breadboard.integratedCircuits[]");
            integratedCircuits.add(new BreadboardIcData(
                    requireInt(ic.get("id"), "breadboard.integratedCircuits[].id"),
                    requireString(ic.get("definitionId"),
                            "breadboard.integratedCircuits[].definitionId"),
                    requireInt(ic.get("moduleIndex"),
                            "breadboard.integratedCircuits[].moduleIndex"),
                    requireInt(ic.get("firstRow"),
                            "breadboard.integratedCircuits[].firstRow"),
                    optionalStringField(ic, "label"),
                    optionalStringField(ic, "state")));
        }

        List<BreadboardJumperData> jumpers = new ArrayList<>();
        for (Object rawJumper : requireArray(
                value.getOrDefault("jumpers", List.of()), "breadboard.jumpers")) {
            Map<String, Object> jumper = requireObject(rawJumper, "breadboard.jumpers[]");
            jumpers.add(new BreadboardJumperData(
                    requireInt(jumper.get("id"), "breadboard.jumpers[].id"),
                    requireString(jumper.get("startHoleId"),
                            "breadboard.jumpers[].startHoleId"),
                    requireString(jumper.get("endHoleId"),
                            "breadboard.jumpers[].endHoleId"),
                    optionalStringField(jumper, "color")));
        }
        return new BreadboardData(
                optionalStringField(value, "size"),
                integratedCircuits,
                jumpers,
                optionalInt(value, "nextIcId", 1),
                optionalInt(value, "nextJumperId", 1),
                requireBooleanList(
                        value.getOrDefault("switchStates", List.of()),
                        "breadboard.switchStates"));
    }

    private static String writeJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Could not encode LogicForge JSON", exception);
        }
    }

    private static Map<String, Object> readJsonObject(String json) {
        try {
            Object parsed = JSON.readValue(
                    json == null ? "" : json,
                    new TypeReference<Map<String, Object>>() { });
            return requireObject(parsed, "root");
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "Invalid JSON: " + exception.getOriginalMessage(), exception);
        }
    }

    private static Map<String, Object> requireObject(Object value, String field) {
        if (!(value instanceof Map<?, ?> raw)) {
            throw new IllegalArgumentException(field + " must be a JSON object");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException(field + " contains a non-string key");
            }
            result.put(key, entry.getValue());
        }
        return result;
    }

    private static List<Object> requireArray(Object value, String field) {
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException(field + " must be a JSON array");
        }
        return new ArrayList<>(list);
    }

    private static String requireString(Object value, String field) {
        if (!(value instanceof String string)) {
            throw new IllegalArgumentException(field + " must be a string");
        }
        return string;
    }

    private static String optionalString(Object value) {
        return value == null ? "" : requireString(value, "label");
    }

    private static String optionalStringField(Map<String, Object> object, String key) {
        return object.containsKey(key) ? optionalString(object.get(key)) : "";
    }

    private static int optionalInt(Map<String, Object> object, String key, int fallback) {
        return object.containsKey(key) ? requireInt(object.get(key), key) : fallback;
    }

    private static int requireInt(Object value, String field) {
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(field + " must be a number");
        }
        double raw = number.doubleValue();
        if (!Double.isFinite(raw) || raw != Math.rint(raw)
                || raw < Integer.MIN_VALUE || raw > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        return (int) raw;
    }

    private static double requireDouble(Object value, String field) {
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(field + " must be a number");
        }
        double raw = number.doubleValue();
        if (!Double.isFinite(raw)) {
            throw new IllegalArgumentException(field + " must be finite");
        }
        return raw;
    }

    private static boolean requireBoolean(Object value, String field) {
        if (!(value instanceof Boolean bool)) {
            throw new IllegalArgumentException(field + " must be true or false");
        }
        return bool;
    }

    private static List<Boolean> requireBooleanList(Object value, String field) {
        List<Boolean> result = new ArrayList<>();
        for (Object item : requireArray(value, field)) {
            result.add(requireBoolean(item, field + "[]"));
        }
        return List.copyOf(result);
    }

}
