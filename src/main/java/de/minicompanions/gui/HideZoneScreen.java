package de.minicompanions.gui;

import de.minicompanions.Mini;
import de.minicompanions.config.ConfigManager;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

/**
 * Ausblenden-Zonen bearbeiten: Mit gedrückter linker Maustaste ein Rechteck aufziehen, Rechtsklick
 * auf eine Zone entfernt sie. In diese Bereiche laufen keine Figuren.
 */
public final class HideZoneScreen extends BaseScreen {
    private int sx;
    private int sy;
    private int ex;
    private int ey;
    private boolean drawing;

    public HideZoneScreen(Screen parent) {
        super(Mini.t("zones.title", "Ausblenden-Zonen"), parent);
    }

    @Override
    protected void init() {
        button(width / 2 - 75, height - 25, 150, 20, Mini.t("gui.done", "Fertig"), this::close);
        button(width / 2 + 80, height - 25, 80, 20, Mini.t("zones.clear", "Alle löschen"), () -> {
            Mini.cfg().hideZones.clear();
            Mini.configs.markDirty(ConfigManager.Part.CONFIG);
        });
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        context.fill(0, 0, width, height, 0x40000000);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) {
            return true;
        }
        int mx = (int) click.x();
        int my = (int) click.y();
        if (click.button() == 1) {
            Mini.cfg().hideZones.removeIf(z -> mx >= z[0] && mx <= z[2] && my >= z[1] && my <= z[3]);
            Mini.configs.markDirty(ConfigManager.Part.CONFIG);
            return true;
        }
        drawing = true;
        sx = ex = mx;
        sy = ey = my;
        return true;
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (drawing) {
            ex = (int) click.x();
            ey = (int) click.y();
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (drawing) {
            drawing = false;
            if (Math.abs(ex - sx) > 4 && Math.abs(ey - sy) > 4) {
                Mini.cfg().hideZones.add(new int[]{Math.min(sx, ex), Math.min(sy, ey), Math.max(sx, ex), Math.max(sy, ey)});
                Mini.configs.markDirty(ConfigManager.Part.CONFIG);
            }
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        for (int[] z : Mini.cfg().hideZones) {
            ctx.fill(z[0], z[1], z[2], z[3], 0x50FF3030);
            ctx.drawStrokedRectangle(z[0], z[1], z[2] - z[0], z[3] - z[1], 0xFFFF6060);
        }
        if (drawing) {
            ctx.fill(Math.min(sx, ex), Math.min(sy, ey), Math.max(sx, ex), Math.max(sy, ey), 0x5030A0FF);
        }
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, Mini.t("zones.help", "Linke Maustaste ziehen = Zone anlegen · Rechtsklick = Zone löschen"),
                width / 2, 20, Ui.TEXT);
    }
}
