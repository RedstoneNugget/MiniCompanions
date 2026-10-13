package de.minicompanions.gui;

import de.minicompanions.Mini;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.function.Consumer;

/** Basis für alle Screens der Mod: Zurück zum Eltern-Screen, Speichern beim Schließen, Helfer für Knöpfe. */
public abstract class BaseScreen extends Screen {
    protected final Screen parent;

    protected BaseScreen(Text title, Screen parent) {
        super(title);
        this.parent = parent;
    }

    @Override
    public void close() {
        if (Mini.configs != null) {
            Mini.configs.flush();
        }
        if (client != null) {
            client.setScreen(parent);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    protected ButtonWidget button(int x, int y, int w, int h, Text label, Runnable action) {
        return addDrawableChild(ButtonWidget.builder(label, b -> action.run()).dimensions(x, y, w, h).build());
    }

    protected ButtonWidget button(int x, int y, int w, int h, Text label, Text tooltip, Runnable action) {
        ButtonWidget b = button(x, y, w, h, label, action);
        if (tooltip != null) {
            b.setTooltip(Tooltip.of(tooltip));
        }
        return b;
    }

    protected TextFieldWidget field(int x, int y, int w, int h, String value, int maxLen, Consumer<String> onChange) {
        TextFieldWidget tf = new TextFieldWidget(textRenderer, x, y, w, h, Text.empty());
        tf.setMaxLength(maxLen);
        tf.setText(value == null ? "" : value);
        tf.setChangedListener(onChange);
        return addDrawableChild(tf);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 6, 0xFFFFFFFF);
    }

    protected static Text onOff(boolean v) {
        return v ? Mini.t("gui.on", "An") : Mini.t("gui.off", "Aus");
    }
}
