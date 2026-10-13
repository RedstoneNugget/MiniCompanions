package de.minicompanions.test;

import de.minicompanions.Mini;
import de.minicompanions.config.FigureData;
import de.minicompanions.figure.Figure;
import de.minicompanions.gui.ActionMenu;
import de.minicompanions.gui.FigureEditScreen;
import de.minicompanions.gui.ManageScreen;
import de.minicompanions.gui.SettingsScreen;
import de.minicompanions.gui.StreamerScreen;
import de.minicompanions.input.Keys;
import de.minicompanions.inventory.InventoryTasks;
import de.minicompanions.streamer.StreamerPipeline;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Automatischer In-Game-Test: startet den echten Client, legt Figuren an, prüft HUD, Inventar, Chat,
 * Zimmer, Größenänderung, Inventar-Aufgabe (ohne Itemverlust) und den Streamer-Modus mit simulierten
 * Nachrichten. Aufruf: gradlew runClientGameTest - Screenshots unter build/run/clientGameTest/screenshots.
 */
public class MiniCompanionsClientGameTest implements FabricClientGameTest {

    private static void check(boolean ok, String what) {
        if (!ok) {
            throw new AssertionError("Prüfung fehlgeschlagen: " + what);
        }
        System.out.println("[MiniCompanions-Test] OK: " + what);
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        context.getInput().resizeWindow(1600, 900);
        context.waitTicks(5);

        // Ausgangszustand: Streamer-Modus nach Neuinstallation AUS
        check(!context.computeOnClient(c -> Mini.streamer.isEnabled()), "Streamer-Modus standardmäßig aus");

        context.setScreen(() -> new ManageScreen(null));
        context.waitTicks(5);
        context.takeScreenshot("00_manage_title");
        context.setScreen(() -> null);

        try (TestSingleplayerContext sp = context.worldBuilder().setUseConsistentSettings(true).create()) {
            sp.getClientWorld().waitForChunksRender();
            sp.getServer().runCommand("time set noon");
            sp.getServer().runCommand("weather clear");
            sp.getServer().runCommand("gamerule doDaylightCycle false");

            // Figuren anlegen (Standard-Skin, damit der Test ohne Netzwerk läuft)
            context.runOnClient(c -> {
                Mini.figures.clearOwn();
                for (int i = 0; i < 4; i++) {
                    FigureData d = new FigureData();
                    d.name = "Test" + i;
                    d.accessories.add(i == 0 ? "TOP_HAT" : i == 1 ? "CAPE" : "GLASSES");
                    d.pet = i == 2 ? "CAT" : "NONE";
                    Mini.figures.add(d);
                }
            });
            context.waitTicks(80);
            int count = context.computeOnClient(c -> Mini.figures.ownCount());
            check(count == 4, "4 Figuren angelegt");
            // Eine Figur kann gerade mitten in einem Sprung sein - wer wirklich festhängt, landet auch nach 10 s nicht
            java.util.function.BooleanSupplier allGrounded = () -> context.computeOnClient(c -> Mini.figures.all().stream()
                    .allMatch(f -> f.body.onGround || f.body.climbing || f.brain.isNavigating()));
            waitFor(context, 200, allGrounded);
            String air = context.computeOnClient(c -> Mini.figures.all().stream()
                    .filter(f -> !(f.body.onGround || f.body.climbing || f.brain.isNavigating()))
                    .map(f -> f.name() + "@" + Math.round(f.body.x) + "," + Math.round(f.body.y) + " " + f.activity)
                    .reduce("", (a, b) -> a + " " + b));
            check(air.isEmpty(), "Figuren sind gelandet (keine schwebt fest)" + air);
            // 10 Sekunden beobachten: keine grundlosen Sprünge (nur Sprünge als Teil eines Weges)
            int randomJumps = 0;
            for (int i = 0; i < 200; i++) {
                context.waitTick();
                randomJumps += context.computeOnClient(c -> (int) Mini.figures.all().stream().filter(f -> f.activity == de.minicompanions.ai.Activity.JUMP
                        && !f.brain.isNavigating() && !f.brain.isHopping() && !f.carried && f.body.vy < -60).count());
            }
            check(randomJumps == 0, "keine grundlosen Sprünge (" + randomJumps + ")");
            context.takeScreenshot("01_hud_figures");

            // Größe: einzeln und global
            context.runOnClient(c -> Mini.figures.setSize("Test0", 300));
            context.waitTicks(20);
            float s0 = context.computeOnClient(c -> Mini.figures.findOwn("Test0").sizePercent(Mini.cfg()));
            check(Math.abs(s0 - 300) < 0.5f, "Größe pro Figur = 300 %");
            float h0 = context.computeOnClient(c -> Mini.figures.findOwn("Test0").body.h);
            float h1 = context.computeOnClient(c -> Mini.figures.findOwn("Test1").body.h);
            check(h0 > h1 * 2.2f, "große Figur hat größere Kollisionsbox (" + h0 + " vs " + h1 + ")");
            context.runOnClient(c -> Mini.cfg().globalSize = 150);
            context.waitTicks(10);
            float s1 = context.computeOnClient(c -> Mini.figures.findOwn("Test1").sizePercent(Mini.cfg()));
            check(Math.abs(s1 - 150) < 0.5f, "globale Größe wirkt sofort");
            context.runOnClient(c -> Mini.cfg().globalSize = 100);
            context.takeScreenshot("02_sizes");

            // Laufrichtung prüfen: große Figur läuft nach rechts, dann nach links (Gesicht muss vorne sein)
            context.runOnClient(c -> {
                Figure f = Mini.figures.findOwn("Test0");
                f.data.sizePercent = 500;
                f.body.x = 60;
                f.brain.command(Mini.figures.screen, 400, f.body.y);
            });
            waitFor(context, 60, () -> context.computeOnClient(c -> {
                Figure f = Mini.figures.findOwn("Test0");
                return f.activity == de.minicompanions.ai.Activity.WALK || f.activity == de.minicompanions.ai.Activity.RUN;
            }));
            context.waitTicks(6);
            int dirR = context.computeOnClient(c -> Mini.figures.findOwn("Test0").facing);
            check(dirR == 1, "läuft nach rechts (facing=" + dirR + ")");
            context.takeScreenshot("02b_walk_right");
            context.waitTicks(4);
            context.takeScreenshot("02b2_walk_right");
            context.runOnClient(c -> {
                Figure f = Mini.figures.findOwn("Test0");
                f.brain.command(Mini.figures.screen, 20, f.body.y);
            });
            context.waitTicks(12);
            int dirL = context.computeOnClient(c -> Mini.figures.findOwn("Test0").facing);
            check(dirL == -1, "läuft nach links (facing=" + dirL + ")");
            context.takeScreenshot("02c_walk_left");
            context.waitTicks(4);
            context.takeScreenshot("02c2_walk_left");
            context.runOnClient(c -> Mini.figures.findOwn("Test0").data.sizePercent = 300);

            // Ziel ganz am linken Rand: die Figur darf dort nicht auf der Stelle weiterlaufen
            context.runOnClient(c -> {
                Figure f = Mini.figures.findOwn("Test1");
                f.brain.command(Mini.figures.screen, 0, f.body.y);
            });
            waitFor(context, 600, () -> context.computeOnClient(c -> !Mini.figures.findOwn("Test1").brain.isNavigating()));
            String edge = context.computeOnClient(c -> {
                Figure f = Mini.figures.findOwn("Test1");
                return "x=" + Math.round(f.body.x) + " nav=" + f.brain.isNavigating() + " act=" + f.activity;
            });
            check(context.computeOnClient(c -> !Mini.figures.findOwn("Test1").brain.isNavigating()
                    && Mini.figures.findOwn("Test1").body.x < 30), "Ziel am Rand erreicht, kein Laufen auf der Stelle (" + edge + ")");

            // Stabilität wie beim Nutzer: große und kleine Figuren, Zimmer oben links, Chat-Zeilen im HUD.
            // 30 s beobachten: niemand läuft länger auf der Stelle oder hüpft dauernd.
            context.runOnClient(c -> {
                int[] sizes = {500, 100, 300, 150};
                for (int i = 0; i < 4; i++) {
                    Mini.figures.findOwn("Test" + i).data.sizePercent = sizes[i];
                }
                Mini.room.setOpen(true);
                Mini.cfg().roomX = 0;
                Mini.cfg().roomY = 10;
                Mini.cfg().roomW = 200;
                Mini.cfg().roomH = 120;
                for (int i = 0; i < 6; i++) {
                    c.inGameHud.getChatHud().addMessage(net.minecraft.text.Text.literal("[Test] Chat-Zeile Nummer " + i + " fuer die Figuren"));
                }
            });
            int[] inPlace = new int[4];
            int[] jumps = new int[4];
            int[] atWall = new int[4];
            int[] hanging = new int[4];
            float[] lastX = new float[4];
            String[] lastAct = new String[4];
            for (int t = 0; t < 600; t++) {
                context.waitTick();
                for (int i = 0; i < 4; i++) {
                    final int idx = i;
                    float[] s = context.computeOnClient(c -> {
                        Figure f = Mini.figures.findOwn("Test" + idx);
                        boolean walking = f.activity == de.minicompanions.ai.Activity.WALK || f.activity == de.minicompanions.ai.Activity.RUN;
                        float wdt = Mini.figures.screen.platforms.width;
                        boolean wall = !f.inRoom && f.body.onGround && (f.body.x < 14 || f.body.x > wdt - 14);
                        boolean hang = f.activity == de.minicompanions.ai.Activity.HANG && !f.body.climbing;
                        return new float[] {f.body.x, f.body.onGround && walking && !f.inRoom ? 1 : 0, f.activity == de.minicompanions.ai.Activity.JUMP ? 1 : 0, wall ? 1 : 0, hang ? 1 : 0};
                    });
                    if (t > 0 && s[1] == 1 && Math.abs(s[0] - lastX[i]) < 0.05f) {
                        inPlace[i]++;
                    }
                    if (s[3] == 1) {
                        atWall[i]++;
                    }
                    if (s[4] == 1) {
                        hanging[i]++;
                    }
                    String act = s[2] == 1 ? "J" : "-";
                    if ("J".equals(act) && !"J".equals(lastAct[i])) {
                        jumps[i]++;
                    }
                    lastAct[i] = act;
                    lastX[i] = s[0];
                }
                if (t == 300) {
                    context.takeScreenshot("02d_sizes_chat_room");
                }
            }
            String stats = java.util.Arrays.toString(inPlace) + " Sprünge " + java.util.Arrays.toString(jumps);
            check(java.util.Arrays.stream(inPlace).allMatch(n -> n < 20), "niemand läuft auf der Stelle (Ticks pro Figur: " + stats + ")");
            check(java.util.Arrays.stream(jumps).allMatch(n -> n <= 12), "kein Dauer-Hüpfen in 30 s (" + stats + ")");
            check(java.util.Arrays.stream(atWall).allMatch(n -> n < 100), "niemand steht lange am Rand (Ticks: " + java.util.Arrays.toString(atWall) + ")");
            check(java.util.Arrays.stream(hanging).allMatch(n -> n == 0), "niemand hängt unter Kanten (durch den Boden) " + java.util.Arrays.toString(hanging));

            // Rüstung: global Diamant, Test1 einzeln Gold, Test2 einzeln keine
            context.runOnClient(c -> {
                Mini.cfg().armorMode = "DIAMOND";
                Mini.figures.findOwn("Test1").data.armor = "GOLD";
                Mini.figures.findOwn("Test2").data.armor = "NONE";
            });
            context.waitTicks(10);
            context.takeScreenshot("02f_armor_choice");
            context.runOnClient(c -> {
                Mini.cfg().armorMode = "PLAYER";
                Mini.figures.findOwn("Test1").data.armor = "";
                Mini.figures.findOwn("Test2").data.armor = "";
            });
            // Große Figur direkt unter frischen Chat-Zeilen: die Figur muss vor dem Chat gezeichnet werden
            context.runOnClient(c -> {
                Figure f = Mini.figures.findOwn("Test0");
                f.data.behavior = false;
                Mini.figures.moveToScreen(f, 60, c.getWindow().getScaledHeight() - 2);
                for (int i = 0; i < 4; i++) {
                    c.inGameHud.getChatHud().addMessage(net.minecraft.text.Text.literal("[Mini] Streamer mode ON - Zeile " + i));
                }
            });
            context.waitTicks(30);
            context.takeScreenshot("02e_big_figure_under_chat");
            context.runOnClient(c -> {
                Mini.figures.findOwn("Test0").data.behavior = true;
                for (int i = 0; i < 4; i++) {
                    Mini.figures.findOwn("Test" + i).data.sizePercent = i == 0 ? 300 : null;
                }
                Mini.room.setOpen(false);
                Mini.room.resetPosition();
            });

            // Ruhemodus: sitzen statt schlafen, danach geht es normal weiter
            context.runOnClient(c -> Mini.cfg().restMode = true);
            context.waitTicks(20);
            check(context.computeOnClient(c -> Mini.figures.all().stream().filter(f -> !f.carried && f.body.onGround)
                    .allMatch(f -> f.activity == de.minicompanions.ai.Activity.SIT)), "Ruhemodus: Figuren sitzen (kein Schlafen)");
            context.runOnClient(c -> Mini.cfg().restMode = false);
            context.waitTicks(40);
            check(context.computeOnClient(c -> Mini.figures.all().stream().noneMatch(f -> f.activity == de.minicompanions.ai.Activity.SIT
                    && f.activityDuration > 1e8f)), "nach dem Ruhemodus stehen alle wieder auf");

            // Inventar: Items geben und Inventar öffnen
            sp.getServer().runCommand("give @a minecraft:dirt 20");
            sp.getServer().runCommand("give @a minecraft:oak_log 3");
            sp.getServer().runCommand("give @a minecraft:dirt 30");
            sp.getServer().runCommand("give @a minecraft:diamond 2");
            sp.getServer().runCommand("give @a minecraft:iron_helmet 1");
            sp.getServer().runCommand("give @a minecraft:bread 5");
            context.waitTicks(10);
            context.setScreen(() -> new InventoryScreen(net.minecraft.client.MinecraftClient.getInstance().player));
            context.waitTicks(200);
            context.takeScreenshot("03_inventory_platforms");
            int slotLedges = context.computeOnClient(c -> (int) Mini.figures.screen.platforms.ledges().stream()
                    .filter(l -> l.id.startsWith("slots:")).count());
            check(slotLedges >= 4, "Slot-Reihen als Plattformen erkannt (" + slotLedges + ")");
            waitFor(context, 400, () -> context.computeOnClient(c -> Mini.figures.all().stream().anyMatch(f -> f.body.onGround
                    && f.body.ledgeId != null && (f.body.ledgeId.startsWith("slots:") || f.body.ledgeId.startsWith("inv:")))));
            long onGui = context.computeOnClient(c -> Mini.figures.all().stream().filter(f -> f.body.ledgeId != null
                    && (f.body.ledgeId.startsWith("slots:") || f.body.ledgeId.startsWith("inv:"))).count());
            check(onGui >= 1, "Figuren klettern aufs Inventar (" + onGui + " auf Fenster/Slots)");
            context.takeScreenshot("03b_inventory_climbed");

            // Inventar-Aufgabe: Rüstung anziehen + Sortieren, ohne Itemverlust
            Map<String, Integer> before = context.computeOnClient(MiniCompanionsClientGameTest::countItems);
            context.runOnClient(c -> Mini.inventory.start(InventoryTasks.TaskType.EQUIP_ARMOR, Mini.figures.findOwn("Test1"), null));
            waitWhileTaskRuns(context);
            boolean helmet = context.computeOnClient(c -> !c.player.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD).isEmpty());
            check(helmet, "Figur hat die Rüstung angezogen");
            context.runOnClient(c -> Mini.inventory.start(InventoryTasks.TaskType.SORT, Mini.figures.findOwn("Test1"), null));
            context.waitTicks(10);
            context.takeScreenshot("04_inventory_task");
            waitWhileTaskRuns(context);
            context.waitTicks(10);
            Map<String, Integer> after = context.computeOnClient(MiniCompanionsClientGameTest::countItems);
            check(before.equals(after), "keine Items verloren beim Sortieren " + before + " -> " + after);
            boolean cursorEmpty = context.computeOnClient(c -> c.player.currentScreenHandler.getCursorStack().isEmpty());
            check(cursorEmpty, "Cursor nach der Aufgabe leer");
            context.setScreen(() -> null);
            context.waitTicks(10);

            // Chat
            context.setScreen(() -> new ChatScreen("", false));
            for (int i = 0; i < 6; i++) {
                final int n = i;
                context.runOnClient(c -> c.inGameHud.getChatHud().addMessage(net.minecraft.text.Text.literal("Testnachricht " + n + " - gg")));
            }
            context.waitTicks(160);
            int chatLedges = context.computeOnClient(c -> (int) Mini.figures.screen.platforms.ledges().stream()
                    .filter(l -> l.id.startsWith("chat:")).count());
            check(chatLedges >= 6, "Chat-Zeilen als Plattformen erkannt (" + chatLedges + ")");
            context.takeScreenshot("05_chat");
            context.setScreen(() -> null);

            // Zimmer: öffnen, verschieben, Größe ändern, Figur hineinsetzen
            context.runOnClient(c -> {
                Mini.room.setOpen(true);
                Mini.cfg().roomX = 30;
                Mini.cfg().roomY = 30;
                Mini.cfg().roomW = 300;
                Mini.cfg().roomH = 180;
                Figure f = Mini.figures.findOwn("Test2");
                Mini.figures.moveToRoom(f, 60, 20);
            });
            context.waitTicks(80);
            boolean inRoom = context.computeOnClient(c -> {
                Figure f = Mini.figures.findOwn("Test2");
                return f.inRoom && f.body.onGround && f.body.y <= Mini.room.walkArea().h() + 0.1f;
            });
            check(inRoom, "Figur steht im Zimmer auf dem Boden");
            context.takeScreenshot("06_room");
            context.runOnClient(c -> {
                Mini.cfg().roomX = 5000;
                Mini.cfg().roomW = 50;
            });
            context.waitTicks(5);
            boolean clamped = context.computeOnClient(c -> {
                int sw = c.getWindow().getScaledWidth();
                return Mini.cfg().roomX + Mini.cfg().roomW <= sw && Mini.cfg().roomW >= 120;
            });
            check(clamped, "Zimmer bleibt im sichtbaren Bereich und hat Mindestgröße");
            context.runOnClient(c -> Mini.room.resetPosition());
            context.waitTicks(20);
            context.takeScreenshot("07_room_reset");

            // Echtes Hineinziehen mit der Maus: J (Zimmer + Maus-Modus), Figur greifen, ins Zimmer ziehen, loslassen
            context.runOnClient(c -> {
                Mini.room.setOpen(true); // vom letzten Mal noch offen: J darf es dann nicht schließen
                for (String n : new String[] {"Test1", "Test3"}) {
                    Figure f = Mini.figures.findOwn(n);
                    if (f.inRoom) {
                        Mini.figures.moveToScreen(f, 600, 300);
                    }
                    f.data.behavior = false;
                }
            });
            context.waitTicks(60);
            context.getInput().pressKey(Keys.room);
            context.waitTicks(5);
            check(context.computeOnClient(c -> Mini.room.isOpen() && c.currentScreen instanceof de.minicompanions.input.OverlayInteractScreen),
                    "J gibt die Maus frei (Zimmer war schon offen und bleibt offen)");
            // knapp daneben klicken beendet den Maus-Modus nicht
            double scale = context.computeOnClient(c -> c.getWindow().getScaleFactor());
            context.getInput().setCursorPos(5 * scale, 40 * scale);
            context.getInput().pressMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT);
            context.waitTicks(3);
            check(context.computeOnClient(c -> c.currentScreen instanceof de.minicompanions.input.OverlayInteractScreen),
                    "Klick daneben beendet den Maus-Modus nicht");
            String r1 = dragIntoRoom(context, "Test1");
            context.takeScreenshot("07c_dragged_into_room");
            check(r1.endsWith("im Zimmer: true"), "Figur mit der Maus ins Zimmer gezogen (" + r1 + ")");
            context.getInput().pressKey(Keys.room);
            context.waitTicks(5);
            check(context.computeOnClient(c -> !Mini.room.isOpen() && c.currentScreen == null), "nochmal J: Zimmer zu, zurück im Spiel");

            // Figur halten, währenddessen schließt sich das Fenster (ESC/J/K), dann loslassen: darf nicht in der Luft hängen bleiben
            context.runOnClient(c -> c.setScreen(new de.minicompanions.input.OverlayInteractScreen()));
            context.waitTicks(3);
            float[] grabAt = context.computeOnClient(c -> {
                Figure f = byName("Test3");
                return new float[] {f.sx(), f.body.y - f.body.h * 0.5f};
            });
            double gScale = context.computeOnClient(c -> c.getWindow().getScaleFactor());
            context.getInput().setCursorPos(grabAt[0] * gScale, grabAt[1] * gScale);
            context.waitTicks(2);
            context.getInput().holdMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT);
            context.waitTicks(2);
            context.getInput().setCursorPos((grabAt[0] + 30) * gScale, (grabAt[1] - 40) * gScale);
            context.waitTicks(10);
            boolean heldBefore = context.computeOnClient(c -> de.minicompanions.input.InputRouter.carried() != null);
            context.runOnClient(c -> c.setScreen(null));
            context.waitTicks(3);
            context.getInput().releaseMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT);
            context.waitTicks(40);
            check(heldBefore && context.computeOnClient(c -> de.minicompanions.input.InputRouter.carried() == null && !byName("Test3").carried
                    && byName("Test3").body.onGround), "Fenster beim Tragen geschlossen: Figur fällt runter statt in der Luft zu hängen (gehalten: " + heldBefore + ")");

            // Aus dem Inventar: Figur steht auf einem leeren Slot und wird ins Zimmer gezogen
            context.setScreen(() -> new InventoryScreen(net.minecraft.client.MinecraftClient.getInstance().player));
            context.waitTicks(5);
            context.runOnClient(c -> {
                var hs = (net.minecraft.client.gui.screen.ingame.HandledScreen<?>) c.currentScreen;
                var acc = (de.minicompanions.mixin.HandledScreenAccessor) hs;
                int ix = acc.minicompanions$getX();
                int iy = acc.minicompanions$getY();
                Mini.room.setOpen(true);
                Mini.cfg().roomX = ix + acc.minicompanions$getBackgroundWidth() + 8;
                Mini.cfg().roomY = 10;
                Mini.cfg().roomW = 120;
                Mini.cfg().roomH = 100;
                var slot = hs.getScreenHandler().slots.get(20); // Hauptinventar, leer
                Mini.figures.moveToScreen(Mini.figures.findOwn("Test3"), ix + slot.x + 8, iy + slot.y - 1);
            });
            context.waitTicks(40);
            String slotInfo = context.computeOnClient(c -> {
                Figure f = Mini.figures.findOwn("Test3");
                var hs = (net.minecraft.client.gui.screen.ingame.HandledScreen<?>) c.currentScreen;
                var acc = (de.minicompanions.mixin.HandledScreenAccessor) hs;
                float mx = f.sx() - acc.minicompanions$getX();
                float my = f.body.y - f.body.h * 0.5f - acc.minicompanions$getY();
                for (var s : hs.getScreenHandler().slots) {
                    if (mx >= s.x - 1 && mx < s.x + 17 && my >= s.y - 1 && my < s.y + 17) {
                        return "Slot " + s.id + (s.hasStack() ? " voll" : " leer");
                    }
                }
                return "kein Slot";
            });
            String r2 = dragIntoRoom(context, "Test3");
            context.takeScreenshot("07d_dragged_from_inventory");
            check(r2.endsWith("im Zimmer: true"), "Figur aus dem Inventar ins Zimmer gezogen (" + slotInfo + ", " + r2 + ")");
            context.runOnClient(c -> {
                Mini.figures.findOwn("Test1").data.behavior = true;
                Mini.figures.findOwn("Test3").data.behavior = true;
                Mini.room.resetPosition();
                c.setScreen(null);
            });

            // Aktionsmenü
            context.runOnClient(c -> {
                Figure f = Mini.figures.findOwn("Test0");
                ActionMenu.open(f, f.sx(), f.sTop());
            });
            context.waitTicks(5);
            context.takeScreenshot("08_action_menu");
            context.runOnClient(c -> ActionMenu.close());

            // Streamer-Modus: an, simulierte Nachrichten, Filter, Limit, Panik, aus
            context.runOnClient(c -> {
                Mini.configs.streamer.unknownNameDefaultSkin = true;
                Mini.configs.streamer.viewerLimit = 2;
                Mini.configs.streamer.cooldownSeconds = 0;
                Mini.configs.streamer.minBubbleIntervalSeconds = 0;
                Mini.streamer.setEnabled(true);
            });
            check(context.computeOnClient(c -> Mini.streamer.isEnabled()), "Streamer-Modus eingeschaltet");
            check(context.computeOnClient(c -> !Mini.streamer.isConnected()), "ohne Kanal keine Verbindung");
            // Automatisch verbinden: Name eintragen reicht (hier ein ungültiger TikTok-Name -> Status "ungültig", ohne Knopf)
            context.runOnClient(c -> Mini.configs.streamer.tiktokChannel = "ungültig name!");
            waitFor(context, 100, () -> context.computeOnClient(c -> Mini.streamer.status(de.minicompanions.streamer.ChatPlatform.TIKTOK)
                    == de.minicompanions.streamer.ChatSource.Status.ERROR));
            check(context.computeOnClient(c -> Mini.streamer.status(de.minicompanions.streamer.ChatPlatform.TIKTOK)
                    == de.minicompanions.streamer.ChatSource.Status.ERROR), "eingetragener TikTok-Name wird automatisch verbunden/geprüft");
            context.runOnClient(c -> Mini.configs.streamer.tiktokChannel = "");
            waitFor(context, 100, () -> context.computeOnClient(c -> Mini.streamer.status(de.minicompanions.streamer.ChatPlatform.TIKTOK)
                    == de.minicompanions.streamer.ChatSource.Status.DISCONNECTED));
            check(context.computeOnClient(c -> Mini.streamer.status(de.minicompanions.streamer.ChatPlatform.TIKTOK)
                    == de.minicompanions.streamer.ChatSource.Status.DISCONNECTED), "Name gelöscht: Verbindung automatisch beendet");
            check(context.computeOnClient(c -> Mini.streamer.simulate("bad", "!skin ../x")) == StreamerPipeline.Outcome.IGNORED_INVALID_NAME,
                    "ungültiger Name ignoriert");
            check(context.computeOnClient(c -> Mini.streamer.simulate("slash", "/skin Notch")) == StreamerPipeline.Outcome.IGNORED_COMMAND,
                    "/skin erzeugt keine Figur (Befehl ist !skin)");
            context.runOnClient(c -> {
                Mini.streamer.simulate("anna", "!skin Notch");
                Mini.streamer.simulate("bob", "!skin jeb_");
                Mini.streamer.simulate("carl", "!skin Dinnerbone");
            });
            waitFor(context, 400, () -> context.computeOnClient(c -> Mini.streamer.viewers().list().size() >= 2));
            check(context.computeOnClient(c -> Mini.streamer.viewers().list().size() == 2), "Limit 2 Zuschauer-Figuren eingehalten");
            // Skins kommen vom Minecraft-Namen aus dem Twitch-Befehl (braucht Internet)
            waitFor(context, 900, () -> context.computeOnClient(c -> Mini.streamer.viewers().list().stream()
                    .allMatch(f -> f.skin != null && f.skin.ready() && f.skin.texture != de.minicompanions.skin.SkinManager.STEVE)));
            String skins = context.computeOnClient(c -> Mini.streamer.viewers().list().stream()
                    .map(f -> f.viewerMcName + "=" + (f.skin == null ? "null" : f.skin.state + "/" + f.skin.error + "/" + f.skin.texture))
                    .reduce("", (a, b) -> a + " " + b));
            check(context.computeOnClient(c -> Mini.streamer.viewers().list().stream()
                    .allMatch(f -> f.skin != null && f.skin.ready() && f.skin.texture != de.minicompanions.skin.SkinManager.STEVE
                            && f.viewerMcName.equalsIgnoreCase(f.data.skinValue))), "echte Skins vom MC-Namen geladen:" + skins);
            var unknown = context.computeOnClient(c -> Mini.skins.loadPlayerAsync("gibtsnichtxyz12"));
            waitFor(context, 400, unknown::isDone);
            check(unknown.isDone() && unknown.join().state == de.minicompanions.skin.SkinManager.State.FAILED
                    && "unknown_player".equals(unknown.join().error) && !unknown.join().retrying, "unbekannter MC-Name erkannt");
            context.runOnClient(c -> Mini.streamer.simulate("anna", "Hallo §cStream! schau https://evil.example/x"));
            context.waitTicks(30);
            String bubble = context.computeOnClient(c -> Mini.streamer.viewers().list().stream()
                    .filter(f -> "anna".equals(f.viewerLogin)).findFirst().map(f -> f.lastViewerMessage).orElse(""));
            check("Hallo Stream! schau [Link]".equals(bubble), "Nachricht gefiltert: '" + bubble + "'");
            // mehrere schnelle Nachrichten: die neueste ersetzt sofort die alte Blase
            context.runOnClient(c -> {
                Mini.configs.streamer.minBubbleIntervalSeconds = 0;
                Mini.configs.streamer.bubbleQueueSize = 1;
                Mini.streamer.simulate("anna", "erste Nachricht");
                Mini.streamer.simulate("anna", "zweite Nachricht");
                Mini.streamer.simulate("anna", "dritte Nachricht");
            });
            context.waitTicks(5);
            String shown = context.computeOnClient(c -> Mini.streamer.viewers().list().stream()
                    .filter(f -> "anna".equals(f.viewerLogin)).findFirst()
                    .map(f -> f.bubbles.current() == null ? "keine" : f.bubbles.current().text()).orElse("keine Figur"));
            check("dritte Nachricht".equals(shown), "neue Nachricht ersetzt sofort die alte Blase: '" + shown + "'");
            // Zuschauer-Figur mit der Maus ins Zimmer ziehen (Streamer hat oft nur Zuschauer-Figuren)
            context.runOnClient(c -> {
                Mini.room.setOpen(true);
                byName("anna").data.behavior = false;
                c.setScreen(new de.minicompanions.input.OverlayInteractScreen());
            });
            context.waitTicks(20);
            String r3 = dragIntoRoom(context, "anna");
            context.takeScreenshot("09b_viewer_in_room");
            check(r3.endsWith("im Zimmer: true"), "Zuschauer-Figur ins Zimmer gezogen (" + r3 + ")");
            context.runOnClient(c -> Mini.streamer.simulate("anna", "hallo aus dem zimmer"));
            context.waitTicks(5);
            context.takeScreenshot("09c_viewer_bubble_in_room");
            context.runOnClient(c -> c.setScreen(null));
            check(context.computeOnClient(c -> Mini.streamer.simulate("zoe", "ich habe keine Figur")) == StreamerPipeline.Outcome.IGNORED_NO_FIGURE,
                    "Nachricht ohne Figur ignoriert");
            context.takeScreenshot("09_streamer_viewers");
            context.setScreen(() -> new StreamerScreen(null));
            context.waitTicks(5);
            context.takeScreenshot("10_streamer_screen");
            context.setScreen(() -> null);
            context.getInput().holdKey(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL);
            context.getInput().pressKey(Keys.panic);
            context.getInput().releaseKey(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL);
            context.waitTicks(5);
            check(context.computeOnClient(c -> Mini.streamer.viewers().list().isEmpty()
                    && Mini.figures.all().stream().noneMatch(f -> f.viewer)), "Panik-Taste löscht alle Zuschauer-Figuren (auch im Zimmer)");
            check(context.computeOnClient(c -> Mini.streamer.moderation().spawnsPaused()), "Chat-Spawns nach Panik pausiert");
            context.runOnClient(c -> {
                Mini.streamer.moderation().setSpawnsPaused(false);
                Mini.streamer.simulate("dora", "!skin Notch");
                Mini.streamer.simulate(de.minicompanions.streamer.ChatPlatform.KICK, "kickfan", "!skin jeb_");
                Mini.configs.streamer.platformNameColors = "ALWAYS";
            });
            waitFor(context, 300, () -> context.computeOnClient(c -> Mini.streamer.viewers().list().size() >= 2));
            check(context.computeOnClient(c -> Mini.streamer.viewers().list().stream().anyMatch(f -> "kick:kickfan".equals(f.viewerLogin)
                    && f.viewerPlatform == de.minicompanions.streamer.ChatPlatform.KICK)), "Kick-Zuschauer bekommt eigene Figur (Plattform Kick)");
            check(context.computeOnClient(c -> de.minicompanions.render.OverlayRenderer.platformColors()), "Namen in Plattform-Farbe einstellbar");
            context.waitTicks(20);
            context.takeScreenshot("10b_platform_colors");
            context.setScreen(() -> new StreamerScreen(null));
            context.waitTicks(5);
            context.takeScreenshot("10c_streamer_platforms");
            context.setScreen(() -> null);
            context.runOnClient(c -> Mini.streamer.setEnabled(false));
            context.waitTicks(2);
            check(context.computeOnClient(c -> Mini.streamer.viewers().list().isEmpty() && Mini.figures.all().stream().noneMatch(f -> f.viewer)),
                    "Streamer-Modus aus entfernt alles sofort");
            check(context.computeOnClient(c -> Mini.streamer.simulate("eve", "!skin Notch")) == StreamerPipeline.Outcome.IGNORED_DISABLED,
                    "Streamer-Modus aus: nichts passiert");
            check(context.computeOnClient(c -> Mini.figures.all().stream().noneMatch(f -> f.activity == de.minicompanions.ai.Activity.SLEEP)),
                    "keine Figur schläft");

            // GUIs öffnen (Absturz-Test)
            context.setScreen(() -> new SettingsScreen(null));
            context.waitTicks(5);
            context.takeScreenshot("11_settings");
            context.setScreen(() -> new FigureEditScreen(null, Mini.configs.figures.get(0)));
            context.waitTicks(10);
            context.takeScreenshot("12_edit");
            context.setScreen(() -> new ManageScreen(null));
            context.waitTicks(5);
            context.takeScreenshot("13_manage");
            context.setScreen(() -> null);
            context.waitTicks(5);
        }
    }

    private static Map<String, Integer> countItems(net.minecraft.client.MinecraftClient c) {
        Map<String, Integer> m = new HashMap<>();
        var inv = c.player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (!s.isEmpty()) {
                m.merge(s.getItem().toString(), s.getCount(), Integer::sum);
            }
        }
        return m;
    }

    private static void waitWhileTaskRuns(ClientGameTestContext context) {
        for (int i = 0; i < 1200 && context.computeOnClient(c -> Mini.inventory.isRunning()); i++) {
            context.waitTick();
        }
        check(!context.computeOnClient(c -> Mini.inventory.isRunning()), "Inventar-Aufgabe beendet");
    }

    /** Eigene oder Zuschauer-Figur über den angezeigten Namen. */
    private static Figure byName(String name) {
        return Mini.figures.all().stream().filter(f -> name.equals(f.name())).findFirst().orElseThrow();
    }

    /** Zieht eine Figur mit echten Maus-Ereignissen (drücken, bewegen, loslassen) ins Zimmer. */
    private static String dragIntoRoom(ClientGameTestContext context, String name) {
        double scale = context.computeOnClient(c -> c.getWindow().getScaleFactor());
        float[] from = context.computeOnClient(c -> {
            Figure f = byName(name);
            return new float[] {f.sx(), f.body.y - f.body.h * 0.5f};
        });
        float[] to = context.computeOnClient(c -> {
            var r = Mini.room.content();
            return new float[] {r.x() + r.w() * 0.6f, r.y() + r.h() * 0.5f};
        });
        context.getInput().setCursorPos(from[0] * scale, from[1] * scale);
        context.waitTicks(2);
        String under = context.computeOnClient(c -> {
            Figure f = Mini.figures.figureAt(from[0], from[1]);
            return f == null ? "nichts" : f.name();
        });
        context.getInput().holdMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitTicks(2);
        for (int i = 1; i <= 12; i++) {
            float k = i / 12f;
            context.getInput().setCursorPos((from[0] + (to[0] - from[0]) * k) * scale, (from[1] + (to[1] - from[1]) * k) * scale);
            context.waitTick();
        }
        context.waitTicks(10);
        boolean carried = context.computeOnClient(c -> de.minicompanions.input.InputRouter.carried() != null);
        context.takeScreenshot("07b_drag_" + name);
        context.getInput().releaseMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitTicks(60);
        boolean in = context.computeOnClient(c -> byName(name).inRoom);
        return "unter Maus: " + under + ", getragen: " + carried + ", im Zimmer: " + in;
    }

    private static void waitFor(ClientGameTestContext context, int ticks, java.util.function.BooleanSupplier cond) {
        for (int i = 0; i < ticks && !cond.getAsBoolean(); i++) {
            context.waitTick();
        }
    }
}
