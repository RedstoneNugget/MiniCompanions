package de.minicompanions.gui;

import de.minicompanions.Mini;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Einfacher Einstellungs-Screen mit Reitern und zweispaltigen Optionszeilen (Schalter, Schieberegler,
 * Auswahl, Textfeld, Aktion). Lange Listen werden mit dem Mausrad gescrollt.
 */
public class OptionsScreen extends BaseScreen {
    /** Eine Optionszeile. */
    public interface Opt {
    }

    public record Toggle(Text label, BooleanSupplier get, Consumer<Boolean> set) implements Opt {
    }

    public record Slider(Text label, int min, int max, int step, IntSupplier get, IntConsumer set, IntFunction<String> fmt) implements Opt {
    }

    public record Cycle<E>(Text label, List<E> values, Supplier<E> get, Consumer<E> set, Function<E, Text> name) implements Opt {
    }

    public record Field(Text label, Supplier<String> get, Consumer<String> set, int maxLen) implements Opt {
    }

    public record Action(Text label, Runnable run, boolean danger) implements Opt {
    }

    public record Header(Text label) implements Opt {
    }

    /** Ein Reiter mit Optionen; optional mit eigenem Inhalt (init/render) statt Optionsliste. */
    public static class Tab {
        public final Text title;
        public final List<Opt> opts = new ArrayList<>();

        public Tab(Text title) {
            this.title = title;
        }

        public Tab add(Opt o) {
            opts.add(o);
            return this;
        }

        /** Eigener Inhalt (wenn überschrieben): Widgets im Bereich x,y,w,h anlegen. @return true wenn behandelt */
        public boolean customInit(OptionsScreen s, int x, int y, int w, int h) {
            return false;
        }

        public void customRender(OptionsScreen s, DrawContext ctx, int mx, int my) {
        }
    }

    protected final List<Tab> tabs = new ArrayList<>();
    protected int tab;
    protected int scroll;
    /** Linker Rand für eigene Inhalte (z. B. Vorschau). */
    protected int leftInset;
    private int maxScroll;
    private final List<Text> headerTexts = new ArrayList<>();
    private final List<int[]> headerPos = new ArrayList<>();
    private Runnable onClose = () -> { };

    public OptionsScreen(Text title, Screen parent) {
        super(title, parent);
    }

    public OptionsScreen onClose(Runnable r) {
        this.onClose = r;
        return this;
    }

    public Tab tab(Text title) {
        Tab t = new Tab(title);
        tabs.add(t);
        return t;
    }

    public void addTab(Tab t) {
        tabs.add(t);
    }

    @Override
    protected void init() {
        rebuild();
    }

    /** Alle Widgets neu aufbauen (nach Reiterwechsel, Scrollen oder Wertänderung mit Folgen). */
    public void rebuild() {
        clearChildren();
        headerTexts.clear();
        headerPos.clear();
        int tabW = Math.min(110, (width - 20) / Math.max(1, tabs.size()));
        int tx = (width - tabW * tabs.size()) / 2;
        for (int i = 0; i < tabs.size(); i++) {
            final int idx = i;
            ButtonWidget b = button(tx + i * tabW, 18, tabW - 2, 18, tabs.get(i).title, () -> {
                tab = idx;
                scroll = 0;
                rebuild();
            });
            b.active = i != tab;
        }
        int top = 42;
        int bottom = height - 30;
        Tab t = tabs.isEmpty() ? null : tabs.get(Math.min(tab, tabs.size() - 1));
        if (t != null && !t.customInit(this, 10, top, width - 20, bottom - top)) {
            layoutOpts(t, top, bottom);
        }
        addBottomButtons();
    }

    /** Knöpfe am unteren Rand (Standard: Fertig). */
    protected void addBottomButtons() {
        button(width / 2 - 75, height - 25, 150, 20, Mini.t("gui.done", "Fertig"), this::close);
    }

    private void layoutOpts(Tab t, int top, int bottom) {
        int areaW = width - leftInset;
        int center = leftInset + areaW / 2;
        int colW = Math.min(200, (areaW - 30) / 2);
        int x0 = center - colW - 5;
        int x1 = center + 5;
        int rowH = 24;
        // Zeilen berechnen: Überschriften belegen eine ganze Zeile
        List<int[]> placement = new ArrayList<>();
        int row = 0;
        int col = 0;
        for (Opt o : t.opts) {
            if (o instanceof Header) {
                if (col == 1) {
                    row++;
                    col = 0;
                }
                placement.add(new int[]{row, -1});
                row++;
            } else {
                placement.add(new int[]{row, col});
                col++;
                if (col == 2) {
                    col = 0;
                    row++;
                }
            }
        }
        int totalRows = row + (col > 0 ? 1 : 0);
        int visibleRows = Math.max(1, (bottom - top) / rowH);
        maxScroll = Math.max(0, totalRows - visibleRows);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        for (int i = 0; i < t.opts.size(); i++) {
            int r = placement.get(i)[0] - scroll;
            int c = placement.get(i)[1];
            if (r < 0 || r >= visibleRows) {
                continue;
            }
            int y = top + r * rowH;
            Opt o = t.opts.get(i);
            if (c < 0) {
                headerTexts.add(((Header) o).label());
                headerPos.add(new int[]{leftInset + (width - leftInset) / 2, y + 8});
                continue;
            }
            int x = c == 0 ? x0 : x1;
            widgetFor(o, x, y, colW);
        }
    }

    @SuppressWarnings("unchecked")
    private void widgetFor(Opt o, int x, int y, int w) {
        if (o instanceof Toggle tg) {
            ButtonWidget[] ref = new ButtonWidget[1];
            ref[0] = button(x, y, w, 20, tg.label().copy().append(": ").append(onOff(tg.get().getAsBoolean())), () -> {
                tg.set().accept(!tg.get().getAsBoolean());
                ref[0].setMessage(tg.label().copy().append(": ").append(onOff(tg.get().getAsBoolean())));
                changed();
            });
        } else if (o instanceof Slider sl) {
            addDrawableChild(new IntSlider(x, y, w, 20, sl.label(), sl.min(), sl.max(), sl.step(), sl.get().getAsInt(), sl.fmt(), v -> {
                sl.set().accept(v);
                changed();
            }));
        } else if (o instanceof Cycle<?> cy) {
            Cycle<Object> c = (Cycle<Object>) cy;
            ButtonWidget[] ref = new ButtonWidget[1];
            ref[0] = button(x, y, w, 20, c.label().copy().append(": ").append(c.name().apply(c.get().get())), () -> {
                int idx = c.values().indexOf(c.get().get());
                Object next = c.values().get((idx + 1) % c.values().size());
                c.set().accept(next);
                ref[0].setMessage(c.label().copy().append(": ").append(c.name().apply(next)));
                changed();
            });
        } else if (o instanceof Field fd) {
            int lw = Math.min(w / 2, textRenderer.getWidth(fd.label()) + 6);
            headerTexts.add(fd.label());
            headerPos.add(new int[]{-(x + 1), y + 6});
            field(x + lw, y, w - lw, 20, fd.get().get(), fd.maxLen(), v -> {
                fd.set().accept(v);
                changed();
            });
        } else if (o instanceof Action ac) {
            button(x, y, w, 20, ac.danger() ? ac.label().copy().withColor(0xFF6060) : ac.label(), () -> {
                ac.run().run();
                changed();
            });
        }
    }

    /** Eine Einstellung wurde geändert (Unterklassen speichern). */
    protected void changed() {
        onChangeHook();
    }

    protected void onChangeHook() {
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (maxScroll > 0) {
            int ns = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(vertical)));
            if (ns != scroll) {
                scroll = ns;
                rebuild();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        for (int i = 0; i < headerTexts.size(); i++) {
            int[] p = headerPos.get(i);
            if (p[0] < 0) {
                ctx.drawText(textRenderer, headerTexts.get(i), -p[0], p[1], 0xFFE0E0E0, true);
            } else {
                ctx.drawCenteredTextWithShadow(textRenderer, headerTexts.get(i), p[0], p[1], Ui.ACCENT);
            }
        }
        if (maxScroll > 0) {
            String s = (scroll + 1) + "/" + (maxScroll + 1);
            ctx.drawText(textRenderer, s, width - textRenderer.getWidth(s) - 6, height - 18, Ui.TEXT_DIM, true);
        }
        Tab t = tabs.isEmpty() ? null : tabs.get(Math.min(tab, tabs.size() - 1));
        if (t != null) {
            t.customRender(this, ctx, mouseX, mouseY);
        }
    }

    @Override
    public void close() {
        onClose.run();
        super.close();
    }

    // Zugriff für eigene Reiter
    public <T extends net.minecraft.client.gui.Element & net.minecraft.client.gui.Drawable & net.minecraft.client.gui.Selectable> T add(T w) {
        return addDrawableChild(w);
    }

    public ButtonWidget addButton(int x, int y, int w, int h, Text label, Runnable action) {
        return button(x, y, w, h, label, action);
    }

    public net.minecraft.client.gui.widget.TextFieldWidget addField(int x, int y, int w, int h, String value, int maxLen, Consumer<String> onChange) {
        return field(x, y, w, h, value, maxLen, onChange);
    }

    public net.minecraft.client.font.TextRenderer tr() {
        return textRenderer;
    }
}
