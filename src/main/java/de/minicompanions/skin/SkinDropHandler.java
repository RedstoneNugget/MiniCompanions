package de.minicompanions.skin;

import de.minicompanions.Mini;
import de.minicompanions.config.ConfigManager;
import de.minicompanions.config.FigureData;
import de.minicompanions.figure.Figure;
import de.minicompanions.figure.FigureEnums;
import de.minicompanions.input.InputRouter;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;

/**
 * Drag & Drop einer PNG ins Spielfenster: Die Datei wird in config/minicompanions/skins kopiert.
 * Liegt die Maus über einer eigenen Figur, bekommt diese den Skin, sonst entsteht eine neue Figur.
 */
public final class SkinDropHandler {
    private SkinDropHandler() {
    }

    public static void onFilesDropped(List<Path> paths) {
        if (Mini.configs == null || !Mini.cfg().enabled || paths == null) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        for (Path p : paths) {
            String name = p.getFileName() == null ? "" : p.getFileName().toString();
            if (!name.toLowerCase(Locale.ROOT).endsWith(".png")) {
                continue;
            }
            try {
                if (!Files.isRegularFile(p) || Files.size(p) > 1024 * 1024) {
                    Mini.chat(Mini.t("drop.too_large", "%s ist keine gültige PNG (max. 1 MB).", name));
                    continue;
                }
                String safe = name.replaceAll("[^A-Za-z0-9 _.()-]", "_");
                if (safe.length() > 80) {
                    safe = safe.substring(safe.length() - 80);
                }
                Path target = Mini.skins.resolveSkinFile(safe);
                if (target == null) {
                    continue;
                }
                Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING);
                final String file = target.getFileName().toString();
                mc.execute(() -> apply(file));
            } catch (IOException e) {
                Mini.chat(Mini.t("drop.failed", "%s konnte nicht übernommen werden.", name));
            }
        }
    }

    private static void apply(String file) {
        Mini.skins.checkFileChanges();
        float mx = InputRouter.mouseX();
        float my = InputRouter.mouseY();
        Figure f = Mini.figures.figureAt(mx, my);
        if (f == null) {
            f = Mini.figures.figureAtInRoom(mx, my);
        }
        if (f != null && !f.viewer) {
            f.data.skinType = FigureEnums.SkinType.FILE.name();
            f.data.skinValue = file;
            Mini.configs.markDirty(ConfigManager.Part.FIGURES);
            Mini.chat(Mini.t("drop.applied", "Skin %s auf %s angewendet.", file, f.data.name));
            return;
        }
        FigureData d = new FigureData();
        d.name = Mini.figures.uniqueName(file.replaceAll("(?i)\\.png$", ""));
        d.skinType = FigureEnums.SkinType.FILE.name();
        d.skinValue = file;
        if (Mini.figures.add(d) != null) {
            Mini.chat(Mini.t("drop.created", "Neue Figur %s mit Skin %s.", d.name, file));
        }
    }
}
