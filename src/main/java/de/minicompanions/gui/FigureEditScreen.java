package de.minicompanions.gui;

import de.minicompanions.Mini;
import de.minicompanions.ai.Activity;
import de.minicompanions.ai.Personality;
import de.minicompanions.config.ConfigManager;
import de.minicompanions.config.FigureData;
import de.minicompanions.figure.Accessory;
import de.minicompanions.figure.Figure;
import de.minicompanions.figure.FigureEnums;
import de.minicompanions.figure.Pose;
import de.minicompanions.render.FigureRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.Arrays;
import java.util.List;

/**
 * Figur bearbeiten: Name, Skin-Quelle, Modell, Größe (Slider, Zahlenfeld, Standard), Persönlichkeit,
 * Verhalten, Transparenz/Tempo/Lautstärke/Sprechhäufigkeit/Tonhöhe, Haustier, Farbfilter, Infotafel
 * und Accessoires. Links eine Vorschau, die sich dreht (oder mit der Maus gedreht werden kann).
 */
public final class FigureEditScreen extends OptionsScreen {
    private static final Pose.View[] VIEWS = {Pose.View.FRONT, Pose.View.RIGHT, Pose.View.BACK, Pose.View.LEFT};

    private final FigureData original;
    private final FigureData work;
    private final boolean isNew;
    private final Figure preview;
    private float rotation;
    private boolean dragRotate;
    private double lastMx;
    /** Skin-Eingabe beim letzten Bild und seit wann sie unverändert ist (Skin erst nach dem Tippen laden). */
    private String skinInput;
    private long skinInputSince;

    public FigureEditScreen(Screen parent, FigureData data) {
        this(parent, data, false);
    }

    public FigureEditScreen(Screen parent, FigureData data, boolean isNew) {
        super(Mini.t("edit.title", "Figur bearbeiten"), parent);
        this.original = data;
        this.isNew = isNew;
        this.work = data.copy();
        this.work.id = data.id;
        this.preview = new Figure(work, false);
        this.preview.setActivity(Activity.IDLE, 1e9f);
        this.skinInput = work.skinType + "|" + work.skinValue;
        this.leftInset = 120;
        var cfg = Mini.cfg();

        tab(Mini.t("edit.general", "Allgemein"))
                .add(new Field(Mini.t("edit.name", "Name"), () -> work.name, v -> work.name = v, 24))
                .add(new Cycle<>(Mini.t("edit.personality", "Persönlichkeit"), Arrays.asList(Personality.values()),
                        () -> Personality.parse(work.personality), v -> work.personality = v.name(),
                        v -> Mini.t("personality." + v.key(), v.key())))
                .add(new Header(Mini.t("edit.skin", "Skin")))
                .add(new Cycle<>(Mini.t("edit.skin_type", "Quelle"), Arrays.asList(FigureEnums.SkinType.values()),
                        () -> FigureEnums.parse(FigureEnums.SkinType.class, work.skinType, FigureEnums.SkinType.DEFAULT),
                        v -> work.skinType = v.name(), v -> Mini.t("skin_type." + v.name().toLowerCase(), v.name())))
                .add(new Field(Mini.t("edit.skin_value", "Wert"), () -> work.skinValue, v -> work.skinValue = v.strip(), 300))
                .add(new Cycle<>(Mini.t("edit.model", "Modell"), Arrays.asList(FigureEnums.Model.values()),
                        () -> FigureEnums.parse(FigureEnums.Model.class, work.model, FigureEnums.Model.AUTO),
                        v -> work.model = v.name(), v -> Mini.t("model." + v.name().toLowerCase(), v.name())))
                .add(new Action(Mini.t("edit.skin_folder", "Skin-Ordner: config/minicompanions/skins"), () -> { }, false))
                .add(new Header(Mini.t("edit.size", "Größe")))
                .add(new Slider(Mini.t("edit.size_slider", "Größe"), cfg.minSize, cfg.maxSize, 5,
                        () -> work.sizePercent != null ? work.sizePercent : cfg.globalSize, v -> work.sizePercent = v, v -> v + "%"))
                .add(new Field(Mini.t("edit.size_number", "Prozent"), () -> work.sizePercent == null ? "" : Integer.toString(work.sizePercent), v -> {
                    try {
                        work.sizePercent = v.isBlank() ? null : Math.max(cfg.minSize, Math.min(cfg.maxSize, Integer.parseInt(v.strip())));
                    } catch (NumberFormatException ignored) {
                        // ungültige Eingabe ignorieren
                    }
                }, 4))
                .add(new Action(Mini.t("edit.size_reset", "Auf Standard zurücksetzen"), () -> {
                    work.sizePercent = null;
                    rebuild();
                }, false))
                .add(new Toggle(Mini.t("edit.behavior", "Verhalten/KI"), () -> work.behavior, v -> work.behavior = v))
                .add(new Toggle(Mini.t("edit.show_name", "Namensschild"), () -> work.showName, v -> work.showName = v));

        tab(Mini.t("edit.tuning", "Feinheiten"))
                .add(new Slider(Mini.t("edit.alpha", "Transparenz"), 10, 100, 5, () -> work.alpha, v -> work.alpha = v, v -> v + "%"))
                .add(new Slider(Mini.t("edit.speed", "Tempo"), 10, 400, 10, () -> work.speed, v -> work.speed = v, v -> v + "%"))
                .add(new Slider(Mini.t("edit.talk", "Sprechhäufigkeit"), 0, 400, 10, () -> work.talkFrequency, v -> work.talkFrequency = v, v -> v + "%"))
                .add(new Slider(Mini.t("edit.volume", "Lautstärke"), 0, 200, 10, () -> work.volume, v -> work.volume = v, v -> v + "%"))
                .add(new Slider(Mini.t("edit.pitch", "Tonhöhe"), 50, 200, 5, () -> work.pitch, v -> work.pitch = v, v -> v + "%"))
                .add(new Cycle<>(Mini.t("edit.pet", "Haustier"), Arrays.asList(FigureEnums.PetType.values()),
                        () -> FigureEnums.parse(FigureEnums.PetType.class, work.pet, FigureEnums.PetType.NONE),
                        v -> work.pet = v.name(), v -> Mini.t("pet." + v.name().toLowerCase(), v.name())))
                .add(new Cycle<>(Mini.t("edit.filter", "Farbfilter"), Arrays.asList(FigureEnums.ColorFilter.values()),
                        () -> FigureEnums.parse(FigureEnums.ColorFilter.class, work.colorFilter, FigureEnums.ColorFilter.NONE),
                        v -> work.colorFilter = v.name(), v -> Mini.t("filter." + v.name().toLowerCase(), v.name())))
                .add(new Cycle<>(Mini.t("edit.infoboard", "Infotafel"), Arrays.asList(FigureEnums.InfoBoard.values()),
                        () -> FigureEnums.parse(FigureEnums.InfoBoard.class, work.infoBoard, FigureEnums.InfoBoard.NONE),
                        v -> work.infoBoard = v.name(), v -> Mini.t("info." + v.name().toLowerCase(), v.name())));

        Tab acc = tab(Mini.t("edit.accessories", "Accessoires"));
        for (Accessory a : Accessory.values()) {
            acc.add(new Toggle(accessoryLabel(a), () -> work.accessories.contains(a.name()), v -> setAccessory(a, v)));
        }
        acc.add(new Field(Mini.t("edit.custom_acc", "Eigenes PNG"), this::customAccessory, this::setCustomAccessory, 64));
    }

    private Text accessoryLabel(Accessory a) {
        return Mini.t("accessory." + a.key(), a.key());
    }

    private void setAccessory(Accessory a, boolean on) {
        work.accessories.remove(a.name());
        if (on) {
            // pro Körperstelle nur ein Accessoire
            work.accessories.removeIf(s -> {
                Accessory o = Accessory.parse(s);
                return o != null && o.slot == a.slot;
            });
            work.accessories.add(a.name());
        }
    }

    private String customAccessory() {
        for (String s : work.accessories) {
            if (s.startsWith("custom:")) {
                return s.substring(7);
            }
        }
        return "";
    }

    private void setCustomAccessory(String file) {
        work.accessories.removeIf(s -> s.startsWith("custom:"));
        String f = file.strip();
        if (!f.isEmpty() && f.matches("[A-Za-z0-9 _.()-]{1,64}")) {
            work.accessories.add("custom:" + (f.toLowerCase().endsWith(".png") ? f : f + ".png"));
        }
    }

    @Override
    protected void addBottomButtons() {
        button(width / 2 - 104, height - 25, 100, 20, Mini.t("gui.save", "Speichern"), this::save);
        button(width / 2 + 4, height - 25, 100, 20, Mini.t("gui.cancel", "Abbrechen"), super::close);
    }

    private void save() {
        work.validate();
        copyEditable(work, original);
        if (isNew) {
            Mini.figures.add(original);
        } else {
            Mini.configs.markDirty(ConfigManager.Part.FIGURES);
        }
        super.close();
    }

    /** Nur die bearbeitbaren Felder übernehmen (Leben, XP und Beziehungen bleiben erhalten). */
    private static void copyEditable(FigureData from, FigureData to) {
        to.name = from.name;
        to.skinType = from.skinType;
        to.skinValue = from.skinValue;
        to.model = from.model;
        to.sizePercent = from.sizePercent;
        to.personality = from.personality;
        to.behavior = from.behavior;
        to.alpha = from.alpha;
        to.speed = from.speed;
        to.volume = from.volume;
        to.talkFrequency = from.talkFrequency;
        to.pitch = from.pitch;
        to.accessories = new java.util.ArrayList<>(from.accessories);
        to.pet = from.pet;
        to.colorFilter = from.colorFilter;
        to.infoBoard = from.infoBoard;
        to.showName = from.showName;
    }

    // ------------------------------------------------------------------ Vorschau

    @Override
    public void tick() {
        super.tick();
        if (!dragRotate) {
            rotation += 0.05f;
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        if (click.x() < leftInset && click.y() > 40) {
            dragRotate = true;
            lastMx = click.x();
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.gui.Click click, double offsetX, double offsetY) {
        if (dragRotate) {
            rotation += (float) (click.x() - lastMx) * 0.05f;
            lastMx = click.x();
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.gui.Click click) {
        dragRotate = false;
        return super.mouseReleased(click);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        int px = 10;
        int py = 44;
        int pw = leftInset - 20;
        int ph = height - 80;
        Ui.panel(ctx, px, py, pw, ph, 0xC0181C26);
        long now = System.currentTimeMillis();
        preview.animTime = (now % 1_000_000L) / 1000f;
        // Skin erst laden, wenn kurz nicht mehr getippt wurde - sonst wird jeder halbe Name
        // ("dia", "diam", ...) bei Mojang abgefragt
        String input = work.skinType + "|" + work.skinValue;
        if (!input.equals(skinInput)) {
            skinInput = input;
            skinInputSince = now;
        }
        if (now - skinInputSince >= 700) {
            preview.refreshSkin(Mini.skins);
        }
        var cfg = Mini.cfg();
        float size = work.sizePercent != null ? work.sizePercent : cfg.globalSize;
        // Vorschau-Einheit: passt immer in das Feld, wächst aber sichtbar mit der Größe
        float unit = Math.max(1f, Math.min((ph - 40) / 32f, 1.2f + size / 100f * 1.2f));
        int view = Math.floorMod(Math.round(rotation), 4);
        FigureRenderer.forcedView = VIEWS[view];
        try {
            ctx.createNewRootLayer();
            FigureRenderer.render(ctx, preview, px + pw / 2f, py + ph - 18, unit, cfg, true);
        } finally {
            FigureRenderer.forcedView = null;
        }
        ctx.createNewRootLayer();
        String info = Math.round(size) + "%" + (work.sizePercent == null ? " (" + Mini.t("edit.global", "global").getString() + ")" : "");
        ctx.drawCenteredTextWithShadow(textRenderer, info, px + pw / 2, py + ph - 12, Ui.TEXT_DIM);
        if (preview.skin != null && preview.skin.state == de.minicompanions.skin.SkinManager.State.FAILED) {
            ctx.drawCenteredTextWithShadow(textRenderer, Mini.t("edit.skin_failed", "Skin nicht ladbar"), px + pw / 2, py + 4, Ui.RED);
        } else if (preview.skin != null && preview.skin.state == de.minicompanions.skin.SkinManager.State.LOADING) {
            ctx.drawCenteredTextWithShadow(textRenderer, Mini.t("edit.skin_loading", "Lade Skin …"), px + pw / 2, py + 4, Ui.TEXT_DIM);
        }
    }

    public static List<String> skinHelp() {
        return List.of("PLAYER: Notch", "FILE: meinskin.png", "URL: https://…/skin.png");
    }
}
