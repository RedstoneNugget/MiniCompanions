package de.minicompanions.gui;

import de.minicompanions.Mini;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Locale;

/** Bearbeitet eine Liste von Einträgen (Blacklist, Whitelist, Wörter): hinzufügen, entfernen, scrollen. */
public final class StringListScreen extends BaseScreen {
    private static final int ROW = 20;
    private final List<String> list;
    private final Runnable onChange;
    private TextFieldWidget input;
    private int scroll;
    private String pending = "";

    public StringListScreen(Screen parent, Text title, List<String> list, Runnable onChange) {
        super(title, parent);
        this.list = list;
        this.onChange = onChange;
    }

    private int visible() {
        return Math.max(1, (height - 90) / ROW);
    }

    @Override
    protected void init() {
        clearChildren();
        int w = Math.min(300, width - 20);
        int x = (width - w) / 2;
        input = field(x, 24, w - 64, 20, pending, 40, v -> pending = v);
        button(x + w - 60, 24, 60, 20, Mini.t("list.add", "Hinzufügen"), this::addEntry);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, list.size() - visible())));
        for (int i = 0; i < visible() && i + scroll < list.size(); i++) {
            final String entry = list.get(i + scroll);
            button(x + w - 20, 52 + i * ROW, 20, 18, Text.literal("✕"), () -> {
                list.remove(entry);
                onChange.run();
                init();
            });
        }
        button(width / 2 - 75, height - 25, 150, 20, Mini.t("gui.done", "Fertig"), this::close);
        setInitialFocus(input);
    }

    private void addEntry() {
        String v = pending.strip().toLowerCase(Locale.ROOT);
        if (!v.isEmpty() && v.length() <= 40 && !list.contains(v)) {
            list.add(v);
            onChange.run();
        }
        pending = "";
        init();
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        if ((input.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || input.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) && this.input.isFocused()) {
            addEntry();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        int ns = Math.max(0, Math.min(Math.max(0, list.size() - visible()), scroll - (int) Math.signum(vertical)));
        if (ns != scroll) {
            scroll = ns;
            init();
        }
        return true;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int w = Math.min(300, width - 20);
        int x = (width - w) / 2;
        for (int i = 0; i < visible() && i + scroll < list.size(); i++) {
            int y = 52 + i * ROW;
            ctx.fill(x, y, x + w - 22, y + 18, i % 2 == 0 ? 0x60202430 : 0x60303644);
        }
        super.render(ctx, mouseX, mouseY, delta);
        for (int i = 0; i < visible() && i + scroll < list.size(); i++) {
            ctx.drawText(textRenderer, list.get(i + scroll), x + 4, 57 + i * ROW, Ui.TEXT, false);
        }
        if (list.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, Mini.t("list.empty", "Liste ist leer."), width / 2, 60, Ui.TEXT_DIM);
        }
    }
}
