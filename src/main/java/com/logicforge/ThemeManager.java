package com.logicforge;

import java.util.prefs.Preferences;

import javafx.scene.Scene;

/** Applies and remembers the optional light-theme override stylesheet. */
public final class ThemeManager {

    private static final String LIGHT_THEME_KEY = "light-theme";
    private static final Preferences PREFERENCES =
            Preferences.userNodeForPackage(ThemeManager.class);

    private ThemeManager() {
    }

    public static boolean isLightTheme() {
        return PREFERENCES.getBoolean(LIGHT_THEME_KEY, false);
    }

    public static void setLightTheme(boolean lightTheme) {
        PREFERENCES.putBoolean(LIGHT_THEME_KEY, lightTheme);
    }

    public static void apply(Scene scene) {
        if (scene == null) {
            return;
        }
        String lightStylesheet = ThemeManager.class
                .getResource("/com/logicforge/light-theme.css")
                .toExternalForm();
        scene.getStylesheets().remove(lightStylesheet);
        if (isLightTheme()) {
            scene.getStylesheets().add(lightStylesheet);
        }
    }
}
