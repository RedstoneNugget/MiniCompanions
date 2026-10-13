package de.minicompanions.input;

import de.minicompanions.Mini;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Durchsichtiger "Maus-Modus" für das normale Spiel: gibt den Mauszeiger frei, damit man Figuren und
 * das Spielzimmer-Fenster bedienen kann, ohne die Spielsteuerung dauerhaft zu blockieren.
 * Pausiert das Spiel nicht. ESC oder die Maus-Modus-Taste beendet ihn.
 */
public final class OverlayInteractScreen extends Screen {
    public OverlayInteractScreen() {
        super(Text.literal("Mini Companions"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        // komplett durchsichtig
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        // Das Overlay wird über das Screen-Ereignis "afterRender" gezeichnet.
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (Keys.matches(Keys.mouseMode, input) && input.key() != GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(null);
        }
        if (Mini.configs != null) {
            Mini.configs.flush();
        }
    }
}
