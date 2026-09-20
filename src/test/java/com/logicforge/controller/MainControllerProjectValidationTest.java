package com.logicforge.controller;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.logicforge.persistence.LogicForgeProjectFile;
import com.logicforge.persistence.LogicForgeProjectFile.ComponentData;
import com.logicforge.model.ComponentConfig;
import com.logicforge.model.ComponentOrientation;
import com.logicforge.model.ComponentType;
import com.logicforge.model.CustomComponentDefinition;
import com.logicforge.model.CustomComponentDefinition.InternalComponent;
import com.logicforge.model.CustomComponentDefinition.InternalWire;
import com.logicforge.model.CustomComponentDefinition.Port;

class MainControllerProjectValidationTest {

    @Test
    void acceptsEmptySegmentStateForOrdinaryComponents() throws Exception {
        ComponentData input = component("LOGIC_INPUT", List.of(true), List.of());
        LogicForgeProjectFile project = projectWith(input);

        assertDoesNotThrow(() -> invokeProjectConversion(project));
    }

    @Test
    void requiresSevenSegmentStateOnlyForTheDisplay() throws Exception {
        ComponentData display = component(
                "SEVEN_SEGMENT_DISPLAY",
                List.of(),
                List.of(true, false));
        LogicForgeProjectFile project = projectWith(display);

        assertThrows(IllegalArgumentException.class, () -> invokeProjectConversion(project));
    }

    @Test
    void validatesEmbeddedCustomComponentDefinitions() throws Exception {
        CustomComponentDefinition definition = inverterDefinition();
        ComponentData custom = new ComponentData(
                1,
                "CUSTOM",
                1,
                1.0,
                100,
                100,
                "EAST",
                "INV1",
                List.of(false),
                false,
                List.of(),
                false,
                false,
                definition.id(),
                "");
        LogicForgeProjectFile valid = new LogicForgeProjectFile(
                LogicForgeProjectFile.CURRENT_FORMAT_VERSION,
                List.of(custom),
                List.of(),
                2,
                1,
                Map.of("CUSTOM", 1),
                List.of(definition));
        LogicForgeProjectFile missingDefinition = new LogicForgeProjectFile(
                LogicForgeProjectFile.CURRENT_FORMAT_VERSION,
                List.of(custom),
                List.of(),
                2,
                1,
                Map.of("CUSTOM", 1),
                List.of());

        assertDoesNotThrow(() -> invokeProjectConversion(valid));
        assertThrows(IllegalArgumentException.class,
                () -> invokeProjectConversion(missingDefinition));
    }

    @Test
    void schematicControllerRejectsBreadboardProjects() {
        LogicForgeProjectFile project = new LogicForgeProjectFile(
                LogicForgeProjectFile.CURRENT_FORMAT_VERSION,
                List.of(), List.of(), 1, 1, Map.of(), List.of(),
                "BREADBOARD", LogicForgeProjectFile.BreadboardData.empty());

        assertThrows(IllegalArgumentException.class, () -> invokeProjectConversion(project));
    }

    private static void invokeProjectConversion(LogicForgeProjectFile project) throws Throwable {
        Method method = MainController.class.getDeclaredMethod(
                "fromProjectFile", LogicForgeProjectFile.class);
        method.setAccessible(true);
        try {
            method.invoke(null, project);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }

    private static ComponentData component(
            String type,
            List<Boolean> outputs,
            List<Boolean> segments) {

        return new ComponentData(
                1,
                type,
                1,
                1.0,
                100,
                100,
                "EAST",
                "TEST1",
                outputs,
                !outputs.isEmpty() && outputs.get(0),
                segments,
                false,
                false,
                "",
                "");
    }

    private static LogicForgeProjectFile projectWith(ComponentData component) {
        return new LogicForgeProjectFile(
                LogicForgeProjectFile.CURRENT_FORMAT_VERSION,
                List.of(component),
                List.of(),
                2,
                1,
                Map.of(component.type(), 1),
                List.of());
    }

    private static CustomComponentDefinition inverterDefinition() {
        InternalComponent input = internal(1, ComponentType.LOGIC_INPUT, List.of(false));
        InternalComponent inverter = internal(2, ComponentType.NOT, List.of(false));
        InternalComponent output = internal(3, ComponentType.LOGIC_OUTPUT, List.of());
        return new CustomComponentDefinition(
                "validation-inverter",
                "Validation Inverter",
                "VINV",
                List.of(new Port("A", 1)),
                List.of(new Port("Y", 3)),
                List.of(input, inverter, output),
                List.of(
                        new InternalWire(1, 1, 0, 2, 0),
                        new InternalWire(2, 2, 0, 3, 0)));
    }

    private static InternalComponent internal(
            int id,
            ComponentType type,
            List<Boolean> outputs) {

        return new InternalComponent(
                id,
                type,
                ComponentConfig.defaults(),
                "",
                id * 100,
                100,
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
