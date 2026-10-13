package de.minicompanions.ai;

import de.minicompanions.Mini;
import de.minicompanions.config.MiniConfig;
import de.minicompanions.figure.BubbleQueue;
import de.minicompanions.figure.Emote;
import de.minicompanions.figure.Figure;
import de.minicompanions.figure.WorldView;
import de.minicompanions.physics.Body;
import de.minicompanions.physics.Ledge;
import de.minicompanions.physics.Physics;
import de.minicompanions.physics.PlatformGraph;
import de.minicompanions.physics.PlatformSet;
import de.minicompanions.util.MathUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Zustandsmaschine und KI einer Figur: Bedürfnisse, Persönlichkeit, Wegfindung über den
 * Plattform-Graphen (Laufen, Springen, Klettern, Hängen), Interaktionen mit anderen Figuren,
 * Objekten im Spielzimmer und Reaktionen auf Spielereignisse.
 */
public final class Brain {
    private static final Random RNG = new Random();
    /** Pfadsuchen pro Frame (Leistung auch bei sehr vielen Figuren). */
    public static int planBudget = 8;

    private final Figure f;

    // --- Navigation ---
    private boolean navigating;
    private String goalLedgeId;
    private float goalX;
    private Runnable arrive;
    private List<PlatformGraph.Step> path;
    private int step;
    private float stepTimer;
    private int fails;
    /** Fortschritts-Kontrolle beim Laufen: bleibt die Figur hängen, wird das Ziel aufgegeben. */
    private float stuckX = Float.NaN;
    private float stuckTime;
    /** Sitzt gerade wegen des Ruhemodus. */
    private boolean restSitting;
    /** Ziel-Kante des letzten Sprungs/Abstiegs (zum Erkennen verfehlter Sprünge). */
    private String expectLedge;
    private String failLedge;
    private int failCount;
    /** Kanten, die nach wiederholten Fehlsprüngen eine Weile gemieden werden (Kanten-ID -> bis wann). */
    private final java.util.Map<String, Long> avoid = new java.util.HashMap<>();
    /** Kurze Pause nach einem Fehlsprung, damit die Figur nicht dauernd hüpft. */
    private float retryPause;
    /** Nächster Sprung als Super-Sprung (doppelt so hoch), z. B. nach einem verfehlten Sprung. */
    private boolean superNext;
    /** Der aktuelle Weg braucht Super-Sprünge (Ziel für normale Sprünge zu hoch). */
    private boolean superPath;
    private boolean running;
    private boolean commanded;

    // --- Klettern ---
    private String climbTo;
    private float climbLandX;

    // --- Hüpfen (Inventar-Arbeit, Spiele): Bewegung auf einer Parabel ohne Physik ---
    private boolean hopping;
    private float hopFromX;
    private float hopFromY;
    private float hopToX;
    private float hopToY;
    private float hopT;
    private float hopDur;
    private Runnable hopDone;

    // --- Gedanken / Soziales ---
    private float thinkTimer = RNG.nextFloat();
    private float talkTimer = 8 + RNG.nextFloat() * 25;
    private float socialTimer = 3 + RNG.nextFloat() * 8;
    private Figure follow;
    private boolean followMouse;
    private float followTimer;
    private float followReplan;
    private Figure partner;

    // --- geplante Reaktion ---
    private float reactDelay = -1;
    private Activity reactActivity;
    private float reactDuration;
    private Emote reactEmote;
    private String reactSituation;

    public Brain(Figure f) {
        this.f = f;
    }

    // =================================================================== Update

    public void update(WorldView w, float dt) {
        MiniConfig cfg = Mini.cfg();
        f.activityTime += dt;
        f.animTime += dt;
        if (f.emote != null && (f.emoteTime -= dt) <= 0) {
            f.emote = null;
        }
        if (f.effectTimer > 0) {
            f.effectTimer -= dt;
            if (f.effectTimer <= 0) {
                f.effectScale = 1f;
            }
        }
        f.effectScaleCur = MathUtil.approach(f.effectScaleCur, f.effectScale, 3f, dt);
        if (f.soundCooldown > 0) {
            f.soundCooldown -= dt;
        }
        needs(dt, cfg);
        Body b = f.body;

        if (f.carried) {
            f.activity = Activity.CARRIED;
            return;
        }
        if (hopping) {
            hop(dt);
            return;
        }
        if (b.climbing) {
            climb(w, dt);
            return;
        }
        if (!b.onGround) {
            if (f.activity == Activity.THROWN) {
                f.throwSpin += dt * 12 * Math.signum(b.vx == 0 ? 1 : b.vx);
            } else if (f.activity != Activity.JUMP && f.activity != Activity.FALL) {
                f.setActivity(b.vy < 0 ? Activity.JUMP : Activity.FALL, 1);
            } else if (f.activity == Activity.JUMP && b.vy > 0) {
                f.activity = Activity.FALL;
            }
            return;
        }
        if (expectLedge != null) {
            landedAfterStep(b);
        }
        if (f.activity == Activity.FALL || f.activity == Activity.JUMP || f.activity == Activity.THROWN) {
            boolean hard = f.activity == Activity.THROWN || b.lastImpact > 340;
            f.throwSpin = 0;
            if (hard) {
                f.setActivity(Activity.SCARED, 0.9f);
                f.showEmote(Emote.STAR, 1.2f);
                if (f.activity == Activity.SCARED && RNG.nextInt(3) == 0) {
                    say("thrown");
                }
            } else if (!navigating) {
                f.setActivity(Activity.IDLE, 0.2f + RNG.nextFloat() * 0.8f);
            } else {
                f.activity = Activity.WALK;
            }
        }

        boolean behave = cfg.enabled && cfg.behaviorEnabled && f.data.behavior;
        if (!behave) {
            b.vx = 0;
            if (f.activity != Activity.POSE && f.activity != Activity.WORK && f.activity != Activity.PLAY) {
                f.activity = Activity.IDLE;
            }
            return;
        }
        if (cfg.restMode) {
            // Ruhemodus: ruhig sitzen bleiben (kein Schlafen)
            b.vx = 0;
            stopNavigation();
            if (!restSitting) {
                restSitting = true;
                f.setActivity(Activity.SIT, 1e9f);
            }
            return;
        }
        if (restSitting) {
            restSitting = false;
            f.setActivity(Activity.IDLE, 0.5f);
        }
        if (f.activity == Activity.POSE || f.activity == Activity.WORK) {
            b.vx = 0;
            if (f.activityTime < f.activityDuration) {
                return;
            }
            f.setActivity(Activity.IDLE, 1);
        }

        // geplante Reaktion (z. B. auf Schaden) mit kleiner Verzögerung pro Figur
        if (reactDelay >= 0) {
            reactDelay -= dt;
            if (reactDelay < 0) {
                stopNavigation();
                b.vx = 0;
                if (reactActivity != null) {
                    f.setActivity(reactActivity, reactDuration);
                }
                if (reactEmote != null) {
                    f.showEmote(reactEmote, Math.max(1.5f, reactDuration));
                }
                if (reactSituation != null) {
                    say(reactSituation);
                }
                return;
            }
        }

        talk(dt, cfg);

        if (followTimer > 0) {
            followTimer -= dt;
            followReplan -= dt;
            if (followReplan <= 0) {
                followReplan = 0.8f;
                float tx;
                float ty;
                if (followMouse) {
                    tx = w.mouseX;
                    ty = w.mouseY;
                } else if (follow != null && !follow.carried) {
                    tx = follow.body.x - follow.facing * follow.body.w * 2;
                    ty = follow.body.y - 1;
                } else {
                    followTimer = 0;
                    tx = b.x;
                    ty = b.y;
                }
                if (Math.abs(tx - b.x) > 6 || Math.abs(ty - b.y) > 4) {
                    goToPoint(w, tx, ty, null);
                    running = Math.abs(tx - b.x) > 60;
                }
            }
        }

        if (retryPause > 0) {
            retryPause -= dt;
            b.vx = 0;
            if (f.activity == Activity.WALK || f.activity == Activity.RUN) {
                f.activity = Activity.IDLE;
            }
            return;
        }
        if (navigating) {
            navigate(w, dt);
            return;
        }

        b.vx = 0;
        if (f.activityTime < f.activityDuration) {
            continueActivity(w, dt);
            return;
        }
        if (partner != null) {
            partner = null;
        }
        thinkTimer -= dt;
        if (thinkTimer <= 0) {
            thinkTimer = 0.15f + RNG.nextFloat() * 0.4f;
            think(w, cfg);
        }
    }

    // =================================================================== Bedürfnisse

    private void needs(float dt, MiniConfig cfg) {
        if (!cfg.needs || f.carried) {
            return;
        }
        var d = f.data;
        // Kein Schlafen mehr: Energie bleibt immer voll
        d.energy = 100;
        d.hunger = Math.min(100, d.hunger + 0.055f * dt);
        float moodDelta = -0.035f;
        if (f.activity == Activity.PETTED || f.activity == Activity.PLAY || f.activity == Activity.DANCE || f.activity == Activity.HUG
                || f.activity == Activity.TICKLED || f.activity == Activity.CHEER) {
            moodDelta = 0.8f;
        }
        if (d.hunger > 80) {
            moodDelta -= 0.25f;
        }
        d.mood = Math.max(0, Math.min(100, d.mood + moodDelta * dt));
    }

    // =================================================================== Denken

    private void think(WorldView w, MiniConfig cfg) {
        Body b = f.body;
        PlatformSet ps = w.platforms;
        Ledge cur = ps.get(b.ledgeId);
        if (cur == null) {
            return;
        }
        commanded = false;
        Personality p = f.personality();
        var d = f.data;

        if (cfg.needs && d.hunger > 78) {
            eat(w);
            return;
        }
        socialTimer -= 1f;
        if (cfg.interactions && socialTimer <= 0) {
            socialTimer = (5 + RNG.nextFloat() * 10) / Math.max(0.3f, p.social);
            Figure o = nearestOther(w, 26 + b.h * 3);
            if (o != null && tryInteract(o, w, cfg)) {
                return;
            }
        }
        // Am Bildschirmrand nicht herumstehen: gleich wieder losgehen
        float edgeMargin = Math.max(14f, b.w * 2f);
        if (b.x < edgeMargin || b.x > ps.width - edgeMargin) {
            wander(w, false);
            return;
        }
        float r = RNG.nextFloat();
        float chaos = p.chaos;
        if (r < 0.5f) {
            wander(w, chaos > 0.8f && RNG.nextBoolean());
        } else if (r < 0.58f) {
            f.setActivity(Activity.IDLE, 0.8f + RNG.nextFloat() * 2f / p.speed);
            f.facing = RNG.nextInt(3) - 1;
        } else if (r < 0.62f) {
            boolean nearEdge = Math.min(b.x - cur.x1, cur.x2 - b.x) < b.w * 1.2f && cur.kind != Ledge.Kind.GROUND;
            if (nearEdge && spaceBelow(ps, cur, b.x) > b.h * 0.5f) {
                f.facing = 0;
                f.setActivity(Activity.SIT_EDGE, 3 + RNG.nextFloat() * 6);
            } else {
                f.setActivity(Activity.SIT, 2 + RNG.nextFloat() * 5);
            }
        } else if (r < 0.67f && d.mood > 35) {
            f.setActivity(Activity.DANCE, 2.5f + RNG.nextFloat() * 3);
            if (RNG.nextInt(3) == 0) {
                f.showEmote(Emote.NOTE, 2.5f);
            }
        } else if (r < 0.71f && w.kind == WorldView.Kind.INVENTORY) {
            f.setActivity(Activity.HIDE, 3 + RNG.nextFloat() * 3);
        } else if (r < 0.82f) {
            f.setActivity(Activity.LOOK, 1.5f + RNG.nextFloat() * 2);
        } else if (r < 0.85f && cfg.stacking && w.figures.size() >= 3 && w.figures.size() <= 64) {
            if (!tryTower(w)) {
                wander(w, false);
            }
        } else {
            wander(w, false);
        }
    }

    /** Platz unter einer Kante an Stelle x (bis zur nächsten Kante oder zum Boden). */
    private static float spaceBelow(PlatformSet ps, Ledge l, float x) {
        Ledge below = ps.firstBelow(x, l.y + 0.5f, l.id);
        return (below == null ? ps.height : below.y) - l.y;
    }

    private void continueActivity(WorldView w, float dt) {
        Body b = f.body;
        switch (f.activity) {
            case LOOK -> f.facing = w.mouseX < b.x - 4 ? -1 : (w.mouseX > b.x + 4 ? 1 : 0);
            case FIGHT, HUG, TALK -> {
                if (partner != null) {
                    f.facing = partner.body.x < b.x ? -1 : 1;
                }
            }
            default -> {
            }
        }
    }

    private void wander(WorldView w, boolean run) {
        Body b = f.body;
        PlatformSet ps = w.platforms;
        PlatformGraph.Spec spec = spec();
        List<Ledge> cands = ps.ledges();
        float total = 0;
        float[] weights = new float[cands.size()];
        for (int i = 0; i < cands.size(); i++) {
            Ledge l = cands.get(i);
            if (l.kind == Ledge.Kind.FIGURE || l.width() < b.w + 1 || avoided(l.id)) {
                continue;
            }
            if (l.kind == Ledge.Kind.GROUND && !Mini.cfg().walkGround && !w.isRoom() && cands.size() > 1) {
                continue;
            }
            float wgt = w.preference(l) * (0.5f + Math.min(1f, l.width() / 80f));
            float dist = Math.abs(l.centerX() - b.x) + Math.abs(l.y - b.y);
            wgt *= 1f / (1f + dist / 400f);
            weights[i] = wgt;
            total += wgt;
        }
        if (total <= 0) {
            return;
        }
        for (int attempt = 0; attempt < 4; attempt++) {
            float pick = RNG.nextFloat() * total;
            for (int i = 0; i < cands.size(); i++) {
                pick -= weights[i];
                if (pick <= 0 && weights[i] > 0) {
                    Ledge l = cands.get(i);
                    // Ziele nicht direkt am Bildschirmrand (dort bleibt man sonst gern hängen)
                    float margin = Math.max(14f, b.w * 2f);
                    float lo = Math.max(l.x1 + b.w, margin);
                    float hi = Math.min(l.x2 - b.w, ps.width - margin);
                    if (hi < lo) {
                        lo = hi = MathUtil.clamp(l.centerX(), Math.min(margin, ps.width / 2f), Math.max(ps.width - margin, ps.width / 2f));
                    }
                    float x = lo + RNG.nextFloat() * (hi - lo);
                    // auf derselben Kante lieber ein Stück weiter weg gehen als nur einen Schritt
                    if (l.id.equals(b.ledgeId) && hi - lo > 80 && Math.abs(x - b.x) < 40) {
                        x = b.x < (lo + hi) / 2 ? Math.min(hi, b.x + 40 + RNG.nextFloat() * 80) : Math.max(lo, b.x - 40 - RNG.nextFloat() * 80);
                    }
                    if (PlatformGraph.fits(ps, l, x, spec)) {
                        running = run;
                        goTo(w, l, x, null);
                        return;
                    }
                    break;
                }
            }
        }
    }

    private void eat(WorldView w) {
        say("hungry");
        finishEating(false);
    }

    private void finishEating(boolean realFood) {
        f.setActivity(Activity.EAT, 2.2f);
        f.data.hunger = Math.max(0, f.data.hunger - (realFood ? 55 : 30));
        f.data.mood = Math.min(100, f.data.mood + 8);
        if (realFood && Mini.cfg().growEffects) {
            f.pulseSize(1.25f, 3.5f);
        }
        say("eat");
        Mini.sounds.figure(f, "eat");
    }

    /** Spieler füttert die Figur (Aktionsmenü/Spielzimmer). */
    public void feed() {
        stopNavigation();
        finishEating(true);
        f.showEmote(Emote.HEART, 2);
    }

    // =================================================================== Soziales

    private Figure nearestOther(WorldView w, float radius) {
        Figure best = null;
        float bd = radius;
        int checked = 0;
        int n = w.figures.size();
        int start = n > 40 ? RNG.nextInt(n) : 0;
        for (int k = 0; k < n && checked < 40; k++) {
            Figure o = w.figures.get((start + k) % n);
            if (o == f || o.carried || !o.body.onGround || o.brain.navigating || !o.activity.interruptible
                    || o.activity == Activity.SLEEP || o.activity == Activity.WORK) {
                continue;
            }
            checked++;
            float d = Math.abs(o.body.x - f.body.x) + Math.abs(o.body.y - f.body.y) * 2;
            if (d < bd) {
                bd = d;
                best = o;
            }
        }
        return best;
    }

    private float relation(Figure o) {
        Float r = f.data.relations.get(o.id());
        return r == null ? 0 : r;
    }

    private void changeRelation(Figure o, float delta) {
        if (!Mini.cfg().relationships) {
            return;
        }
        f.data.relations.merge(o.id(), delta, (a, c) -> Math.max(-100, Math.min(100, a + c)));
        o.data.relations.merge(f.id(), delta, (a, c) -> Math.max(-100, Math.min(100, a + c)));
        if (f.data.relations.size() > 200) {
            f.data.relations.keySet().iterator().next();
        }
    }

    private boolean tryInteract(Figure o, WorldView w, MiniConfig cfg) {
        float rel = relation(o);
        boolean couple = o.id().equals(f.data.partner);
        Personality p = f.personality();
        float r = RNG.nextFloat();
        Activity a;
        float dur;
        String line;
        Emote e = null;
        if (couple || rel > 55) {
            if (r < 0.5f) {
                a = Activity.HUG;
                dur = 2.5f;
                line = couple ? "love" : "friend";
                e = Emote.HEART;
            } else if (r < 0.75f) {
                follow(o, 8);
                say("follow");
                changeRelation(o, 1);
                return true;
            } else {
                a = Activity.TALK;
                dur = 3;
                line = "chat";
            }
            changeRelation(o, 2);
            if (!couple && rel > 92 && f.data.partner == null && o.data.partner == null && RNG.nextFloat() < 0.15f && !f.viewer && !o.viewer) {
                marry(o);
                return true;
            }
        } else if (rel < -35 && cfg.fighting && p.brave > 0.45f) {
            a = Activity.FIGHT;
            dur = 2.5f;
            line = "fight";
            e = Emote.ANGER;
            changeRelation(o, -1);
        } else if (rel < 8) {
            a = Activity.WAVE;
            dur = 1.6f;
            line = "greet";
            changeRelation(o, 4);
        } else {
            if (r < 0.15f && cfg.fighting && p.chaos > 0.6f) {
                a = Activity.FIGHT;
                dur = 2;
                line = "fight";
                e = Emote.ANGER;
                changeRelation(o, -6);
            } else {
                a = Activity.TALK;
                dur = 3;
                line = "chat";
                changeRelation(o, 3);
            }
        }
        startPair(o, a, dur);
        say(line);
        if (e != null) {
            f.showEmote(e, dur);
            o.showEmote(e, dur);
        }
        if (a == Activity.FIGHT) {
            final Figure other = o;
            reactLater(dur, () -> fightResult(other));
        }
        return true;
    }

    private final List<float[]> laterTimes = new ArrayList<>();
    private final List<Runnable> laterActions = new ArrayList<>();

    private void reactLater(float seconds, Runnable r) {
        laterTimes.add(new float[]{seconds});
        laterActions.add(r);
    }

    /** Verzögerte Aktionen abarbeiten (vom FigureManager pro Frame aufgerufen). */
    public void tickLater(float dt) {
        for (int i = laterTimes.size() - 1; i >= 0; i--) {
            float[] t = laterTimes.get(i);
            t[0] -= dt;
            if (t[0] <= 0) {
                Runnable r = laterActions.remove(i);
                laterTimes.remove(i);
                r.run();
            }
        }
    }

    private void fightResult(Figure o) {
        float mine = f.personality().brave + RNG.nextFloat();
        float theirs = o.personality().brave + RNG.nextFloat();
        Figure winner = mine >= theirs ? f : o;
        Figure loser = winner == f ? o : f;
        winner.setActivity(Activity.CHEER, 1.5f);
        winner.brain.say("win");
        loser.setActivity(Activity.SAD, 1.5f);
        loser.brain.say("lose");
        loser.showEmote(Emote.TEAR, 1.5f);
    }

    private void startPair(Figure o, Activity a, float dur) {
        stopNavigation();
        o.brain.stopNavigation();
        partner = o;
        o.brain.partner = f;
        f.body.vx = 0;
        o.body.vx = 0;
        f.facing = o.body.x < f.body.x ? -1 : 1;
        o.facing = -f.facing;
        f.setActivity(a, dur);
        o.setActivity(a, dur);
    }

    public void marry(Figure o) {
        f.data.partner = o.id();
        o.data.partner = f.id();
        f.data.relations.put(o.id(), 100f);
        o.data.relations.put(f.id(), 100f);
        startPair(o, Activity.HUG, 3);
        f.showEmote(Emote.HEART, 4);
        o.showEmote(Emote.HEART, 4);
        say("married");
        Mini.particles.hearts(f.sx(), f.sTop(), 10);
        Mini.configs.markDirty(de.minicompanions.config.ConfigManager.Part.FIGURES);
    }

    public void befriend(Figure o) {
        f.data.relations.put(o.id(), 70f);
        o.data.relations.put(f.id(), 70f);
        startPair(o, Activity.HUG, 2);
        f.showEmote(Emote.HEART, 2);
        say("friend");
        Mini.configs.markDirty(de.minicompanions.config.ConfigManager.Part.FIGURES);
    }

    public void duel(Figure o) {
        startPair(o, Activity.FIGHT, 3);
        f.showEmote(Emote.ANGER, 3);
        o.showEmote(Emote.ANGER, 3);
        say("fight");
        final Figure other = o;
        reactLater(3, () -> fightResult(other));
    }

    public void follow(Figure o, float seconds) {
        follow = o;
        followMouse = false;
        followTimer = seconds;
        followReplan = 0;
    }

    public void followMouse(float seconds) {
        follow = null;
        followMouse = true;
        followTimer = seconds;
        followReplan = 0;
    }

    /** Menschenturm: diese Figur ist die Basis, andere klettern nacheinander hinauf. */
    private boolean tryTower(WorldView w) {
        List<Figure> helpers = new ArrayList<>();
        for (Figure o : w.figures) {
            if (o != f && !o.carried && o.body.onGround && !o.brain.navigating && o.activity.interruptible && o.activity != Activity.SLEEP
                    && Math.abs(o.body.x - f.body.x) < 90 && Math.abs(o.body.y - f.body.y) < 30 && Math.abs(o.body.h - f.body.h) < f.body.h * 0.6f) {
                helpers.add(o);
                if (helpers.size() >= 3) {
                    break;
                }
            }
        }
        if (helpers.size() < 2) {
            return false;
        }
        stopNavigation();
        f.setActivity(Activity.BOOST, 9 + helpers.size() * 2);
        f.facing = 0;
        say("tower");
        climbChain(w, f, helpers, 0);
        return true;
    }

    private static void climbChain(WorldView w, Figure base, List<Figure> helpers, int idx) {
        if (idx >= helpers.size()) {
            Figure top = helpers.get(helpers.size() - 1);
            top.setActivity(Activity.CHEER, 2);
            top.brain.say("tower_top");
            return;
        }
        Figure climber = helpers.get(idx);
        Ledge head = w.platforms.get("fig:" + base.id());
        if (head == null) {
            return;
        }
        climber.brain.goTo(w, head, base.body.x, () -> {
            climber.setActivity(Activity.BOOST, 8 + (helpers.size() - idx) * 1.5f);
            climber.facing = 0;
            climbChain(w, climber, helpers, idx + 1);
        });
    }

    // =================================================================== Navigation

    /** Wie {@link #spec()}, aber mit Super-Sprüngen (doppelte Höhe, mehr Weite). */
    public PlatformGraph.Spec superSpec() {
        PlatformGraph.Spec s = spec();
        return new PlatformGraph.Spec(s.halfWidth(), s.height(), s.jumpHeight() * 2, s.jumpReach() * 1.5f, s.useFigures());
    }

    public PlatformGraph.Spec spec() {
        Body b = f.body;
        float h = b.h;
        return new PlatformGraph.Spec(b.w / 2f, h, Math.max(36f, h * 2.3f), Math.max(24f, h * 1.6f), Mini.cfg().stacking);
    }

    /** Geht zu einer Kante und Position; danach wird {@code onArrive} ausgeführt. */
    public void goTo(WorldView w, Ledge target, float x, Runnable onArrive) {
        navigating = true;
        goalLedgeId = target.id;
        goalX = x;
        arrive = onArrive;
        path = null;
        step = 0;
        stepTimer = 0;
        fails = 0;
        stuckX = Float.NaN;
        superPath = false;
    }

    /** Zu einem Punkt gehen (Klick im Spielzimmer, Folgen): nächste Kante unter dem Punkt. */
    public boolean goToPoint(WorldView w, float x, float y, Runnable onArrive) {
        Ledge l = w.platforms.firstBelow(x, y - 2, null);
        if (l == null) {
            return false;
        }
        float tx = MathUtil.clamp(x, l.x1 + f.body.w / 2f, l.x2 - f.body.w / 2f);
        goTo(w, l, tx, onArrive);
        return true;
    }

    /** Spieler-Befehl: dorthin gehen (wird nicht von der KI überschrieben). */
    public void command(WorldView w, float x, float y) {
        if (goToPoint(w, x, y, () -> {
            f.setActivity(Activity.WAVE, 1.2f);
            commanded = false;
        })) {
            commanded = true;
            running = Math.abs(x - f.body.x) > 80;
            f.showEmote(Emote.EXCLAMATION, 1);
        }
    }

    /**
     * Der Screen hat gewechselt (z. B. Inventar geöffnet): aktuelle ruhige Tätigkeit beenden und
     * gleich ein neues Ziel auf den neuen Plattformen suchen.
     */
    public void onScreenChanged(WorldView w) {
        if (f.carried || hopping || f.body.climbing || !f.body.onGround || f.activity == Activity.WORK
                || f.activity == Activity.POSE || f.activity == Activity.SLEEP || commanded) {
            return;
        }
        stopNavigation();
        if (RNG.nextFloat() < 0.8f) {
            wander(w, false);
        } else {
            f.activityTime = f.activityDuration;
        }
    }

    public void stopNavigation() {
        navigating = false;
        expectLedge = null;
        path = null;
        arrive = null;
        commanded = false;
    }

    /** Nach einem Sprung/Abstieg gelandet: am Ziel? Sonst Fehlversuch zählen und ggf. aufgeben. */
    private void landedAfterStep(Body b) {
        String want = expectLedge;
        expectLedge = null;
        if (want.equals(b.ledgeId)) {
            failCount = 0;
            failLedge = null;
            return;
        }
        failCount = want.equals(failLedge) ? failCount + 1 : 1;
        failLedge = want;
        if (failCount >= 2) {
            // zweimal verfehlt: diese Kante eine Weile meiden und etwas anderes machen
            avoid.put(want, System.currentTimeMillis() + 20_000);
            failCount = 0;
            failLedge = null;
            stopNavigation();
            f.setActivity(Activity.LOOK, 1 + RNG.nextFloat());
        } else {
            // kurz verschnaufen, dann neu planen (kein Dauer-Hüpfen)
            retryPause = 0.6f + RNG.nextFloat() * 0.6f;
            // nicht geschafft: beim nächsten Versuch ein Super-Sprung, doppelt so hoch
            superNext = true;
            path = null;
        }
    }

    /** Wird diese Kante gerade gemieden? */
    private boolean avoided(String ledgeId) {
        if (avoid.isEmpty()) {
            return false;
        }
        Long until = avoid.get(ledgeId);
        if (until == null) {
            return false;
        }
        if (until < System.currentTimeMillis()) {
            avoid.remove(ledgeId);
            return false;
        }
        return true;
    }

    public boolean isNavigating() {
        return navigating;
    }

    private void navigate(WorldView w, float dt) {
        Body b = f.body;
        PlatformSet ps = w.platforms;
        Ledge goal = ps.get(goalLedgeId);
        if (goal == null) {
            stopNavigation();
            f.setActivity(Activity.IDLE, 0.5f);
            return;
        }
        Ledge cur = ps.get(b.ledgeId);
        if (cur == null) {
            return;
        }
        if (cur.id.equals(goal.id)) {
            float tx = MathUtil.clamp(goalX, cur.x1 + 0.5f, cur.x2 - 0.5f);
            if (walkStep(w, tx, dt)) {
                navigating = false;
                running = false;
                b.vx = 0;
                Runnable a = arrive;
                arrive = null;
                commanded = false;
                if (a != null) {
                    a.run();
                } else {
                    f.setActivity(Activity.IDLE, 0.6f + RNG.nextFloat() * 2f);
                }
            }
            return;
        }
        if (path == null || step >= path.size() || !path.get(step).fromId().equals(cur.id)) {
            if (planBudget <= 0) {
                // auf die Wegplanung warten: stehen statt auf der Stelle laufen
                b.vx = 0;
                if (f.activity == Activity.WALK || f.activity == Activity.RUN) {
                    f.activity = Activity.IDLE;
                }
                return;
            }
            planBudget--;
            path = PlatformGraph.findPath(ps, cur, b.x, goal, goalX, spec(), 600, this::avoided);
            superPath = false;
            if (path == null) {
                // mit normalen Sprüngen unerreichbar: mit Super-Sprüngen (doppelt so hoch) versuchen
                path = PlatformGraph.findPath(ps, cur, b.x, goal, goalX, superSpec(), 600, this::avoided);
                superPath = path != null;
            }
            step = 0;
            stepTimer = 0;
            if (path == null || path.isEmpty()) {
                fails++;
                path = null;
                if (fails > 2) {
                    stopNavigation();
                    f.setActivity(Activity.LOOK, 1.2f);
                    if (RNG.nextInt(4) == 0) {
                        f.showEmote(Emote.QUESTION, 1.5f);
                    }
                }
                return;
            }
        }
        PlatformGraph.Step st = path.get(step);
        stepTimer += dt;
        if (stepTimer > 10) {
            path = null;
            fails++;
            return;
        }
        float launch = MathUtil.clamp(st.launchX(), cur.x1, cur.x2);
        if (!walkStep(w, launch, dt)) {
            return;
        }
        expectLedge = st.toId();
        switch (st.type()) {
            case JUMP -> jump(st.landX(), st.toY(), superPath || superNext);
            case DROP -> drop(cur, st);
            case CLIMB -> startClimb(st);
        }
        step++;
        stepTimer = 0;
    }

    private float speed() {
        MiniConfig cfg = Mini.cfg();
        float size = f.sizePercent(cfg) / 100f;
        float s = 24f * f.personality().speed * f.data.speed / 100f * cfg.globalSpeed / 100f;
        if (cfg.speedFollowsSize) {
            s *= (float) Math.sqrt(Math.max(0.2f, size));
        }
        if (f.data.mood < 20) {
            s *= 0.75f;
        }
        return running ? s * 1.9f : s;
    }

    /** Läuft auf der aktuellen Kante zu x. @return true wenn angekommen */
    private boolean walkStep(WorldView w, float tx, float dt) {
        Body b = f.body;
        // Ziele am Bildschirmrand: die Figur kann nur bis zur halben Breite an den Rand (sonst Laufen auf der Stelle)
        float half = b.w / 2f;
        tx = MathUtil.clamp(tx, half, Math.max(half, w.platforms.width - half));
        float dx = tx - b.x;
        float sp = speed();
        if (Math.abs(dx) <= Math.max(0.8f, sp * dt)) {
            b.x = tx;
            b.vx = 0;
            stuckX = Float.NaN;
            return true;
        }
        // Kommt die Figur nicht mehr voran (Rand, Hindernis), gilt das Ziel nach kurzer Zeit als erreicht
        if (Float.isNaN(stuckX) || Math.abs(b.x - stuckX) > 0.5f) {
            stuckX = b.x;
            stuckTime = 0;
        } else {
            stuckTime += dt;
            if (stuckTime > 0.6f) {
                stuckX = Float.NaN;
                b.vx = 0;
                return true;
            }
        }
        float dir = Math.signum(dx);
        // Ausblenden-Zonen nicht betreten
        if (w.platforms.overlapsBlocked(b.x + dir * (b.w / 2 + 2), b.y, b.w / 2, b.h)) {
            stopNavigation();
            b.vx = 0;
            f.setActivity(Activity.IDLE, 0.8f);
            return false;
        }
        b.vx = dir * sp;
        f.facing = (int) dir;
        Activity want = running ? Activity.RUN : Activity.WALK;
        if (f.activity != want) {
            f.activity = want;
            f.activityTime = 0;
            f.activityDuration = 99;
        }
        return false;
    }

    private void jump(float landX, float toY, boolean superJump) {
        Body b = f.body;
        float apex = 7 + b.h * 0.35f;
        if (superJump) {
            // Super-Sprung: Scheitelpunkt doppelt so hoch über dem Start wie ein normaler Sprung
            float rise = Math.max(0, b.y - toY) + apex;
            apex = 2 * rise - Math.max(0, b.y - toY);
            superNext = false;
            f.showEmote(Emote.STAR, 1.2f);
            Mini.particles.sparkle(f.sx(), f.sTop() + f.body.h, 6);
        }
        float[] v = Physics.jumpVelocity(b.x, b.y, landX, toY, apex);
        b.leaveGround();
        b.vx = v[0];
        b.vy = v[1];
        f.facing = landX < b.x - 0.5f ? -1 : (landX > b.x + 0.5f ? 1 : f.facing);
        f.setActivity(Activity.JUMP, 1);
        Mini.sounds.figure(f, "jump");
    }

    private void drop(Ledge cur, PlatformGraph.Step st) {
        Body b = f.body;
        boolean offEdge = !cur.containsX(st.landX());
        b.leaveGround();
        if (offEdge) {
            b.vx = Math.signum(st.landX() - b.x) * Math.max(30, speed());
            b.vy = -50;
        } else {
            b.dropThrough = cur.id;
            b.vx = 0;
            b.vy = -40;
        }
        f.setActivity(Activity.JUMP, 1);
    }

    private void startClimb(PlatformGraph.Step st) {
        Body b = f.body;
        b.leaveGround();
        b.climbing = true;
        b.x = st.launchX();
        b.vx = 0;
        b.vy = 0;
        climbTo = st.toId();
        climbLandX = st.landX();
        f.setActivity(Activity.CLIMB, 99);
    }

    private void climb(WorldView w, float dt) {
        Body b = f.body;
        Ledge t = w.platforms.get(climbTo);
        if (t == null) {
            b.climbing = false;
            b.vy = 0;
            f.setActivity(Activity.FALL, 1);
            return;
        }
        float size = f.sizePercent(Mini.cfg()) / 100f;
        float climbSpeed = 38f * (float) Math.sqrt(Math.max(0.3f, size)) * f.data.speed / 100f;
        if (f.activity == Activity.HANG) {
            b.vy = 0;
            b.y = t.y + b.h * 0.8f;
            if (f.activityTime >= f.activityDuration) {
                b.climbing = false;
                b.setGround(t);
                b.x = MathUtil.clamp(climbLandX, t.x1 + b.w / 2, t.x2 - b.w / 2);
                f.setActivity(Activity.IDLE, 0.3f);
            }
            return;
        }
        b.vy = -climbSpeed;
        b.vx = 0;
        if (b.y <= t.y + b.h * 0.8f) {
            f.setActivity(Activity.HANG, 0.35f);
            b.vy = 0;
        }
    }

    /** Hüpft (ohne Physik) auf einer Parabel zu einem Punkt - für Inventar-Arbeit und Spiele. */
    public void hopTo(float x, float y, float duration, Runnable done) {
        stopNavigation();
        Body b = f.body;
        hopping = true;
        hopFromX = b.x;
        hopFromY = b.y;
        hopToX = x;
        hopToY = y;
        hopT = 0;
        hopDur = Math.max(0.05f, duration);
        hopDone = done;
        b.held = true;
        b.climbing = false;
        b.leaveGround();
        f.facing = x < b.x ? -1 : (x > b.x ? 1 : 0);
        f.setActivity(Activity.JUMP, duration);
    }

    public boolean isHopping() {
        return hopping;
    }

    private void hop(float dt) {
        Body b = f.body;
        hopT += dt;
        float t = Math.min(1f, hopT / hopDur);
        b.x = MathUtil.lerp(hopFromX, hopToX, t);
        float arc = Math.max(6f, Math.abs(hopToX - hopFromX) * 0.3f) + Math.max(0, hopFromY - hopToY);
        b.y = MathUtil.lerp(hopFromY, hopToY, t) - (float) Math.sin(t * Math.PI) * arc * 0.5f;
        if (t >= 1f) {
            hopping = false;
            b.held = false;
            b.vx = 0;
            b.vy = 0;
            f.setActivity(Activity.WORK, 0.25f);
            Runnable r = hopDone;
            hopDone = null;
            if (r != null) {
                r.run();
            }
        }
    }

    /** Hüpfen abbrechen (Not-Stopp). */
    public void cancelHop() {
        if (hopping) {
            hopping = false;
            hopDone = null;
            f.body.held = false;
        }
    }

    // =================================================================== Reaktionen, Sprechen, XP

    /** Reaktion mit leicht zufälliger Verzögerung, damit nicht alle Figuren gleichzeitig reagieren. */
    public void react(String situation, Activity a, float duration, Emote e) {
        if (f.carried || f.activity == Activity.WORK || f.activity == Activity.POSE) {
            return;
        }
        reactDelay = RNG.nextFloat() * 0.7f;
        reactSituation = situation;
        reactActivity = a;
        reactDuration = duration;
        reactEmote = e;
    }

    private void talk(float dt, MiniConfig cfg) {
        talkTimer -= dt;
        if (talkTimer > 0) {
            return;
        }
        float freq = cfg.talkFrequency / 100f * f.data.talkFrequency / 100f * f.personality().talk;
        talkTimer = freq <= 0.01f ? 1e9f : (25 + RNG.nextFloat() * 45) / freq;
        if (freq <= 0.01f || f.viewer) {
            return;
        }
        var d = f.data;
        String situation = d.hunger > 70 ? "hungry" : d.mood < 25 ? "bored" : d.mood > 75 ? "happy" : "idle";
        say(situation);
    }

    /** Spruch aus den Sprüche-Dateien als Sprechblase. */
    public void say(String situation) {
        if (f.viewer || Mini.lines == null) {
            return;
        }
        String line = Mini.lines.get(f.personality(), situation);
        if (line != null) {
            sayText(line.replace("{name}", f.name()));
        }
    }

    /** Freien Text sprechen (eigene Figur). */
    public void sayText(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        long now = System.currentTimeMillis();
        long dur = 2500 + Math.min(4000, text.length() * 60L);
        f.bubbles.push(new BubbleQueue.Bubble(text, false, 0xFFFFFFFF, 0xFF202020, 0, dur, now), 2, now);
        Mini.sounds.figure(f, "talk");
    }
}
