package de.minicompanions.render;

import de.minicompanions.Mini;
import de.minicompanions.ai.Activity;
import de.minicompanions.config.MiniConfig;
import de.minicompanions.figure.Accessory;
import de.minicompanions.figure.ArmorMode;
import de.minicompanions.figure.Figure;
import de.minicompanions.figure.FigureEnums;
import de.minicompanions.figure.Pose;
import de.minicompanions.skin.SkinManager;
import de.minicompanions.util.Colors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;

/**
 * Zeichnet eine Figur als 2D-Gliederpuppe aus der Skin-Textur: Kopf, Körper, Arme und Beine werden
 * einzeln transformiert. Vorder-, Rück- und Seitenansicht nutzen die passenden Skin-Flächen.
 * Die Textur wird mit Nearest-Neighbor gesampelt, daher bleibt die Pixel-Grafik scharf.
 */
public final class FigureRenderer {
    private FigureRenderer() {
    }

    /** UV-Bereich einer Fläche: u, v, Breite, Höhe. */
    private record Face(int u, int v, int w, int h) {
    }

    /** Hauptfarbe (ARGB-Multiplikator) für Farbfilter und Transparenz. */
    public static int tint(Figure f, MiniConfig cfg, float alphaMul) {
        float a = f.data.alpha / 100f * cfg.globalAlpha / 100f * alphaMul;
        if (f.hiding || f.activity == Activity.HIDE) {
            a *= 0.45f;
        }
        int rgb = 0xFFFFFF;
        if (cfg.colorFilters) {
            FigureEnums.ColorFilter cf = FigureEnums.parse(FigureEnums.ColorFilter.class, f.data.colorFilter, FigureEnums.ColorFilter.NONE);
            switch (cf) {
                case RAINBOW -> rgb = Colors.hsv((f.animTime * 0.25f) % 1f, 0.45f, 1f, 255) & 0xFFFFFF;
                case GHOST -> {
                    rgb = 0xC8E4FF;
                    a *= 0.5f;
                }
                case RED -> rgb = 0xFF9090;
                case GREEN -> rgb = 0x90FF90;
                case BLUE -> rgb = 0x9AB4FF;
                case GOLD -> rgb = 0xFFE07A;
                case DARK -> rgb = 0x707070;
                default -> {
                }
            }
        }
        int alpha = Math.max(8, Math.min(255, Math.round(a * 255)));
        return (alpha << 24) | rgb;
    }

    /**
     * Zeichnet die Figur. (x, y) = Fußmitte in GUI-Koordinaten, {@code unit} = GUI-Pixel pro Skin-Pixel.
     */
    /** Erzwungene Ansicht (Vorschau mit Drehung im Editor) oder null. */
    public static Pose.View forcedView;

    public static void render(DrawContext ctx, Figure f, float x, float y, float unit, MiniConfig cfg, boolean detail) {
        Pose p = f.pose;
        Animator.animate(f, p);
        if (forcedView != null) {
            p.view = forcedView;
        }
        SkinManager.Skin skin = f.skin;
        Identifier tex = skin == null ? SkinManager.STEVE : skin.texture;
        boolean slim = f.slim();
        int color = tint(f, cfg, 1f);
        int dark = Colors.darken(color, 0.72f);
        Matrix3x2fStack m = ctx.getMatrices();

        // Schatten
        if (detail && cfg.shadows && f.body.onGround && p.roll == 0 && f.activity != Activity.HANG) {
            int sw = Math.max(2, Math.round(f.body.w * 0.8f));
            int sx = Math.round(x);
            int sy = Math.round(y);
            ctx.fill(sx - sw, sy - 1, sx + sw, sy, Colors.mulAlpha(0x60000000, (color >>> 24) / 255f));
        }

        m.pushMatrix();
        m.translate(x, y);
        m.scale(unit, unit);
        m.translate(p.shiftX, p.bobY);
        if (p.roll != 0) {
            if (f.activity == Activity.SLEEP) {
                m.translate(-16, -4);
                m.rotate(p.roll);
            } else {
                m.translate(0, -16);
                m.rotate(p.roll);
                m.translate(0, 16);
            }
        }
        if (p.squash != 1f) {
            if (f.activity == Activity.SPAWN) {
                m.scale(p.squash, p.squash);
            } else {
                m.scale(p.squash < 1f ? 1f + (1f - p.squash) * 0.5f : 1f, p.squash);
            }
        }

        int dir = p.view == Pose.View.LEFT ? -1 : 1;
        boolean front = p.view == Pose.View.FRONT;
        boolean back = p.view == Pose.View.BACK;
        boolean sideView = !front && !back;

        // Zubehör hinter dem Körper (Umhang/Rucksack von vorne)
        if (detail && cfg.accessories) {
            drawBackAccessories(ctx, f, p, dir, true);
        }

        // --- Beine ---
        if (sideView) {
            // fernes Bein zuerst (dunkler); gleiche Drehrichtung wie das nahe Bein, damit sie gegenläufig schwingen
            boolean seeLeft = p.view == Pose.View.LEFT; // nach rechts laufend sieht man die rechte Körperseite
            drawLimb(ctx, tex, 0, -12, -2, 4, 12 * p.legScale, (seeLeft ? p.legR : p.legL) * dir,
                    seeLeft ? limb(RLEG, LEFT_FACE, false) : limb(LLEG, RIGHT_FACE, false),
                    seeLeft ? limb(RLEG_O, LEFT_FACE, false) : limb(LLEG_O, RIGHT_FACE, false), dark, p.legScale);
        }
        if (!sideView) {
            float lr = front ? p.legR : -p.legL;
            float ll = front ? -p.legL : p.legR;
            Face rf = front ? limb(RLEG, FRONT_FACE, false) : limb(LLEG, BACK_FACE, false);
            Face ro = front ? limb(RLEG_O, FRONT_FACE, false) : limb(LLEG_O, BACK_FACE, false);
            Face lf = front ? limb(LLEG, FRONT_FACE, false) : limb(RLEG, BACK_FACE, false);
            Face lo = front ? limb(LLEG_O, FRONT_FACE, false) : limb(RLEG_O, BACK_FACE, false);
            drawLimb(ctx, tex, -2, -12, -2, 4, 12 * p.legScale, lr, rf, ro, color, p.legScale);
            drawLimb(ctx, tex, 2, -12, -2, 4, 12 * p.legScale, -ll, lf, lo, color, p.legScale);
        } else {
            boolean seeLeft = p.view == Pose.View.LEFT; // nach rechts laufend sieht man die rechte Körperseite
            drawLimb(ctx, tex, 0, -12, -2, 4, 12 * p.legScale, (seeLeft ? p.legL : p.legR) * dir,
                    seeLeft ? limb(LLEG, LEFT_FACE, false) : limb(RLEG, RIGHT_FACE, false),
                    seeLeft ? limb(LLEG_O, LEFT_FACE, false) : limb(RLEG_O, RIGHT_FACE, false), color, p.legScale);
        }

        // --- Oberkörper (dreht sich um die Hüfte) ---
        m.pushMatrix();
        m.translate(0, -12);
        m.rotate(p.body * (sideView ? dir : 1));
        m.translate(0, 12);

        // fernerer Arm in Seitenansicht
        if (sideView) {
            boolean seeLeft = p.view == Pose.View.LEFT; // nach rechts laufend sieht man die rechte Körperseite
            int aw = 4;
            drawLimb(ctx, tex, 0, -22, -2, aw, 12, (seeLeft ? p.armR : p.armL) * dir,
                    seeLeft ? limbArm(true, LEFT_FACE, slim, false) : limbArm(false, RIGHT_FACE, slim, false),
                    seeLeft ? limbArm(true, LEFT_FACE, slim, true) : limbArm(false, RIGHT_FACE, slim, true), dark, 1f);
        }

        // Körper
        Face bodyF;
        Face bodyO;
        int bx;
        int bw;
        if (front) {
            bodyF = new Face(20, 20, 8, 12);
            bodyO = new Face(20, 36, 8, 12);
            bx = -4;
            bw = 8;
        } else if (back) {
            bodyF = new Face(32, 20, 8, 12);
            bodyO = new Face(32, 36, 8, 12);
            bx = -4;
            bw = 8;
        } else if (p.view == Pose.View.LEFT) {
            bodyF = new Face(28, 20, 4, 12);
            bodyO = new Face(28, 36, 4, 12);
            bx = -2;
            bw = 4;
        } else {
            bodyF = new Face(16, 20, 4, 12);
            bodyO = new Face(16, 36, 4, 12);
            bx = -2;
            bw = 4;
        }
        quad(ctx, tex, bx, -24, bw, 12, bodyF, color);
        quad(ctx, tex, bx, -24, bw, 12, bodyO, color);
        int[] armor = detail ? armorColors(f, cfg) : NO_ARMOR;
        if (armor[1] != 0) {
            drawChestplate(ctx, bx, bw, armor[1]);
        }

        // Arme
        int aw = slim ? 3 : 4;
        if (front) {
            // rechter Arm der Figur = links im Bild
            drawLimb(ctx, tex, -4 - aw / 2f, -22, -aw / 2f, aw, 12, -p.armR, limbArm(true, FRONT_FACE, slim, false), limbArm(true, FRONT_FACE, slim, true), color, 1f);
            drawLimb(ctx, tex, 4 + aw / 2f, -22, -aw / 2f, aw, 12, p.armL, limbArm(false, FRONT_FACE, slim, false), limbArm(false, FRONT_FACE, slim, true), color, 1f);
        } else if (back) {
            drawLimb(ctx, tex, -4 - aw / 2f, -22, -aw / 2f, aw, 12, -p.armL, limbArm(false, BACK_FACE, slim, false), limbArm(false, BACK_FACE, slim, true), color, 1f);
            drawLimb(ctx, tex, 4 + aw / 2f, -22, -aw / 2f, aw, 12, p.armR, limbArm(true, BACK_FACE, slim, false), limbArm(true, BACK_FACE, slim, true), color, 1f);
        }

        // Rückseiten-Zubehör (Umhang/Rucksack von hinten/seitlich)
        if (detail && cfg.accessories) {
            drawBackAccessories(ctx, f, p, dir, false);
            drawNeckAccessories(ctx, f, p, dir);
        }

        // Kopf
        m.pushMatrix();
        m.translate(0, -24 + p.headY);
        m.rotate(p.head);
        Face headF;
        Face headO;
        if (front) {
            headF = new Face(8, 8, 8, 8);
            headO = new Face(40, 8, 8, 8);
        } else if (back) {
            headF = new Face(24, 8, 8, 8);
            headO = new Face(56, 8, 8, 8);
        } else if (p.view == Pose.View.LEFT) {
            headF = new Face(16, 8, 8, 8);
            headO = new Face(48, 8, 8, 8);
        } else {
            headF = new Face(0, 8, 8, 8);
            headO = new Face(32, 8, 8, 8);
        }
        quad(ctx, tex, -4, -8, 8, 8, headF, color);
        quad(ctx, tex, -4, -8, 8, 8, headO, color);
        if (p.eyesClosed && front) {
            int ec = Colors.mulAlpha(0xFF2A1E18, (color >>> 24) / 255f);
            ctx.fill(-3, -4, -1, -3, ec);
            ctx.fill(1, -4, 3, -3, ec);
        }
        if (armor[0] != 0) {
            drawHelmet(ctx, armor[0]);
        }
        if (detail && cfg.accessories) {
            drawHeadAccessories(ctx, f, p, dir);
        }
        // Regenschirm / Laterne (Reaktion auf Wetter und Nacht, nur auf dem Bildschirm)
        m.popMatrix();

        // naher Arm in Seitenansicht (über dem Körper)
        if (sideView) {
            boolean seeLeft = p.view == Pose.View.LEFT; // nach rechts laufend sieht man die rechte Körperseite
            drawLimb(ctx, tex, 0, -22, -2, 4, 12, (seeLeft ? p.armL : p.armR) * dir,
                    seeLeft ? limbArm(false, LEFT_FACE, slim, false) : limbArm(true, RIGHT_FACE, slim, false),
                    seeLeft ? limbArm(false, LEFT_FACE, slim, true) : limbArm(true, RIGHT_FACE, slim, true), color, 1f);
        }
        // Gegenstände in der Hand
        if (detail) {
            drawHandItems(ctx, f, p, dir, front, sideView);
        }
        m.popMatrix(); // Oberkörper

        if (f.minecart) {
            PixelSprites.minecart(ctx, color);
        }
        m.popMatrix();
    }

    // ------------------------------------------------------------------ Skin-Flächen

    private static final int RIGHT_FACE = 0;
    private static final int FRONT_FACE = 1;
    private static final int LEFT_FACE = 2;
    private static final int BACK_FACE = 3;
    // Gliedmaßen-Basis (u0, v0)
    private static final int[] RLEG = {0, 16};
    private static final int[] RLEG_O = {0, 32};
    private static final int[] LLEG = {16, 48};
    private static final int[] LLEG_O = {0, 48};

    private static Face limb(int[] base, int face, boolean slim) {
        int u0 = base[0];
        int v0 = base[1] + 4;
        return switch (face) {
            case RIGHT_FACE -> new Face(u0, v0, 4, 12);
            case FRONT_FACE -> new Face(u0 + 4, v0, 4, 12);
            case LEFT_FACE -> new Face(u0 + 8, v0, 4, 12);
            default -> new Face(u0 + 12, v0, 4, 12);
        };
    }

    /** Arm-Flächen (rechter Arm u0=40,v0=16 / linker Arm u0=32,v0=48; Overlays +16 bzw. u0=48). */
    private static Face limbArm(boolean rightArm, int face, boolean slim, boolean overlay) {
        int u0 = rightArm ? 40 : (overlay ? 48 : 32);
        int v0 = rightArm ? (overlay ? 32 : 16) : 48;
        v0 += 4;
        int fw = slim ? 3 : 4;
        return switch (face) {
            case RIGHT_FACE -> new Face(u0, v0, 4, 12);
            case FRONT_FACE -> new Face(u0 + 4, v0, fw, 12);
            case LEFT_FACE -> new Face(u0 + 4 + fw, v0, 4, 12);
            default -> new Face(u0 + 8 + fw, v0, fw, 12);
        };
    }

    private static void quad(DrawContext ctx, Identifier tex, int x, int y, int w, int h, Face face, int color) {
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, tex, x, y, face.u, face.v, w, h, face.w, face.h, 64, 64, color);
    }

    /**
     * Gliedmaße: Drehpunkt (px, py), Winkel, Rechteck ab (offX, 0) mit Breite w und Höhe h.
     */
    private static void drawLimb(DrawContext ctx, Identifier tex, float px, float py, float offX, int w, float h, float angle,
                                 Face base, Face overlay, int color, float scaleY) {
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(px, py);
        m.rotate(angle);
        if (scaleY != 1f) {
            m.scale(1f, Math.max(0.05f, scaleY));
        }
        m.translate(offX, 0);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, tex, 0, 0, base.u, base.v, w, 12, base.w, base.h, 64, 64, color);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, tex, 0, 0, overlay.u, overlay.v, w, 12, overlay.w, overlay.h, 64, 64, color);
        m.popMatrix();
    }

    // ------------------------------------------------------------------ Ausrüstung des Spielers

    private static int armorColor(ItemStack s) {
        if (s == null || s.isEmpty()) {
            return 0;
        }
        String id = Registries.ITEM.getId(s.getItem()).getPath();
        if (id.contains("netherite")) {
            return 0xFF4A4245;
        }
        if (id.contains("diamond")) {
            return 0xFF5CDBD5;
        }
        if (id.contains("golden")) {
            return 0xFFF2D13A;
        }
        if (id.contains("iron")) {
            return 0xFFD8D8D8;
        }
        if (id.contains("chainmail")) {
            return 0xFF9A9A9A;
        }
        if (id.contains("leather")) {
            return 0xFF8A5A30;
        }
        if (id.contains("turtle")) {
            return 0xFF47A036;
        }
        if (id.contains("copper")) {
            return 0xFFC77A4A;
        }
        return 0;
    }

    private static final int[] NO_ARMOR = {0, 0};

    /** Farben für Helm und Brustpanzer dieser Figur (0 = keiner), je nach Einstellung. */
    private static int[] armorColors(Figure f, MiniConfig cfg) {
        String mode = !f.viewer && f.data.armor != null && !f.data.armor.isEmpty() ? f.data.armor : cfg.armorMode;
        ArmorMode a = ArmorMode.parse(mode);
        if (a == null || a == ArmorMode.NONE) {
            return NO_ARMOR;
        }
        if (a == ArmorMode.PLAYER) {
            // Zuschauer-Figuren tragen nie die Rüstung des Streamers
            var pl = MinecraftClient.getInstance().player;
            if (f.viewer || pl == null) {
                return NO_ARMOR;
            }
            return new int[] {armorColor(pl.getEquippedStack(EquipmentSlot.HEAD)), armorColor(pl.getEquippedStack(EquipmentSlot.CHEST))};
        }
        return new int[] {a.helmet, a.chest};
    }

    private static void drawHelmet(DrawContext ctx, int c) {
        ctx.fill(-5, -9, 5, -6, c);
        ctx.fill(-5, -6, -4, -2, c);
        ctx.fill(4, -6, 5, -2, c);
        ctx.fill(-5, -9, 5, -8, Colors.darken(c, 0.8f));
    }

    private static void drawChestplate(DrawContext ctx, int bx, int bw, int c) {
        ctx.fill(bx, -24, bx + bw, -15, Colors.withAlpha(c, 0xE0));
        ctx.fill(bx, -16, bx + bw, -15, Colors.darken(c, 0.7f));
    }

    private static void drawHandItems(DrawContext ctx, Figure f, Pose p, int dir, boolean front, boolean side) {
        ItemStack item = f.carriedItem;
        boolean lantern = false;
        if ((item == null || item.isEmpty()) && Mini.cfg().equipmentMirror && !f.viewer) {
            var pl = MinecraftClient.getInstance().player;
            if (pl != null) {
                item = pl.getMainHandStack();
            }
        }
        if (Mini.events != null && Mini.cfg().nightLantern && Mini.events.isNightOutside() && (item == null || item.isEmpty())) {
            lantern = true;
        }
        Matrix3x2fStack m = ctx.getMatrices();
        float hx = front ? -6 : 0;
        float angle = front ? -p.armR : p.armR * dir;
        m.pushMatrix();
        m.translate(hx, -22);
        m.rotate(angle);
        m.translate(0, 11);
        if (item != null && !item.isEmpty()) {
            m.scale(0.4f, 0.4f);
            ctx.drawItem(item, -8, -4);
        } else if (lantern) {
            PixelSprites.lantern(ctx);
        }
        m.popMatrix();
        if (Mini.events != null && Mini.cfg().reactWeather && Mini.events.isRainingOutside()
                && f.activity != Activity.SLEEP && f.activity != Activity.CLIMB) {
            PixelSprites.umbrella(ctx, front ? 6 : 0);
        }
    }

    // ------------------------------------------------------------------ Accessoires

    private static boolean has(Figure f, Accessory a) {
        return f.data.accessories.contains(a.name());
    }

    private static void drawHeadAccessories(DrawContext ctx, Figure f, Pose p, int dir) {
        boolean front = p.view == Pose.View.FRONT;
        boolean back = p.view == Pose.View.BACK;
        for (String s : f.data.accessories) {
            if (s.startsWith("custom:")) {
                AccessoryTextures.drawCustom(ctx, s.substring(7));
                continue;
            }
            Accessory a = Accessory.parse(s);
            if (a == null) {
                continue;
            }
            switch (a) {
                case TOP_HAT -> PixelSprites.topHat(ctx);
                case PARTY_HAT -> PixelSprites.partyHat(ctx);
                case CROWN -> PixelSprites.crown(ctx);
                case CAP -> PixelSprites.cap(ctx, front ? 0 : (back ? 0 : dir));
                case HEADPHONES -> PixelSprites.headphones(ctx, front || back);
                case GLASSES -> {
                    if (!back) {
                        PixelSprites.glasses(ctx, front ? 0 : dir, false);
                    }
                }
                case SUNGLASSES -> {
                    if (!back) {
                        PixelSprites.glasses(ctx, front ? 0 : dir, true);
                    }
                }
                default -> {
                }
            }
        }
    }

    private static void drawBackAccessories(DrawContext ctx, Figure f, Pose p, int dir, boolean behind) {
        boolean front = p.view == Pose.View.FRONT;
        boolean back = p.view == Pose.View.BACK;
        if (has(f, Accessory.CAPE)) {
            if (behind && front) {
                PixelSprites.capeFront(ctx);
            } else if (!behind && back) {
                PixelSprites.capeBack(ctx, f.animTime);
            } else if (!behind && !front && !back) {
                PixelSprites.capeSide(ctx, dir, f.animTime, Math.abs(f.body.vx) > 1);
            }
        }
        if (has(f, Accessory.BACKPACK)) {
            if (!behind && back) {
                PixelSprites.backpackBack(ctx);
            } else if (!behind && !front) {
                PixelSprites.backpackSide(ctx, dir);
            } else if (!behind && front) {
                PixelSprites.backpackStraps(ctx);
            }
        }
    }

    private static void drawNeckAccessories(DrawContext ctx, Figure f, Pose p, int dir) {
        if (has(f, Accessory.SCARF)) {
            PixelSprites.scarf(ctx, p.view == Pose.View.FRONT || p.view == Pose.View.BACK ? 0 : dir, f.animTime);
        }
        if (has(f, Accessory.BOW_TIE) && p.view == Pose.View.FRONT) {
            PixelSprites.bowTie(ctx);
        }
    }
}
