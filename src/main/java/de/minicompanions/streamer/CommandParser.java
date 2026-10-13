package de.minicompanions.streamer;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Erkennt den Spawn-Befehl (Standard {@code !skin <Minecraft-Benutzername>}) in Stream-Chat-Nachrichten.
 * Der Name wird strikt geprüft und NIE als Pfad, Link oder Befehl verwendet.
 */
public final class CommandParser {
    /** Gültige Minecraft-Namen: 3 bis 16 Zeichen, nur a-z, A-Z, 0-9 und _. */
    public static final Pattern MC_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    public static final String DEFAULT_PREFIXES = "!";

    private CommandParser() {
    }

    /** Ergebnis der Analyse. */
    public enum Kind { NOT_COMMAND, OTHER_COMMAND, SPAWN_VALID, SPAWN_INVALID }

    public record Result(Kind kind, String mcName) {
    }

    /**
     * @param prefixes erlaubte Präfix-Zeichen für den Spawn-Befehl, z. B. "!"
     * @param command  Befehlsname ohne Präfix, z. B. "skin"
     */
    public static Result parse(String text, String prefixes, String command) {
        if (text == null) {
            return new Result(Kind.NOT_COMMAND, null);
        }
        String t = text.strip();
        if (t.isEmpty() || !startsWithPrefix(t, prefixes)) {
            return new Result(Kind.NOT_COMMAND, null);
        }
        if (prefixesOrDefault(prefixes).indexOf(t.charAt(0)) < 0) {
            // z. B. "/skin Notch", wenn nur "!" erlaubt ist: kein Spawn, aber auch keine Sprechblase
            return new Result(Kind.OTHER_COMMAND, null);
        }
        String body = t.substring(1);
        String[] parts = body.split("\\s+", 3);
        String cmd = sanitizeCommandName(command);
        if (parts.length == 0 || !parts[0].toLowerCase(Locale.ROOT).equals(cmd)) {
            return new Result(Kind.OTHER_COMMAND, null);
        }
        // genau ein Argument
        if (parts.length != 2) {
            return new Result(Kind.SPAWN_INVALID, null);
        }
        String name = parts[1];
        if (!isValidMcName(name)) {
            return new Result(Kind.SPAWN_INVALID, null);
        }
        return new Result(Kind.SPAWN_VALID, name);
    }

    /** Sieht die Nachricht wie ein Befehl aus (eigenes Präfix, / oder !)? Solche Nachrichten werden nie als Blase gezeigt. */
    public static boolean startsWithPrefix(String text, String prefixes) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return prefixesOrDefault(prefixes).indexOf(text.charAt(0)) >= 0 || text.charAt(0) == '/' || text.charAt(0) == '!';
    }

    private static String prefixesOrDefault(String prefixes) {
        return prefixes == null || prefixes.isEmpty() ? DEFAULT_PREFIXES : prefixes;
    }

    public static boolean isValidMcName(String name) {
        return name != null && MC_NAME.matcher(name).matches();
    }

    /** Befehlsnamen aus der Config absichern: nur Buchstaben/Ziffern, Standard "skin". */
    public static String sanitizeCommandName(String command) {
        if (command == null) {
            return "skin";
        }
        String c = command.strip().toLowerCase(Locale.ROOT);
        while (!c.isEmpty() && (c.charAt(0) == '/' || c.charAt(0) == '!')) {
            c = c.substring(1);
        }
        return c.matches("[a-z0-9_]{1,20}") ? c : "skin";
    }
}
