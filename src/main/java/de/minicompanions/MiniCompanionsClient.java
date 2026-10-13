package de.minicompanions;

import de.minicompanions.ai.Lines;
import de.minicompanions.commands.MiniCommands;
import de.minicompanions.config.ConfigManager;
import de.minicompanions.config.PresetManager;
import de.minicompanions.events.GameEventWatcher;
import de.minicompanions.events.McChatWatcher;
import de.minicompanions.figure.Figure;
import de.minicompanions.figure.FigureManager;
import de.minicompanions.input.InputRouter;
import de.minicompanions.input.Keys;
import de.minicompanions.inventory.InventoryTasks;
import de.minicompanions.render.AccessoryTextures;
import de.minicompanions.render.OverlayRenderer;
import de.minicompanions.render.Particles;
import de.minicompanions.render.Sounds;
import de.minicompanions.room.RoomWindow;
import de.minicompanions.skin.SkinManager;
import de.minicompanions.streamer.StreamerModeManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Einstiegspunkt der rein clientseitigen Mod "Mini-Begleiter".
 * Registriert keine Blöcke/Items/Entities, sendet keine eigenen Pakete und braucht keinen Server-Mod.
 */
public final class MiniCompanionsClient implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("MiniCompanions");
    public static PresetManager presets;
    private static int tickCounter;

    @Override
    public void onInitializeClient() {
        Path dir = FabricLoader.getInstance().getConfigDir().resolve(Mini.MOD_ID);
        Mini.configs = new ConfigManager(dir);
        Mini.configs.loadAll();
        Path skinCache = Mini.configs.sub("cache").resolve("skins");
        try {
            Files.createDirectories(skinCache);
        } catch (IOException e) {
            LOG.warn("[MiniCompanions] Cache-Ordner nicht anlegbar: {}", e.toString());
        }
        // heruntergeladene Skins nicht ansammeln: bei jedem Start frisch laden
        int cleared = SkinManager.clearDiskCache(skinCache);
        if (cleared > 0) {
            LOG.info("[MiniCompanions] {} Skins aus dem Cache gelöscht", cleared);
        }
        Mini.skins = new SkinManager(Mini.configs.sub("skins"), skinCache);
        Mini.skins.setErrorReporter(MiniCompanionsClient::reportSkinError);
        Mini.lines = new Lines();
        Mini.particles = new Particles();
        Mini.sounds = new Sounds();
        Mini.figures = new FigureManager();
        Mini.room = new RoomWindow();
        Mini.inventory = new InventoryTasks();
        Mini.events = new GameEventWatcher();
        Mini.streamer = new StreamerModeManager();
        presets = new PresetManager();
        Mini.figures.syncFromConfig();

        Keys.register();
        Mini.events.register();
        new McChatWatcher().register();

        // Das HUD-Overlay wird über InGameHudMixin ganz am Ende des HUD gezeichnet (über dem Chat)
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            ScreenEvents.afterRender(screen).register(OverlayRenderer::renderScreen);
            InputRouter.attach(screen);
        });
        ClientTickEvents.END_CLIENT_TICK.register(MiniCompanionsClient::tick);
        ClientCommandRegistrationCallback.EVENT.register(MiniCommands::register);
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            reloadLines();
            Mini.streamer.onStartup();
            if (!Mini.configs.warnings.isEmpty()) {
                LOG.warn("[MiniCompanions] Defekte Dateien wurden gesichert und ersetzt: {}", Mini.configs.warnings);
            }
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            Mini.inventory.emergencyStop(false);
            Mini.streamer.shutdown();
            Mini.configs.saveAll();
            Mini.skins.shutdown();
        });
        Mini.configs.setReloadListener(MiniCompanionsClient::onExternalReload);
        LOG.info("[MiniCompanions] geladen: {} Figuren, Streamer-Modus {}", Mini.configs.figures.size(),
                Mini.configs.streamer.streamerMode ? "an" : "aus");
    }

    private static void tick(MinecraftClient client) {
        tickCounter++;
        Actions.tickPending(client);
        Actions.tickKeys(client);
        Mini.streamer.tick();
        Mini.events.tick(client);
        Mini.configs.tick();
        if (tickCounter % 40 == 0) {
            Mini.skins.checkFileChanges();
        }
        if (tickCounter == 60 && !Mini.configs.warnings.isEmpty() && client.player != null) {
            Mini.chat(Mini.t("msg.config_broken", "Eine Config-Datei war beschädigt und wurde gesichert (Backup/Standard geladen)."));
        }
    }

    /** Sprüche in der eingestellten Sprache laden. */
    public static void reloadLines() {
        String lang = Mini.cfg().language;
        if (lang == null || "auto".equals(lang)) {
            String mcLang = MinecraftClient.getInstance().options == null ? "en_us" : MinecraftClient.getInstance().options.language;
            lang = mcLang != null && mcLang.toLowerCase(Locale.ROOT).startsWith("de") ? "de_de" : "en_us";
        }
        Mini.lines.load(lang, Mini.configs.sub("lines"));
    }

    /** Befehl /mini reload: alles neu laden (Config, Figuren, Skins, Sprüche). */
    public static void reloadAll() {
        Mini.configs.flush();
        for (ConfigManager.Part p : ConfigManager.Part.values()) {
            Mini.configs.load(p);
            onExternalReload(p);
        }
        Mini.skins.reloadAll();
        AccessoryTextures.clear();
        reloadLines();
    }

    private static void onExternalReload(ConfigManager.Part part) {
        switch (part) {
            case FIGURES -> Mini.figures.syncFromConfig();
            case MODERATION -> Mini.streamer.moderation().replaceLists(Mini.configs.moderation);
            case STREAMER, VIEWERS -> Mini.streamer.configReloaded();
            case CONFIG -> reloadLines();
            default -> {
            }
        }
    }

    /** Fehler beim Laden eines Skins melden - nur für eigene Figuren (keine Chat-Flut durch Zuschauer). */
    private static void reportSkinError(String info) {
        int sep = info.indexOf('|');
        String reason = sep < 0 ? info : info.substring(0, sep);
        String what = sep < 0 ? "" : info.substring(sep + 1);
        boolean own = false;
        for (Figure f : Mini.figures.all()) {
            if (!f.viewer && f.data.skinValue != null && f.data.skinValue.equalsIgnoreCase(what)) {
                own = true;
                break;
            }
        }
        if (!own) {
            return;
        }
        String shown = what.length() > 60 ? what.substring(0, 60) + "…" : what;
        Mini.chat(switch (reason) {
            case "unknown_player" -> Mini.t("skin.err.unknown", "Spieler %s nicht gefunden - Standard-Skin wird verwendet.", shown);
            case "file_missing" -> Mini.t("skin.err.missing", "Datei %s fehlt im Ordner config/minicompanions/skins - Standard-Skin.", shown);
            case "bad_size" -> Mini.t("skin.err.size", "%s ist kein 64x64- oder 64x32-Skin - Standard-Skin.", shown);
            case "not_png" -> Mini.t("skin.err.png", "%s ist keine gültige PNG - Standard-Skin.", shown);
            case "too_large" -> Mini.t("skin.err.large", "%s ist zu groß (max. 1 MB) - Standard-Skin.", shown);
            default -> Mini.t("skin.err.generic", "Skin %s konnte nicht geladen werden (%s) - Standard-Skin.", shown, reason);
        });
    }
}
