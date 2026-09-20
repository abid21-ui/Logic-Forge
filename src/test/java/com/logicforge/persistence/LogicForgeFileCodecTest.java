package com.logicforge.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.logicforge.persistence.LogicForgeProjectFile.ComponentData;
import com.logicforge.persistence.LogicForgeProjectFile.BreadboardData;
import com.logicforge.persistence.LogicForgeProjectFile.BreadboardIcData;
import com.logicforge.persistence.LogicForgeProjectFile.BreadboardJumperData;
import com.logicforge.persistence.LogicForgeProjectFile.PointData;
import com.logicforge.persistence.LogicForgeProjectFile.WireData;

class LogicForgeFileCodecTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void roundTripsOrdinaryAndSevenSegmentComponents() throws Exception {
        LogicForgeProjectFile project = sampleProject();

        Path projectFile = temporaryDirectory.resolve("round-trip.lgf");
        LogicForgeFileCodec.write(projectFile, project);
        LogicForgeProjectFile restored = LogicForgeFileCodec.read(projectFile);

        assertEquals(project, restored);
        assertEquals(List.of(), restored.components().get(0).sevenSegmentStates());
        assertEquals(7, restored.components().get(1).sevenSegmentStates().size());
    }

    @Test
    void rejectsWrongFormatMarker() {
        String json = LogicForgeFileCodec.toJson(sampleProject())
                .replace("LogicForge Circuit", "Different Format");

        assertThrows(IllegalArgumentException.class, () -> LogicForgeFileCodec.fromJson(json));
    }

    @Test
    void rejectsDuplicateJsonObjectKeys() {
        String json = "{\"format\":\"LogicForge Circuit\",\"format\":\"LogicForge Circuit\"}";

        assertThrows(IllegalArgumentException.class, () -> LogicForgeFileCodec.fromJson(json));
    }

    @Test
    void opensLegacyVersionOneProjectsWithoutCustomComponents() {
        String json = """
                {
                  "format": "LogicForge Circuit",
                  "formatVersion": 1,
                  "components": [
                    {
                      "id": 1,
                      "type": "LOGIC_INPUT",
                      "config": {"bitWidth": 1, "clockFrequencyHz": 1.0},
                      "x": 100,
                      "y": 100,
                      "orientation": "EAST",
                      "label": "IN1",
                      "outputStates": [true],
                      "displayState": true,
                      "sevenSegmentStates": [],
                      "storedState": false,
                      "previousClockState": false
                    }
                  ],
                  "wires": [],
                  "nextComponentId": 2,
                  "nextWireId": 1,
                  "labelCounters": {"LOGIC_INPUT": 1}
                }
                """;

        LogicForgeProjectFile restored = LogicForgeFileCodec.fromJson(json);

        assertEquals(1, restored.formatVersion());
        assertEquals(List.of(), restored.customDefinitions());
        assertEquals("", restored.components().get(0).customDefinitionId());
        assertEquals("", restored.components().get(0).icDefinitionId());
        assertEquals("SCHEMATIC", restored.workspaceMode());
        assertEquals(BreadboardData.empty(), restored.breadboard());
    }

    @Test
    void roundTripsVersionFourIcIdentityAndState() {
        ComponentData ic = new ComponentData(
                1,
                "INTEGRATED_CIRCUIT",
                1,
                1.0,
                300,
                240,
                "EAST",
                "74HC74-1",
                List.of(false, true, false, true),
                false,
                List.of(),
                false,
                false,
                "",
                "",
                "74HC74",
                "1010");
        LogicForgeProjectFile project = new LogicForgeProjectFile(
                LogicForgeProjectFile.CURRENT_FORMAT_VERSION,
                List.of(ic),
                List.of(),
                2,
                1,
                Map.of("INTEGRATED_CIRCUIT", 1),
                List.of());

        LogicForgeProjectFile restored = LogicForgeFileCodec.fromJson(
                LogicForgeFileCodec.toJson(project));

        assertEquals(project, restored);
        assertEquals("74HC74", restored.components().get(0).icDefinitionId());
        assertEquals("1010", restored.components().get(0).icState());
    }

    @Test
    void roundTripsVersionSixBreadboardWorkspaceAndSwitchStates() {
        List<Boolean> switches = new java.util.ArrayList<>(java.util.Collections.nCopies(16, false));
        switches.set(3, true);
        BreadboardData breadboard = new BreadboardData(
                "FULL",
                List.of(new BreadboardIcData(4, "74HC00", 0, 8, "NAND4", "")),
                List.of(new BreadboardJumperData(7, "M0R0C3", "M0R1C0", "CYAN")),
                5,
                8,
                switches);
        LogicForgeProjectFile project = new LogicForgeProjectFile(
                LogicForgeProjectFile.CURRENT_FORMAT_VERSION,
                List.of(), List.of(), 1, 1, Map.of(), List.of(),
                "BREADBOARD", breadboard);

        LogicForgeProjectFile restored = LogicForgeFileCodec.fromJson(
                LogicForgeFileCodec.toJson(project));

        assertEquals(project, restored);
        assertEquals("BREADBOARD", restored.workspaceMode());
        assertEquals(breadboard, restored.breadboard());
        assertEquals(true, restored.breadboard().switchStates().get(3));
    }

    private static LogicForgeProjectFile sampleProject() {
        ComponentData input = new ComponentData(
                1,
                "LOGIC_INPUT",
                1,
                1.0,
                120,
                200,
                "EAST",
                "IN1",
                List.of(true),
                true,
                List.of(),
                false,
                false,
                "",
                "");
        ComponentData display = new ComponentData(
                2,
                "SEVEN_SEGMENT_DISPLAY",
                1,
                1.0,
                520,
                160,
                "SOUTH",
                "7SEG1",
                List.of(),
                true,
                List.of(true, true, true, true, true, true, false),
                false,
                false,
                "",
                "");
        WireData wire = new WireData(
                1,
                1,
                0,
                2,
                0,
                List.of(new PointData(360, 240)));

        return new LogicForgeProjectFile(
                LogicForgeProjectFile.CURRENT_FORMAT_VERSION,
                List.of(input, display),
                List.of(wire),
                3,
                2,
                Map.of("LOGIC_INPUT", 1, "SEVEN_SEGMENT_DISPLAY", 1),
                List.of());
    }
}
