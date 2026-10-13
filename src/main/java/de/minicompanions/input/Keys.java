package de.minicompanions.input;

import de.minicompanions.Mini;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Alle Tasten der Mod (frei belegbar in den Minecraft-Steuerungsoptionen, eigene Kategorie).
 * Eigene Tasten, damit es keine Konflikte mit "Take Your Stream Chat" ([ und ]) gibt.
 */
public final class Keys {
    public static KeyBinding manage;
    public static KeyBinding room;
    public static KeyBinding mouseMode;
    public static KeyBinding streamerToggle;
    public static KeyBinding panic;
    public static KeyBinding pauseSpawns;
    public static KeyBinding messagesOff;
    public static KeyBinding emergencyStop;
    public static KeyBinding overlayToggle;
    public static KeyBinding giantMode;
    public static KeyBinding miniMode;
    public static KeyBinding photoMode;
    public static KeyBinding roomMinimize;
    public static KeyBinding roomReset;
    public static KeyBinding restMode;

    private Keys() {
    }

    public static void register() {
        KeyBinding.Category cat = KeyBinding.Category.create(Identifier.of(Mini.MOD_ID, "main"));
        manage = reg("manage", GLFW.GLFW_KEY_K, cat);
        room = reg("room", GLFW.GLFW_KEY_J, cat);
        mouseMode = reg("mouse_mode", GLFW.GLFW_KEY_U, cat);
        streamerToggle = reg("streamer_toggle", InputUtil.UNKNOWN_KEY.getCode(), cat);
        panic = reg("panic", GLFW.GLFW_KEY_DELETE, cat);
        pauseSpawns = reg("pause_spawns", InputUtil.UNKNOWN_KEY.getCode(), cat);
        messagesOff = reg("messages_off", InputUtil.UNKNOWN_KEY.getCode(), cat);
        emergencyStop = reg("emergency_stop", GLFW.GLFW_KEY_END, cat);
        overlayToggle = reg("overlay_toggle", InputUtil.UNKNOWN_KEY.getCode(), cat);
        giantMode = reg("giant_mode", InputUtil.UNKNOWN_KEY.getCode(), cat);
        miniMode = reg("mini_mode", InputUtil.UNKNOWN_KEY.getCode(), cat);
        photoMode = reg("photo_mode", InputUtil.UNKNOWN_KEY.getCode(), cat);
        roomMinimize = reg("room_minimize", InputUtil.UNKNOWN_KEY.getCode(), cat);
        roomReset = reg("room_reset", InputUtil.UNKNOWN_KEY.getCode(), cat);
        restMode = reg("rest_mode", InputUtil.UNKNOWN_KEY.getCode(), cat);
    }

    private static KeyBinding reg(String name, int key, KeyBinding.Category cat) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding("key." + Mini.MOD_ID + "." + name, InputUtil.Type.KEYSYM, key, cat));
    }

    /** Panik-Taste: optional nur zusammen mit rechter Strg-Taste (Standard: Rechts-Strg + Entf). */
    public static boolean panicModifierOk() {
        if (!Mini.cfg().panicNeedsCtrl) {
            return true;
        }
        var window = MinecraftClient.getInstance().getWindow();
        return InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    public static boolean shiftDown() {
        var window = MinecraftClient.getInstance().getWindow();
        return InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_LEFT_SHIFT) || InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    public static boolean altDown() {
        var window = MinecraftClient.getInstance().getWindow();
        return InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_LEFT_ALT) || InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_RIGHT_ALT);
    }

    public static boolean ctrlDown() {
        var window = MinecraftClient.getInstance().getWindow();
        return InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_LEFT_CONTROL) || InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    public static boolean matches(KeyBinding kb, KeyInput input) {
        return kb != null && !kb.isUnbound() && kb.matchesKey(input);
    }
}
