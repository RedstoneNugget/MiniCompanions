package de.minicompanions.figure;

import java.util.Locale;

/**
 * Welche Rüstung die Figuren tragen: keine, wie der Spieler oder ein festes Material.
 * Einstellbar für alle Figuren (Einstellungen) und pro Figur (Rechtsklick → Aussehen).
 */
public enum ArmorMode {
    NONE(0, 0),
    PLAYER(0, 0),
    LEATHER(0xFF8A5A30, 0xFF8A5A30),
    CHAINMAIL(0xFF9A9A9A, 0xFF9A9A9A),
    IRON(0xFFD8D8D8, 0xFFD8D8D8),
    GOLD(0xFFF2D13A, 0xFFF2D13A),
    DIAMOND(0xFF5CDBD5, 0xFF5CDBD5),
    NETHERITE(0xFF4A4245, 0xFF4A4245),
    COPPER(0xFFC77A4A, 0xFFC77A4A),
    /** Schildkrötenpanzer gibt es nur als Helm. */
    TURTLE(0xFF47A036, 0);

    /** Farbe von Helm und Brustpanzer (0 = nichts zeichnen). */
    public final int helmet;
    public final int chest;

    ArmorMode(int helmet, int chest) {
        this.helmet = helmet;
        this.chest = chest;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static boolean isValid(String s) {
        return parse(s) != null;
    }

    public static ArmorMode parse(String s) {
        if (s == null) {
            return null;
        }
        try {
            return valueOf(s);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
