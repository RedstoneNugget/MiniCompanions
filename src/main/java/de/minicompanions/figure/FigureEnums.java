package de.minicompanions.figure;

import java.util.Locale;

/** Kleine Aufzählungen rund um Figuren. */
public final class FigureEnums {
    private FigureEnums() {
    }

    /** Woher der Skin kommt. */
    public enum SkinType { DEFAULT, PLAYER, FILE, URL }

    /** Armmodell. AUTO erkennt Alex-Skins anhand der Textur bzw. der Mojang-Metadaten. */
    public enum Model { AUTO, WIDE, SLIM }

    public enum PetType { NONE, CAT, DOG, PARROT, SHEEP }

    public enum ColorFilter { NONE, RAINBOW, GHOST, RED, GREEN, BLUE, GOLD, DARK }

    /** Was eine Figur als "Infotafel" auf ihrem Schild zeigt. */
    public enum InfoBoard { NONE, TIME, COORDS, FPS, PING, DURABILITY, BIOME }

    public static <E extends Enum<E>> E parse(Class<E> type, String s, E fallback) {
        if (s == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, s.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    public static <E extends Enum<E>> E next(E v) {
        E[] all = v.getDeclaringClass().getEnumConstants();
        return all[(v.ordinal() + 1) % all.length];
    }
}
