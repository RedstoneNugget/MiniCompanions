package de.minicompanions.figure;

import de.minicompanions.ai.Activity;
import de.minicompanions.ai.Brain;
import de.minicompanions.ai.Personality;
import de.minicompanions.config.FigureData;
import de.minicompanions.config.MiniConfig;
import de.minicompanions.physics.Body;
import de.minicompanions.skin.SkinManager;
import de.minicompanions.streamer.ChatRoles;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Eine Figur zur Laufzeit. Eigene Figuren verweisen auf gespeicherte {@link FigureData};
 * Zuschauer-Figuren haben eine flüchtige FigureData, die nie gespeichert wird.
 */
public final class Figure {
    /** Basis-Höhe einer Figur bei 100 % in GUI-Pixeln (32 Skin-Pixel * 0,5). */
    public static final float BASE_UNIT = 0.5f;
    private static final Random RNG = new Random();

    public final FigureData data;
    public final boolean viewer;
    public final Body body = new Body();
    public final Brain brain;
    public final BubbleQueue bubbles = new BubbleQueue();
    public final Pose pose = new Pose();

    // --- Zuschauer ---
    public String viewerLogin;
    public String viewerDisplay;
    public String viewerMcName;
    public ChatRoles viewerRoles = ChatRoles.NONE;
    public Integer viewerColor;
    /** Plattform des Zuschauers (Twitch, Kick, YouTube, TikTok). */
    public de.minicompanions.streamer.ChatPlatform viewerPlatform = de.minicompanions.streamer.ChatPlatform.TWITCH;
    public long spawnedAt = System.currentTimeMillis();

    // --- Zustand ---
    public Activity activity = Activity.SPAWN;
    public float activityTime;
    public float activityDuration = 0.6f;
    /** -1 links, +1 rechts, 0 nach vorne. */
    public int facing;
    public float animTime;
    public Emote emote;
    public float emoteTime;
    public SkinManager.Skin skin;
    private String skinKey = "";
    /** Kurzfristiger Größen-Effekt (Wachsen/Schrumpfen), wird weich angenähert. */
    public float effectScale = 1f;
    public float effectScaleCur = 1f;
    public float effectTimer;
    public final float variation = 0.88f + RNG.nextFloat() * 0.24f;
    public boolean selected;
    /** Lebt im Zimmer-Fenster (Koordinaten dann relativ zum Zimmer-Inhalt). */
    public boolean inRoom;
    /** Wird getragen oder geworfen (Maus). */
    public boolean carried;
    public float throwSpin;
    /** Reitet auf dem Mauszeiger / einer anderen Figur / einer Mini-Lore. */
    public boolean rideCursor;
    public Figure rideOn;
    public boolean minecart;
    /** Für Inventar-Aufgaben sichtbar getragenes Item. */
    public ItemStack carriedItem = ItemStack.EMPTY;
    /** Zuletzt gezeigte Zuschauer-Nachricht (nur solange die Figur existiert, für die Moderationsliste). */
    public String lastViewerMessage = "";
    public boolean hiddenMessages;
    // Haustier
    public float petX;
    public float petY;
    public float petVy;
    public boolean petOnShoulder;
    public float petAnim;
    // Sounds
    public float soundCooldown;
    /** Unsichtbar wegen "Verstecken" (halb durchsichtig gezeichnet). */
    public boolean hiding;
    public final List<String> tempNotes = new ArrayList<>(0);

    public Figure(FigureData data, boolean viewer) {
        this.data = data;
        this.viewer = viewer;
        this.brain = new Brain(this);
        this.inRoom = !viewer && data.inRoom;
        this.facing = RNG.nextBoolean() ? 1 : -1;
    }

    public String id() {
        return data.id;
    }

    public String name() {
        return viewer ? viewerDisplay : data.name;
    }

    public Personality personality() {
        return Personality.parse(data.personality);
    }

    /** Effektive Größe in Prozent (pro Figur hat Vorrang vor global). */
    public float sizePercent(MiniConfig cfg) {
        float base = data.sizePercent != null ? data.sizePercent : (viewer ? cfg.viewerDefaultSize : cfg.globalSize);
        if ("GIANT".equals(cfg.sizeMode)) {
            base *= 2f;
        } else if ("MINI".equals(cfg.sizeMode)) {
            base *= 0.5f;
        }
        if (cfg.randomSizeVariation) {
            base *= variation;
        }
        base *= effectScaleCur;
        return Math.max(cfg.minSize, Math.min(cfg.maxSize, base));
    }

    /**
     * Größe eines Skin-Pixels in GUI-Pixeln. Mit Pixel-Snapping wird auf ganze Bildschirm-Pixel gerundet,
     * damit die Pixel-Grafik scharf bleibt.
     */
    public float unit(MiniConfig cfg, double guiScale) {
        float u = BASE_UNIT * sizePercent(cfg) / 100f;
        if (cfg.pixelSnap && guiScale > 0) {
            // Erst ab 2 Bildschirm-Pixeln pro Skin-Pixel runden: dort ist die Abweichung klein (max. 25 %),
            // darunter würde das Runden die gewünschte Größe zu stark verfälschen. Die Textur wird ohnehin
            // mit Nearest-Neighbor gezeichnet (nie verwaschen).
            float px = (float) (u * guiScale);
            if (px >= 2f) {
                u = Math.round(px) / (float) guiScale;
            }
        }
        return u;
    }

    /** Größe des Körpers an die aktuelle Einheit anpassen. */
    public void applySize(float unit) {
        body.w = Math.max(2f, 8f * unit);
        body.h = Math.max(4f, 32f * unit);
    }

    public void setActivity(Activity a, float duration) {
        activity = a;
        activityTime = 0;
        activityDuration = duration;
    }

    public void showEmote(Emote e, float seconds) {
        emote = e;
        emoteTime = seconds;
    }

    /** Skin aktualisieren, falls sich die Quelle geändert hat. */
    public void refreshSkin(SkinManager skins) {
        FigureEnums.SkinType t = FigureEnums.parse(FigureEnums.SkinType.class, data.skinType, FigureEnums.SkinType.DEFAULT);
        String k = SkinManager.key(t, data.skinValue == null ? "" : data.skinValue.strip());
        if (skin == null || !k.equals(skinKey)) {
            skinKey = k;
            skin = skins.get(t, data.skinValue);
        }
    }

    /** Alex-Modell? (manuelle Einstellung oder automatisch erkannt) */
    public boolean slim() {
        FigureEnums.Model m = FigureEnums.parse(FigureEnums.Model.class, data.model, FigureEnums.Model.AUTO);
        if (m == FigureEnums.Model.SLIM) {
            return true;
        }
        if (m == FigureEnums.Model.WIDE) {
            return false;
        }
        return skin != null && skin.slim;
    }

    /** Größen-Effekt starten (z. B. nach dem Essen wachsen). */
    public void pulseSize(float factor, float seconds) {
        effectScale = factor;
        effectTimer = seconds;
    }

    /** Ursprung des Zimmer-Inhalts auf dem Bildschirm (wird pro Frame vom FigureManager gesetzt). */
    public static float roomOriginX;
    public static float roomOriginY;

    /** X-Position auf dem Bildschirm (auch für Figuren im Zimmer). */
    public float sx() {
        return inRoom ? body.x + roomOriginX : body.x;
    }

    /** Kopf-Oberkante auf dem Bildschirm. */
    public float sTop() {
        return (inRoom ? roomOriginY : 0) + body.y - body.h;
    }

    /** Kopfhöhe (oberes Ende) in Weltkoordinaten. */
    public float top() {
        return body.y - body.h;
    }

    public boolean hit(double mx, double my) {
        // etwas großzügiger als die Figur selbst, damit auch kleine Figuren leicht zu greifen sind
        float half = Math.max(body.w * 0.9f, 5f);
        return mx >= body.x - half && mx <= body.x + half && my >= body.y - body.h - 3 && my <= body.y + 2;
    }
}
