package de.minicompanions.gui;

import de.minicompanions.Actions;
import de.minicompanions.Mini;
import de.minicompanions.config.ConfigManager;
import de.minicompanions.config.FigureData;
import de.minicompanions.figure.Figure;
import de.minicompanions.skin.SkinManager;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Verwaltungs-GUI: Liste aller eigenen Figuren mit Name, Skin-Quelle, Größe, Persönlichkeit,
 * Verhalten an/aus, Bearbeiten, Duplizieren und Entfernen. Dazu "Neue Figur", "Zufällige Figur",
 * "Alle entfernen" und Zugang zu Einstellungen, Streamer-Fenster, Zimmer, Presets und Erfolgen.
 */
public final class ManageScreen extends BaseScreen {
    private static final int ROW_H = 24;
    private int scroll;
    private boolean confirmClear;
    private final List<int[]> rowPos = new ArrayList<>();
    private final List<Figure> rowFig = new ArrayList<>();

    public ManageScreen(Screen parent) {
        super(Mini.t("manage.title", "Mini-Begleiter"), parent);
    }

    private List<Figure> own() {
        List<Figure> l = new ArrayList<>();
        for (Figure f : Mini.figures.all()) {
            if (!f.viewer) {
                l.add(f);
            }
        }
        return l;
    }

    private int listTop() {
        return 40;
    }

    private int listBottom() {
        return height - 56;
    }

    private int visibleRows() {
        return Math.max(1, (listBottom() - listTop()) / ROW_H);
    }

    @Override
    protected void init() {
        clearChildren();
        rowPos.clear();
        rowFig.clear();
        List<Figure> figs = own();
        int maxScroll = Math.max(0, figs.size() - visibleRows());
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        int lw = Math.min(460, width - 20);
        int lx = (width - lw) / 2;
        for (int i = 0; i < visibleRows() && i + scroll < figs.size(); i++) {
            Figure f = figs.get(i + scroll);
            int y = listTop() + i * ROW_H;
            rowPos.add(new int[]{lx, y, lw});
            rowFig.add(f);
            int bx = lx + lw - 4;
            bx -= 20;
            button(bx, y + 2, 20, 20, Text.literal("✕"), Mini.t("manage.remove", "Entfernen"), () -> {
                Mini.figures.remove(f);
                init();
            });
            bx -= 22;
            button(bx, y + 2, 20, 20, Text.literal("⧉"), Mini.t("manage.duplicate", "Duplizieren"), () -> {
                Mini.figures.duplicate(f);
                init();
            });
            bx -= 52;
            button(bx, y + 2, 50, 20, f.data.behavior ? Mini.t("manage.ai_on", "KI an") : Mini.t("manage.ai_off", "KI aus"),
                    Mini.t("manage.behavior", "Verhalten ein/aus"), () -> {
                        f.data.behavior = !f.data.behavior;
                        Mini.configs.markDirty(ConfigManager.Part.FIGURES);
                        init();
                    });
            bx -= 62;
            button(bx, y + 2, 60, 20, Mini.t("manage.edit", "Bearbeiten"), () -> client.setScreen(new FigureEditScreen(this, f.data)));
        }
        int bw = 100;
        int by = height - 52;
        int bx0 = width / 2 - (bw * 4 + 12) / 2;
        button(bx0, by, bw, 20, Mini.t("manage.new", "Neue Figur"), () -> {
            FigureData d = new FigureData();
            d.name = Mini.figures.uniqueName("Mini");
            client.setScreen(new FigureEditScreen(this, d, true));
        });
        button(bx0 + bw + 4, by, bw, 20, Mini.t("manage.random", "Zufällige Figur"), () -> {
            Mini.figures.addRandom();
            init();
        });
        button(bx0 + (bw + 4) * 2, by, bw, 20, confirmClear ? Mini.t("manage.clear_confirm", "Wirklich alle?") : Mini.t("manage.clear", "Alle entfernen"), () -> {
            if (confirmClear) {
                Mini.figures.clearOwn();
                confirmClear = false;
            } else {
                confirmClear = true;
            }
            init();
        });
        button(bx0 + (bw + 4) * 3, by, bw, 20, Mini.streamer.isEnabled() ? Mini.t("manage.streamer_on", "Streamer: AN") : Mini.t("manage.streamer_off", "Streamer: AUS"),
                Mini.t("manage.streamer_tip", "Streamer-Modus ein-/ausschalten"), () -> {
                    Actions.toggleStreamerMode();
                    init();
                });
        by += 24;
        int sw = 80;
        int sx0 = width / 2 - (sw * 4 + 12) / 2;
        button(sx0, by, sw, 20, Mini.t("manage.settings", "Einstellungen"), () -> client.setScreen(new SettingsScreen(this)));
        button(sx0 + (sw + 4), by, sw, 20, Mini.t("manage.streamer", "Streamer …"), () -> client.setScreen(new StreamerScreen(this)));
        button(sx0 + (sw + 4) * 2, by, sw, 20, Mini.room.isOpen() ? Mini.t("manage.room_close", "Zimmer zu") : Mini.t("manage.room_open", "Zimmer auf"), () -> {
            Mini.room.toggle();
            init();
        });
        button(sx0 + (sw + 4) * 3, by, sw, 20, Mini.t("manage.presets", "Presets …"), () -> client.setScreen(new PresetScreen(this)));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        int max = Math.max(0, own().size() - visibleRows());
        int ns = Math.max(0, Math.min(max, scroll - (int) Math.signum(vertical)));
        if (ns != scroll) {
            scroll = ns;
            init();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        for (int i = 0; i < rowPos.size(); i++) {
            int[] p = rowPos.get(i);
            ctx.fill(p[0], p[1], p[0] + p[2], p[1] + ROW_H - 1, i % 2 == 0 ? 0x60202430 : 0x60303644);
        }
        super.render(ctx, mouseX, mouseY, delta);
        List<Figure> figs = own();
        Text count = Mini.t("manage.count", "%s Figuren · Zuschauer-Figuren: %s", figs.size(), Mini.streamer.viewers().count());
        ctx.drawCenteredTextWithShadow(textRenderer, count, width / 2, 24, Ui.TEXT_DIM);
        if (figs.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, Mini.t("manage.empty", "Noch keine Figuren - klicke auf \"Neue Figur\" oder ziehe eine PNG ins Fenster."),
                    width / 2, listTop() + 20, Ui.TEXT);
        }
        for (int i = 0; i < rowPos.size(); i++) {
            int[] p = rowPos.get(i);
            Figure f = rowFig.get(i);
            drawHead(ctx, f, p[0] + 4, p[1] + 4);
            int tx = p[0] + 24;
            ctx.drawText(textRenderer, Text.literal(Ui.fit(textRenderer, f.data.name, 120)), tx, p[1] + 3, Ui.TEXT, true);
            String sub = skinLabel(f.data) + " · " + Math.round(f.sizePercent(Mini.cfg())) + "% · "
                    + Mini.t("personality." + f.personality().key(), f.personality().key()).getString()
                    + (f.inRoom ? " · " + Mini.t("manage.in_room", "im Zimmer").getString() : "");
            ctx.drawText(textRenderer, Text.literal(Ui.fit(textRenderer, sub, p[2] - 24 - 172)), tx, p[1] + 13, Ui.TEXT_DIM, false);
        }
    }

    /** Kopf (Gesicht + Hut-Ebene) aus der Skin-Textur als Vorschau. */
    public static void drawHead(DrawContext ctx, Figure f, int x, int y) {
        f.refreshSkin(Mini.skins);
        var tex = f.skin == null ? SkinManager.STEVE : f.skin.texture;
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, tex, x, y, 8, 8, 16, 16, 8, 8, 64, 64);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, tex, x, y, 40, 8, 16, 16, 8, 8, 64, 64);
    }

    private static String skinLabel(FigureData d) {
        return switch (d.skinType) {
            case "PLAYER" -> Mini.t("skin.player_short", "Spieler: %s", d.skinValue).getString();
            case "FILE" -> Mini.t("skin.file_short", "Datei: %s", d.skinValue).getString();
            case "URL" -> Mini.t("skin.url_short", "Link").getString();
            default -> Mini.t("skin.default_short", "Standard-Skin").getString();
        };
    }
}
