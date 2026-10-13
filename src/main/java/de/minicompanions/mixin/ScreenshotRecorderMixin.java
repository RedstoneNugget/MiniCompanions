package de.minicompanions.mixin;

import de.minicompanions.render.RecordingMode;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.util.function.Consumer;

/**
 * Aufnahmemodus: Soll das Overlay nicht auf F2-Screenshots erscheinen, wird der Screenshot
 * um ein Bild verschoben und in diesem Bild das Overlay ausgeblendet.
 */
@Mixin(ScreenshotRecorder.class)
public class ScreenshotRecorderMixin {
    @Inject(method = "saveScreenshot(Ljava/io/File;Lnet/minecraft/client/gl/Framebuffer;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"), cancellable = true)
    private static void minicompanions$saveScreenshot(File gameDirectory, Framebuffer framebuffer, Consumer<Text> messageReceiver, CallbackInfo ci) {
        if (RecordingMode.deferScreenshot(gameDirectory, framebuffer, messageReceiver)) {
            ci.cancel();
        }
    }
}
