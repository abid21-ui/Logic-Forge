package com.logicforge.model;

import static com.logicforge.model.IcDefinition.IcFunction.ADDER_4_BIT;
import static com.logicforge.model.IcDefinition.IcFunction.COMPARATOR_4_BIT;
import static com.logicforge.model.IcDefinition.IcFunction.DECODER_3_TO_8;
import static com.logicforge.model.IcDefinition.IcFunction.DUAL_D_FLIP_FLOP;
import static com.logicforge.model.IcDefinition.IcFunction.HEX_INVERTER;
import static com.logicforge.model.IcDefinition.IcFunction.MULTIPLEXER_8_TO_1;
import static com.logicforge.model.IcDefinition.IcFunction.QUAD_AND;
import static com.logicforge.model.IcDefinition.IcFunction.QUAD_MULTIPLEXER_2_TO_1;
import static com.logicforge.model.IcDefinition.IcFunction.QUAD_NAND;
import static com.logicforge.model.IcDefinition.IcFunction.QUAD_NOR;
import static com.logicforge.model.IcDefinition.IcFunction.QUAD_OR;
import static com.logicforge.model.IcDefinition.IcFunction.QUAD_XOR;
import static com.logicforge.model.IcDefinition.IcPackage.DIP_14;
import static com.logicforge.model.IcDefinition.IcPackage.DIP_16;
import static com.logicforge.model.IcDefinition.IcPinRole.GND;
import static com.logicforge.model.IcDefinition.IcPinRole.INPUT;
import static com.logicforge.model.IcDefinition.IcPinRole.OUTPUT;
import static com.logicforge.model.IcDefinition.IcPinRole.VCC;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.logicforge.model.IcDefinition.IcFunction;
import com.logicforge.model.IcDefinition.IcPackage;
import com.logicforge.model.IcDefinition.IcPin;
import com.logicforge.model.IcDefinition.IcPinRole;

/** Built-in, datasheet-verified 74HC DIP catalogue. */
public final class IcCatalog {

    private static final Map<String, IcDefinition> DEFINITIONS = buildDefinitions();

    private IcCatalog() { }

    public static List<IcDefinition> all() {
        return List.copyOf(DEFINITIONS.values());
    }

    public static IcDefinition find(String id) {
        if (id == null) {
            return null;
        }
        return DEFINITIONS.get(id.strip().toUpperCase(Locale.ROOT));
    }

    public static IcDefinition require(String id) {
        IcDefinition definition = find(id);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown integrated circuit " + id);
        }
        return definition;
    }

    private static Map<String, IcDefinition> buildDefinitions() {
        Map<String, IcDefinition> definitions = new LinkedHashMap<>();

        add(definitions, quadGate("74HC00", "Quad 2-input NAND gate", QUAD_NAND));
        add(definitions, definition("74HC02", "Quad 2-input NOR gate", DIP_14, QUAD_NOR,
                p(1, "1Y", OUTPUT), p(2, "1A", INPUT), p(3, "1B", INPUT),
                p(4, "2Y", OUTPUT), p(5, "2A", INPUT), p(6, "2B", INPUT), p(7, "GND", GND),
                p(8, "3A", INPUT), p(9, "3B", INPUT), p(10, "3Y", OUTPUT),
                p(11, "4A", INPUT), p(12, "4B", INPUT), p(13, "4Y", OUTPUT), p(14, "VCC", VCC)));
        add(definitions, definition("74HC04", "Hex inverter", DIP_14, HEX_INVERTER,
                p(1, "1A", INPUT), p(2, "1Y", OUTPUT), p(3, "2A", INPUT), p(4, "2Y", OUTPUT),
                p(5, "3A", INPUT), p(6, "3Y", OUTPUT), p(7, "GND", GND),
                p(8, "4Y", OUTPUT), p(9, "4A", INPUT), p(10, "5Y", OUTPUT), p(11, "5A", INPUT),
                p(12, "6Y", OUTPUT), p(13, "6A", INPUT), p(14, "VCC", VCC)));
        add(definitions, quadGate("74HC08", "Quad 2-input AND gate", QUAD_AND));
        add(definitions, quadGate("74HC32", "Quad 2-input OR gate", QUAD_OR));
        add(definitions, quadGate("74HC86", "Quad 2-input XOR gate", QUAD_XOR));

        add(definitions, definition("74HC138", "3-to-8 decoder / demultiplexer", DIP_16, DECODER_3_TO_8,
                p(1, "A", INPUT), p(2, "B", INPUT), p(3, "C", INPUT),
                p(4, "/G2A", INPUT), p(5, "/G2B", INPUT), p(6, "G1", INPUT),
                p(7, "/Y7", OUTPUT), p(8, "GND", GND), p(9, "/Y6", OUTPUT),
                p(10, "/Y5", OUTPUT), p(11, "/Y4", OUTPUT), p(12, "/Y3", OUTPUT),
                p(13, "/Y2", OUTPUT), p(14, "/Y1", OUTPUT), p(15, "/Y0", OUTPUT), p(16, "VCC", VCC)));

        add(definitions, definition("74HC151", "8-to-1 data selector / multiplexer", DIP_16, MULTIPLEXER_8_TO_1,
                p(1, "D3", INPUT), p(2, "D2", INPUT), p(3, "D1", INPUT), p(4, "D0", INPUT),
                p(5, "Y", OUTPUT), p(6, "W", OUTPUT), p(7, "/G", INPUT), p(8, "GND", GND),
                p(9, "C", INPUT), p(10, "B", INPUT), p(11, "A", INPUT), p(12, "D7", INPUT),
                p(13, "D6", INPUT), p(14, "D5", INPUT), p(15, "D4", INPUT), p(16, "VCC", VCC)));

        add(definitions, definition("74HC157", "Quad 2-to-1 multiplexer", DIP_16, QUAD_MULTIPLEXER_2_TO_1,
                p(1, "A/B", INPUT), p(2, "1A", INPUT), p(3, "1B", INPUT), p(4, "1Y", OUTPUT),
                p(5, "2A", INPUT), p(6, "2B", INPUT), p(7, "2Y", OUTPUT), p(8, "GND", GND),
                p(9, "3Y", OUTPUT), p(10, "3B", INPUT), p(11, "3A", INPUT), p(12, "4Y", OUTPUT),
                p(13, "4B", INPUT), p(14, "4A", INPUT), p(15, "/G", INPUT), p(16, "VCC", VCC)));

        add(definitions, definition("74HC283", "4-bit binary full adder", DIP_16, ADDER_4_BIT,
                p(1, "S2", OUTPUT), p(2, "B2", INPUT), p(3, "A2", INPUT), p(4, "S1", OUTPUT),
                p(5, "A1", INPUT), p(6, "B1", INPUT), p(7, "C0", INPUT), p(8, "GND", GND),
                p(9, "C4", OUTPUT), p(10, "S4", OUTPUT), p(11, "B4", INPUT), p(12, "A4", INPUT),
                p(13, "S3", OUTPUT), p(14, "A3", INPUT), p(15, "B3", INPUT), p(16, "VCC", VCC)));

        add(definitions, definition("74HC85", "4-bit magnitude comparator", DIP_16, COMPARATOR_4_BIT,
                p(1, "B3", INPUT), p(2, "IA<B", INPUT), p(3, "IA=B", INPUT), p(4, "IA>B", INPUT),
                p(5, "QA>B", OUTPUT), p(6, "QA=B", OUTPUT), p(7, "QA<B", OUTPUT), p(8, "GND", GND),
                p(9, "B0", INPUT), p(10, "A0", INPUT), p(11, "B1", INPUT), p(12, "A1", INPUT),
                p(13, "A2", INPUT), p(14, "B2", INPUT), p(15, "A3", INPUT), p(16, "VCC", VCC)));

        add(definitions, definition("74HC74", "Dual positive-edge D flip-flop", DIP_14, DUAL_D_FLIP_FLOP,
                p(1, "1/CLR", INPUT), p(2, "1D", INPUT), p(3, "1CLK", INPUT), p(4, "1/PRE", INPUT),
                p(5, "1Q", OUTPUT), p(6, "1/Q", OUTPUT), p(7, "GND", GND),
                p(8, "2/Q", OUTPUT), p(9, "2Q", OUTPUT), p(10, "2/PRE", INPUT),
                p(11, "2CLK", INPUT), p(12, "2D", INPUT), p(13, "2/CLR", INPUT), p(14, "VCC", VCC)));

        return Map.copyOf(definitions);
    }

    private static IcDefinition quadGate(String partNumber, String name, IcFunction function) {
        return definition(partNumber, name, DIP_14, function,
                p(1, "1A", INPUT), p(2, "1B", INPUT), p(3, "1Y", OUTPUT),
                p(4, "2A", INPUT), p(5, "2B", INPUT), p(6, "2Y", OUTPUT), p(7, "GND", GND),
                p(8, "3Y", OUTPUT), p(9, "3A", INPUT), p(10, "3B", INPUT),
                p(11, "4Y", OUTPUT), p(12, "4A", INPUT), p(13, "4B", INPUT), p(14, "VCC", VCC));
    }

    private static IcDefinition definition(
            String partNumber,
            String name,
            IcPackage packageType,
            IcFunction function,
            IcPin... pins) {

        return new IcDefinition(
                partNumber,
                partNumber,
                name,
                "74HC",
                packageType,
                function,
                List.of(pins));
    }

    private static IcPin p(int number, String name, IcPinRole role) {
        return new IcPin(number, name, role);
    }

    private static void add(Map<String, IcDefinition> definitions, IcDefinition definition) {
        definitions.put(definition.id(), definition);
    }
}
