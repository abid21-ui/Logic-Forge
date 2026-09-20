# LogicForge — Visual Digital Logic Simulator

## Introduction

LogicForge is a beginner-friendly visual digital-logic simulator for learning how gates, combinational blocks, sequential circuits, integrated circuits, and physical breadboards work together.

The project was developed for:

- **University:** Islamic University of Technology (IUT)
- **Course:** CSE 4402 — Visual Programming Lab
- **Level:** 2nd-year undergraduate

### Project goals

LogicForge aims to:

1. Make digital-logic concepts easy to explore visually.
2. Let students build and test circuits without physical hardware.
3. Connect abstract schematic diagrams with breadboard-style wiring.
4. Provide guided demonstrations for common classroom circuits.
5. Encourage experimentation through immediate, visible signal feedback.

### Authors

| Name | Student ID |
| --- | --- |
| Abid | 230041144 |
| Nazmus Sakib | 230041118 |
| Abida Awwal Ava | 230041158 |

## Features

### Schematic workspace

- Drag-and-drop circuit construction on a snap-to-grid workspace.
- Logic inputs, switches, VCC, ground, junctions, bulbs, and logic outputs.
- AND, NAND, OR, NOR, NOT, and XOR gates with configurable input counts.
- Multiplexer and demultiplexer blocks with selectable widths.
- Decoder and priority-encoder blocks.
- Half adder, full adder, magnitude comparator, and BCD-to-seven-segment decoder.
- Seven-segment display component with active-high segment inputs.
- Clock, SR, JK, D, and T flip-flops.
- Configurable counters and shift registers.
- Physical integrated-circuit symbols from the built-in IC catalog.
- Reusable custom components created from selected subcircuits.
- Named component labels and visible pin names.
- Rotatable components and orthogonal signal wires.
- Automatic wire routing, bend anchors, crossing bridges, and signal-state styling.
- Combinational evaluation, sequential updates, feedback settling, and unstable-feedback warnings.
- Play/pause simulation controls and live circuit status information.

### Breadboard workspace

- Mini, half, full, double, triple, quadruple, and quintuple board sizes.
- Sixteen input switches and sixteen output bulbs.
- Dedicated VCC and GND connection points outside the control panels.
- DIP IC placement using the physical pin numbering of each catalog part.
- Breadboard terminal strips with real row-level electrical connectivity.
- Jumper wires that join terminal strips and support shared power rows between ICs.
- IC-aware wire routing that treats DIP packages as obstacles.
- Orthogonal wires with bridge marks at crossings.
- Multiple rotating jumper colors, including endpoint-colored sockets and control pins.
- IC power validation, floating signals, low/high signals, and conflict detection.
- Breadboard zoom controls from the Views menu, toolbar shortcuts, and Ctrl + mouse wheel.
- Breadboard undo/redo, copy/paste for ICs, board resizing, and PNG export.
- Persistent breadboard switch, IC, jumper, and runtime state.

### Menus, themes, and project tools

- Consistent Home, Edit, Views, and Demos menus in both workspaces.
- Dark and light themes.
- Workspace grid, zoom controls, component palette, properties panel, and status-bar toggles.
- Copy, paste, undo, redo, delete, clear, and select-all editing commands.
- Open and save support for versioned `.lgf` LogicForge project files.
- Recent-project list on the welcome screen.
- Clean project titles without version labels in the application interface.

### Guided demonstrations

The schematic Demos menu includes:

- 4-bit parity tester
- 4-bit BCD seven-segment display
- 4-way MUX playground
- 4-bit adder and MUX workbench
- 4-bit ring light chaser
- Master-slave JK flip-flop
- 4-bit MUX combination lock
- 4-bit two's-complement subtractor

The breadboard Demos menu includes:

- 1-bit full adder built from basic 74HC gates
- Two's-complement subtractor using a full-adder arrangement

## Team contributions

The work was organized into the following contribution areas:

### Abid — schematic logic and component library

- Designed the schematic component palette and symbol organization.
- Implemented the core gate, arithmetic, multiplexer, decoder, encoder, and comparator demonstrations.
- Worked on sequential components, simulation evaluation, and reusable custom blocks.

### Nazmus Sakib — breadboard simulation and physical wiring

- Implemented breadboard layouts, terminal-strip connectivity, DIP IC placement, and power validation.
- Worked on jumper routing, IC obstacle avoidance, wire crossings, signal conflicts, and breadboard demos.
- Added board resizing, zoom, breadboard persistence, and IC editing operations.

### Abida Awwal Ava — interface, demonstrations, and verification

- Organized the welcome screen, menus, palette, properties panel, themes, and status information.
- Prepared the guided learning demonstrations and user-facing labels.
- Reviewed interaction behavior, project persistence, documentation, and final usability details.

## Technology stack

- Java 21
- JavaFX 21
- Maven
- Jackson JSON for project-file serialization
- JUnit 5 for automated tests

## Requirements

- JDK 21 or newer
- Maven 3.9 or newer
- A desktop environment capable of running JavaFX

## Running the project

### Using Maven

From the project directory, run:

```bash
mvn clean javafx:run
```

To run the test suite:

```bash
mvn test
```

To create a packaged build:

```bash
mvn clean package
```

The generated files are placed in the `target/` directory.

### Using the verification scripts

On Linux or macOS:

```bash
./verify.sh
```

On Windows:

```bat
verify.cmd
```

The scripts check the Maven build and test process using the project configuration.

### Importing into an IDE

1. Open the extracted project folder in IntelliJ IDEA, Eclipse, or another Maven-compatible IDE.
2. Import it as an existing Maven project.
3. Allow Maven to download the JavaFX, Jackson, and JUnit dependencies.
4. Run `com.logicforge.App`, or run the Maven goal `javafx:run`.

## Basic use

1. Start a new schematic or breadboard from the welcome screen.
2. Drag components from the palette or place ICs from the IC controls.
3. Drag from an output pin to an input pin to create a wire.
4. Click inputs and switches to change their logic states.
5. Press **Play** to run sequential circuits and clocks.
6. Open the Demos menu for ready-made teaching examples.
7. Use the Properties panel to inspect labels, configuration, and live signal state.
8. Save the project when the circuit is ready.

## Demo video

A project demonstration video will be added here:

> **YouTube:** link to be added

## License and academic use

LogicForge was created as an academic Visual Programming Lab project. It is intended for classroom learning, circuit experimentation, and demonstration purposes.
