package de.minicompanions.render;

import de.minicompanions.Mini;
import de.minicompanions.ai.Activity;
import de.minicompanions.config.MiniConfig;
import de.minicompanions.figure.BubbleQueue;
import de.minicompanions.figure.Figure;
import de.minicompanions.figure.FigureEnums;
import de.minicompanions.figure.WorldView;
import de.minicompanions.gui.ActionMenu;
import de.minicompanions.input.InputRouter;
import de.minicompanions.physics.Ledge;
import de.minicompanions.util.Colors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.RenderTickCounter;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Zeichnet das komplette Overlay: Figuren, Haustiere, Partikel, Emotes, Namensschilder, Sprechblasen,
 * Overlay-Fenster, Aktionsmenü und Statusanzeigen. Wird im HUD (kein Screen offen) oder nach dem
 * Rendern eines Screens aufgerufen - pro Bild genau einmal.
 */
public final class OverlayRenderer {
    private static final List<Figure> SORTED = new ArrayList<>();
    private static long lastNanos;

    private OverlayRenderer() {
    }

    /** HUD-Element (nur ohne offenen Screen). */
    public static void renderHud(DrawContext ctx, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen != null) {
            return;
        }
        render(ctx, -1000, -1000, tick.getTickProgress(false));
    }

    /** Nach dem Rendern eines beliebigen Screens. */
    public static void renderScreen(Screen screen, DrawContext ctx, int mx, int my, float delta) {
        render(ctx, mx, my, delta);
    }

    private static void render(DrawContext ctx, int mx, int my, float delta) {
        if (Mini.configs == null) {
            return;
        }
        MiniConfig cfg = Mini.cfg();
        if (!cfg.enabled) {
            return;
        }
        long now = System.nanoTime();
        if (now - lastNanos < 500_000) {
            return; // bereits in diesem Bild gezeichnet
        }
        float dt = lastNanos == 0 ? 0.016f : Math.min(0.05f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        InputRouter.updateMouse(mx, my);
        InputRouter.checkCarry();
        Mini.figures.frame(mx, my);
        Mini.particles.update(dt);
        Mini.inventory.frame(dt);
        if (RecordingMode.isHidden()) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        double gs = mc.getWindow().getScaleFactor();
        WorldView screen = Mini.figures.screen;
        // Zimmer-Fenster zuerst, damit Figuren auf seiner Titelleiste davor stehen
        ctx.createNewRootLayer();
        Mini.room.render(ctx, tr, mx, my, gs);
        ctx.createNewRootLayer();
        drawWorld(ctx, tr, screen, 0, 0, gs, cfg, screen.platforms.width);
        Mini.particles.render(ctx, 0, 0);
        // getragene Figur ganz oben
        Figure carried = InputRouter.carried();
        if (carried != null) {
            ctx.createNewRootLayer();
            if (Mini.room.contentHit(mx, my)) {
                Mini.room.renderDropHint(ctx);
            }
            float unit = carried.unit(cfg, gs);
            FigureRenderer.render(ctx, carried, carried.body.x, carried.body.y, unit, cfg, true);
        }
        ctx.createNewRootLayer();
        if (!(mc.currentScreen instanceof de.minicompanions.gui.BaseScreen)) {
            HudStatus.render(ctx, tr, screen.platforms.width, screen.platforms.height);
        }
        if (cfg.debugOverlay) {
            DebugOverlay.render(ctx, tr, screen, 0, 0);
        }
        ActionMenu.renderCurrent(ctx, tr, mx, my);
    }

    /** Zeichnet alle Figuren einer Welt (Bildschirm oder Zimmer mit Ursprung ox/oy). */
    public static void drawWorld(DrawContext ctx, TextRenderer tr, WorldView w, float ox, float oy, double gs, MiniConfig cfg, float worldW) {
        SORTED.clear();
        for (Figure f : w.figures) {
            if (!f.carried) {
                SORTED.add(f);
            }
        }
        SORTED.sort(Comparator.comparingDouble(f -> f.body.y));
        boolean lod = SORTED.size() > 300;
        float screenW = ox + worldW;
        float screenH = oy + w.platforms.height;
        for (Figure f : SORTED) {
            float x = ox + f.body.x;
            float y = oy + f.body.y;
            if (x < ox - 40 || x > screenW + 40 || y < oy - 10 || y > screenH + 80) {
                continue; // Culling
            }
            float unit = f.unit(cfg, gs);
            drawPet(ctx, f, ox, oy, unit, cfg);
            FigureRenderer.render(ctx, f, x, y, unit, cfg, !lod);
        }
        ctx.createNewRootLayer();
        long nowMs = System.currentTimeMillis();
        for (Figure f : SORTED) {
            float x = ox + f.body.x;
            float y = oy + f.body.y;
            if (x < ox - 40 || x > screenW + 40 || y < oy - 10 || y > screenH + 80) {
                continue;
            }
            drawAbove(ctx, tr, f, x, y, f.unit(cfg, gs), cfg, nowMs, w.isRoom() ? screenW : Math.max(screenW, worldW), lod);
        }
    }

    private static void drawPet(DrawContext ctx, Figure f, float ox, float oy, float unit, MiniConfig cfg) {
        if (!cfg.pets) {
            return;
        }
        FigureEnums.PetType type = FigureEnums.parse(FigureEnums.PetType.class, f.data.pet, FigureEnums.PetType.NONE);
        if (type == FigureEnums.PetType.NONE) {
            return;
        }
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(ox + f.petX, oy + f.petY);
        float s = Math.max(0.25f, unit * 0.9f);
        m.scale(s, s);
        boolean moving = Math.abs(f.petX - (f.body.x - f.facing * f.body.w * 1.7f)) > 2 || Math.abs(f.body.vx) > 1;
        PixelSprites.pet(ctx, type, f.petAnim, f.facing, moving);
        m.popMatrix();
    }

    /** Emote, Infotafel, Namensschild und Sprechblase über der Figur. */
    private static void drawAbove(DrawContext ctx, TextRenderer tr, Figure f, float x, float y, float unit, MiniConfig cfg, long nowMs,
                                  float screenW, boolean lod) {
        float top = y - f.body.h - unit * 2;
        if (f.activity == Activity.SLEEP) {
            top = y - 9 * unit;
        } else if (f.activity == Activity.SIT || f.activity == Activity.RIDE) {
            top += 7.8f * unit;
        } else if (f.activity == Activity.SIT_EDGE) {
            top += 12 * unit;
        } else if (f.activity == Activity.HANG && !f.body.climbing) {
            top += 26 * unit;
        }
        float alpha = (f.data.alpha / 100f) * (cfg.globalAlpha / 100f);
        // Namensschild und Blase wachsen mit der Figur, aber in vernünftigen Grenzen (lesbar, nie bildschirmfüllend)
        float nameScale = Math.max(0.5f, Math.min(1.25f, 0.35f + unit * 0.7f));
        float bubbleBase = Math.max(0.6f, Math.min(1.5f, 0.55f + unit * 0.6f));
        Matrix3x2fStack m = ctx.getMatrices();
        // Infotafel
        FigureEnums.InfoBoard ib = FigureEnums.parse(FigureEnums.InfoBoard.class, f.data.infoBoard, FigureEnums.InfoBoard.NONE);
        if (ib != FigureEnums.InfoBoard.NONE && !lod && Mini.events != null) {
            String text = Mini.events.infoText(ib);
            int tw = tr.getWidth(text);
            m.pushMatrix();
            m.translate(x + f.body.w * 1.4f, y);
            m.scale(unit, unit);
            int boardW = Math.max(16, (int) Math.ceil(tw * 0.5f) + 4);
            PixelSprites.signBoard(ctx, boardW);
            m.translate(0, -19);
            m.scale(0.5f, 0.5f);
            ctx.drawText(tr, text, -tw / 2, -4, 0xFF2A1A0A, false);
            m.popMatrix();
        }
        if (f.emote != null && cfg.emotes) {
            m.pushMatrix();
            m.translate(x, top);
            float es = Math.max(0.6f, unit * 1.2f);
            m.scale(es, es);
            PixelSprites.emote(ctx, f.emote, f.animTime);
            m.popMatrix();
            top -= 9 * es;
        }
        if (lod) {
            return;
        }
        String name = null;
        int nameColor = 0xFFFFFFFF;
        if (f.viewer) {
            name = f.viewerDisplay;
            if (platformColors()) {
                // mehrere Plattformen: Name in der Farbe der Plattform (Twitch lila, Kick grün, ...)
                nameColor = f.viewerPlatform.color();
            } else if (Mini.streamer.cfg().roleColors) {
                nameColor = roleColor(f, 0xFFFFFFFF);
            }
        } else if (cfg.nameTags && f.data.showName) {
            name = f.data.name;
        }
        if (name != null) {
            top -= BubbleRenderer.nameTag(ctx, tr, name, nameColor, x, top, nameScale, alpha);
        }
        BubbleQueue.Bubble b = f.bubbles.current();
        if (b != null) {
            if (b.viewer() && (!Mini.streamer.messagesVisible() || f.hiddenMessages)) {
                return;
            }
            float opacity = 1f;
            float bs = bubbleBase * cfg.bubbleScale / 100f;
            if (b.viewer()) {
                opacity = Mini.streamer.cfg().bubbleOpacity / 100f;
                bs = bubbleBase * Mini.streamer.cfg().bubbleScale / 100f;
            }
            BubbleRenderer.render(ctx, tr, b, f.bubbles.alpha(nowMs), x, top, bs, opacity, screenW);
        }
    }

    /** Namen in Plattform-Farbe? (Einstellung: aus / nur bei mehreren Plattformen / immer) */
    public static boolean platformColors() {
        String mode = Mini.streamer.cfg().platformNameColors;
        return "ALWAYS".equals(mode) || "MULTI".equals(mode) && Mini.streamer.activePlatformCount() > 1;
    }

    /** Rollen-Farbe (Mod grün, VIP pink, Sub lila) für Zuschauer. */
    public static int roleColor(Figure f, int fallback) {
        var r = f.viewerRoles;
        if (r == null) {
            return fallback;
        }
        if (r.broadcaster()) {
            return 0xFFFF5050;
        }
        if (r.moderator()) {
            return 0xFF40D040;
        }
        if (r.vip()) {
            return 0xFFFF70D0;
        }
        if (r.subscriber()) {
            return 0xFFB080FF;
        }
        if (f.viewerColor != null) {
            return 0xFF000000 | f.viewerColor;
        }
        return fallback;
    }

    /** Debug: Plattformen hervorheben. */
    public static void drawLedges(DrawContext ctx, WorldView w, float ox, float oy) {
        for (Ledge l : w.platforms.ledges()) {
            int col = switch (l.kind) {
                case SLOT -> 0xC000FF00;
                case CHAT, CHAT_INPUT -> 0xC000C0FF;
                case WIDGET -> 0xC0FFC000;
                case FIGURE -> 0xC0FF00FF;
                case HUD -> 0xC0FF4040;
                default -> 0xC0FFFFFF;
            };
            int x1 = Math.round(ox + Math.max(0, l.x1));
            int x2 = Math.round(ox + Math.min(w.platforms.width, l.x2));
            int y = Math.round(oy + l.y);
            ctx.fill(x1, y, x2, y + 1, col);
            if (l.climbable) {
                ctx.fill(x1, y, x1 + 1, Math.round(oy + l.bottom), Colors.withAlpha(col, 0x60));
                ctx.fill(x2 - 1, y, x2, Math.round(oy + l.bottom), Colors.withAlpha(col, 0x60));
            }
        }
    }
}
