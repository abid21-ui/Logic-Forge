package com.logicforge.model;

import java.util.ArrayList;
import java.util.List;

/** Types of components available in the LogicForge palette. */
public enum ComponentType {
    SWITCH(
            "Input Switch",
            "SW",
            "A mechanical-style toggle source. Click it to switch between logic 0 and logic 1.",
            Category.INPUT_OUTPUT),
    LOGIC_INPUT(
            "Logic Input",
            "0/1",
            "A compact digital input source that displays its current 0 or 1 state. Click it to toggle.",
            Category.INPUT_OUTPUT),
    VCC(
            "VCC",
            "VCC",
            "A permanent logic-high source that continuously outputs 1.",
            Category.INPUT_OUTPUT),
    GROUND(
            "Ground",
            "GND",
            "A permanent logic-low source that continuously outputs 0.",
            Category.INPUT_OUTPUT),
    JUNCTION(
            "Junction",
            "\u2022",
            "A pass-through connection point for clean branches and custom-component ports.",
            Category.INPUT_OUTPUT),
    AND(
            "AND Gate",
            "AND",
            "Outputs 1 only when every input is 1. Configurable from 2 to 4 inputs.",
            Category.LOGIC_GATE),
    NAND(
            "NAND Gate",
            "NAND",
            "Outputs the inverse of AND: 0 only when every input is 1. Configurable from 2 to 4 inputs.",
            Category.LOGIC_GATE),
    OR(
            "OR Gate",
            "OR",
            "Outputs 1 when at least one input is 1. Configurable from 2 to 4 inputs.",
            Category.LOGIC_GATE),
    NOR(
            "NOR Gate",
            "NOR",
            "Outputs the inverse of OR: 1 only when every input is 0. Configurable from 2 to 4 inputs.",
            Category.LOGIC_GATE),
    NOT(
            "NOT Gate",
            "NOT",
            "Inverts its input signal.",
            Category.LOGIC_GATE),
    XOR(
            "XOR Gate",
            "XOR",
            "Outputs 1 when an odd number of inputs are 1. Configurable from 2 to 4 inputs.",
            Category.LOGIC_GATE),
    BULB(
            "Output Bulb",
            "BULB",
            "A visual lamp output that glows when its input is logic 1.",
            Category.INPUT_OUTPUT),
    LOGIC_OUTPUT(
            "Logic Output",
            "0/1",
            "A compact digital output indicator that displays 0 or 1 and lights up for logic 1.",
            Category.INPUT_OUTPUT),
    SEVEN_SEGMENT_DISPLAY(
            "Seven-Segment Display",
            "7-SEG",
            "An active-high LED display with independently controlled A through G segment inputs.",
            Category.INPUT_OUTPUT),
    MULTIPLEXER(
            "Multiplexer",
            "MUX",
            "Routes one of 2, 4, 8, or 16 data inputs to a single output using 1 to 4 select bits.",
            Category.COMBINATIONAL),
    DECODER(
            "Decoder",
            "DEC",
            "Converts a 1 to 4-bit binary input into one of 2 to 16 active output lines.",
            Category.COMBINATIONAL),
    ENCODER(
            "Priority Encoder",
            "ENC",
            "Encodes one of 2 to 16 input lines into a 1 to 4-bit binary output. Highest active input has priority.",
            Category.COMBINATIONAL),
    BCD_TO_7SEGMENT(
            "BCD to 7-Segment Decoder",
            "BCD→7SEG",
            "Converts a 4-bit binary-coded decimal value (0–9) into active-high A through G segment outputs.",
            Category.COMBINATIONAL),
    HALF_ADDER(
            "Half Adder",
            "HA",
            "Adds two 1 to 4-bit values without carry-in and produces SUM and CARRY outputs.",
            Category.COMBINATIONAL),
    FULL_ADDER(
            "Full Adder",
            "ADD",
            "Adds two 1 to 4-bit values plus carry-in and produces a ripple-carry sum.",
            Category.COMBINATIONAL),
    COMPARATOR(
            "Magnitude Comparator",
            "CMP",
            "Compares two unsigned 1 to 4-bit values and reports greater, equal, or less.",
            Category.COMBINATIONAL),
    DEMULTIPLEXER(
            "Demultiplexer",
            "DEMUX",
            "Routes one input to one of 2, 4, 8, or 16 outputs using 1 to 4 select bits.",
            Category.COMBINATIONAL),
    CLOCK(
            "Clock",
            "CLK",
            "Generates a periodic square-wave signal while the simulation is running.",
            Category.SEQUENTIAL),
    SR_FLIP_FLOP(
            "SR Flip-Flop",
            "SR FF",
            "Rising-edge-triggered SR flip-flop with Q and Q̅ outputs.",
            Category.SEQUENTIAL),
    JK_FLIP_FLOP(
            "JK Flip-Flop",
            "JK FF",
            "Rising-edge-triggered JK flip-flop with Q and Q̅ outputs.",
            Category.SEQUENTIAL),
    D_FLIP_FLOP(
            "D Flip-Flop",
            "D FF",
            "Rising-edge-triggered D flip-flop with Q and Q̅ outputs.",
            Category.SEQUENTIAL),
    T_FLIP_FLOP(
            "T Flip-Flop",
            "T FF",
            "Rising-edge-triggered T flip-flop with Q and Q̅ outputs.",
            Category.SEQUENTIAL),
    COUNTER(
            "Binary Counter",
            "COUNT",
            "A rising-edge 1 to 4-bit up-counter with an active-high reset.",
            Category.SEQUENTIAL),
    SHIFT_REGISTER(
            "Shift Register",
            "SHIFT",
            "A 1 to 4-bit serial-in, parallel-out shift register with an active-high reset.",
            Category.SEQUENTIAL),
    INTEGRATED_CIRCUIT(
            "Integrated Circuit",
            "IC",
            "A physical 74HC integrated circuit with numbered DIP pins and explicit power connections.",
            Category.INTEGRATED_CIRCUIT),
    CUSTOM(
            "Custom Component",
            "BLOCK",
            "A reusable component created by abstracting a complete LogicForge subcircuit.",
            Category.CUSTOM);

    public enum Category {
        INPUT_OUTPUT,
        LOGIC_GATE,
        COMBINATIONAL,
        SEQUENTIAL,
        INTEGRATED_CIRCUIT,
        CUSTOM
    }

    private final String displayName;
    private final String symbol;
    private final String description;
    private final Category category;

    ComponentType(String displayName, String symbol, String description, Category category) {
        this.displayName = displayName;
        this.symbol = symbol;
        this.description = description;
        this.category = category;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getDescription() {
        return description;
    }

    public Category getCategory() {
        return category;
    }

    public boolean isToggleSource() {
        return this == SWITCH || this == LOGIC_INPUT;
    }

    public boolean isConstantSource() {
        return this == VCC || this == GROUND;
    }

    public boolean isClock() {
        return this == CLOCK;
    }

    public boolean isFlipFlop() {
        return this == SR_FLIP_FLOP
                || this == JK_FLIP_FLOP
                || this == D_FLIP_FLOP
                || this == T_FLIP_FLOP;
    }

    public boolean isClockedStateBlock() {
        return this == COUNTER || this == SHIFT_REGISTER;
    }

    public boolean isSequentialBoundary() {
        return isFlipFlop()
                || isClockedStateBlock()
                || isClock()
                || isToggleSource()
                || isConstantSource();
    }

    public boolean isConfigurable() {
        return isVariableInputGate()
                || this == MULTIPLEXER
                || this == DECODER
                || this == ENCODER
                || this == HALF_ADDER
                || this == FULL_ADDER
                || this == COMPARATOR
                || this == DEMULTIPLEXER
                || this == COUNTER
                || this == SHIFT_REGISTER
                || this == CLOCK;
    }

    public boolean isVariableInputGate() {
        return this == AND
                || this == NAND
                || this == OR
                || this == NOR
                || this == XOR;
    }

    public boolean canBeSource(ComponentConfig config) {
        return outputCount(config) > 0;
    }

    public boolean canBeTarget(ComponentConfig config) {
        return inputCount(config) > 0;
    }

    public int inputCount(ComponentConfig config) {
        int bits = config.bitWidth();
        return switch (this) {
            case SWITCH, LOGIC_INPUT, VCC, GROUND, CLOCK -> 0;
            case JUNCTION -> 1;
            case AND, NAND, OR, NOR, XOR -> bits;
            case NOT, BULB, LOGIC_OUTPUT -> 1;
            case SEVEN_SEGMENT_DISPLAY -> 7;
            case MULTIPLEXER -> (1 << bits) + bits;
            case DECODER -> bits;
            case ENCODER -> 1 << bits;
            case BCD_TO_7SEGMENT -> 4;
            case HALF_ADDER -> bits * 2;
            case FULL_ADDER -> bits * 2 + 1;
            case COMPARATOR -> bits * 2;
            case DEMULTIPLEXER -> bits + 1;
            case SR_FLIP_FLOP, JK_FLIP_FLOP -> 5;
            case D_FLIP_FLOP, T_FLIP_FLOP -> 4;
            case COUNTER -> 2;
            case SHIFT_REGISTER -> 3;
            case INTEGRATED_CIRCUIT -> throw new IllegalStateException(
                    "Integrated-circuit pin counts come from its definition");
            case CUSTOM -> throw new IllegalStateException(
                    "Custom-component pin counts come from its definition");
        };
    }

    public int outputCount(ComponentConfig config) {
        int bits = config.bitWidth();
        return switch (this) {
            case BULB, LOGIC_OUTPUT, SEVEN_SEGMENT_DISPLAY -> 0;
            case DECODER -> 1 << bits;
            case ENCODER -> bits;
            case BCD_TO_7SEGMENT -> 7;
            case HALF_ADDER -> bits + 1;
            case FULL_ADDER -> bits + 1;
            case COMPARATOR -> 3;
            case DEMULTIPLEXER -> 1 << bits;
            case JUNCTION -> 1;
            case SR_FLIP_FLOP, JK_FLIP_FLOP, D_FLIP_FLOP, T_FLIP_FLOP -> 2;
            case COUNTER, SHIFT_REGISTER -> bits;
            case INTEGRATED_CIRCUIT -> throw new IllegalStateException(
                    "Integrated-circuit pin counts come from its definition");
            case CUSTOM -> throw new IllegalStateException(
                    "Custom-component pin counts come from its definition");
            default -> 1;
        };
    }

    public List<String> inputNames(ComponentConfig config) {
        int bits = config.bitWidth();
        List<String> names = new ArrayList<>();
        switch (this) {
            case SWITCH, LOGIC_INPUT, VCC, GROUND, CLOCK -> { }
            case JUNCTION -> names.add("IN");
            case AND, NAND, OR, NOR, XOR -> {
                for (int index = 0; index < bits; index++) {
                    names.add(String.valueOf((char) ('A' + index)));
                }
            }
            case NOT, BULB, LOGIC_OUTPUT -> names.add("IN");
            case SEVEN_SEGMENT_DISPLAY -> {
                for (char segment = 'A'; segment <= 'G'; segment++) {
                    names.add(String.valueOf(segment));
                }
            }
            case MULTIPLEXER -> {
                for (int index = 0; index < (1 << bits); index++) {
                    names.add("X" + index);
                }
                for (int index = 0; index < bits; index++) {
                    names.add("S" + index);
                }
            }
            case DECODER -> {
                for (int index = 0; index < bits; index++) {
                    names.add("X" + index);
                }
            }
            case ENCODER -> {
                for (int index = 0; index < (1 << bits); index++) {
                    names.add("X" + index);
                }
            }
            case BCD_TO_7SEGMENT -> {
                names.add("B0");
                names.add("B1");
                names.add("B2");
                names.add("B3");
            }
            case HALF_ADDER, FULL_ADDER, COMPARATOR -> {
                for (int bit = 0; bit < bits; bit++) {
                    names.add("A" + bit);
                }
                for (int bit = 0; bit < bits; bit++) {
                    names.add("B" + bit);
                }
                if (this == FULL_ADDER) {
                    names.add("CIN");
                }
            }
            case DEMULTIPLEXER -> {
                names.add("X");
                for (int bit = 0; bit < bits; bit++) {
                    names.add("S" + bit);
                }
            }
            case SR_FLIP_FLOP -> {
                names.add("S");
                names.add("R");
                names.add("CLK");
                names.add("/PRE");
                names.add("/CLR");
            }
            case JK_FLIP_FLOP -> {
                names.add("J");
                names.add("K");
                names.add("CLK");
                names.add("/PRE");
                names.add("/CLR");
            }
            case D_FLIP_FLOP -> {
                names.add("D");
                names.add("CLK");
                names.add("/PRE");
                names.add("/CLR");
            }
            case T_FLIP_FLOP -> {
                names.add("T");
                names.add("CLK");
                names.add("/PRE");
                names.add("/CLR");
            }
            case COUNTER -> {
                names.add("CLK");
                names.add("RESET");
            }
            case SHIFT_REGISTER -> {
                names.add("DATA");
                names.add("CLK");
                names.add("RESET");
            }
            case INTEGRATED_CIRCUIT -> throw new IllegalStateException(
                    "Integrated-circuit pin names come from its definition");
            case CUSTOM -> throw new IllegalStateException(
                    "Custom-component pin names come from its definition");
        }
        return List.copyOf(names);
    }

    public List<String> outputNames(ComponentConfig config) {
        int bits = config.bitWidth();
        List<String> names = new ArrayList<>();
        switch (this) {
            case BULB, LOGIC_OUTPUT, SEVEN_SEGMENT_DISPLAY -> { }
            case DECODER -> {
                for (int index = 0; index < (1 << bits); index++) {
                    names.add("Y" + index);
                }
            }
            case ENCODER -> {
                for (int index = 0; index < bits; index++) {
                    names.add("Y" + index);
                }
            }
            case BCD_TO_7SEGMENT -> {
                for (char segment = 'A'; segment <= 'G'; segment++) {
                    names.add(String.valueOf(segment));
                }
            }
            case HALF_ADDER, FULL_ADDER -> {
                for (int bit = 0; bit < bits; bit++) {
                    names.add("S" + bit);
                }
                names.add("COUT");
            }
            case COMPARATOR -> {
                names.add("A>B");
                names.add("A=B");
                names.add("A<B");
            }
            case DEMULTIPLEXER -> {
                for (int index = 0; index < (1 << bits); index++) {
                    names.add("Y" + index);
                }
            }
            case COUNTER, SHIFT_REGISTER -> {
                for (int bit = 0; bit < bits; bit++) {
                    names.add("Q" + bit);
                }
            }
            case SR_FLIP_FLOP, JK_FLIP_FLOP, D_FLIP_FLOP, T_FLIP_FLOP -> {
                names.add("Q");
                names.add("Q̅");
            }
            case VCC -> names.add("VCC");
            case GROUND -> names.add("GND");
            case JUNCTION -> names.add("Y");
            case INTEGRATED_CIRCUIT -> throw new IllegalStateException(
                    "Integrated-circuit pin names come from its definition");
            case CUSTOM -> throw new IllegalStateException(
                    "Custom-component pin names come from its definition");
            default -> names.add("Y");
        }
        return List.copyOf(names);
    }

    public String configurationSummary(ComponentConfig config) {
        int bits = config.bitWidth();
        return switch (this) {
            case MULTIPLEXER -> (1 << bits) + ":1 multiplexer";
            case DECODER -> bits + "-to-" + (1 << bits) + " decoder";
            case ENCODER -> (1 << bits) + "-to-" + bits + " priority encoder";
            case BCD_TO_7SEGMENT -> "4-bit BCD to active-high A–G";
            case HALF_ADDER -> bits + "-bit A + B without carry-in";
            case FULL_ADDER -> bits + "-bit A + B + carry-in";
            case COMPARATOR -> bits + "-bit unsigned magnitude comparator";
            case DEMULTIPLEXER -> "1-to-" + (1 << bits) + " demultiplexer";
            case CLOCK -> trimFrequency(config.clockFrequencyHz()) + " Hz";
            case VCC -> "Permanent logic 1 source";
            case GROUND -> "Permanent logic 0 source";
            case JUNCTION -> "1-input pass-through and fan-out point";
            case AND, NAND, OR, NOR, XOR -> bits + "-input " + symbol + " gate";
            case SR_FLIP_FLOP, JK_FLIP_FLOP, D_FLIP_FLOP, T_FLIP_FLOP -> "Rising-edge triggered • async /PRE and /CLR";
            case COUNTER -> bits + "-bit rising-edge up-counter • active-high RESET";
            case SHIFT_REGISTER -> bits + "-bit serial-in/parallel-out • active-high RESET";
            case SEVEN_SEGMENT_DISPLAY -> "7 active-high segment inputs";
            case INTEGRATED_CIRCUIT -> "Definition-driven physical DIP package";
            case CUSTOM -> "User-defined reusable subcircuit";
            default -> "Standard 1-bit component";
        };
    }

    public String defaultLabelPrefix() {
        return switch (this) {
            case SWITCH -> "SW";
            case LOGIC_INPUT -> "IN";
            case VCC -> "VCC";
            case GROUND -> "GND";
            case JUNCTION -> "J";
            case BULB -> "LAMP";
            case LOGIC_OUTPUT -> "OUT";
            case SEVEN_SEGMENT_DISPLAY -> "7SEG";
            case AND -> "AND";
            case NAND -> "NAND";
            case OR -> "OR";
            case NOR -> "NOR";
            case NOT -> "NOT";
            case XOR -> "XOR";
            case MULTIPLEXER -> "MUX";
            case DECODER -> "DEC";
            case ENCODER -> "ENC";
            case BCD_TO_7SEGMENT -> "BCD7";
            case HALF_ADDER -> "HA";
            case FULL_ADDER -> "ADD";
            case COMPARATOR -> "CMP";
            case DEMULTIPLEXER -> "DEMUX";
            case CLOCK -> "CLK";
            case SR_FLIP_FLOP -> "SRFF";
            case JK_FLIP_FLOP -> "JKFF";
            case D_FLIP_FLOP -> "DFF";
            case T_FLIP_FLOP -> "TFF";
            case COUNTER -> "CTR";
            case SHIFT_REGISTER -> "SHIFT";
            case INTEGRATED_CIRCUIT -> "IC";
            case CUSTOM -> "BLOCK";
        };
    }

    public int clockInputIndex(ComponentConfig config) {
        return switch (this) {
            case SR_FLIP_FLOP, JK_FLIP_FLOP -> 2;
            case D_FLIP_FLOP, T_FLIP_FLOP -> 1;
            case COUNTER -> 0;
            case SHIFT_REGISTER -> 1;
            default -> -1;
        };
    }

    public int resetInputIndex(ComponentConfig config) {
        return switch (this) {
            case COUNTER -> 1;
            case SHIFT_REGISTER -> 2;
            default -> -1;
        };
    }

    public int presetInputIndex(ComponentConfig config) {
        return isFlipFlop() ? inputCount(config) - 2 : -1;
    }

    public int clearInputIndex(ComponentConfig config) {
        return isFlipFlop() ? inputCount(config) - 1 : -1;
    }

    private static String trimFrequency(double frequency) {
        if (Math.rint(frequency) == frequency) {
            return Integer.toString((int) frequency);
        }
        return Double.toString(frequency);
    }
}
