package de.minicompanions.skin;

import net.minecraft.client.texture.NativeImage;

/** Hilfen für Skin-Bilder: 64x32 nach 64x64 umwandeln, Alex-Modell erkennen. */
public final class SkinImages {
    private SkinImages() {
    }

    /**
     * Gibt ein 64x64-Bild zurück. 64x32-Skins (altes Format) werden umgewandelt: linkes Bein und
     * linker Arm entstehen gespiegelt aus rechtem Bein/Arm (wie in Vanilla).
     *
     * @return dasselbe Bild, ein neues Bild oder null bei ungültiger Größe
     */
    public static NativeImage normalize(NativeImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        if (w == 64 && h == 64) {
            return img;
        }
        if (w != 64 || h != 32) {
            return null;
        }
        NativeImage out = new NativeImage(64, 64, true);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 64; x++) {
                out.setColorArgb(x, y, img.getColorArgb(x, y));
            }
        }
        // Gliedmaßen: rechte Seite (u0, v0) -> linke Seite (u1, v1), Flächen gespiegelt
        copyLimb(out, 0, 16, 16, 48);  // Bein
        copyLimb(out, 40, 16, 32, 48); // Arm
        return out;
    }

    /** Kopiert eine 4x12x4-Gliedmaße mit horizontal gespiegelten Flächen. */
    private static void copyLimb(NativeImage img, int u0, int v0, int u1, int v1) {
        // obere/untere Fläche (je 4x4)
        mirrorRect(img, u0 + 4, v0, u1 + 4, v1, 4, 4);
        mirrorRect(img, u0 + 8, v0, u1 + 8, v1, 4, 4);
        // Seiten: rechts<->links tauschen, vorne/hinten spiegeln
        mirrorRect(img, u0, v0 + 4, u1 + 8, v1 + 4, 4, 12);
        mirrorRect(img, u0 + 4, v0 + 4, u1 + 4, v1 + 4, 4, 12);
        mirrorRect(img, u0 + 8, v0 + 4, u1, v1 + 4, 4, 12);
        mirrorRect(img, u0 + 12, v0 + 4, u1 + 12, v1 + 4, 4, 12);
    }

    private static void mirrorRect(NativeImage img, int sx, int sy, int dx, int dy, int w, int h) {
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                img.setColorArgb(dx + (w - 1 - x), dy + y, img.getColorArgb(sx + x, sy + y));
            }
        }
    }

    /** Alex-Skins haben 3 Pixel breite Arme: die Spalten 54/55 im Armbereich sind durchsichtig. */
    public static boolean detectSlim(NativeImage img) {
        if (img.getWidth() != 64 || img.getHeight() != 64) {
            return false;
        }
        int transparent = 0;
        for (int y = 20; y < 32; y++) {
            if ((img.getColorArgb(54, y) >>> 24) == 0 && (img.getColorArgb(55, y) >>> 24) == 0) {
                transparent++;
            }
        }
        return transparent >= 10;
    }
}
