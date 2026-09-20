package com.logicforge;

/**
 * Plain Java launcher. Keeping this class separate makes Maven and IDE launching
 * more reliable because the main class itself does not extend Application.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        App.main(args);
    }
}
