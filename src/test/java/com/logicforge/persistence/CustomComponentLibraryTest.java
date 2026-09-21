package com.logicforge.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.logicforge.model.ComponentConfig;
import com.logicforge.model.ComponentOrientation;
import com.logicforge.model.ComponentType;
import com.logicforge.model.CustomComponentDefinition;
import com.logicforge.model.CustomComponentDefinition.InternalComponent;
import com.logicforge.model.CustomComponentDefinition.InternalWire;
import com.logicforge.model.CustomComponentDefinition.Port;

class CustomComponentLibraryTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void persistsAReusableDefinition() throws Exception {
        CustomComponentDefinition definition = inverterBlock();
        CustomComponentLibrary library = new CustomComponentLibrary(
                temporaryDirectory.resolve("custom-components.json"));

        library.save(List.of(definition));

        assertEquals(definition, library.load().get(definition.id()));
    }

    @Test
    void savingAnEmptyCollectionDeletesAllPaletteDefinitions() throws Exception {
        CustomComponentLibrary library = new CustomComponentLibrary(
                temporaryDirectory.resolve("custom-components.json"));
        library.save(List.of(inverterBlock()));

        library.save(List.of());

        assertTrue(library.load().isEmpty());
    }

    @Test
    void opensDefinitionsSavedBeforeDescriptionsWereAdded() {
        CustomComponentDefinition definition = inverterBlock();
        String json = LogicForgeFileCodec.customComponentLibraryToJson(List.of(definition));
        // Simulate an old file regardless of the formatter's whitespace.
        String legacyJson = json.replaceAll(
                "(?m)\\s*\"description\"\\s*:\\s*\"Outputs the complement of its input\\.\",?", "");

        CustomComponentDefinition restored =
                LogicForgeFileCodec.customComponentLibraryFromJson(legacyJson).get(0);

        assertEquals("", restored.description());
    }

    private static CustomComponentDefinition inverterBlock() {
        InternalComponent input = component(1, ComponentType.LOGIC_INPUT, List.of(false));
        InternalComponent inverter = component(2, ComponentType.NOT, List.of(false));
        InternalComponent output = component(3, ComponentType.LOGIC_OUTPUT, List.of());
        return new CustomComponentDefinition(
                "inverter-block",
                "Inverter Block",
                "INV",
                "Outputs the complement of its input.",
                List.of(new Port("A", 1)),
                List.of(new Port("Y", 3)),
                List.of(input, inverter, output),
                List.of(
                        new InternalWire(1, 1, 0, 2, 0),
                        new InternalWire(2, 2, 0, 3, 0)));
    }

    private static InternalComponent component(
            int id,
            ComponentType type,
            List<Boolean> outputs) {

        return new InternalComponent(
                id,
                type,
                ComponentConfig.defaults(),
                "",
                id * 80,
                80,
                ComponentOrientation.EAST,
                type.defaultLabelPrefix() + id,
                outputs,
                false,
                List.of(),
                false,
                false,
                "");
    }
}
