package de.minicompanions.figure;

import de.minicompanions.physics.Ledge;
import de.minicompanions.physics.PlatformSet;

import java.util.ArrayList;
import java.util.List;

/**
 * Eine Welt, in der Figuren leben: der Bildschirm (mit den Plattformen des offenen Screens) oder
 * das Zimmer-Fenster. Wird pro Frame aktualisiert.
 */
public final class WorldView {
    /** Welche Art Screen gerade offen ist (steuert Plattform-Vorlieben der KI). */
    public enum Kind { GAME, INVENTORY, CHAT, OTHER_SCREEN, ROOM }

    public PlatformSet platforms = new PlatformSet(320, 240);
    public final List<Figure> figures = new ArrayList<>();
    public Kind kind = Kind.GAME;
    public float mouseX = -1000;
    public float mouseY = -1000;
    /** Ursprung der Welt auf dem Bildschirm (Zimmer: Inhaltsbereich des Fensters). */
    public float originX;
    public float originY;
    /** Nacht in der Spielwelt (Müdigkeit, Laternen). */
    public boolean night;

    public boolean isRoom() {
        return kind == Kind.ROOM;
    }

    /** Bevorzugte Plattformarten je nach Screen. */
    public float preference(Ledge l) {
        return switch (kind) {
            case INVENTORY -> switch (l.kind) {
                case SLOT -> 3f;
                case WINDOW -> 2f;
                case WIDGET -> 1f;
                case GROUND -> 0.6f;
                default -> 0.8f;
            };
            case CHAT -> switch (l.kind) {
                case CHAT -> 3f;
                case CHAT_INPUT -> 2.5f;
                case GROUND -> 0.5f;
                default -> 0.8f;
            };
            case OTHER_SCREEN -> switch (l.kind) {
                case WIDGET -> 2.5f;
                case GROUND -> 0.8f;
                default -> 1f;
            };
            case ROOM -> 1f;
            default -> switch (l.kind) {
                case HUD -> 1.6f;
                case GROUND -> 1.5f;
                case OVERLAY_WINDOW -> 1.2f;
                default -> 1f;
            };
        };
    }
}
