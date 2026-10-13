package de.minicompanions.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Globale Einstellungen (config/minicompanions/config.json). Jedes Feature ist einzeln abschaltbar.
 */
public final class MiniConfig {
    // --- Allgemein ---
    public boolean enabled = true;
    /** 0 = kein Limit. Zuschauer-Figuren zählen nicht mit. */
    public int maxFigures = 0;
    public String language = "auto";

    // --- Größe ---
    public int globalSize = 100;
    public int minSize = 25;
    public int maxSize = 500;
    public int viewerDefaultSize = 100;
    /** Ganzzahlige Pixel-Skalierung (scharfe Darstellung). */
    public boolean pixelSnap = true;
    public boolean speedFollowsSize = true;
    public boolean randomSizeVariation = false;
    public boolean growEffects = true;
    /** NORMAL, GIANT, MINI (per Hotkey umschaltbar). */
    public String sizeMode = "NORMAL";

    // --- Darstellung ---
    public int globalAlpha = 100;
    public int globalSpeed = 100;
    public int talkFrequency = 100;
    public boolean nameTags = true;
    public boolean emotes = true;
    public boolean shadows = true;
    public boolean particles = true;
    /** Item des Spielers in der Hand der Figuren zeigen. */
    public boolean equipmentMirror = true;
    /** Rüstung aller Figuren (siehe ArmorMode); null = aus alter Config übernehmen. */
    public String armorMode;
    public boolean accessories = true;
    public boolean pets = true;
    public boolean vehicles = true;
    public boolean colorFilters = true;
    public int bubbleScale = 100;

    // --- Ton ---
    public boolean sounds = false;
    public int soundVolume = 40;

    // --- Bewegungswelt (pro Screen-Typ) ---
    public boolean walkGround = true;
    public boolean walkHud = true;
    public boolean walkInventory = true;
    public boolean walkChat = true;
    public boolean walkOtherScreens = true;
    /** Figuren auf dem Bildschirm können auf der Titelleiste des Zimmer-Fensters stehen. */
    public boolean walkRoomWindow = true;

    // --- Zimmer-Fenster (frei verschiebbar und skalierbar, Position wird gespeichert) ---
    public boolean roomOpen = false;
    public boolean roomMinimized = false;
    public int roomX = 20;
    public int roomY = 20;
    public int roomW = 240;
    public int roomH = 150;
    public boolean figureClicks = true;
    public boolean throwing = true;
    public boolean stacking = true;
    public List<int[]> hideZones = new ArrayList<>();

    // --- Verhalten ---
    public boolean behaviorEnabled = true;
    public boolean interactions = true;
    public boolean fighting = true;
    public boolean needs = true;
    public boolean relationships = true;
    public boolean restMode = false;

    // --- Reaktionen auf das Spiel (nur Client-Daten) ---
    public boolean reactGameEvents = true;
    public boolean reactWeather = true;
    public boolean reactBiome = true;
    /** Figuren tragen nachts eine Laterne. */
    public boolean nightLantern = true;
    public boolean reactCombat = true;
    public boolean dangerWarnings = true;
    public boolean rareItemCheers = true;
    public boolean spectatorComments = true;

    // --- Minecraft-Chat ---
    public boolean mcChatReactions = true;
    public boolean mcChatBubbles = true;
    public List<String> chatKeywords = new ArrayList<>(List.of("gg", "lol", "xd", "hi", "hallo"));

    // --- Inventar-Aktionen ---
    public boolean inventoryActions = true;
    public boolean inventoryOffInMultiplayer = true;
    public int inventoryClickDelayMs = 180;
    public int inventoryJitterMs = 70;
    public boolean inventoryVisualize = true;

    // --- Aufnahme / Komfort ---
    public boolean hideInScreenshots = false;
    public boolean overlayHidden = false;
    public boolean windowsVisible = true;
    public boolean debugOverlay = false;
    public boolean panicNeedsCtrl = true;
    public boolean hudStatusIcon = true;

    // --- Fortschritt ---

    public void validate() {
        if (armorMode == null) {
            // frühere Einstellung "Ausrüstung spiegeln" übernehmen
            armorMode = equipmentMirror ? "PLAYER" : "NONE";
        }
        if (!de.minicompanions.figure.ArmorMode.isValid(armorMode)) {
            armorMode = "PLAYER";
        }
        maxFigures = clamp(maxFigures, 0, 100000);
        minSize = clamp(minSize, 10, 100);
        maxSize = clamp(maxSize, 100, 1000);
        globalSize = clamp(globalSize, minSize, maxSize);
        viewerDefaultSize = clamp(viewerDefaultSize, minSize, maxSize);
        globalAlpha = clamp(globalAlpha, 10, 100);
        globalSpeed = clamp(globalSpeed, 10, 400);
        talkFrequency = clamp(talkFrequency, 0, 400);
        soundVolume = clamp(soundVolume, 0, 100);
        bubbleScale = clamp(bubbleScale, 50, 300);
        inventoryClickDelayMs = clamp(inventoryClickDelayMs, 60, 2000);
        inventoryJitterMs = clamp(inventoryJitterMs, 0, 1000);
        if (!"GIANT".equals(sizeMode) && !"MINI".equals(sizeMode)) {
            sizeMode = "NORMAL";
        }
        roomW = clamp(roomW, 120, 4000);
        roomH = clamp(roomH, 80, 4000);
        roomX = clamp(roomX, -100, 8000);
        roomY = clamp(roomY, -100, 8000);
        if (hideZones == null) {
            hideZones = new ArrayList<>();
        }
        hideZones.removeIf(z -> z == null || z.length != 4);
        if (chatKeywords == null) {
            chatKeywords = new ArrayList<>();
        }
        if (language == null) {
            language = "auto";
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
