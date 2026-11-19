/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.utils.misc;

import meteordevelopment.meteorclient.MeteorClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class FilesystemCleaner {
    private FilesystemCleaner() {
    }

    public static void deleteCrashReports() {
        deleteContents(gameDir().resolve("crash-reports"));
    }

    public static void deleteRotatedLogs() {
        Path logs = gameDir().resolve("logs");
        if (!Files.isDirectory(logs)) return;

        try (Stream<Path> paths = Files.list(logs)) {
            paths.filter(Files::isRegularFile)
                .filter(path -> {
                    String name = path.getFileName().toString();
                    if (name.equals("latest.log") || name.equals("debug.log")) return false;
                    return name.endsWith(".log") || name.endsWith(".log.gz");
                })
                .forEach(FilesystemCleaner::delete);
        } catch (IOException ignored) {}
    }

    public static void deleteScreenshots() {
        deleteContents(gameDir().resolve("screenshots"));
    }

    public static void clearConfig() {
        deleteContents(MeteorClient.FOLDER.toPath());
    }

    private static Path gameDir() {
        try {
            return FabricLoader.getInstance().getGameDir();
        } catch (Exception e) {
            return Path.of(".");
        }
    }

    private static void deleteContents(Path directory) {
        if (!Files.isDirectory(directory)) return;

        try (Stream<Path> paths = Files.list(directory)) {
            paths.forEach(FilesystemCleaner::delete);
        } catch (IOException ignored) {}
    }

    private static void delete(Path path) {
        try {
            if (Files.isDirectory(path)) {
                try (Stream<Path> paths = Files.list(path)) {
                    paths.forEach(FilesystemCleaner::delete);
                }
            }
            Files.deleteIfExists(path);
        } catch (IOException ignored) {}
    }
}
