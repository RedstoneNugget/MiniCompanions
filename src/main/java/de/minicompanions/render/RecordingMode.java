package de.minicompanions.render;

import de.minicompanions.Mini;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;

import java.io.File;
import java.util.function.Consumer;

/**
 * Aufnahmemodus: Overlay per Hotkey ein-/ausblenden und optional aus F2-Screenshots heraushalten.
 * Bei aktivem "Aus Screenshots ausblenden" wird der Screenshot um ein Bild verschoben, das ohne
 * Overlay gezeichnet wird.
 */
public final class RecordingMode {
    private static boolean hideForShot;
    private static boolean taking;
    private static File pendingDir;
    private static Framebuffer pendingFb;
    private static Consumer<Text> pendingConsumer;

    private RecordingMode() {
    }

    public static boolean isHidden() {
        return Mini.configs != null && (Mini.cfg().overlayHidden || hideForShot);
    }

    /** Vom Screenshot-Mixin: @return true = Screenshot wurde verschoben (Original abbrechen). */
    public static boolean deferScreenshot(File dir, Framebuffer fb, Consumer<Text> consumer) {
        if (taking || Mini.configs == null || !Mini.cfg().hideInScreenshots || Mini.cfg().overlayHidden || !Mini.cfg().enabled) {
            return false;
        }
        pendingDir = dir;
        pendingFb = fb;
        pendingConsumer = consumer;
        hideForShot = true;
        return true;
    }

    /** Vom GameRenderer-Mixin am Ende jedes Bildes. */
    public static void afterFrame() {
        if (!hideForShot) {
            return;
        }
        hideForShot = false;
        if (pendingFb == null) {
            return;
        }
        taking = true;
        try {
            ScreenshotRecorder.saveScreenshot(pendingDir, pendingFb, pendingConsumer);
        } finally {
            taking = false;
            pendingDir = null;
            pendingFb = null;
            pendingConsumer = null;
        }
    }
}
