package de.minicompanions.mixin;

import de.minicompanions.input.InputRouter;
import de.minicompanions.skin.SkinDropHandler;
import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;
import java.util.List;

/** Drag & Drop von PNG-Skins ins Spielfenster und Zustand der linken Maustaste – auch ohne offenen Screen. */
@Mixin(Mouse.class)
public class MouseMixin {
    @Inject(method = "onFilesDropped", at = @At("HEAD"))
    private void minicompanions$onFilesDropped(long window, List<Path> paths, int invalidFilesCount, CallbackInfo ci) {
        SkinDropHandler.onFilesDropped(paths);
    }

    /** Jedes Drücken/Loslassen der linken Maustaste mitlesen (Minecraft selbst merkt es sich nur ohne Screen). */
    @Inject(method = "onMouseButton", at = @At("HEAD"))
    private void minicompanions$onMouseButton(long window, MouseInput input, int action, CallbackInfo ci) {
        if (input.button() == 0) {
            InputRouter.setLeftDown(action != 0);
        }
    }
}
