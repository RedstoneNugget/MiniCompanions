package de.minicompanions.gui;

import de.minicompanions.Mini;
import de.minicompanions.config.ConfigManager;
import de.minicompanions.config.MiniConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Globale Einstellungen: jedes Feature einzeln schaltbar, Größe, Bewegungswelt pro Screen-Typ,
 * Zimmer-Fenster (Position/Größe), Reaktionen, Inventar und Aufnahmemodus.
 */
public final class SettingsScreen extends OptionsScreen {
    public SettingsScreen(Screen parent) {
        super(Mini.t("settings.title", "Mini-Begleiter: Einstellungen"), parent);
        MiniConfig c = Mini.cfg();
        tab(Mini.t("settings.general", "Allgemein"))
                .add(new Toggle(Mini.t("opt.enabled", "Mod aktiv"), () -> c.enabled, v -> c.enabled = v))
                .add(new Action(Mini.t("opt.keys", "Tasten ändern … (Abschnitt „Mini-Begleiter“)"), () -> net.minecraft.client.MinecraftClient.getInstance()
                        .setScreen(new net.minecraft.client.gui.screen.option.KeybindsScreen(this, net.minecraft.client.MinecraftClient.getInstance().options)), false))
                .add(new Toggle(Mini.t("opt.behavior", "Verhalten/KI"), () -> c.behaviorEnabled, v -> c.behaviorEnabled = v))
                .add(new Slider(Mini.t("opt.max_figures", "Max. Figuren"), 0, 500, 1, () -> c.maxFigures, v -> c.maxFigures = v,
                        v -> v == 0 ? Mini.t("opt.unlimited", "unbegrenzt").getString() : Integer.toString(v)))
                .add(new Cycle<>(Mini.t("opt.language", "Sprache Sprüche"), List.of("auto", "de_de", "en_us"), () -> c.language, v -> {
                    c.language = v;
                    de.minicompanions.MiniCompanionsClient.reloadLines();
                }, Text::literal))
                .add(new Toggle(Mini.t("opt.rest", "Ruhemodus"), () -> c.restMode, v -> c.restMode = v))
                .add(new Slider(Mini.t("opt.talk", "Sprechhäufigkeit"), 0, 400, 10, () -> c.talkFrequency, v -> c.talkFrequency = v, v -> v + "%"))
                .add(new Slider(Mini.t("opt.alpha", "Transparenz"), 10, 100, 5, () -> c.globalAlpha, v -> c.globalAlpha = v, v -> v + "%"))
                .add(new Slider(Mini.t("opt.speed", "Geschwindigkeit"), 10, 400, 10, () -> c.globalSpeed, v -> c.globalSpeed = v, v -> v + "%"))
                .add(new Toggle(Mini.t("opt.sounds", "Mini-Sounds"), () -> c.sounds, v -> c.sounds = v))
                .add(new Slider(Mini.t("opt.volume", "Lautstärke"), 0, 100, 5, () -> c.soundVolume, v -> c.soundVolume = v, v -> v + "%"))
                .add(new Toggle(Mini.t("opt.debug", "Debug-Overlay"), () -> c.debugOverlay, v -> c.debugOverlay = v))
                .add(new Toggle(Mini.t("opt.hud_icon", "Status-Symbole im HUD"), () -> c.hudStatusIcon, v -> c.hudStatusIcon = v))
                .add(new Action(Mini.t("opt.reset", "Alles zurücksetzen (mit Backup)"), () -> {
                    var backup = Mini.configs.resetAll();
                    Mini.figures.syncFromConfig();
                    Mini.chat(Mini.t("msg.reset", "Einstellungen zurückgesetzt. Backup: %s", backup.getFileName().toString()));
                    MinecraftClient.getInstance().setScreen(new SettingsScreen(parent));
                }, true));

        tab(Mini.t("settings.size", "Größe"))
                .add(new Slider(Mini.t("opt.global_size", "Globale Größe"), 25, 500, 5, () -> c.globalSize, v -> c.globalSize = v, v -> v + "%"))
                .add(new Slider(Mini.t("opt.viewer_size", "Zuschauer-Figuren"), 25, 500, 5, () -> c.viewerDefaultSize, v -> c.viewerDefaultSize = v, v -> v + "%"))
                .add(new Slider(Mini.t("opt.min_size", "Mindestgröße"), 10, 100, 5, () -> c.minSize, v -> c.minSize = v, v -> v + "%"))
                .add(new Slider(Mini.t("opt.max_size", "Maximalgröße"), 100, 1000, 10, () -> c.maxSize, v -> c.maxSize = v, v -> v + "%"))
                .add(new Toggle(Mini.t("opt.pixel_snap", "Scharfe Pixel (ganzzahlig)"), () -> c.pixelSnap, v -> c.pixelSnap = v))
                .add(new Toggle(Mini.t("opt.speed_size", "Tempo folgt Größe"), () -> c.speedFollowsSize, v -> c.speedFollowsSize = v))
                .add(new Toggle(Mini.t("opt.random_size", "Zufällige Größenschwankung"), () -> c.randomSizeVariation, v -> c.randomSizeVariation = v))
                .add(new Toggle(Mini.t("opt.grow", "Wachsen/Schrumpfen-Effekte"), () -> c.growEffects, v -> c.growEffects = v))
                .add(new Cycle<>(Mini.t("opt.size_mode", "Modus"), List.of("NORMAL", "GIANT", "MINI"), () -> c.sizeMode, v -> c.sizeMode = v,
                        v -> Mini.t("size_mode." + v.toLowerCase(), v)))
                .add(new Slider(Mini.t("opt.bubble_scale", "Sprechblasen-Größe"), 50, 300, 10, () -> c.bubbleScale, v -> c.bubbleScale = v, v -> v + "%"));

        tab(Mini.t("settings.world", "Bewegung"))
                .add(new Header(Mini.t("settings.world.where", "Wo dürfen Figuren laufen?")))
                .add(new Toggle(Mini.t("opt.walk_ground", "Boden"), () -> c.walkGround, v -> c.walkGround = v))
                .add(new Toggle(Mini.t("opt.walk_hud", "HUD (Hotbar, Herzen …)"), () -> c.walkHud, v -> c.walkHud = v))
                .add(new Toggle(Mini.t("opt.walk_inventory", "Inventar/Container"), () -> c.walkInventory, v -> c.walkInventory = v))
                .add(new Toggle(Mini.t("opt.walk_chat", "Chat"), () -> c.walkChat, v -> c.walkChat = v))
                .add(new Toggle(Mini.t("opt.walk_other", "Andere Screens"), () -> c.walkOtherScreens, v -> c.walkOtherScreens = v))
                .add(new Toggle(Mini.t("opt.walk_room", "Auf dem Zimmer-Fenster"), () -> c.walkRoomWindow, v -> c.walkRoomWindow = v))
                .add(new Header(Mini.t("settings.world.interact", "Interaktion")))
                .add(new Toggle(Mini.t("opt.clicks", "Klicks auf Figuren"), () -> c.figureClicks, v -> c.figureClicks = v))
                .add(new Toggle(Mini.t("opt.throwing", "Werfen/Abprallen"), () -> c.throwing, v -> c.throwing = v))
                .add(new Toggle(Mini.t("opt.stacking", "Menschenturm/Huckepack"), () -> c.stacking, v -> c.stacking = v))
                .add(new Action(Mini.t("opt.hide_zones", "Ausblenden-Zonen bearbeiten …"), () ->
                        MinecraftClient.getInstance().setScreen(new HideZoneScreen(this)), false));

        tab(Mini.t("settings.room", "Zimmer"))
                .add(new Toggle(Mini.t("opt.room_open", "Zimmer anzeigen"), () -> c.roomOpen, v -> {
                    c.roomOpen = v;
                    c.roomMinimized = false;
                }))
                .add(new Toggle(Mini.t("opt.room_min", "Minimiert"), () -> c.roomMinimized, v -> c.roomMinimized = v))
                .add(new Slider(Mini.t("opt.room_x", "Position X"), 0, screenW(), 1, () -> c.roomX, v -> c.roomX = v, null))
                .add(new Slider(Mini.t("opt.room_y", "Position Y"), 0, screenH(), 1, () -> c.roomY, v -> c.roomY = v, null))
                .add(new Slider(Mini.t("opt.room_w", "Breite"), 120, Math.max(121, screenW()), 2, () -> c.roomW, v -> c.roomW = v, null))
                .add(new Slider(Mini.t("opt.room_h", "Höhe"), 80, Math.max(81, screenH()), 2, () -> c.roomH, v -> c.roomH = v, null))
                .add(new Action(Mini.t("opt.room_reset", "Position zurücksetzen"), () -> {
                    Mini.room.resetPosition();
                    rebuild();
                }, false));

        tab(Mini.t("settings.behavior", "Verhalten"))
                .add(new Toggle(Mini.t("opt.interactions", "Figuren interagieren"), () -> c.interactions, v -> c.interactions = v))
                .add(new Toggle(Mini.t("opt.fighting", "Kämpfe/Rivalen"), () -> c.fighting, v -> c.fighting = v))
                .add(new Toggle(Mini.t("opt.needs", "Bedürfnisse"), () -> c.needs, v -> c.needs = v))
                .add(new Toggle(Mini.t("opt.relations", "Freundschaften"), () -> c.relationships, v -> c.relationships = v))
                .add(new Toggle(Mini.t("opt.emotes", "Emotes"), () -> c.emotes, v -> c.emotes = v))
                .add(new Toggle(Mini.t("opt.name_tags", "Namensschilder"), () -> c.nameTags, v -> c.nameTags = v))
                .add(new Toggle(Mini.t("opt.shadows", "Schatten"), () -> c.shadows, v -> c.shadows = v))
                .add(new Toggle(Mini.t("opt.particles", "Partikel/Effekte"), () -> c.particles, v -> c.particles = v))
                .add(new Toggle(Mini.t("opt.accessories", "Accessoires"), () -> c.accessories, v -> c.accessories = v))
                .add(new Toggle(Mini.t("opt.pets", "Haustiere"), () -> c.pets, v -> c.pets = v))
                .add(new Toggle(Mini.t("opt.vehicles", "Fahrzeuge/Reiten"), () -> c.vehicles, v -> c.vehicles = v))
                .add(new Toggle(Mini.t("opt.filters", "Farbfilter"), () -> c.colorFilters, v -> c.colorFilters = v))
                .add(new Cycle<>(Mini.t("opt.armor", "Rüstung der Figuren"), java.util.Arrays.asList(de.minicompanions.figure.ArmorMode.values()),
                        () -> de.minicompanions.figure.ArmorMode.parse(c.armorMode), v -> c.armorMode = v.name(),
                        v -> Mini.t("armor." + v.key(), v.name())))
                .add(new Toggle(Mini.t("opt.equipment", "Item des Spielers in der Hand zeigen"), () -> c.equipmentMirror, v -> c.equipmentMirror = v));

        tab(Mini.t("settings.reactions", "Reaktionen"))
                .add(new Toggle(Mini.t("opt.react_game", "Spielereignisse"), () -> c.reactGameEvents, v -> c.reactGameEvents = v))
                .add(new Toggle(Mini.t("opt.react_weather", "Wetter"), () -> c.reactWeather, v -> c.reactWeather = v))
                .add(new Toggle(Mini.t("opt.react_biome", "Tageszeit/Biom"), () -> c.reactBiome, v -> c.reactBiome = v))
                .add(new Toggle(Mini.t("opt.night_lantern", "Laterne bei Nacht"), () -> c.nightLantern, v -> c.nightLantern = v))
                .add(new Toggle(Mini.t("opt.react_combat", "Kampf"), () -> c.reactCombat, v -> c.reactCombat = v))
                .add(new Toggle(Mini.t("opt.danger", "Gefahrenwarnungen"), () -> c.dangerWarnings, v -> c.dangerWarnings = v))
                .add(new Toggle(Mini.t("opt.rare", "Seltene Funde"), () -> c.rareItemCheers, v -> c.rareItemCheers = v))
                .add(new Toggle(Mini.t("opt.spectator", "Zuschauer-Kommentare"), () -> c.spectatorComments, v -> c.spectatorComments = v))
                .add(new Toggle(Mini.t("opt.mc_chat", "Minecraft-Chat-Reaktionen"), () -> c.mcChatReactions, v -> c.mcChatReactions = v))
                .add(new Toggle(Mini.t("opt.mc_bubbles", "Chat in Sprechblasen"), () -> c.mcChatBubbles, v -> c.mcChatBubbles = v));

        tab(Mini.t("settings.inventory", "Inventar"))
                .add(new Toggle(Mini.t("opt.inv_actions", "Inventar-Aktionen"), () -> c.inventoryActions, v -> c.inventoryActions = v))
                .add(new Toggle(Mini.t("opt.inv_mp", "Im Multiplayer aus"), () -> c.inventoryOffInMultiplayer, v -> c.inventoryOffInMultiplayer = v))
                .add(new Slider(Mini.t("opt.inv_delay", "Klick-Verzögerung"), 60, 2000, 10, () -> c.inventoryClickDelayMs, v -> c.inventoryClickDelayMs = v, v -> v + " ms"))
                .add(new Slider(Mini.t("opt.inv_jitter", "Zufalls-Streuung"), 0, 1000, 10, () -> c.inventoryJitterMs, v -> c.inventoryJitterMs = v, v -> v + " ms"))
                .add(new Toggle(Mini.t("opt.inv_visual", "Figuren zeigen die Arbeit"), () -> c.inventoryVisualize, v -> c.inventoryVisualize = v));

        tab(Mini.t("settings.recording", "Aufnahme"))
                .add(new Toggle(Mini.t("opt.hide_screens", "Aus F2-Screenshots ausblenden"), () -> c.hideInScreenshots, v -> c.hideInScreenshots = v))
                .add(new Toggle(Mini.t("opt.overlay_hidden", "Overlay ausgeblendet"), () -> c.overlayHidden, v -> c.overlayHidden = v))
                .add(new Toggle(Mini.t("opt.panic_ctrl", "Panik nur mit Rechts-Strg"), () -> c.panicNeedsCtrl, v -> c.panicNeedsCtrl = v));
        onClose(() -> {
            c.validate();
            Mini.configs.markDirty(ConfigManager.Part.CONFIG);
            Mini.configs.flush();
        });
    }

    private static int screenW() {
        return Math.max(200, MinecraftClient.getInstance().getWindow().getScaledWidth());
    }

    private static int screenH() {
        return Math.max(150, MinecraftClient.getInstance().getWindow().getScaledHeight());
    }

    @Override
    protected void onChangeHook() {
        Mini.configs.markDirty(ConfigManager.Part.CONFIG);
    }
}
