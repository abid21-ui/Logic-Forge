package com.logicforge.model;

/** Per-instance configuration for variable-width and clock components. */
public record ComponentConfig(int bitWidth, double clockFrequencyHz) {

    public ComponentConfig {
        if (bitWidth < 1 || bitWidth > 4) {
            throw new IllegalArgumentException("bitWidth must be between 1 and 4");
        }
        if (clockFrequencyHz <= 0) {
            throw new IllegalArgumentException("clockFrequencyHz must be positive");
        }
    }

    public static ComponentConfig defaults() {
        return new ComponentConfig(1, 1.0);
    }

    public static ComponentConfig bits(int bitWidth) {
        return new ComponentConfig(bitWidth, 1.0);
    }

    public static ComponentConfig clock(double frequencyHz) {
        return new ComponentConfig(1, frequencyHz);
    }
}
