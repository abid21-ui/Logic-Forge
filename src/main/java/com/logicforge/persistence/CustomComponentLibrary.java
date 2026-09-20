package com.logicforge.persistence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.logicforge.model.CustomComponentDefinition;
import com.logicforge.model.CustomComponentRuntime;

/** Persistent per-user palette of abstracted custom components. */
public final class CustomComponentLibrary {

    private final Path path;

    public CustomComponentLibrary() {
        this(defaultPath());
    }

    public CustomComponentLibrary(Path path) {
        this.path = path.toAbsolutePath().normalize();
    }

    public Path path() {
        return path;
    }

    public Map<String, CustomComponentDefinition> load() throws IOException {
        if (!Files.exists(path)) {
            return Map.of();
        }
        List<CustomComponentDefinition> definitions =
                LogicForgeFileCodec.customComponentLibraryFromJson(
                        Files.readString(path, StandardCharsets.UTF_8));
        Map<String, CustomComponentDefinition> byId = new LinkedHashMap<>();
        for (CustomComponentDefinition definition : definitions) {
            if (byId.putIfAbsent(definition.id(), definition) != null) {
                throw new IllegalArgumentException("Duplicate custom-component definition ID");
            }
        }
        for (CustomComponentDefinition definition : byId.values()) {
            new CustomComponentRuntime(definition, byId::get);
        }
        return Map.copyOf(byId);
    }

    public void save(Iterable<CustomComponentDefinition> definitions) throws IOException {
        Map<String, CustomComponentDefinition> unique = new LinkedHashMap<>();
        for (CustomComponentDefinition definition : definitions) {
            unique.put(definition.id(), definition);
        }
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temporary = Files.createTempFile(
                parent == null ? Path.of(".") : parent,
                ".logicforge-custom-",
                ".tmp");
        try {
            Files.writeString(
                    temporary,
                    LogicForgeFileCodec.customComponentLibraryToJson(
                            List.copyOf(unique.values())) + System.lineSeparator(),
                    StandardCharsets.UTF_8);
            try {
                Files.move(
                        temporary,
                        path,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static Path defaultPath() {
        String userHome = System.getProperty("user.home", ".");
        return Path.of(userHome, ".logicforge", "custom-components.json");
    }
}
