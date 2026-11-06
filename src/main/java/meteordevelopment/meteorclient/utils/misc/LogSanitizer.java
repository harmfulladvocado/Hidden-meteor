/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.utils.misc;

import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractOutputStreamAppender;
import org.apache.logging.log4j.core.filter.AbstractFilter;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class LogSanitizer {
    private static final String[] DENIED_TOKENS = {
        "meteordevelopment_discord-ipc",
        "meteordevelopment_orbit",
        "org_meteordev_starscript",
        "net_raphimc_minecraftauth",
        "net_lenni0451_",
        "org_javassist_javassist",
        "org_reflections_reflections",
        "de_florianreuth_waybackauthlib",
        "io_netty_netty-codec-socks",
        "io_netty_netty-handler-proxy",
        "meteordevelopment",
        "meteorclient",
        "meteor-client",
        "meteordev",
        "[Meteor]",
        "fabric_loader",
        "fabric-loader"
    };

    private static final Pattern[] FAKE_LINE_PATTERNS = {
        Pattern.compile(".*?: Reflections took \\d+ ms to scan \\d+ url"),
        Pattern.compile(".*?: Found \\d+ font famil"),
        Pattern.compile(".*?: Path Manager: "),
        Pattern.compile("\\[\\d{2}:\\d{2}:\\d{2}\\] \\[(?:main|Render thread)/INFO\\]: Loading$"),
        Pattern.compile("\\[\\d{2}:\\d{2}:\\d{2}\\] \\[.+?/INFO\\]: Loaded in \\d+ milliseconds?$"),
        Pattern.compile("\\[\\d{2}:\\d{2}:\\d{2}\\] \\[.+?/INFO\\]: Loaded \\d+ recipes$"),
        Pattern.compile("\\[\\d{2}:\\d{2}:\\d{2}\\] \\[.+?/INFO\\]: Loaded \\d+ advancements$"),
        Pattern.compile("\\[\\d{2}:\\d{2}:\\d{2}\\] \\[.+?/INFO\\]: Initializing (?:Lithium|Meteor Client|Sodium|MeteorClient)$"),
        Pattern.compile("\\[\\d{2}:\\d{2}:\\d{2}\\] \\[Thread-\\d+/INFO\\]: Saving$"),
        Pattern.compile(".*?: Saved in \\d+ milliseconds"),
        Pattern.compile(".*?: Panic flag detected"),
    };

    private static final Pattern LOG_LINE_START = Pattern.compile("(?=\\[\\d{2}:\\d{2}:\\d{2}\\] \\[)");

    private static final String[] KNOWN_TOP_LEVEL_MODS = {
        "fabric-api",
        "fabricloader",
        "java",
        "lithium",
        "minecraft",
        "modmenu",
        "placeholder-api",
    };

    private static final String[] MODS_WITH_NO_CHILDREN = {
        "lithium",
        "java",
        "minecraft",
        "placeholder-api",
    };

    private static boolean filterInstalled = false;

    private static volatile String capturedSpongeLine = null;
    private static volatile String fabricLoaderVersion = null;
    private static volatile String fabricApiVersion = null;

    public static void install() {
        installLogFilter();
        sanitizeLogFiles();

        Runtime.getRuntime().addShutdownHook(new Thread(LogSanitizer::sanitizeLogFiles));
    }

    public static void installLogFilter() {
        if (filterInstalled) return;
        try {
            fabricLoaderVersion = FabricLoader.getInstance()
                .getModContainer("fabricloader")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("0.19.5");
        } catch (Exception ignored) {
            fabricLoaderVersion = "0.19.5";
        }

        try {
            fabricApiVersion = FabricLoader.getInstance()
                .getModContainer("fabric-api")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse(null);
        } catch (Exception ignored) {}

        try {
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            ctx.getConfiguration().addFilter(new AbstractFilter() {
                @Override
                public Result filter(LogEvent event) {
                    String msg = event.getMessage().getFormattedMessage();

                    for (String token : DENIED_TOKENS) {
                        if (msg.contains(token)) {
                            if (capturedSpongeLine == null && msg.contains("SpongePowered MIXIN Subsystem")) {
                                capturedSpongeLine = buildLogLine(event, msg);
                            }
                            return Result.DENY;
                        }
                    }

                    if (isFakeMeteorMessage(msg)) return Result.DENY;

                    return Result.NEUTRAL;
                }
            });
            ctx.updateLoggers();
            filterInstalled = true;
        } catch (Exception ignored) {}
    }

    private static String buildLogLine(LogEvent event, String formattedMsg) {
        try {
            LocalTime time = Instant.ofEpochMilli(event.getInstant().getEpochMillisecond())
                .atZone(ZoneId.systemDefault()).toLocalTime();
            String ts = String.format("%02d:%02d:%02d", time.getHour(), time.getMinute(), time.getSecond());
            String thread = event.getThreadName();
            return "[" + ts + "] [" + thread + "/INFO]: " + formattedMsg;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isFakeMeteorMessage(String msg) {
        if (msg.equals("Loading")) return true;
        if (msg.equals("Saving")) return true;
        if (msg.startsWith("Loaded in ") && msg.contains("milliseconds")) return true;
        if (msg.startsWith("Saved in ") && msg.contains("milliseconds")) return true;
        if (msg.startsWith("Reflections took ") && msg.contains("to scan")) return true;
        if (msg.startsWith("Found ") && msg.contains("font famil")) return true;
        if (msg.startsWith("Path Manager: ")) return true;
        if (msg.equals("Initializing Lithium")
            || msg.equals("Initializing Meteor Client")
            || msg.equals("Initializing MeteorClient")
            || msg.equals("Initializing Sodium")) return true;
        if (msg.startsWith("Panic flag detected")) return true;
        return false;
    }

    public static void sanitizeLogFiles() {
        flushAppenders();

        Path logDir;
        try {
            logDir = FabricLoader.getInstance().getGameDir().resolve("logs");
        } catch (Exception e) {
            logDir = Path.of("logs");
        }

        sanitizeFile(logDir.resolve("latest.log").toFile());
        sanitizeFile(logDir.resolve("debug.log").toFile());
    }

    private static void flushAppenders() {
        try {
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            for (Appender appender : ctx.getConfiguration().getAppenders().values()) {
                if (appender instanceof AbstractOutputStreamAppender<?> streamAppender) {
                    streamAppender.getManager().flush();
                }
            }
        } catch (Exception ignored) {}
    }

    private static void sanitizeFile(File file) {
        if (!file.exists() || !file.isFile() || file.length() == 0) return;

        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            byte[] bytes = new byte[(int) raf.length()];
            raf.readFully(bytes);
            String content = new String(bytes, StandardCharsets.UTF_8);

            String normalized = LOG_LINE_START.matcher(
                content.replaceAll("(\\])(\\[\\d{2}:\\d{2}:\\d{2}\\])", "$1\n$2")
            ).replaceAll("\n");

            String[] lines = normalized.split("\r?\n", -1);
            List<String> cleaned = new ArrayList<>(lines.length);

            String fileScopedSpongeLine = null;

            for (String line : lines) {
                String sanitized = line.replace("\u0000", "");
                if (sanitized.isBlank()) continue;

                if (fileScopedSpongeLine == null && sanitized.contains("SpongePowered MIXIN Subsystem")) {
                    fileScopedSpongeLine = sanitized;
                }

                boolean deny = false;
                for (String token : DENIED_TOKENS) {
                    if (sanitized.contains(token)) {
                        deny = true;
                        break;
                    }
                }
                if (!deny) cleaned.add(sanitized);
            }

            cleaned.removeIf(line -> {
                for (Pattern p : FAKE_LINE_PATTERNS) {
                    if (p.matcher(line).find()) return true;
                }
                return false;
            });

            cleaned.removeIf(l -> l.contains("Loaded configuration file for Lithium"));
            cleaned.removeIf(l -> l.contains("SpongePowered MIXIN Subsystem"));

            sanitizeModList(cleaned);

            if (fabricLoaderVersion != null) {
                for (int i = 0; i < cleaned.size(); i++) {
                    String line = cleaned.get(i);
                    if (line.contains("\\-- mixinextras") || line.contains("└-- mixinextras") || line.contains("|-- mixinextras")) {
                        boolean alreadyPresent = i > 0 && cleaned.get(i - 1).contains("fabricloader");
                        if (!alreadyPresent) {
                            cleaned.add(i, "\t- fabricloader " + fabricLoaderVersion);
                        }
                        break;
                    }
                }
            }

            boolean fabricApiParentPresent = cleaned.stream().anyMatch(l -> {
                String t = l.trim();
                return t.startsWith("- fabric-api ") || t.startsWith("|-- fabric-api ") || t.startsWith("\\-- fabric-api ");
            });
            if (!fabricApiParentPresent) {
                for (int i = 0; i < cleaned.size(); i++) {
                    if (cleaned.get(i).contains("fabric-advancement-api-v1")) {
                        cleaned.add(i, "\t- fabric-api " + fabricApiVersion);
                        break;
                    }
                }
            }

            int headerIndex = -1;
            int modListEnd = -1;
            for (int i = 0; i < cleaned.size(); i++) {
                String t = cleaned.get(i).trim();
                if (t.matches(".*\\]: Loading \\d+ mods:.*")) {
                    headerIndex = i;
                } else if (headerIndex >= 0 && (t.startsWith("- ") || t.startsWith("|-- ") || t.startsWith("\\-- "))) {
                    modListEnd = i;
                } else if (modListEnd >= 0 && t.startsWith("[")) {
                    break;
                }
            }

            if (headerIndex >= 0) {
                String l = cleaned.get(headerIndex);
                int count = countModEntries(cleaned, headerIndex);
                cleaned.set(headerIndex, l.replaceFirst("Loading \\d+ mods", "Loading " + count + " mods"));
            }

            String spongeLine = capturedSpongeLine != null ? capturedSpongeLine : fileScopedSpongeLine;
            if (modListEnd >= 0) {
                int insertAt = modListEnd + 1;
                String tsSource = insertAt < cleaned.size() ? cleaned.get(insertAt) : cleaned.get(modListEnd);
                String ts = extractTimestamp(tsSource);
                List<String> toInsert = new ArrayList<>();
                if (spongeLine != null) toInsert.add(spongeLine);
                toInsert.add("[" + ts + "] [main/INFO]: Loaded configuration file for Lithium: 171 options available, 0 override(s) found.");
                cleaned.addAll(insertAt, toInsert);
            }

            for (int i = 0; i < cleaned.size(); i++) {
                String current = cleaned.get(i);
                if (current.trim().startsWith("\\--")) {
                    cleaned.set(i, current.replaceFirst("\\\\--", "|--"));
                }
            }

            for (int i = 0; i < cleaned.size(); i++) {
                String current = cleaned.get(i);
                if (current.trim().startsWith("|--")) {
                    boolean nextIsPipe = false;
                    for (int j = i + 1; j < cleaned.size(); j++) {
                        String next = cleaned.get(j).trim();
                        if (next.startsWith("|--")) {
                            nextIsPipe = true;
                            break;
                        } else if (next.startsWith("- ") || next.startsWith("[")) {
                            break;
                        }
                    }
                    if (!nextIsPipe) {
                        cleaned.set(i, current.replaceFirst("\\|--", "\\\\--"));
                    }
                }
            }

            String result = String.join(System.lineSeparator(), cleaned) + System.lineSeparator();
            byte[] outputBytes = result.getBytes(StandardCharsets.UTF_8);

            raf.setLength(0);
            raf.seek(0);
            raf.write(outputBytes);
        } catch (Exception ignored) {}
    }

    private static boolean isKnownTopLevelMod(String nameAndVersion) {
        for (String mod : KNOWN_TOP_LEVEL_MODS) {
            if (nameAndVersion.equals(mod) || nameAndVersion.startsWith(mod + " ") || nameAndVersion.startsWith(mod + "\t")) return true;
        }
        return false;
    }

    private static boolean isNoChildrenMod(String nameAndVersion) {
        for (String mod : MODS_WITH_NO_CHILDREN) {
            if (nameAndVersion.equals(mod) || nameAndVersion.startsWith(mod + " ") || nameAndVersion.startsWith(mod + "\t")) return true;
        }
        return false;
    }

    private static void sanitizeModList(List<String> cleaned) {
        int start = -1;
        int end = -1;

        for (int i = 0; i < cleaned.size(); i++) {
            String line = cleaned.get(i).trim();
            if (start < 0 && line.matches(".*\\]: Loading \\d+ mods:.*")) {
                start = i;
            } else if (start >= 0 && line.startsWith("[")) {
                end = i;
                break;
            }
        }

        if (start < 0) return;
        if (end < 0) end = cleaned.size();

        for (int i = end - 1; i >= start; i--) {
            cleaned.remove(i);
        }
    }

    private static int countModEntries(List<String> lines, int headerIndex) {
        int count = 0;
        for (int i = headerIndex + 1; i < lines.size(); i++) {
            String t = lines.get(i).trim();
            if (t.startsWith("- ") || t.startsWith("|-- ") || t.startsWith("\\-- ")) {
                count++;
            } else if (t.startsWith("[")) {
                break;
            }
        }
        return count;
    }

    private static String extractTimestamp(String line) {
        if (line.length() >= 9 && line.charAt(0) == '[') {
            int close = line.indexOf(']');
            if (close > 0) return line.substring(1, close);
        }
        LocalTime now = LocalTime.now();
        return String.format("%02d:%02d:%02d", now.getHour(), now.getMinute(), now.getSecond());
    }
}
