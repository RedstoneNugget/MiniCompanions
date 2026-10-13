package de.minicompanions.mixin;

import de.minicompanions.render.RecordingMode;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Nach jedem fertigen Bild: verschobenen Screenshot (ohne Overlay) aufnehmen. */
@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "render(Lnet/minecraft/client/render/RenderTickCounter;Z)V", at = @At("TAIL"))
    private void minicompanions$afterRender(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        RecordingMode.afterFrame();
    }
}
