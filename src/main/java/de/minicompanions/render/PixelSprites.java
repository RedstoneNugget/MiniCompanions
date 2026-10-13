package de.minicompanions.render;

import de.minicompanions.figure.Emote;
import de.minicompanions.figure.FigureEnums;
import de.minicompanions.util.Colors;
import net.minecraft.client.gui.DrawContext;

/**
 * Kleine Pixel-Grafiken, direkt mit Rechtecken gezeichnet (keine Textur-Dateien nötig).
 * Koordinaten sind lokale Pixel im aktuellen Matrix-Raum (Skin-Pixel bzw. Raum-Pixel).
 */
public final class PixelSprites {
    private PixelSprites() {
    }

    private static void r(DrawContext c, int x1, int y1, int x2, int y2, int col) {
        c.fill(x1, y1, x2, y2, col);
    }

    // =================================================================== Kopf-Accessoires (Kopf: x -4..4, y -8..0)

    public static void topHat(DrawContext c) {
        r(c, -6, -9, 6, -8, 0xFF1A1A1A);
        r(c, -4, -15, 4, -9, 0xFF222222);
        r(c, -4, -11, 4, -10, 0xFFB02020);
        r(c, -4, -15, 4, -14, 0xFF333333);
    }

    public static void partyHat(DrawContext c) {
        int[] cols = {0xFFE84A5F, 0xFFFFD34D, 0xFF4AB8E8};
        for (int i = 0; i < 7; i++) {
            int half = 4 - (i * 4) / 7;
            r(c, -half, -9 - i, half + (half == 0 ? 1 : 0), -8 - i, cols[(i / 2) % 3]);
        }
        r(c, -1, -18, 1, -16, 0xFFFFFFFF);
    }

    public static void crown(DrawContext c) {
        int g = 0xFFF5C518;
        r(c, -4, -10, 4, -8, g);
        r(c, -4, -12, -3, -10, g);
        r(c, -1, -12, 1, -10, g);
        r(c, 3, -12, 4, -10, g);
        r(c, -1, -10, 1, -9, 0xFFE02020);
    }

    public static void cap(DrawContext c, int dir) {
        int col = 0xFF2F5FD0;
        r(c, -4, -10, 4, -7, col);
        r(c, -4, -10, 4, -9, Colors.darken(col, 0.8f));
        if (dir == 0) {
            r(c, -4, -7, 4, -6, Colors.darken(col, 0.7f));
        } else if (dir > 0) {
            r(c, 4, -8, 8, -7, Colors.darken(col, 0.7f));
        } else {
            r(c, -8, -8, -4, -7, Colors.darken(col, 0.7f));
        }
    }

    public static void headphones(DrawContext c, boolean frontOrBack) {
        int band = 0xFF303030;
        r(c, -5, -9, 5, -8, band);
        if (frontOrBack) {
            r(c, -6, -6, -4, -2, 0xFFE04040);
            r(c, 4, -6, 6, -2, 0xFFE04040);
            r(c, -5, -8, -4, -6, band);
            r(c, 4, -8, 5, -6, band);
        } else {
            r(c, -2, -6, 2, -2, 0xFFE04040);
            r(c, -1, -8, 1, -6, band);
        }
    }

    public static void glasses(DrawContext c, int dir, boolean sun) {
        int frame = 0xFF141414;
        int lens = sun ? 0xFF101010 : 0x80C8E8FF;
        if (dir == 0) {
            r(c, -4, -5, -1, -3, frame);
            r(c, 1, -5, 4, -3, frame);
            r(c, -3, -5, -2, -4, lens);
            r(c, 2, -5, 3, -4, lens);
            r(c, -1, -5, 1, -4, frame);
        } else {
            int x = dir > 0 ? 1 : -4;
            r(c, x, -5, x + 3, -3, frame);
            r(c, x + 1, -5, x + 2, -4, lens);
            r(c, dir > 0 ? -3 : 0, -5, dir > 0 ? 1 : 3, -4, frame);
        }
    }

    // =================================================================== Rücken/Hals (Oberkörper: x -4..4, y -24..-12)

    public static void capeFront(DrawContext c) {
        int col = 0xFFB01E2E;
        r(c, -5, -24, -4, -9, col);
        r(c, 4, -24, 5, -9, col);
    }

    public static void capeBack(DrawContext c, float t) {
        int col = 0xFFB01E2E;
        int sway = (int) Math.round(Math.sin(t * 2) * 0.6);
        r(c, -5, -24, 5, -10, col);
        r(c, -5 + sway, -10, 5 + sway, -6, Colors.darken(col, 0.85f));
        r(c, -5, -24, 5, -23, 0xFFE8C040);
    }

    public static void capeSide(DrawContext c, int dir, float t, boolean moving) {
        int col = 0xFFB01E2E;
        int flow = moving ? 3 : 1;
        int bx = -dir * 2;
        if (dir > 0) {
            r(c, bx - 1, -24, bx, -12, col);
            r(c, bx - 1 - flow, -12, bx, -7, Colors.darken(col, 0.85f));
        } else {
            r(c, bx, -24, bx + 1, -12, col);
            r(c, bx, -12, bx + 1 + flow, -7, Colors.darken(col, 0.85f));
        }
    }

    public static void backpackBack(DrawContext c) {
        r(c, -3, -23, 3, -14, 0xFF7A4E2A);
        r(c, -3, -19, 3, -18, 0xFF5A381E);
        r(c, -1, -18, 1, -16, 0xFFD0A040);
    }

    public static void backpackSide(DrawContext c, int dir) {
        int x = dir > 0 ? -5 : 2;
        r(c, x, -23, x + 3, -14, 0xFF7A4E2A);
        r(c, x, -19, x + 3, -18, 0xFF5A381E);
    }

    public static void backpackStraps(DrawContext c) {
        r(c, -3, -24, -2, -17, 0xFF5A381E);
        r(c, 2, -24, 3, -17, 0xFF5A381E);
    }

    public static void scarf(DrawContext c, int dir, float t) {
        int col = 0xFFD8342C;
        int stripe = 0xFFF0F0F0;
        if (dir == 0) {
            r(c, -4, -24, 4, -22, col);
            r(c, -2, -24, 0, -22, stripe);
            r(c, 1, -22, 3, -17, col);
        } else {
            r(c, -2, -24, 2, -22, col);
            int tail = (int) Math.round(Math.sin(t * 5) * 0.8);
            r(c, -dir * 2 - (dir > 0 ? 3 : 0), -23 + tail, -dir * 2 + (dir > 0 ? 0 : 3), -22 + tail, col);
        }
    }

    public static void bowTie(DrawContext c) {
        r(c, -2, -24, 2, -22, 0xFF8020A0);
        r(c, -1, -23, 1, -22, 0xFFB050D0);
    }

    // =================================================================== Gegenstände

    public static void lantern(DrawContext c) {
        r(c, -1, -1, 1, 0, 0xFF3A3A3A);
        r(c, -2, 0, 2, 4, 0xFF2A2A2A);
        r(c, -1, 1, 1, 3, 0xFFFFC040);
    }

    public static void umbrella(DrawContext c, int x) {
        int col = 0xFF3A7BD5;
        r(c, x, -38, x + 1, -22, 0xFF4A4A4A);
        r(c, x - 7, -39, x + 8, -38, col);
        r(c, x - 6, -41, x + 7, -39, col);
        r(c, x - 4, -42, x + 5, -41, Colors.darken(col, 0.85f));
        r(c, x - 7, -38, x - 6, -37, col);
        r(c, x + 7, -38, x + 8, -37, col);
    }

    public static void minecart(DrawContext c, int tint) {
        int a = tint >>> 24;
        int body = Colors.withAlpha(0xFF6E6E6E, a);
        r(c, -8, -8, 8, -1, body);
        r(c, -8, -8, 8, -7, Colors.withAlpha(0xFF9A9A9A, a));
        r(c, -6, -1, -3, 1, Colors.withAlpha(0xFF2A2A2A, a));
        r(c, 3, -1, 6, 1, Colors.withAlpha(0xFF2A2A2A, a));
    }

    /** Infotafel (Schild) mit Platz für Text - Ursprung: Stockfuß. */
    public static void signBoard(DrawContext c, int w) {
        r(c, -1, -14, 1, 0, 0xFF6B4F2A);
        r(c, -w / 2 - 1, -24, w / 2 + 1, -13, 0xFF4A3418);
        r(c, -w / 2, -23, w / 2, -14, 0xFFC9A26B);
    }

    // =================================================================== Emotes (7x7, Mitte unten = Ursprung)

    public static void emote(DrawContext c, Emote e, float t) {
        switch (e) {
            case HEART -> {
                int col = 0xFFE8344C;
                r(c, -3, -6, -1, -5, col);
                r(c, 1, -6, 3, -5, col);
                r(c, -4, -5, 4, -3, col);
                r(c, -3, -3, 3, -2, col);
                r(c, -2, -2, 2, -1, col);
                r(c, -1, -1, 1, 0, col);
                r(c, -3, -5, -2, -4, 0xFFFF9AAA);
            }
            case ZZZ -> {
                int col = 0xFFE8F0FF;
                int o = (int) (t * 2) % 3;
                r(c, -3 + o, -7, 1 + o, -6, col);
                r(c, -1 + o, -6, 0 + o, -5, col);
                r(c, -2 + o, -5, -1 + o, -4, col);
                r(c, -3 + o, -4, 1 + o, -3, col);
                r(c, 1, -2, 3, -1, col);
                r(c, 1, 0, 3, 1, col);
                r(c, 2, -1, 3, 0, col);
            }
            case EXCLAMATION -> {
                r(c, -1, -8, 1, -3, 0xFFFFD020);
                r(c, -1, -2, 1, 0, 0xFFFFD020);
            }
            case QUESTION -> {
                int col = 0xFF90D0FF;
                r(c, -2, -8, 2, -7, col);
                r(c, 2, -7, 3, -5, col);
                r(c, 0, -5, 2, -4, col);
                r(c, 0, -4, 1, -3, col);
                r(c, 0, -2, 1, -1, col);
            }
            case SWEAT -> {
                int col = 0xFF6EC8FF;
                r(c, 0, -6, 1, -5, col);
                r(c, -1, -5, 2, -3, col);
                r(c, 0, -3, 1, -2, col);
            }
            case NOTE -> {
                int col = 0xFFFFFFFF;
                int bob = (int) Math.round(Math.sin(t * 6));
                r(c, 0, -8 + bob, 1, -2 + bob, col);
                r(c, 1, -8 + bob, 3, -7 + bob, col);
                r(c, -2, -3 + bob, 1, -1 + bob, col);
            }
            case ANGER -> {
                int col = 0xFFE02020;
                r(c, -3, -6, -1, -5, col);
                r(c, 1, -6, 3, -5, col);
                r(c, -3, -3, -1, -2, col);
                r(c, 1, -3, 3, -2, col);
                r(c, -1, -5, 1, -3, col);
            }
            case STAR -> {
                int col = 0xFFFFE040;
                r(c, -1, -7, 1, -5, col);
                r(c, -4, -5, 4, -3, col);
                r(c, -2, -3, 2, -1, col);
                r(c, -3, -1, -1, 0, col);
                r(c, 1, -1, 3, 0, col);
            }
            case SPARKLE -> {
                int col = 0xFFFFFFFF;
                int s = (int) (t * 8) % 2;
                r(c, -1, -7 + s, 0, -1 - s, col);
                r(c, -3 - s, -4, 2 + s, -3, col);
            }
            case TEAR -> {
                r(c, 1, -6, 2, -4, 0xFF6EC8FF);
                r(c, 0, -4, 3, -2, 0xFF6EC8FF);
            }
            case BUBBLES -> {
                int o = (int) (t * 4) % 4;
                r(c, -2, -2 - o, 0, -o, 0xA0B0E8FF);
                r(c, 1, -6 - o / 2, 3, -4 - o / 2, 0xA0B0E8FF);
            }
            case SKULL -> {
                r(c, -3, -7, 3, -2, 0xFFEFEFEF);
                r(c, -2, -1, 2, 0, 0xFFEFEFEF);
                r(c, -2, -5, -1, -4, 0xFF202020);
                r(c, 1, -5, 2, -4, 0xFF202020);
            }
            case COLD -> {
                int col = 0xFFB8E8FF;
                r(c, -1, -7, 0, 0, col);
                r(c, -4, -4, 3, -3, col);
                r(c, -3, -6, -2, -5, col);
                r(c, 1, -6, 2, -5, col);
                r(c, -3, -2, -2, -1, col);
                r(c, 1, -2, 2, -1, col);
            }
            case IDEA -> {
                r(c, -2, -8, 2, -4, 0xFFFFF080);
                r(c, -1, -4, 1, -2, 0xFFB0B0B0);
            }
        }
    }

    // =================================================================== Haustiere (Ursprung: Fußmitte, Pixel)

    public static void pet(DrawContext c, FigureEnums.PetType type, float t, int facing, boolean moving) {
        int dir = facing < 0 ? -1 : 1;
        int step = moving ? (int) (t * 10) % 2 : 0;
        switch (type) {
            case CAT -> {
                int fur = 0xFFE0A050;
                int dark = 0xFFB07028;
                r(c, -4, -5, 4, -2, fur);
                r(c, -4, -2, -3, 0 - step, dark);
                r(c, 3, -2, 4, step - 1 + 1, dark);
                int hx = dir > 0 ? 3 : -7;
                r(c, hx, -8, hx + 4, -4, fur);
                r(c, hx, -9, hx + 1, -8, dark);
                r(c, hx + 3, -9, hx + 4, -8, dark);
                r(c, hx + (dir > 0 ? 2 : 1), -7, hx + (dir > 0 ? 3 : 2), -6, 0xFF203020);
                int tx = dir > 0 ? -6 : 4;
                r(c, tx, -7 + (int) Math.round(Math.sin(t * 4)), tx + 2, -5, dark);
            }
            case DOG -> {
                int fur = 0xFFD8D8D8;
                int dark = 0xFF9A9A9A;
                r(c, -5, -6, 5, -2, fur);
                r(c, -5, -2, -4, 0 - step, dark);
                r(c, 4, -2, 5, 0 - (1 - step), dark);
                int hx = dir > 0 ? 4 : -8;
                r(c, hx, -9, hx + 4, -5, fur);
                r(c, dir > 0 ? hx + 4 : hx - 1, -7, dir > 0 ? hx + 5 : hx, -6, 0xFF202020);
                r(c, hx + (dir > 0 ? 0 : 3), -9, hx + (dir > 0 ? 1 : 4), -6, dark);
                int tx = dir > 0 ? -7 : 5;
                r(c, tx, -8 + (moving ? (int) (t * 12) % 2 : 0), tx + 2, -6, fur);
                r(c, -4, -5, 4, -4, 0xFFC02020);
            }
            case PARROT -> {
                int wingUp = (int) (t * 14) % 2;
                r(c, -2, -6, 2, -1, 0xFFE02020);
                r(c, dir > 0 ? 1 : -3, -8, dir > 0 ? 3 : -1, -6, 0xFFE02020);
                r(c, dir > 0 ? 3 : -4, -7, dir > 0 ? 4 : -3, -6, 0xFFF0C030);
                r(c, -3, -5 - wingUp, -1, -2 - wingUp, 0xFF2060E0);
                r(c, -1, -1, 1, 1, 0xFF20A040);
            }
            case SHEEP -> {
                int wool = 0xFFF2F2F2;
                r(c, -6, -8, 6, -2, wool);
                r(c, -5, -2, -4, 0 - step, 0xFF505050);
                r(c, 4, -2, 5, 0 - (1 - step), 0xFF505050);
                int hx = dir > 0 ? 5 : -9;
                r(c, hx, -9, hx + 4, -5, 0xFFD8C8B8);
                r(c, hx + (dir > 0 ? 2 : 1), -8, hx + (dir > 0 ? 3 : 2), -7, 0xFF202020);
            }
            default -> {
            }
        }
    }

}
