package de.minicompanions.gui;

import de.minicompanions.Mini;
import de.minicompanions.MiniCompanionsClient;
import de.minicompanions.config.PresetManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.List;

/** Presets speichern, laden (ersetzen/hinzufügen), löschen, als Code kopieren und aus der Zwischenablage importieren. */
public final class PresetScreen extends BaseScreen {
    private static final int ROW = 22;
    private String name = "";
    private int scroll;
    private Text status = Text.empty();

    public PresetScreen(Screen parent) {
        super(Mini.t("presets.title", "Presets"), parent);
    }

    private PresetManager pm() {
        return MiniCompanionsClient.presets;
    }

    @Override
    protected void init() {
        clearChildren();
        int w = Math.min(380, width - 20);
        int x = (width - w) / 2;
        field(x, 24, w - 168, 20, name, 32, v -> name = v);
        button(x + w - 164, 24, 80, 20, Mini.t("presets.save", "Speichern"), () -> {
            String n = PresetManager.safeName(name.isBlank() ? "preset" : name);
            pm().save(n);
            status = Mini.t("presets.saved", "Gespeichert: %s", n);
            init();
        });
        button(x + w - 80, 24, 80, 20, Mini.t("presets.import", "Code einfügen"), () -> {
            String clip = client.keyboard.getClipboard();
            var p = pm().importCode(clip);
            if (p == null) {
                status = Mini.t("presets.bad_code", "Kein gültiger Preset-Code in der Zwischenablage.");
            } else {
                int n = pm().apply(p, false);
                status = Mini.t("presets.imported", "%s Figuren importiert.", n);
            }
        });
        button(x, 48, w, 20, Mini.t("presets.export_current", "Aktuelle Figuren als Code kopieren"), () -> {
            client.keyboard.setClipboard(pm().exportCode(pm().current("export")));
            status = Mini.t("presets.copied", "Code in die Zwischenablage kopiert.");
        });
        List<String> names = pm().list();
        int visible = Math.max(1, (height - 110) / ROW);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, names.size() - visible)));
        for (int i = 0; i < visible && i + scroll < names.size(); i++) {
            String n = names.get(i + scroll);
            int y = 76 + i * ROW;
            int bx = x + w;
            bx -= 20;
            button(bx, y, 20, 20, Text.literal("✕"), Mini.t("presets.delete", "Löschen"), () -> {
                pm().delete(n);
                init();
            });
            bx -= 52;
            button(bx, y, 50, 20, Mini.t("presets.code", "Code"), () -> {
                client.keyboard.setClipboard(pm().exportCode(pm().read(n)));
                status = Mini.t("presets.copied", "Code in die Zwischenablage kopiert.");
            });
            bx -= 62;
            button(bx, y, 60, 20, Mini.t("presets.add", "Hinzufügen"), () -> {
                int c = pm().apply(pm().read(n), false);
                status = Mini.t("presets.loaded", "%s Figuren geladen.", c);
            });
            bx -= 62;
            button(bx, y, 60, 20, Mini.t("presets.load", "Laden"), Mini.t("presets.load_tip", "Ersetzt deine aktuellen Figuren"), () -> {
                int c = pm().apply(pm().read(n), true);
                status = Mini.t("presets.loaded", "%s Figuren geladen.", c);
            });
        }
        button(width / 2 - 75, height - 25, 150, 20, Mini.t("gui.done", "Fertig"), this::close);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scroll = Math.max(0, scroll - (int) Math.signum(vertical));
        init();
        return true;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        int w = Math.min(380, width - 20);
        int x = (width - w) / 2;
        List<String> names = pm().list();
        int visible = Math.max(1, (height - 110) / ROW);
        for (int i = 0; i < visible && i + scroll < names.size(); i++) {
            ctx.drawText(textRenderer, Ui.fit(textRenderer, names.get(i + scroll), w - 210), x + 2, 82 + i * ROW, Ui.TEXT, true);
        }
        if (names.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, Mini.t("presets.none", "Noch keine Presets gespeichert."), width / 2, 84, Ui.TEXT_DIM);
        }
        ctx.drawCenteredTextWithShadow(textRenderer, status, width / 2, height - 40, Ui.ACCENT);
    }
}
