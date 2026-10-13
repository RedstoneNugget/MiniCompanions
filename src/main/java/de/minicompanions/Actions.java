package de.minicompanions;

import de.minicompanions.config.ConfigManager;
import de.minicompanions.gui.ManageScreen;
import de.minicompanions.gui.StreamerScreen;
import de.minicompanions.input.Keys;
import de.minicompanions.input.OverlayInteractScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;

/**
 * Zentrale Aktionen für Tasten, Befehle und Knöpfe.
 */
public final class Actions {
    private static Screen pendingScreen;

    private Actions() {
    }

    /** Screen im nächsten Tick öffnen (z. B. aus einem Chat-Befehl, der den Chat erst schließt). */
    public static void openLater(Screen s) {
        pendingScreen = s;
    }

    public static void tickPending(MinecraftClient mc) {
        if (pendingScreen != null) {
            Screen s = pendingScreen;
            pendingScreen = null;
            mc.setScreen(s);
        }
    }

    /** Panik: alle Zuschauer-Figuren und -Blasen sofort weg, Warteschlange leer, Spawns pausiert. */
    public static void panic() {
        int removed = Mini.streamer.panic();
        Mini.inventory.emergencyStop(false);
        Mini.chat(Mini.t("msg.panic", "PANIK: %s Zuschauer-Figuren entfernt, Blasen gelöscht, Chat-Spawns pausiert.", removed));
    }

    public static void toggleMessagesOff() {
        var mod = Mini.streamer.moderation();
        mod.setMessagesHidden(!mod.messagesHidden());
        if (mod.messagesHidden()) {
            Mini.streamer.viewers().hideAllMessages();
        }
        Mini.chat(mod.messagesHidden() ? Mini.t("msg.messages_off", "Zuschauer-Nachrichten ausgeblendet.")
                : Mini.t("msg.messages_on", "Zuschauer-Nachrichten wieder sichtbar."));
    }

    public static void togglePauseSpawns() {
        var mod = Mini.streamer.moderation();
        mod.setSpawnsPaused(!mod.spawnsPaused());
        Mini.chat(mod.spawnsPaused() ? Mini.t("msg.spawns_paused", "Chat-Spawns pausiert.")
                : Mini.t("msg.spawns_resumed", "Chat-Spawns wieder aktiv."));
    }

    public static void toggleOverlay() {
        Mini.cfg().overlayHidden = !Mini.cfg().overlayHidden;
        Mini.configs.markDirty(ConfigManager.Part.CONFIG);
    }

    public static void toggleStreamerMode() {
        Mini.streamer.setEnabled(!Mini.streamer.isEnabled());
    }

    public static void toggleSizeMode(String mode) {
        var cfg = Mini.cfg();
        cfg.sizeMode = mode.equals(cfg.sizeMode) ? "NORMAL" : mode;
        Mini.configs.markDirty(ConfigManager.Part.CONFIG);
    }

    public static void toggleRest() {
        Mini.cfg().restMode = !Mini.cfg().restMode;
        Mini.configs.markDirty(ConfigManager.Part.CONFIG);
        if (!Mini.cfg().restMode) {
            for (var f : Mini.figures.all()) {
                if (f.activity == de.minicompanions.ai.Activity.SLEEP) {
                    f.setActivity(de.minicompanions.ai.Activity.YAWN, 1.2f);
                    f.emote = null;
                }
            }
        }
    }

    public static void openManage() {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.setScreen(new ManageScreen(mc.currentScreen instanceof OverlayInteractScreen ? null : mc.currentScreen));
    }

    /**
     * Zimmer-Taste. Im normalen Spiel: Zimmer zeigen und Maus freigeben, damit man Figuren hineinziehen kann
     * (auch wenn das Zimmer vom letzten Mal schon offen ist). Im Maus-Modus: Zimmer schließen und zurück ins Spiel.
     */
    public static void toggleRoom() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen == null) {
            Mini.room.setOpen(true);
            mc.setScreen(new OverlayInteractScreen());
            return;
        }
        Mini.room.toggle();
        if (!Mini.room.isOpen() && mc.currentScreen instanceof OverlayInteractScreen ois) {
            ois.close();
        }
    }

    public static void openStreamer() {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.setScreen(new StreamerScreen(mc.currentScreen instanceof OverlayInteractScreen ? null : mc.currentScreen));
    }

    /** Tasten im Spiel (kein Screen offen). */
    public static void tickKeys(MinecraftClient mc) {
        while (Keys.manage.wasPressed()) {
            if (mc.currentScreen == null) {
                openManage();
            }
        }
        while (Keys.room.wasPressed()) {
            toggleRoom();
        }
        while (Keys.mouseMode.wasPressed()) {
            if (mc.currentScreen == null) {
                mc.setScreen(new OverlayInteractScreen());
            }
        }
        while (Keys.streamerToggle.wasPressed()) {
            toggleStreamerMode();
        }
        while (Keys.panic.wasPressed()) {
            if (Keys.panicModifierOk()) {
                panic();
            }
        }
        while (Keys.pauseSpawns.wasPressed()) {
            togglePauseSpawns();
        }
        while (Keys.messagesOff.wasPressed()) {
            toggleMessagesOff();
        }
        while (Keys.emergencyStop.wasPressed()) {
            Mini.inventory.emergencyStop(true);
        }
        while (Keys.overlayToggle.wasPressed()) {
            toggleOverlay();
        }
        while (Keys.giantMode.wasPressed()) {
            toggleSizeMode("GIANT");
        }
        while (Keys.miniMode.wasPressed()) {
            toggleSizeMode("MINI");
        }
        while (Keys.photoMode.wasPressed()) {
            Mini.figures.photoPose();
        }
        while (Keys.roomMinimize.wasPressed()) {
            Mini.room.toggleMinimized();
        }
        while (Keys.roomReset.wasPressed()) {
            Mini.room.resetPosition();
        }
        while (Keys.restMode.wasPressed()) {
            toggleRest();
        }
    }

    /** Tasten im Maus-Modus (dort greifen KeyBindings nicht automatisch). */
    public static boolean handleGlobalKey(KeyInput key) {
        if (Keys.matches(Keys.manage, key)) {
            openManage();
            return true;
        }
        if (Keys.matches(Keys.room, key)) {
            toggleRoom();
            return true;
        }
        if (Keys.matches(Keys.streamerToggle, key)) {
            toggleStreamerMode();
            return true;
        }
        if (Keys.matches(Keys.giantMode, key)) {
            toggleSizeMode("GIANT");
            return true;
        }
        if (Keys.matches(Keys.miniMode, key)) {
            toggleSizeMode("MINI");
            return true;
        }
        if (Keys.matches(Keys.photoMode, key)) {
            Mini.figures.photoPose();
            return true;
        }
        if (Keys.matches(Keys.roomMinimize, key)) {
            Mini.room.toggleMinimized();
            return true;
        }
        if (Keys.matches(Keys.roomReset, key)) {
            Mini.room.resetPosition();
            return true;
        }
        if (Keys.matches(Keys.restMode, key)) {
            toggleRest();
            return true;
        }
        return false;
    }
}
