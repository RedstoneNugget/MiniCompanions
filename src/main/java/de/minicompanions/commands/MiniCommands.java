package de.minicompanions.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import de.minicompanions.Actions;
import de.minicompanions.Mini;
import de.minicompanions.MiniCompanionsClient;
import de.minicompanions.config.FigureData;
import de.minicompanions.figure.Figure;
import de.minicompanions.figure.FigureEnums;
import de.minicompanions.figure.FigureManager;
import de.minicompanions.gui.ManageScreen;
import de.minicompanions.gui.SettingsScreen;
import de.minicompanions.gui.StreamerScreen;
import de.minicompanions.input.OverlayInteractScreen;
import de.minicompanions.inventory.InventoryTasks;
import de.minicompanions.streamer.CommandParser;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;

import java.util.Locale;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * Reine Client-Befehle unter /mini (werden nie an den Server gesendet).
 */
public final class MiniCommands {
    private MiniCommands() {
    }

    private static final SuggestionProvider<FabricClientCommandSource> FIGURES = (ctx, b) ->
            CommandSource.suggestMatching(Mini.figures.all().stream().filter(f -> !f.viewer).map(f -> f.data.name.contains(" ") ? "\"" + f.data.name + "\"" : f.data.name), b);
    private static final SuggestionProvider<FabricClientCommandSource> FIGURES_ALL = (ctx, b) ->
            CommandSource.suggestMatching(java.util.stream.Stream.concat(java.util.stream.Stream.of("all"),
                    Mini.figures.all().stream().filter(f -> !f.viewer).map(f -> f.data.name.contains(" ") ? "\"" + f.data.name + "\"" : f.data.name)), b);
    private static final SuggestionProvider<FabricClientCommandSource> VIEWERS = (ctx, b) ->
            CommandSource.suggestMatching(Mini.streamer.viewers().list().stream().map(f -> f.viewerLogin), b);
    private static final SuggestionProvider<FabricClientCommandSource> TASKS = (ctx, b) ->
            CommandSource.suggestMatching(java.util.Arrays.stream(InventoryTasks.TaskType.values()).filter(t -> t != InventoryTasks.TaskType.BRING_ITEM).map(InventoryTasks.TaskType::key), b);
    private static final SuggestionProvider<FabricClientCommandSource> PRESETS = (ctx, b) ->
            CommandSource.suggestMatching(MiniCompanionsClient.presets.list().stream().map(n -> n.contains(" ") ? "\"" + n + "\"" : n), b);

    public static void register(CommandDispatcher<FabricClientCommandSource> d, CommandRegistryAccess access) {
        d.register(literal("mini")
                .executes(c -> {
                    Actions.openLater(new ManageScreen(null));
                    return 1;
                })
                .then(literal("add").then(argument("skin", StringArgumentType.greedyString()).executes(MiniCommands::add)))
                .then(literal("remove").then(argument("name", StringArgumentType.greedyString()).suggests(FIGURES).executes(c -> {
                    Figure f = Mini.figures.findOwn(StringArgumentType.getString(c, "name").replace("\"", ""));
                    if (f == null) {
                        return error(c, Mini.t("cmd.not_found", "Keine Figur mit diesem Namen."));
                    }
                    Mini.figures.remove(f);
                    return ok(c, Mini.t("cmd.removed", "%s entfernt.", f.data.name));
                })))
                .then(literal("list").executes(MiniCommands::list))
                .then(literal("clear").executes(c -> {
                    int n = Mini.figures.ownCount();
                    Mini.figures.clearOwn();
                    return ok(c, Mini.t("cmd.cleared", "%s Figuren entfernt.", n));
                }))
                .then(literal("say").then(argument("name", StringArgumentType.string()).suggests(FIGURES)
                        .then(argument("text", StringArgumentType.greedyString()).executes(c -> {
                            Figure f = Mini.figures.findOwn(StringArgumentType.getString(c, "name"));
                            if (f == null) {
                                return error(c, Mini.t("cmd.not_found", "Keine Figur mit diesem Namen."));
                            }
                            FigureManager.say(f, StringArgumentType.getString(c, "text"));
                            return 1;
                        }))))
                .then(literal("give").then(argument("name", StringArgumentType.string()).suggests(FIGURES)
                        .then(argument("task", StringArgumentType.word()).suggests(TASKS).executes(MiniCommands::give))))
                .then(literal("preset")
                        .then(literal("save").then(argument("name", StringArgumentType.greedyString()).executes(c -> {
                            String n = StringArgumentType.getString(c, "name");
                            MiniCompanionsClient.presets.save(n);
                            return ok(c, Mini.t("presets.saved", "Gespeichert: %s", n));
                        })))
                        .then(literal("delete").then(argument("name", StringArgumentType.greedyString()).suggests(PRESETS).executes(c ->
                                MiniCompanionsClient.presets.delete(StringArgumentType.getString(c, "name").replace("\"", ""))
                                        ? ok(c, Mini.t("presets.deleted", "Preset gelöscht.")) : error(c, Mini.t("presets.not_found", "Preset nicht gefunden.")))))
                        .then(literal("export").then(argument("name", StringArgumentType.greedyString()).suggests(PRESETS).executes(c -> {
                            var p = MiniCompanionsClient.presets.read(StringArgumentType.getString(c, "name").replace("\"", ""));
                            if (p == null) {
                                return error(c, Mini.t("presets.not_found", "Preset nicht gefunden."));
                            }
                            c.getSource().getClient().keyboard.setClipboard(MiniCompanionsClient.presets.exportCode(p));
                            return ok(c, Mini.t("presets.copied", "Code in die Zwischenablage kopiert."));
                        })))
                        .then(literal("import").then(argument("code", StringArgumentType.greedyString()).executes(c -> {
                            var p = MiniCompanionsClient.presets.importCode(StringArgumentType.getString(c, "code"));
                            if (p == null) {
                                return error(c, Mini.t("presets.bad_code", "Kein gültiger Preset-Code."));
                            }
                            return ok(c, Mini.t("presets.imported", "%s Figuren importiert.", MiniCompanionsClient.presets.apply(p, false)));
                        })))
                        .then(argument("name", StringArgumentType.greedyString()).suggests(PRESETS).executes(c -> {
                            var p = MiniCompanionsClient.presets.read(StringArgumentType.getString(c, "name").replace("\"", ""));
                            if (p == null) {
                                return error(c, Mini.t("presets.not_found", "Preset nicht gefunden."));
                            }
                            return ok(c, Mini.t("presets.loaded", "%s Figuren geladen.", MiniCompanionsClient.presets.apply(p, true)));
                        })))
                .then(literal("size").then(argument("target", StringArgumentType.string()).suggests(FIGURES_ALL)
                        .then(literal("reset").executes(c -> size(c, null)))
                        .then(argument("percent", IntegerArgumentType.integer(10, 1000)).executes(c -> size(c, IntegerArgumentType.getInteger(c, "percent"))))))
                .then(literal("play").executes(c -> {
                    Mini.room.setOpen(true);
                    Actions.openLater(new OverlayInteractScreen());
                    return ok(c, Mini.t("cmd.room_open", "Zimmer geöffnet (Maus-Modus aktiv, ESC zum Beenden)."));
                }))
                .then(literal("room")
                        .then(literal("open").executes(c -> {
                            Mini.room.setOpen(true);
                            return 1;
                        }))
                        .then(literal("close").executes(c -> {
                            Mini.room.setOpen(false);
                            return 1;
                        }))
                        .then(literal("reset").executes(c -> {
                            Mini.room.resetPosition();
                            return ok(c, Mini.t("cmd.room_reset", "Zimmer-Position zurückgesetzt."));
                        })))
                .then(literal("stream").executes(c -> {
                    Actions.openLater(new StreamerScreen(null));
                    return 1;
                }))
                .then(literal("streammode").then(argument("state", StringArgumentType.word())
                        .suggests((c, b) -> CommandSource.suggestMatching(new String[]{"an", "aus", "on", "off"}, b))
                        .executes(c -> {
                            String s = StringArgumentType.getString(c, "state").toLowerCase(Locale.ROOT);
                            boolean on = s.equals("an") || s.equals("on") || s.equals("ein") || s.equals("true");
                            boolean off = s.equals("aus") || s.equals("off") || s.equals("false");
                            if (!on && !off) {
                                return error(c, Mini.t("cmd.on_off", "Bitte \"an\" oder \"aus\" angeben."));
                            }
                            Mini.streamer.setEnabled(on);
                            return 1;
                        })))
                .then(literal("panic").executes(c -> {
                    Actions.panic();
                    return 1;
                }))
                .then(literal("kick").then(argument("viewer", StringArgumentType.word()).suggests(VIEWERS).executes(c -> {
                    String v = StringArgumentType.getString(c, "viewer");
                    Mini.streamer.kick(v);
                    return ok(c, Mini.t("cmd.kicked", "Figur von %s gelöscht.", v));
                })))
                .then(literal("ban").then(argument("viewer", StringArgumentType.word()).suggests(VIEWERS).executes(c -> {
                    String v = StringArgumentType.getString(c, "viewer");
                    Mini.streamer.ban(v);
                    return ok(c, Mini.t("cmd.banned", "%s gesperrt und Figur gelöscht.", v));
                })))
                .then(literal("unban").then(argument("viewer", StringArgumentType.word()).executes(c -> {
                    String v = StringArgumentType.getString(c, "viewer");
                    return Mini.streamer.moderation().unbanViewer(v) ? ok(c, Mini.t("cmd.unbanned", "%s entsperrt.", v))
                            : error(c, Mini.t("cmd.not_banned", "%s war nicht gesperrt.", v));
                })))
                .then(literal("pause").executes(c -> {
                    Actions.togglePauseSpawns();
                    return 1;
                }))
                .then(literal("streamtest").then(argument("viewer", StringArgumentType.word())
                        .then(argument("message", StringArgumentType.greedyString()).executes(c -> {
                            var o = Mini.streamer.simulate(StringArgumentType.getString(c, "viewer"), StringArgumentType.getString(c, "message"));
                            return ok(c, Mini.t("cmd.streamtest", "Simuliert: %s", o.name()));
                        }))))
                .then(literal("settings").executes(c -> {
                    Actions.openLater(new SettingsScreen(null));
                    return 1;
                }))
                .then(literal("mouse").executes(c -> {
                    Actions.openLater(new OverlayInteractScreen());
                    return 1;
                }))
                .then(literal("photo").executes(c -> {
                    Mini.figures.photoPose();
                    return 1;
                }))
                .then(literal("rest").executes(c -> {
                    Actions.toggleRest();
                    return ok(c, Mini.cfg().restMode ? Mini.t("cmd.rest_on", "Ruhemodus an.") : Mini.t("cmd.rest_off", "Ruhemodus aus."));
                }))
                .then(literal("stop").executes(c -> {
                    Mini.inventory.emergencyStop(true);
                    return 1;
                }))
                .then(literal("reload").executes(c -> {
                    MiniCompanionsClient.reloadAll();
                    return ok(c, Mini.t("cmd.reloaded", "Config, Skins und Sprüche neu geladen."));
                }))
                .then(literal("debug").executes(c -> {
                    Mini.cfg().debugOverlay = !Mini.cfg().debugOverlay;
                    Mini.configs.markDirty(de.minicompanions.config.ConfigManager.Part.CONFIG);
                    return 1;
                })));
    }

    private static int ok(CommandContext<FabricClientCommandSource> c, Text t) {
        c.getSource().sendFeedback(Text.literal("[Mini] ").append(t));
        return 1;
    }

    private static int error(CommandContext<FabricClientCommandSource> c, Text t) {
        c.getSource().sendError(Text.literal("[Mini] ").append(t));
        return 0;
    }

    /** /mini add <skin>: Spielername, Datei (*.png aus dem skins-Ordner), Link oder "default". */
    private static int add(CommandContext<FabricClientCommandSource> c) {
        String in = StringArgumentType.getString(c, "skin").strip();
        FigureData d = new FigureData();
        String lower = in.toLowerCase(Locale.ROOT);
        if (lower.equals("default") || lower.equals("standard")) {
            d.skinType = FigureEnums.SkinType.DEFAULT.name();
            d.name = Mini.figures.uniqueName("Mini");
        } else if (lower.startsWith("https://") || lower.startsWith("http://")) {
            d.skinType = FigureEnums.SkinType.URL.name();
            d.skinValue = in;
            d.name = Mini.figures.uniqueName("Mini");
        } else if (lower.endsWith(".png") || lower.startsWith("file:")) {
            String file = lower.startsWith("file:") ? in.substring(5).strip() : in;
            if (Mini.skins.resolveSkinFile(file) == null) {
                return error(c, Mini.t("cmd.bad_file", "Ungültiger Dateiname (nur Dateien im Ordner config/minicompanions/skins)."));
            }
            d.skinType = FigureEnums.SkinType.FILE.name();
            d.skinValue = file;
            d.name = Mini.figures.uniqueName(file.replaceAll("(?i)\\.png$", ""));
        } else if (CommandParser.isValidMcName(in)) {
            d.skinType = FigureEnums.SkinType.PLAYER.name();
            d.skinValue = in;
            d.name = Mini.figures.uniqueName(in);
        } else {
            return error(c, Mini.t("cmd.bad_skin", "Unbekannte Skin-Quelle. Beispiele: Notch, meinskin.png, https://…/skin.png"));
        }
        Figure f = Mini.figures.add(d);
        return f == null ? 0 : ok(c, Mini.t("cmd.added", "%s hinzugefügt.", d.name));
    }

    private static int list(CommandContext<FabricClientCommandSource> c) {
        int n = 0;
        for (Figure f : Mini.figures.all()) {
            String line = (f.viewer ? "[Zuschauer] " + f.viewerDisplay + " (" + f.viewerMcName + ")" : f.data.name)
                    + " - " + Math.round(f.sizePercent(Mini.cfg())) + "%, " + f.activity.name().toLowerCase(Locale.ROOT)
                    + (f.inRoom ? ", Zimmer" : "");
            c.getSource().sendFeedback(Text.literal(" • " + line));
            n++;
        }
        if (n == 0) {
            return ok(c, Mini.t("cmd.no_figures", "Keine Figuren. Füge eine mit /mini add <Spielername> hinzu."));
        }
        return n;
    }

    private static int give(CommandContext<FabricClientCommandSource> c) {
        Figure f = Mini.figures.findOwn(StringArgumentType.getString(c, "name"));
        if (f == null) {
            return error(c, Mini.t("cmd.not_found", "Keine Figur mit diesem Namen."));
        }
        String task = StringArgumentType.getString(c, "task").toLowerCase(Locale.ROOT);
        for (InventoryTasks.TaskType t : InventoryTasks.TaskType.values()) {
            if (t.key().equals(task) && t != InventoryTasks.TaskType.BRING_ITEM) {
                Mini.inventory.start(t, f, null);
                return 1;
            }
        }
        return error(c, Mini.t("cmd.bad_task", "Unbekannte Aufgabe."));
    }

    private static int size(CommandContext<FabricClientCommandSource> c, Integer percent) {
        String target = StringArgumentType.getString(c, "target");
        int n = Mini.figures.setSize(target, percent);
        if (n == 0) {
            return error(c, Mini.t("cmd.not_found", "Keine Figur mit diesem Namen."));
        }
        return ok(c, percent == null ? Mini.t("cmd.size_reset", "Größe zurückgesetzt (%s Figuren).", n)
                : Mini.t("cmd.size_set", "Größe auf %s%% gesetzt (%s Figuren).", percent, n));
    }
}
