package de.minicompanions.mixin;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lesezugriff auf Position und Größe des Container-Fensters (für Slot-Plattformen). */
@Mixin(HandledScreen.class)
public interface HandledScreenAccessor {
    @Accessor("x")
    int minicompanions$getX();

    @Accessor("y")
    int minicompanions$getY();

    @Accessor("backgroundWidth")
    int minicompanions$getBackgroundWidth();

    @Accessor("backgroundHeight")
    int minicompanions$getBackgroundHeight();
}
