package de.minicompanions;

import de.minicompanions.ai.Lines;
import de.minicompanions.config.ConfigManager;
import de.minicompanions.config.MiniConfig;
import de.minicompanions.events.GameEventWatcher;
import de.minicompanions.figure.FigureManager;
import de.minicompanions.inventory.InventoryTasks;
import de.minicompanions.render.Particles;
import de.minicompanions.render.Sounds;
import de.minicompanions.skin.SkinManager;
import de.minicompanions.streamer.StreamerModeManager;
import de.minicompanions.room.RoomWindow;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Zentrale Zugriffsstelle auf alle Dienste der Mod (nur Client).
 */
public final class Mini {
    public static final String MOD_ID = "minicompanions";

    public static ConfigManager configs;
    public static SkinManager skins;
    public static FigureManager figures;
    public static RoomWindow room;
    public static StreamerModeManager streamer;
    public static Lines lines;
    public static Particles particles;
    public static InventoryTasks inventory;
    public static GameEventWatcher events;
    public static Sounds sounds;

    private Mini() {
    }

    public static MiniConfig cfg() {
        return configs.config;
    }

    /** Übersetzbarer Text mit deutschem Fallback (falls ein Schlüssel in einer Sprachdatei fehlt). */
    public static Text t(String key, String fallback, Object... args) {
        return Text.translatableWithFallback(MOD_ID + "." + key, fallback, args);
    }

    /** Meldung im eigenen Chat (nur lokal, wird nie an den Server gesendet). */
    public static void chat(Text msg) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.inGameHud != null) {
            mc.inGameHud.getChatHud().addMessage(Text.literal("[Mini] ").formatted(Formatting.GOLD).append(msg.copy().formatted(Formatting.GRAY)));
        }
    }

    public static boolean isMultiplayer() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.world != null && !mc.isInSingleplayer();
    }
}
