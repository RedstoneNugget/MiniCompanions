package de.minicompanions.gui;

import de.minicompanions.Actions;
import de.minicompanions.Mini;
import de.minicompanions.config.ConfigManager;
import de.minicompanions.figure.Figure;
import de.minicompanions.streamer.ChatPlatform;
import de.minicompanions.streamer.ChatRoles;
import de.minicompanions.streamer.SpawnQueue;
import de.minicompanions.streamer.StreamerConfig;
import de.minicompanions.streamer.StreamerModeManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.Arrays;
import java.util.List;

/**
 * Streamer-Fenster: Hauptschalter, Kanal und Verbindung, Befehl und Limits, Zuschauer-Nachrichten,
 * Moderation (Panik, Pause, Sperrlisten) und Liste der aktiven Zuschauer-Figuren mit Sofort-Löschen.
 */
public final class StreamerScreen extends OptionsScreen {
    private int listScroll;

    public StreamerScreen(Screen parent) {
        super(Mini.t("streamer.title", "Streamer"), parent);
        StreamerModeManager sm = Mini.streamer;
        StreamerConfig c = sm.cfg();

        addTab(new Tab(Mini.t("streamer.tab.connection", "Verbindung")) {
            @Override
            public boolean customInit(OptionsScreen s, int x, int y, int w, int h) {
                int rw = Math.min(360, w);
                int lx = s.width / 2 - rw / 2;
                s.addButton(lx, y, rw, 20, sm.isEnabled() ? Mini.t("streamer.mode_on", "Streamer-Modus: AN") : Mini.t("streamer.mode_off", "Streamer-Modus: AUS"), () -> {
                    Actions.toggleStreamerMode();
                    s.rebuild();
                });
                int ry = y + 28;
                for (ChatPlatform p : ChatPlatform.values()) {
                    int fieldX = lx + 52;
                    int fieldW = 132;
                    s.addField(fieldX, ry, fieldW, 18, c.channelOf(p), p == ChatPlatform.TWITCH ? 25 : 120, v -> {
                        c.setChannel(p, v);
                        Mini.configs.markDirty(ConfigManager.Part.STREAMER);
                    });
                    s.addButton(fieldX + fieldW + 4, ry - 1, 34, 20, onOff(c.isOn(p)), () -> {
                        c.setOn(p, !c.isOn(p));
                        Mini.configs.markDirty(ConfigManager.Part.STREAMER);
                        s.rebuild();
                    });
                    ry += 24;
                }
                boolean active = sm.isEnabled() && sm.hasConnections();
                s.addButton(lx, ry + 4, rw, 20, active ? Mini.t("streamer.disconnect_all", "Alle trennen") : Mini.t("streamer.connect_all", "Verbinden"), () -> {
                    if (active) {
                        sm.disconnect();
                    } else {
                        if (!sm.isEnabled()) {
                            Actions.toggleStreamerMode();
                        }
                        sm.connect();
                    }
                    s.rebuild();
                });
                return true;
            }

            @Override
            public void customRender(OptionsScreen s, DrawContext ctx, int mx, int my) {
                int rw = Math.min(360, s.width - 20);
                int lx = s.width / 2 - rw / 2;
                int ry = 42 + 28;
                for (ChatPlatform p : ChatPlatform.values()) {
                    ctx.fill(lx, ry + 4, lx + 4, ry + 14, p.color());
                    ctx.drawText(s.tr(), p.displayName(), lx + 8, ry + 5, Ui.TEXT, true);
                    int sx = lx + 52 + 132 + 4 + 34 + 6;
                    ctx.drawText(s.tr(), Ui.fit(s.tr(), sm.platformStatusText(p).getString(), lx + rw - sx), sx, ry + 5, platformColor(sm, p), true);
                    ry += 24;
                }
                int by = ry + 30;
                ctx.drawCenteredTextWithShadow(s.tr(), Mini.t("streamer.platform_hint", "Pro Plattform den eigenen Namen eintragen - alle eingetragenen laufen gleichzeitig."),
                        s.width / 2, by, Ui.TEXT_DIM);
                String notice = sm.viewers().lastNotice();
                if (!notice.isEmpty()) {
                    ctx.drawCenteredTextWithShadow(s.tr(), notice, s.width / 2, by + 12, Ui.ACCENT);
                }
                ctx.drawCenteredTextWithShadow(s.tr(), Mini.t("hud.viewer_count", "Zuschauer-Figuren: %s/%s", sm.viewers().count(), c.viewerLimit),
                        s.width / 2, by + 24, Ui.TEXT);
            }
        });

        tab(Mini.t("streamer.tab.options", "Optionen"))
                .add(new Toggle(Mini.t("streamer.auto_connect_opt", "Automatisch verbinden"), () -> c.autoConnect, v -> c.autoConnect = v))
                .add(new Toggle(Mini.t("streamer.auto_enable_opt", "Streamer-Modus beim Start an"), () -> c.autoEnableOnStart, v -> c.autoEnableOnStart = v))
                .add(new Cycle<>(Mini.t("streamer.name_colors", "Namen in Plattform-Farbe"), List.of("OFF", "MULTI", "ALWAYS"), () -> c.platformNameColors,
                        v -> c.platformNameColors = v, v -> switch (v) {
                            case "OFF" -> Mini.t("streamer.name_colors.off", "aus");
                            case "ALWAYS" -> Mini.t("streamer.name_colors.always", "immer");
                            default -> Mini.t("streamer.name_colors.multi", "bei mehreren Plattformen");
                        }))
                .add(new Toggle(Mini.t("streamer.hud_icon_opt", "HUD-Symbol"), () -> c.showHudIcon, v -> c.showHudIcon = v))
                .add(new Toggle(Mini.t("streamer.counter_opt", "Zähler im HUD"), () -> c.showCounter, v -> c.showCounter = v))
                .add(new Header(Mini.t("streamer.tiktok_header", "TikTok (optional)")))
                .add(new Field(Mini.t("streamer.tiktok_key", "Euler-Stream-Schlüssel"), () -> c.tiktokApiKey, v -> c.tiktokApiKey = v, 200));

        tab(Mini.t("streamer.tab.command", "Befehl & Limits"))
                .add(new Slider(Mini.t("streamer.limit", "Figuren-Limit"), 0, 100, 1, () -> c.viewerLimit, v -> c.viewerLimit = v, null))
                .add(new Cycle<>(Mini.t("streamer.policy", "Wenn voll"), List.of("IGNORE", "REPLACE_OLDEST"), () -> c.limitPolicy, v -> c.limitPolicy = v,
                        v -> "IGNORE".equals(v) ? Mini.t("streamer.policy.ignore", "ignorieren") : Mini.t("streamer.policy.replace", "älteste ersetzen")))
                .add(new Field(Mini.t("streamer.command", "Befehl"), () -> c.commandName, v -> c.commandName = v, 20))
                .add(new Field(Mini.t("streamer.prefixes", "Präfixe"), () -> c.commandPrefixes, v -> c.commandPrefixes = v, 4))
                .add(new Slider(Mini.t("streamer.cooldown", "Cooldown"), 0, 600, 5, () -> c.cooldownSeconds, v -> c.cooldownSeconds = v, v -> v + " s"))
                .add(new Slider(Mini.t("streamer.lifetime", "Lebensdauer"), 0, 120, 1, () -> c.lifetimeMinutes, v -> c.lifetimeMinutes = v,
                        v -> v == 0 ? Mini.t("opt.unlimited", "unbegrenzt").getString() : v + " min"))
                .add(new Cycle<>(Mini.t("streamer.roles", "Wer darf?"), Arrays.asList(ChatRoles.Filter.values()), c::roleFilter, v -> c.roleFilter = v.name(),
                        v -> Mini.t("roles." + v.name().toLowerCase(), v.name())))
                .add(new Toggle(Mini.t("streamer.approval", "Freigabe für Spawns"), () -> c.approvalMode, v -> c.approvalMode = v))
                .add(new Toggle(Mini.t("streamer.approval_msg", "Freigabe für Nachrichten"), () -> c.approvalForMessages, v -> c.approvalForMessages = v))
                .add(new Toggle(Mini.t("streamer.default_skin", "Unbekannt = Standard-Skin"), () -> c.unknownNameDefaultSkin, v -> c.unknownNameDefaultSkin = v))
                .add(new Toggle(Mini.t("streamer.greeting", "Begrüßung beim Spawn"), () -> c.spawnGreeting, v -> c.spawnGreeting = v))
                .add(new Toggle(Mini.t("streamer.new_bubble", "\"Neu: Name\"-Blase"), () -> c.newViewerBubble, v -> c.newViewerBubble = v))
                .add(new Slider(Mini.t("streamer.rate", "Spawns pro Sekunde"), 1, 20, 1, () -> c.maxSpawnsPerSecond, v -> c.maxSpawnsPerSecond = v, null))
                .add(new Slider(Mini.t("streamer.queue", "Warteschlange max."), 1, 200, 1, () -> c.spawnQueueMax, v -> c.spawnQueueMax = v, null));

        tab(Mini.t("streamer.tab.messages", "Nachrichten"))
                .add(new Toggle(Mini.t("streamer.show_messages", "Nachrichten über Figuren"), () -> c.showMessages, v -> c.showMessages = v))
                .add(new Slider(Mini.t("streamer.duration", "Anzeigedauer"), 1, 30, 1, () -> c.messageSeconds, v -> c.messageSeconds = v, v -> v + " s"))
                .add(new Slider(Mini.t("streamer.max_chars", "Max. Zeichen"), 10, 300, 5, () -> c.maxChars, v -> c.maxChars = v, null))
                .add(new Slider(Mini.t("streamer.min_interval", "Mindestabstand"), 0, 30, 1, () -> c.minBubbleIntervalSeconds, v -> c.minBubbleIntervalSeconds = v, v -> v + " s"))
                .add(new Slider(Mini.t("streamer.opacity", "Deckkraft"), 10, 100, 5, () -> c.bubbleOpacity, v -> c.bubbleOpacity = v, v -> v + "%"))
                .add(new Slider(Mini.t("streamer.scale", "Blasengröße"), 50, 300, 10, () -> c.bubbleScale, v -> c.bubbleScale = v, v -> v + "%"))
                .add(new Cycle<>(Mini.t("streamer.bubble_color", "Blasenfarbe"), List.of(0xFFFFFFFF, 0xFF202430, 0xFFFFF3B0, 0xFFD8F0FF, 0xFFFFD8E8),
                        () -> c.bubbleColor, v -> {
                            c.bubbleColor = v;
                            c.textColor = v == 0xFF202430 ? 0xFFF0F0F0 : 0xFF202020;
                        }, v -> Text.literal("■").withColor(v & 0xFFFFFF)))
                .add(new Toggle(Mini.t("streamer.role_colors", "Rollen-Farben"), () -> c.roleColors, v -> c.roleColors = v))
                .add(new Slider(Mini.t("streamer.queue_size", "Blasen-Warteschlange"), 1, 5, 1, () -> c.bubbleQueueSize, v -> c.bubbleQueueSize = v,
                        v -> v == 1 ? Mini.t("streamer.queue_replace", "aus (neue ersetzt alte)").getString() : String.valueOf(v)))
                .add(new Toggle(Mini.t("streamer.show_cmd", "!skin als Blase zeigen"), () -> c.showSkinCommandBubble, v -> c.showSkinCommandBubble = v))
                .add(new Cycle<>(Mini.t("streamer.censor", "Schimpfwörter"), List.of("CENSOR", "DROP"), () -> c.censorMode, v -> c.censorMode = v,
                        v -> "CENSOR".equals(v) ? Mini.t("streamer.censor.stars", "als *** zensieren") : Mini.t("streamer.censor.drop", "Nachricht verwerfen")))
                .add(new Slider(Mini.t("streamer.msg_rate", "Nachrichten/Sekunde"), 1, 50, 1, () -> c.maxMessagesPerSecond, v -> c.maxMessagesPerSecond = v, null))
                .add(new Header(Mini.t("streamer.per_role", "Anzeige pro Rolle")))
                .add(new Toggle(Mini.t("streamer.role.regular", "Zuschauer"), () -> c.bubblesRegular, v -> c.bubblesRegular = v))
                .add(new Toggle(Mini.t("streamer.role.subs", "Abonnenten"), () -> c.bubblesSubs, v -> c.bubblesSubs = v))
                .add(new Toggle(Mini.t("streamer.role.vips", "VIPs"), () -> c.bubblesVips, v -> c.bubblesVips = v))
                .add(new Toggle(Mini.t("streamer.role.mods", "Moderatoren"), () -> c.bubblesMods, v -> c.bubblesMods = v));

        var mod = sm.moderation();
        var lists = mod.lists();
        tab(Mini.t("streamer.tab.moderation", "Moderation"))
                .add(new Action(Mini.t("streamer.panic", "PANIK - alles löschen"), Actions::panic, true))
                .add(new Toggle(Mini.t("streamer.pause", "Chat-Spawns pausieren"), mod::spawnsPaused, mod::setSpawnsPaused))
                .add(new Toggle(Mini.t("streamer.messages_off", "Nachrichten aus"), mod::messagesHidden, v -> {
                    mod.setMessagesHidden(v);
                    if (v) {
                        sm.viewers().hideAllMessages();
                    }
                }))
                .add(new Toggle(Mini.t("streamer.whitelist_on", "Nur Whitelist"), () -> c.useWhitelist, v -> c.useWhitelist = v))
                .add(new Toggle(Mini.t("streamer.default_words", "Standard-Wortliste"), () -> lists.useDefaultWords, v -> {
                    lists.useDefaultWords = v;
                    mod.listsChanged();
                }))
                .add(new Header(Mini.t("streamer.lists", "Listen bearbeiten")))
                .add(new Action(Mini.t("streamer.banned_viewers", "Gesperrte Zuschauer (%s)", lists.bannedViewers.size()),
                        () -> open(new StringListScreen(this, Mini.t("streamer.banned_viewers_title", "Gesperrte Zuschauer"), lists.bannedViewers, mod::listsChanged)), false))
                .add(new Action(Mini.t("streamer.banned_skins", "Gesperrte Skins (%s)", lists.bannedSkins.size()),
                        () -> open(new StringListScreen(this, Mini.t("streamer.banned_skins_title", "Gesperrte Minecraft-Namen"), lists.bannedSkins, mod::listsChanged)), false))
                .add(new Action(Mini.t("streamer.banned_words", "Gesperrte Wörter (%s)", lists.bannedWords.size()),
                        () -> open(new StringListScreen(this, Mini.t("streamer.banned_words_title", "Eigene gesperrte Wörter"), lists.bannedWords, mod::listsChanged)), false))
                .add(new Action(Mini.t("streamer.whitelist", "Whitelist (%s)", lists.whitelist.size()),
                        () -> open(new StringListScreen(this, Mini.t("streamer.whitelist_title", "Whitelist (Zuschauer)"), lists.whitelist, mod::listsChanged)), false));

        addTab(new Tab(Mini.t("streamer.tab.active", "Aktive Figuren")) {
            @Override
            public boolean customInit(OptionsScreen s, int x, int y, int w, int h) {
                initActive(s, x, y, w, h);
                return true;
            }

            @Override
            public void customRender(OptionsScreen s, DrawContext ctx, int mx, int my) {
                renderActive(s, ctx);
            }
        });

        onClose(() -> {
            c.validate();
            Mini.configs.markDirty(ConfigManager.Part.STREAMER);
            Mini.configs.markDirty(ConfigManager.Part.MODERATION);
        });
    }

    private static void open(Screen s) {
        MinecraftClient.getInstance().setScreen(s);
    }

    private static int platformColor(StreamerModeManager sm, ChatPlatform p) {
        return switch (sm.status(p)) {
            case CONNECTED -> Ui.GREEN;
            case ERROR -> Ui.RED;
            case CONNECTING, RECONNECTING -> Ui.ACCENT;
            default -> Ui.TEXT_DIM;
        };
    }

    @Override
    protected void onChangeHook() {
        Mini.configs.markDirty(ConfigManager.Part.STREAMER);
    }

    // ------------------------------------------------------------------ Aktive Zuschauer-Figuren

    private static final int ROW = 22;
    private final java.util.List<Object[]> rows = new java.util.ArrayList<>();

    private void initActive(OptionsScreen s, int x, int y, int w, int h) {
        rows.clear();
        var sm = Mini.streamer;
        List<Object> entries = new java.util.ArrayList<>();
        entries.addAll(sm.pipeline() == null ? List.of() : sm.pipeline().queue().approvals());
        List<SpawnQueue.PendingMessage> msgs = sm.pipeline() == null ? List.of() : sm.pipeline().queue().messageApprovals();
        entries.addAll(msgs);
        entries.addAll(sm.viewers().list());
        int visible = Math.max(1, (h - 4) / ROW);
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, entries.size() - visible)));
        int lw = Math.min(520, w);
        int lx = s.width / 2 - lw / 2;
        for (int i = 0; i < visible && i + listScroll < entries.size(); i++) {
            Object e = entries.get(i + listScroll);
            int ry = y + i * ROW;
            rows.add(new Object[]{e, lx, ry, lw});
            int bx = lx + lw;
            if (e instanceof Figure f) {
                bx -= 60;
                s.addButton(bx, ry + 1, 58, 18, Mini.t("menu.ban_short", "Sperren").copy().withColor(0xFF6060), () -> {
                    sm.ban(f.viewerLogin);
                    rebuild();
                });
                bx -= 62;
                boolean muted = sm.moderation().isMuted(f.viewerLogin);
                s.addButton(bx, ry + 1, 60, 18, muted ? Mini.t("menu.unmute_short", "Laut") : Mini.t("menu.mute_short", "Stumm"), () -> {
                    if (muted) {
                        sm.moderation().unmute(f.viewerLogin);
                    } else {
                        sm.moderation().mute(f.viewerLogin);
                        f.bubbles.clearViewer();
                    }
                    rebuild();
                });
                bx -= 62;
                s.addButton(bx, ry + 1, 60, 18, Mini.t("menu.delete", "Löschen"), () -> {
                    sm.kick(f.viewerLogin);
                    rebuild();
                });
            } else if (e instanceof SpawnQueue.Request r) {
                bx -= 60;
                s.addButton(bx, ry + 1, 58, 18, Mini.t("streamer.deny", "Ablehnen"), () -> {
                    sm.pipeline().deny(r.login());
                    rebuild();
                });
                bx -= 62;
                s.addButton(bx, ry + 1, 60, 18, Mini.t("streamer.approve", "Freigeben"), () -> {
                    sm.pipeline().approve(r.login());
                    rebuild();
                });
            } else if (e instanceof SpawnQueue.PendingMessage pm) {
                final int idx = msgs.indexOf(pm);
                bx -= 60;
                s.addButton(bx, ry + 1, 58, 18, Mini.t("streamer.deny", "Ablehnen"), () -> {
                    sm.pipeline().denyMessage(idx);
                    rebuild();
                });
                bx -= 62;
                s.addButton(bx, ry + 1, 60, 18, Mini.t("streamer.approve", "Freigeben"), () -> {
                    sm.pipeline().approveMessage(idx);
                    rebuild();
                });
            }
        }
    }

    private void renderActive(OptionsScreen s, DrawContext ctx) {
        var tr = s.tr();
        if (rows.isEmpty()) {
            ctx.drawCenteredTextWithShadow(tr, Mini.t("streamer.no_active", "Keine Zuschauer-Figuren aktiv."), s.width / 2, 60, Ui.TEXT_DIM);
            return;
        }
        long now = System.currentTimeMillis();
        int life = Mini.streamer.cfg().lifetimeMinutes;
        for (Object[] r : rows) {
            Object e = r[0];
            int x = (int) r[1];
            int y = (int) r[2];
            int w = (int) r[3];
            ctx.fill(x, y, x + w, y + ROW - 1, 0x60202430);
            String line1;
            String line2;
            if (e instanceof Figure f) {
                String rest = life <= 0 ? "∞" : Math.max(0, (life * 60_000L - (now - f.spawnedAt)) / 1000) + "s";
                line1 = f.viewerDisplay + "  (MC: " + f.viewerMcName + ")  " + rest;
                line2 = f.lastViewerMessage == null ? "" : f.lastViewerMessage;
            } else if (e instanceof SpawnQueue.Request rq) {
                line1 = Mini.t("streamer.pending_spawn", "Wartet auf Freigabe: %s", rq.displayName()).getString();
                line2 = "MC: " + rq.mcName();
            } else {
                SpawnQueue.PendingMessage pm = (SpawnQueue.PendingMessage) e;
                line1 = Mini.t("streamer.pending_msg", "Nachricht von %s", pm.displayName()).getString();
                line2 = pm.text();
            }
            int textW = w - 190;
            ctx.drawText(tr, Ui.fit(tr, line1, textW), x + 4, y + 2, Ui.TEXT, false);
            ctx.drawText(tr, Ui.fit(tr, line2, textW), x + 4, y + 12, Ui.TEXT_DIM, false);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (tab == tabs.size() - 1) {
            listScroll = Math.max(0, listScroll - (int) Math.signum(vertical));
            rebuild();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public void tick() {
        super.tick();
        // Aktive Liste regelmäßig auffrischen
        if (tab == tabs.size() - 1 && client != null && client.world != null && client.world.getTime() % 20 == 0) {
            rebuild();
        }
    }
}
