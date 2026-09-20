package com.logicforge.persistence;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.prefs.Preferences;

/** Stores a small, ordered list of recently opened or saved LogicForge projects. */
public final class RecentProjects {

    private static final int MAX_RECENT_PROJECTS = 4;
    private static final String KEY_PREFIX = "recent-project-";
    private static final Preferences PREFERENCES =
            Preferences.userNodeForPackage(RecentProjects.class);

    private RecentProjects() { }

    public static List<Path> existingProjects() {
        List<Path> existing = new ArrayList<>();
        Set<Path> unique = new LinkedHashSet<>();
        for (int index = 0; index < MAX_RECENT_PROJECTS; index++) {
            String stored = PREFERENCES.get(KEY_PREFIX + index, "");
            if (stored.isBlank()) {
                continue;
            }
            try {
                Path path = Path.of(stored).toAbsolutePath().normalize();
                if (Files.isRegularFile(path) && unique.add(path)) {
                    existing.add(path);
                }
            } catch (RuntimeException ignored) {
                // Ignore paths that are no longer valid on this machine.
            }
        }
        return List.copyOf(existing);
    }

    public static void remember(Path projectPath) {
        if (projectPath == null) {
            return;
        }
        Path normalized = projectPath.toAbsolutePath().normalize();
        List<Path> projects = new ArrayList<>();
        projects.add(normalized);
        for (Path recent : existingProjects()) {
            if (!recent.equals(normalized) && projects.size() < MAX_RECENT_PROJECTS) {
                projects.add(recent);
            }
        }
        for (int index = 0; index < MAX_RECENT_PROJECTS; index++) {
            if (index < projects.size()) {
                PREFERENCES.put(KEY_PREFIX + index, projects.get(index).toString());
            } else {
                PREFERENCES.remove(KEY_PREFIX + index);
            }
        }
    }
}
