package de.minicompanions.mixin;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** Lesezugriff auf die sichtbaren Chat-Zeilen, damit Figuren darauf stehen können (nur lesen). */
@Mixin(ChatHud.class)
public interface ChatHudAccessor {
    @Accessor("visibleMessages")
    List<ChatHudLine.Visible> minicompanions$getVisibleMessages();

    @Accessor("scrolledLines")
    int minicompanions$getScrolledLines();
}
