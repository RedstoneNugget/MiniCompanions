package de.minicompanions.events;

import de.minicompanions.Mini;
import de.minicompanions.ai.Activity;
import de.minicompanions.config.MiniConfig;
import de.minicompanions.figure.Emote;
import de.minicompanions.figure.Figure;
import de.minicompanions.figure.FigureEnums;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Reaktionen der Figuren auf das Spiel - ausschließlich anhand von Daten, die der Client ohnehin kennt
 * (eigene Werte, sichtbare/geladene Objekte in direkter Nähe). Keine Röntgen- oder Durchblick-Informationen.
 */
public final class GameEventWatcher {
    private static final Random RNG = new Random();
    private final Map<String, Long> cooldowns = new HashMap<>();
    private float lastHealth = -1;
    private int lastLevel = -1;
    private boolean wasDead;
    private boolean raining;
    private boolean thundering;
    private boolean night;
    private boolean morningDone;
    private RegistryKey<World> lastDim;
    private String lastBiome = "";
    private boolean underwater;
    private boolean bossSeen;
    private final Map<String, Integer> rareCounts = new HashMap<>();
    private int tickCounter;
    private Entity lastTarget;
    private long lastTargetTime;
    private int killStreak;
    private long lastKillTime;
    private boolean rainingNow;
    private boolean nightNow;

    public void register() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient() && player == MinecraftClient.getInstance().player) {
                onAttack(entity);
            }
            return ActionResult.PASS;
        });
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient() && player == MinecraftClient.getInstance().player && RNG.nextInt(12) == 0) {
                spectate("watch_build");
            }
            return ActionResult.PASS;
        });
    }

    public boolean isRainingOutside() {
        return rainingNow;
    }

    public boolean isNightOutside() {
        return nightNow;
    }

    private boolean ready(String key, long cooldownMs) {
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(key);
        if (last != null && now - last < cooldownMs) {
            return false;
        }
        cooldowns.put(key, now);
        return true;
    }

    private static void all(String situation, Activity a, float dur, Emote e) {
        Mini.figures.reactAll(situation, a, dur, e, 1);
    }

    private void spectate(String situation) {
        if (!Mini.cfg().spectatorComments || !ready(situation, 25_000)) {
            return;
        }
        Figure f = Mini.figures.randomOnScreen(true);
        if (f != null) {
            f.brain.react(situation, Activity.LOOK, 1.5f, null);
        }
    }

    private void onAttack(Entity e) {
        MiniConfig cfg = Mini.cfg();
        if (!cfg.reactGameEvents || !cfg.reactCombat) {
            return;
        }
        lastTarget = e;
        lastTargetTime = System.currentTimeMillis();
        if (ready("attack", 12_000)) {
            Figure f = Mini.figures.randomOnScreen(false);
            if (f != null) {
                f.brain.react("combat", Activity.CHEER, 1.2f, null);
            }
        }
    }

    public void tick(MinecraftClient mc) {
        MiniConfig cfg = Mini.cfg();
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) {
            lastHealth = -1;
            lastLevel = -1;
            rainingNow = false;
            nightNow = false;
            return;
        }
        rainingNow = mc.world.isRaining() && !mc.world.getRegistryKey().equals(World.NETHER) && !mc.world.getRegistryKey().equals(World.END)
                && mc.world.getBiome(p.getBlockPos()).value().hasPrecipitation();
        long tod = mc.world.getTimeOfDay() % 24000L;
        nightNow = tod > 13000 && tod < 23000 && mc.world.getRegistryKey().equals(World.OVERWORLD);
        if (!cfg.reactGameEvents) {
            return;
        }
        tickCounter++;

        // --- Gesundheit, Tod, Level ---
        float hp = p.getHealth();
        if (lastHealth >= 0 && hp < lastHealth - 0.5f && !p.isDead()) {
            if (ready("hurt", 4000)) {
                all("hurt", Activity.SCARED, 0.9f, Emote.EXCLAMATION);
            }
        }
        if (hp > 0 && hp <= 6 && lastHealth > 6 && ready("lowhealth", 30_000)) {
            all("lowhealth", Activity.SAD, 2f, Emote.SWEAT);
        }
        lastHealth = hp;
        boolean dead = p.isDead() || mc.currentScreen instanceof DeathScreen;
        if (dead && !wasDead) {
            all("death", Activity.SAD, 4f, Emote.TEAR);
        }
        wasDead = dead;
        if (lastLevel >= 0 && p.experienceLevel > lastLevel) {
            all("levelup", Activity.CHEER, 2.5f, Emote.STAR);
            if (cfg.growEffects) {
                for (Figure f : Mini.figures.all()) {
                    f.pulseSize(1.3f, 3f);
                }
            }
            Figure f = Mini.figures.randomOnScreen(false);
            if (f != null) {
                Mini.particles.fireworks(f.sx(), f.sTop() - 6);
            }
        }
        lastLevel = p.experienceLevel;

        if (tickCounter % 20 != 0) {
            return;
        }
        // --- ab hier 1x pro Sekunde ---
        if (cfg.reactWeather) {
            boolean thunder = mc.world.isThundering();
            if (rainingNow && !raining && ready("rain", 120_000)) {
                String biome = biomeId(mc);
                boolean snow = biome.contains("snow") || biome.contains("frozen") || biome.contains("ice") || biome.contains("grove") || biome.contains("peaks");
                all(snow ? "snow" : "rain", Activity.LOOK, 1.5f, snow ? Emote.COLD : null);
            }
            if (thunder && !thundering) {
                all("thunder", Activity.SCARED, 1.5f, Emote.EXCLAMATION);
            }
            if (thunder && RNG.nextInt(25) == 0) {
                all(null, Activity.SCARED, 0.8f, null);
            }
            raining = rainingNow;
            thundering = thunder;
            snowballs(mc);
        }
        if (cfg.reactBiome) {
            if (nightNow && !night && ready("night", 300_000)) {
                all("night", Activity.LOOK, 1.5f, null);
            }
            if (tod < 1000 && night) {
                morningDone = false;
            }
            if (tod >= 0 && tod < 1500 && !morningDone && !nightNow) {
                morningDone = true;
                all("morning", Activity.WAVE, 1.5f, null);
            }
            night = nightNow;
            RegistryKey<World> dim = mc.world.getRegistryKey();
            if (lastDim != null && !dim.equals(lastDim)) {
                if (dim.equals(World.NETHER)) {
                    all("nether", Activity.SCARED, 2f, Emote.SWEAT);
                } else if (dim.equals(World.END)) {
                    all("end", Activity.LOOK, 2f, Emote.SPARKLE);
                } else {
                    all("portal", Activity.CHEER, 1.5f, null);
                }
            }
            lastDim = dim;
            if (tickCounter % 60 == 0) {
                String biome = biomeId(mc);
                if (!biome.equals(lastBiome)) {
                    if ((biome.contains("desert") || biome.contains("badlands") || biome.contains("savanna")) && ready("desert", 180_000)) {
                        all("desert", Activity.IDLE, 2f, Emote.SWEAT);
                    }
                    lastBiome = biome;
                }
            }
            if (p.getY() > 190 && dim.equals(World.OVERWORLD) && ready("height", 180_000)) {
                all("height", Activity.SCARED, 1.5f, Emote.SWEAT);
            }
            boolean sub = p.isSubmergedInWater();
            if (sub) {
                for (Figure f : Mini.figures.all()) {
                    if (RNG.nextInt(3) == 0) {
                        Mini.particles.bubbles(f.sx(), f.sTop(), 2);
                    }
                }
                if (!underwater && ready("underwater", 60_000)) {
                    all("underwater", Activity.LOOK, 1f, Emote.BUBBLES);
                }
            }
            underwater = sub;
        }
        if (cfg.reactCombat) {
            combat(mc, p);
        }
        if (cfg.dangerWarnings) {
            dangers(mc, p);
        }
        if (cfg.rareItemCheers) {
            rareItems(p);
        }
        if (cfg.spectatorComments && mc.interactionManager != null && mc.interactionManager.isBreakingBlock() && RNG.nextInt(8) == 0) {
            spectate("watch_mine");
        }
    }

    private static String biomeId(MinecraftClient mc) {
        return mc.world.getBiome(mc.player.getBlockPos()).getKey().map(k -> k.getValue().getPath()).orElse("");
    }

    private void snowballs(MinecraftClient mc) {
        if (!rainingNow || RNG.nextInt(4) != 0) {
            return;
        }
        String biome = biomeId(mc);
        if (!(biome.contains("snow") || biome.contains("frozen") || biome.contains("ice"))) {
            return;
        }
        Figure a = Mini.figures.randomOnScreen(false);
        Figure b = Mini.figures.randomOnScreen(false);
        if (a == null || b == null || a == b) {
            return;
        }
        a.facing = b.body.x < a.body.x ? -1 : 1;
        a.setActivity(Activity.PLAY, 0.6f);
        Mini.particles.snowball(a.sx(), a.sTop() + 2, b.sx(), b.sTop());
    }

    private void combat(MinecraftClient mc, ClientPlayerEntity p) {
        long now = System.currentTimeMillis();
        if (lastTarget instanceof LivingEntity le && now - lastTargetTime < 3000 && (le.isDead() || le.isRemoved())) {
            lastTarget = null;
            if (now - lastKillTime > 30_000) {
                killStreak = 0;
            }
            killStreak++;
            lastKillTime = now;
            if (killStreak == 3 || killStreak == 5 || killStreak == 10 || killStreak % 20 == 0) {
                all("killstreak", Activity.CHEER, 2f, Emote.STAR);
            }
        }
        boolean boss = false;
        for (Entity e : mc.world.getEntities()) {
            if ((e instanceof WitherEntity || e instanceof EnderDragonEntity) && e.squaredDistanceTo(p) < 120 * 120 && ((LivingEntity) e).isAlive()) {
                boss = true;
                break;
            }
        }
        if (boss && !bossSeen) {
            all("boss", Activity.SCARED, 2.5f, Emote.EXCLAMATION);
        } else if (!boss && bossSeen) {
            all("boss_end", Activity.CHEER, 3f, Emote.STAR);
        }
        bossSeen = boss;
    }

    private void dangers(MinecraftClient mc, ClientPlayerEntity p) {
        for (Entity e : mc.world.getEntities()) {
            if (e == p || !(e instanceof LivingEntity le) || !le.isAlive()) {
                continue;
            }
            double d = e.squaredDistanceTo(p);
            if (e instanceof CreeperEntity && d < 49 && p.canSee(e) && ready("creeper", 15_000)) {
                all("creeper", Activity.SCARED, 1.5f, Emote.EXCLAMATION);
            } else if (e instanceof SpiderEntity && d < 64 && p.canSee(e) && ready("spider", 40_000)) {
                all("spider", Activity.SCARED, 1.2f, Emote.EXCLAMATION);
            } else if (e instanceof PhantomEntity && d < 400 && p.canSee(e) && ready("phantom", 40_000)) {
                all("phantom", Activity.SCARED, 1.2f, Emote.EXCLAMATION);
            }
        }
        // Lava nur direkt um die Füße (sichtbar, keine Suche durch Wände)
        BlockPos base = p.getBlockPos();
        outer:
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy <= 0; dy++) {
                    if (mc.world.getBlockState(base.add(dx, dy, dz)).isOf(Blocks.LAVA)) {
                        if (ready("lava", 30_000)) {
                            all("lava", Activity.SCARED, 1.5f, Emote.EXCLAMATION);
                        }
                        break outer;
                    }
                }
            }
        }
        ItemStack hand = p.getMainHandStack();
        if (hand.isDamageable() && hand.getMaxDamage() > 0) {
            float left = (hand.getMaxDamage() - hand.getDamage()) / (float) hand.getMaxDamage();
            if (left < 0.1f && ready("durability", 90_000)) {
                all("durability", Activity.LOOK, 1.5f, Emote.EXCLAMATION);
            }
        }
        if (p.getHungerManager().getFoodLevel() <= 6 && ready("starving", 90_000)) {
            all("starving", Activity.SAD, 1.5f, null);
        }
    }

    private void rareItems(ClientPlayerEntity p) {
        List<net.minecraft.item.Item> rare = List.of(Items.DIAMOND, Items.NETHERITE_INGOT, Items.NETHERITE_SCRAP, Items.ANCIENT_DEBRIS,
                Items.TOTEM_OF_UNDYING, Items.ELYTRA, Items.NETHER_STAR, Items.ENCHANTED_GOLDEN_APPLE, Items.EMERALD);
        var inv = p.getInventory();
        for (net.minecraft.item.Item item : rare) {
            int count = 0;
            for (int i = 0; i < inv.size(); i++) {
                ItemStack s = inv.getStack(i);
                if (s.isOf(item)) {
                    count += s.getCount();
                }
            }
            String k = item.toString();
            Integer prev = rareCounts.put(k, count);
            if (prev != null && count > prev && ready("rare_" + k, 20_000)) {
                Figure f = Mini.figures.randomOnScreen(false);
                if (f != null) {
                    String line = Mini.lines.get(f.personality(), "rare");
                    if (line != null) {
                        f.brain.sayText(line.replace("{item}", new ItemStack(item).getName().getString()).replace("{name}", f.name()));
                    }
                    Mini.particles.fireworks(f.sx(), f.sTop() - 6);
                }
                all(null, Activity.CHEER, 2f, Emote.STAR);
            }
        }
    }

    /** Text für die Infotafel einer Figur. */
    public String infoText(FigureEnums.InfoBoard type) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity p = mc.player;
        return switch (type) {
            case TIME -> {
                if (mc.world == null) {
                    yield "--:--";
                }
                long t = (mc.world.getTimeOfDay() + 6000) % 24000L;
                yield String.format("%02d:%02d", t / 1000, (t % 1000) * 60 / 1000);
            }
            case COORDS -> p == null ? "-" : p.getBlockX() + " " + p.getBlockY() + " " + p.getBlockZ();
            case FPS -> mc.getCurrentFps() + " FPS";
            case PING -> {
                if (p == null || mc.getNetworkHandler() == null) {
                    yield "-";
                }
                var e = mc.getNetworkHandler().getPlayerListEntry(p.getUuid());
                yield (e == null ? 0 : e.getLatency()) + " ms";
            }
            case DURABILITY -> {
                if (p == null || !p.getMainHandStack().isDamageable()) {
                    yield "-";
                }
                ItemStack s = p.getMainHandStack();
                yield (s.getMaxDamage() - s.getDamage()) + "/" + s.getMaxDamage();
            }
            case BIOME -> p == null || mc.world == null ? "-" : biomeId(mc);
            default -> "";
        };
    }
}
