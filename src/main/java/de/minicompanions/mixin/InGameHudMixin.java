package de.minicompanions.mixin;

import de.minicompanions.render.OverlayRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Figuren ganz am Ende des HUD zeichnen. Minecraft legt für Chat, Spielerliste und Untertitel eigene
 * Zeichenebenen an; würden die Figuren davor gezeichnet, läge z. B. der Chat-Text über ihnen.
 */
@Mixin(InGameHud.class)
public class InGameHudMixin {
    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V", at = @At("TAIL"))
    private void minicompanions$afterHud(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (!MinecraftClient.getInstance().options.hudHidden) {
            OverlayRenderer.renderHud(context, tickCounter);
        }
    }
}
