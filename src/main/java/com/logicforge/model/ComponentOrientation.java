package com.logicforge.model;

/** Four cardinal orientations supported by workspace components. */
public enum ComponentOrientation {
    EAST(0),
    SOUTH(90),
    WEST(180),
    NORTH(270);

    private final double degrees;

    ComponentOrientation(double degrees) {
        this.degrees = degrees;
    }

    public double degrees() {
        return degrees;
    }

    public ComponentOrientation clockwise() {
        return values()[(ordinal() + 1) % values().length];
    }
}
