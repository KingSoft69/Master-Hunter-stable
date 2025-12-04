package dev.masterhunter.addon.modules.Utility;

import dev.masterhunter.addon.Main;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.World;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CoordinateLeakLogger extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    // Settings
    private final Setting<Boolean> enabled = sgGeneral.add(new BoolSetting.Builder()
        .name("enabled")
        .description("Enable coordinate leak logging.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> logToFile = sgGeneral.add(new BoolSetting.Builder()
        .name("log-to-file")
        .description("Save detected coordinates to text file.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> logToChat = sgGeneral.add(new BoolSetting.Builder()
        .name("log-to-chat")
        .description("Show detection message in Meteor chat.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> includeTimestamp = sgGeneral.add(new BoolSetting.Builder()
        .name("include-timestamp")
        .description("Include timestamp in logs.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoDimensionConvert = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-dimension-convert")
        .description("Auto convert between Nether/Overworld coordinates.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> fileName = sgGeneral.add(new StringSetting.Builder()
        .name("file-name")
        .description("Log file name.")
        .defaultValue("coordinate_leaks")
        .build()
    );

    private final Setting<String> fileExtension = sgGeneral.add(new StringSetting.Builder()
        .name("file-extension")
        .description("Log file extension.")
        .defaultValue("txt")
        .build()
    );

    private final Setting<Boolean> appendToFile = sgGeneral.add(new BoolSetting.Builder()
        .name("append-to-file")
        .description("Append instead of overwriting.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> minCoordinateValue = sgGeneral.add(new IntSetting.Builder()
        .name("min-coordinate-value")
        .description("Minimum coordinate absolute value to log (filters small coords).")
        .defaultValue(1000)
        .range(0, 30000000)
        .sliderRange(0, 100000)
        .build()
    );

    // Minecraft world limits
    private static final int MAX_COORD = 30000000;
    private static final int MIN_Y = -64;
    private static final int MAX_Y = 320;
    private static final int DEFAULT_OVERWORLD_Y = 64;
    private static final int DEFAULT_NETHER_Y = 120;

    // Regex patterns for coordinate detection
    // Pattern for X: Y: Z: format (3 numbers)
    private static final Pattern XYZ_LABELED_PATTERN = Pattern.compile(
        "[Xx]:\\s*(-?\\d+)\\s*[Yy]:\\s*(-?\\d+)\\s*[Zz]:\\s*(-?\\d+)",
        Pattern.CASE_INSENSITIVE
    );

    // Pattern for X: Z: format (2 numbers, no Y)
    private static final Pattern XZ_LABELED_PATTERN = Pattern.compile(
        "[Xx]:\\s*(-?\\d+)\\s*[Zz]:\\s*(-?\\d+)",
        Pattern.CASE_INSENSITIVE
    );

    // Pattern for brackets with 3 numbers: [x, y, z] or (x, y, z)
    private static final Pattern BRACKET_XYZ_PATTERN = Pattern.compile(
        "[\\[\\(]\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*[\\]\\)]"
    );

    // Pattern for brackets with 2 numbers: [x, z] or (x, z)
    private static final Pattern BRACKET_XZ_PATTERN = Pattern.compile(
        "[\\[\\(]\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*[\\]\\)]"
    );

    // Pattern for slash format with 3 numbers: x/y/z
    private static final Pattern SLASH_XYZ_PATTERN = Pattern.compile(
        "(-?\\d+)/(-?\\d+)/(-?\\d+)"
    );

    // Pattern for comma separated 3 numbers (not in brackets)
    private static final Pattern COMMA_XYZ_PATTERN = Pattern.compile(
        "(?<![\\[\\(])(-?\\d+)\\s*,\\s*(-?\\d+)\\s*,\\s*(-?\\d+)(?![\\]\\)])"
    );

    // Pattern for text prefixed coords (3 numbers): at/coords:/base at + 3 space/comma separated numbers
    private static final Pattern TEXT_PREFIX_XYZ_PATTERN = Pattern.compile(
        "(?:at|coords?:?|base\\s+at)\\s*(-?\\d+)[,\\s]+(-?\\d+)[,\\s]+(-?\\d+)",
        Pattern.CASE_INSENSITIVE
    );

    // Pattern for text prefixed coords (2 numbers): at/coords:/base at + 2 space/comma separated numbers
    private static final Pattern TEXT_PREFIX_XZ_PATTERN = Pattern.compile(
        "(?:at|coords?:?|base\\s+at)\\s*(-?\\d+)[,\\s]+(-?\\d+)(?![,\\s]*-?\\d)",
        Pattern.CASE_INSENSITIVE
    );

    // Pattern for space separated 3 numbers (standalone)
    private static final Pattern SPACE_XYZ_PATTERN = Pattern.compile(
        "(?:^|\\s)(-?\\d+)\\s+(-?\\d+)\\s+(-?\\d+)(?:\\s|$)"
    );

    // Pattern for space separated 2 numbers (most common on 2b2t): x z
    private static final Pattern SPACE_XZ_PATTERN = Pattern.compile(
        "(?:^|\\s)(-?\\d+)\\s+(-?\\d+)(?:\\s|$)"
    );

    // Pattern for dimension keywords
    private static final Pattern NETHER_KEYWORD_PATTERN = Pattern.compile(
        "\\b(nether|hell|n(?:eth)?)\\b",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern OVERWORLD_KEYWORD_PATTERN = Pattern.compile(
        "\\b(overworld|ow|surface)\\b",
        Pattern.CASE_INSENSITIVE
    );

    // Pattern to extract player name: <PlayerName> or [PlayerName]
    private static final Pattern PLAYER_NAME_PATTERN = Pattern.compile(
        "^[<\\[]([^>\\]]+)[>\\]]"
    );

    // Pattern for whisper format: PlayerName whispers:
    private static final Pattern WHISPER_PATTERN = Pattern.compile(
        "^(\\w+)\\s+whispers?:",
        Pattern.CASE_INSENSITIVE
    );

    // State
    private File currentLogFile;
    private int leakCount = 0;

    public CoordinateLeakLogger() {
        super(Main.CATEGORY, "coordinate-leak-logger", "Monitors chat for leaked coordinates and logs them to a file.");
    }

    @Override
    public void onActivate() {
        leakCount = 0;
        initializeLogFile();
        info("Coordinate leak logging started. Log file: " + (currentLogFile != null ? currentLogFile.getName() : "none"));
    }

    @Override
    public void onDeactivate() {
        if (currentLogFile != null) {
            info("Coordinate leak logging stopped. Total leaks found: " + leakCount);
        }
        currentLogFile = null;
    }

    @Override
    public String getInfoString() {
        return "Leaks: " + leakCount;
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (!enabled.get()) return;

        String message = event.getMessage().getString();
        if (message == null || message.trim().isEmpty()) return;

        // Try to extract coordinates
        CoordinateResult result = extractCoordinates(message);
        if (result == null) return;

        // Check minimum coordinate threshold
        if (Math.abs(result.x) < minCoordinateValue.get() && Math.abs(result.z) < minCoordinateValue.get()) {
            return;
        }

        // Extract player name
        String playerName = extractPlayerName(message);

        // Detect dimension
        Dimension dimension = detectDimension(message, result);

        // Convert coordinates if enabled
        int[] convertedCoords = null;
        if (autoDimensionConvert.get()) {
            convertedCoords = convertCoordinates(result.x, result.y, result.z, dimension);
        }

        // Format and log
        String logEntry = formatLogEntry(playerName, result, dimension, convertedCoords, message);

        if (logToFile.get()) {
            writeToFile(logEntry);
        }

        if (logToChat.get()) {
            String chatMessage = String.format("Coordinate leak detected from %s: %d, %s%d",
                playerName,
                result.x,
                result.hasY ? result.y + ", " : "",
                result.z);
            info(chatMessage);
        }

        leakCount++;
    }

    private CoordinateResult extractCoordinates(String message) {
        Matcher matcher;

        // Try X: Y: Z: format first (3 numbers)
        matcher = XYZ_LABELED_PATTERN.matcher(message);
        if (matcher.find()) {
            int x = parseCoordinate(matcher.group(1));
            int y = parseCoordinate(matcher.group(2));
            int z = parseCoordinate(matcher.group(3));
            if (isValidCoordinate(x, y, z)) {
                return new CoordinateResult(x, y, z, true);
            }
        }

        // Try brackets with 3 numbers: [x, y, z]
        matcher = BRACKET_XYZ_PATTERN.matcher(message);
        if (matcher.find()) {
            int x = parseCoordinate(matcher.group(1));
            int y = parseCoordinate(matcher.group(2));
            int z = parseCoordinate(matcher.group(3));
            if (isValidCoordinate(x, y, z)) {
                return new CoordinateResult(x, y, z, true);
            }
        }

        // Try slash format with 3 numbers: x/y/z
        matcher = SLASH_XYZ_PATTERN.matcher(message);
        if (matcher.find()) {
            int x = parseCoordinate(matcher.group(1));
            int y = parseCoordinate(matcher.group(2));
            int z = parseCoordinate(matcher.group(3));
            if (isValidCoordinate(x, y, z)) {
                return new CoordinateResult(x, y, z, true);
            }
        }

        // Try comma separated 3 numbers
        matcher = COMMA_XYZ_PATTERN.matcher(message);
        if (matcher.find()) {
            int x = parseCoordinate(matcher.group(1));
            int y = parseCoordinate(matcher.group(2));
            int z = parseCoordinate(matcher.group(3));
            if (isValidCoordinate(x, y, z)) {
                return new CoordinateResult(x, y, z, true);
            }
        }

        // Try text prefixed 3 numbers
        matcher = TEXT_PREFIX_XYZ_PATTERN.matcher(message);
        if (matcher.find()) {
            int x = parseCoordinate(matcher.group(1));
            int y = parseCoordinate(matcher.group(2));
            int z = parseCoordinate(matcher.group(3));
            if (isValidCoordinate(x, y, z)) {
                return new CoordinateResult(x, y, z, true);
            }
        }

        // Try space separated 3 numbers
        matcher = SPACE_XYZ_PATTERN.matcher(message);
        if (matcher.find()) {
            int x = parseCoordinate(matcher.group(1));
            int y = parseCoordinate(matcher.group(2));
            int z = parseCoordinate(matcher.group(3));
            if (isValidCoordinate(x, y, z)) {
                return new CoordinateResult(x, y, z, true);
            }
        }

        // Now try 2-number patterns (X Z only)

        // Try X: Z: format (2 numbers)
        matcher = XZ_LABELED_PATTERN.matcher(message);
        if (matcher.find()) {
            int x = parseCoordinate(matcher.group(1));
            int z = parseCoordinate(matcher.group(2));
            if (isValidXZCoordinate(x, z)) {
                return new CoordinateResult(x, getDefaultY(), z, false);
            }
        }

        // Try brackets with 2 numbers: [x, z]
        matcher = BRACKET_XZ_PATTERN.matcher(message);
        if (matcher.find()) {
            int x = parseCoordinate(matcher.group(1));
            int z = parseCoordinate(matcher.group(2));
            if (isValidXZCoordinate(x, z)) {
                return new CoordinateResult(x, getDefaultY(), z, false);
            }
        }

        // Try text prefixed 2 numbers
        matcher = TEXT_PREFIX_XZ_PATTERN.matcher(message);
        if (matcher.find()) {
            int x = parseCoordinate(matcher.group(1));
            int z = parseCoordinate(matcher.group(2));
            if (isValidXZCoordinate(x, z)) {
                return new CoordinateResult(x, getDefaultY(), z, false);
            }
        }

        // Try space separated 2 numbers (most common on 2b2t)
        matcher = SPACE_XZ_PATTERN.matcher(message);
        if (matcher.find()) {
            int x = parseCoordinate(matcher.group(1));
            int z = parseCoordinate(matcher.group(2));
            if (isValidXZCoordinate(x, z)) {
                return new CoordinateResult(x, getDefaultY(), z, false);
            }
        }

        return null;
    }

    private int parseCoordinate(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            // Return value outside valid coordinate range to fail validation
            return MAX_COORD + 1;
        }
    }

    private boolean isValidCoordinate(int x, int y, int z) {
        return Math.abs(x) <= MAX_COORD
            && Math.abs(z) <= MAX_COORD
            && y >= MIN_Y
            && y <= MAX_Y;
    }

    private boolean isValidXZCoordinate(int x, int z) {
        return Math.abs(x) <= MAX_COORD && Math.abs(z) <= MAX_COORD;
    }

    private int getDefaultY() {
        if (mc.world != null && mc.world.getRegistryKey() == World.NETHER) {
            return DEFAULT_NETHER_Y;
        }
        return DEFAULT_OVERWORLD_Y;
    }

    private String extractPlayerName(String message) {
        // Try <PlayerName> or [PlayerName] format
        Matcher matcher = PLAYER_NAME_PATTERN.matcher(message);
        if (matcher.find()) {
            return matcher.group(1);
        }

        // Try whisper format
        matcher = WHISPER_PATTERN.matcher(message);
        if (matcher.find()) {
            return matcher.group(1);
        }

        return "Unknown";
    }

    private Dimension detectDimension(String message, CoordinateResult coords) {
        // Check for explicit dimension keywords
        if (NETHER_KEYWORD_PATTERN.matcher(message).find()) {
            return Dimension.NETHER;
        }
        if (OVERWORLD_KEYWORD_PATTERN.matcher(message).find()) {
            return Dimension.OVERWORLD;
        }

        // Use current player dimension if available
        if (mc.world != null) {
            if (mc.world.getRegistryKey() == World.NETHER) {
                return Dimension.NETHER;
            }
            return Dimension.OVERWORLD;
        }

        // Guess based on coordinate magnitude (Nether coords are typically smaller)
        if (Math.abs(coords.x) < 4000 && Math.abs(coords.z) < 4000) {
            return Dimension.NETHER;
        }

        return Dimension.OVERWORLD;
    }

    private int[] convertCoordinates(int x, int y, int z, Dimension dimension) {
        if (dimension == Dimension.NETHER) {
            // Convert Nether to Overworld (multiply by 8)
            return new int[]{x * 8, y, z * 8};
        } else {
            // Convert Overworld to Nether (divide by 8)
            return new int[]{x / 8, y, z / 8};
        }
    }

    private String formatLogEntry(String playerName, CoordinateResult coords, Dimension dimension,
                                   int[] convertedCoords, String originalMessage) {
        StringBuilder sb = new StringBuilder();

        // Timestamp
        if (includeTimestamp.get()) {
            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            sb.append("[").append(now.format(formatter)).append("] ");
        }

        // Player name
        sb.append("From: ").append(playerName).append(" | ");

        // Coordinates
        sb.append("Coords: ");
        if (coords.hasY) {
            sb.append("[").append(coords.x).append(", ").append(coords.y).append(", ").append(coords.z).append("]");
        } else {
            sb.append("X: ").append(coords.x).append(" Z: ").append(coords.z)
                .append(" (Y: ").append(coords.y).append(" default)");
        }

        // Dimension
        sb.append(" | Dimension: ").append(dimension.name);

        // Converted coordinates
        if (convertedCoords != null) {
            String otherDimension = (dimension == Dimension.NETHER) ? "Overworld" : "Nether";
            sb.append(" | ").append(otherDimension).append(": [")
                .append(convertedCoords[0]).append(", ")
                .append(convertedCoords[1]).append(", ")
                .append(convertedCoords[2]).append("]");
        }

        sb.append("\n");
        sb.append("Original: \"").append(originalMessage).append("\"\n");
        sb.append("---\n");

        return sb.toString();
    }

    private void writeToFile(String entry) {
        if (currentLogFile == null) {
            initializeLogFile();
        }

        if (currentLogFile != null) {
            try (FileWriter writer = new FileWriter(currentLogFile, appendToFile.get())) {
                writer.write(entry);
                writer.flush();
            } catch (IOException e) {
                error("Failed to write to log file: " + e.getMessage());
            }
        }
    }

    private void initializeLogFile() {
        try {
            // Create logs directory if it doesn't exist
            File logsDir = new File("logs");
            if (!logsDir.exists()) {
                logsDir.mkdirs();
            }

            String fullFileName = fileName.get() + "." + fileExtension.get();
            currentLogFile = new File(logsDir, fullFileName);

            // Create file if it doesn't exist
            if (!currentLogFile.exists()) {
                currentLogFile.createNewFile();
            }
        } catch (IOException e) {
            error("Failed to initialize log file: " + e.getMessage());
            currentLogFile = null;
        }
    }

    // Helper class to store coordinate results
    private static class CoordinateResult {
        final int x;
        final int y;
        final int z;
        final boolean hasY;

        CoordinateResult(int x, int y, int z, boolean hasY) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.hasY = hasY;
        }
    }

    // Enum for dimension
    private enum Dimension {
        OVERWORLD("Overworld"),
        NETHER("Nether");

        final String name;

        Dimension(String name) {
            this.name = name;
        }
    }

    // Public methods for external access
    public int getLeakCount() {
        return leakCount;
    }

    public File getCurrentLogFile() {
        return currentLogFile;
    }
}
