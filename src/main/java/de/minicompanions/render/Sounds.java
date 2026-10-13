package de.minicompanions.render;

import de.minicompanions.Mini;
import de.minicompanions.figure.Figure;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

/**
 * Optionale Mini-Sounds (nur lokal abgespielt, global stummschaltbar, Tonhöhe pro Figur).
 */
public final class Sounds {
    public void figure(Figure f, String type) {
        if (!Mini.cfg().sounds || f.soundCooldown > 0 || f.data.volume <= 0) {
            return;
        }
        f.soundCooldown = 0.15f;
        float pitch = f.data.pitch / 100f;
        float size = f.sizePercent(Mini.cfg()) / 100f;
        pitch *= (float) Math.max(0.6, Math.min(1.6, 1.25 / Math.sqrt(size)));
        float vol = Mini.cfg().soundVolume / 100f * f.data.volume / 100f * 0.35f;
        switch (type) {
            case "jump" -> play(SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), pitch * 1.4f, vol * 0.6f);
            case "talk" -> play(SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), pitch * (1.2f + (float) Math.random() * 0.3f), vol * 0.5f);
            case "eat" -> play(SoundEvents.ENTITY_GENERIC_EAT.value(), pitch * 1.5f, vol);
            case "pickup" -> play(SoundEvents.ENTITY_ITEM_PICKUP, pitch * 1.6f, vol);
            case "happy" -> play(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), pitch * 1.5f, vol);
            case "hurt" -> play(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), pitch, vol);
            default -> {
            }
        }
    }

    public void ui(String type) {
        if (!Mini.cfg().sounds) {
            return;
        }
        float vol = Mini.cfg().soundVolume / 100f * 0.4f;
        switch (type) {
            case "firework" -> play(SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, 1.6f, vol * 0.5f);
            case "click" -> play(SoundEvents.UI_BUTTON_CLICK.value(), 1.4f, vol);
            case "win" -> play(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.6f, vol * 0.6f);
            case "pop" -> play(SoundEvents.ENTITY_CHICKEN_EGG, 1.8f, vol);
            default -> {
            }
        }
    }

    private static void play(SoundEvent e, float pitch, float vol) {
        if (vol <= 0.001f) {
            return;
        }
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.ui(e, Math.max(0.5f, Math.min(2f, pitch)), vol));
    }
}
