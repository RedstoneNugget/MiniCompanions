package de.minicompanions.render;

import de.minicompanions.Mini;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Eigene Accessoires als PNG aus config/minicompanions/accessories/ (z. B. "hut.png").
 * Werden asynchron geladen und über dem Kopf in 8x8 Skin-Pixeln gezeichnet.
 */
public final class AccessoryTextures {
    private static final Map<String, Identifier> LOADED = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> PENDING = new ConcurrentHashMap<>();
    private static final Identifier NONE = Identifier.of(Mini.MOD_ID, "none");

    private AccessoryTextures() {
    }

    public static void drawCustom(DrawContext ctx, String file) {
        Identifier id = get(file);
        if (id == null || id == NONE) {
            return;
        }
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, id, -5, -15, 0, 0, 10, 10, 10, 10, 0xFFFFFFFF);
    }

    private static Identifier get(String file) {
        Identifier id = LOADED.get(file);
        if (id != null) {
            return id;
        }
        if (PENDING.putIfAbsent(file, Boolean.TRUE) != null) {
            return null;
        }
        if (!file.matches("[A-Za-z0-9 _.()-]{1,64}") || !file.toLowerCase(Locale.ROOT).endsWith(".png")) {
            LOADED.put(file, NONE);
            return null;
        }
        Path dir = Mini.configs.sub("accessories");
        Path p = dir.resolve(file).normalize();
        CompletableFuture.runAsync(() -> {
            try {
                if (!p.startsWith(dir) || !Files.exists(p) || Files.size(p) > 256 * 1024) {
                    LOADED.put(file, NONE);
                    return;
                }
                NativeImage img = NativeImage.read(Files.readAllBytes(p));
                if (img.getWidth() > 128 || img.getHeight() > 128) {
                    img.close();
                    LOADED.put(file, NONE);
                    return;
                }
                Identifier tid = Identifier.of(Mini.MOD_ID, "accessory/" + Integer.toHexString(file.hashCode()));
                MinecraftClient.getInstance().execute(() -> {
                    MinecraftClient.getInstance().getTextureManager().registerTexture(tid, new NativeImageBackedTexture(() -> "minicompanions accessory " + file, img));
                    LOADED.put(file, tid);
                });
            } catch (Exception e) {
                LOADED.put(file, NONE);
            }
        });
        return null;
    }

    public static void clear() {
        LOADED.clear();
        PENDING.clear();
    }
}
