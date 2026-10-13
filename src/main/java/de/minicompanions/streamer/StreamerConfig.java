package de.minicompanions.streamer;

/**
 * Einstellungen des Streamer-Moduls (gespeichert in config/minicompanions/streamer.json).
 * Standard nach Neuinstallation: Streamer-Modus AUS.
 */
public final class StreamerConfig {
    // --- Hauptschalter ---
    public boolean streamerMode = false;
    public boolean autoEnableOnStart = false;
    /** Bei eingeschaltetem Streamer-Modus automatisch mit allen eingetragenen Plattformen verbinden. */
    public boolean autoConnect = true;
    public boolean showHudIcon = true;
    public boolean showCounter = true;

    // --- Verbindung (jede Plattform mit eigenem Kanal-/Benutzernamen, mehrere gleichzeitig möglich) ---
    /** Veraltet (früher eine einzige Plattform), wird nicht mehr benutzt. */
    public String platform = "TWITCH";
    /** Twitch-Kanal. */
    public String channel = "";
    public boolean twitchOn = true;
    /** Kick-Kanal (Name oder Link). */
    public String kickChannel = "";
    public boolean kickOn = true;
    /** YouTube-Kanal (@Handle, Kanal-ID oder Video-Link). */
    public String youtubeChannel = "";
    public boolean youtubeOn = true;
    /** TikTok-Benutzername. */
    public String tiktokChannel = "";
    public boolean tiktokOn = true;
    /** Optionaler Euler-Stream-Schlüssel für TikTok (mehr Kontingent); leer = kostenlose Stufe ohne Konto. */
    public String tiktokApiKey = "";
    /** Namen in der Plattform-Farbe: OFF, MULTI (nur wenn mehrere Plattformen aktiv sind), ALWAYS. */
    public String platformNameColors = "MULTI";

    // --- Befehl & Limits ---
    public int viewerLimit = 10;
    /** IGNORE = neue Befehle ignorieren, REPLACE_OLDEST = älteste Zuschauer-Figur ersetzen. */
    public String limitPolicy = "IGNORE";
    public String commandName = "skin";
    /** Präfix-Zeichen für den Spawn-Befehl: Standard {@code !skin <Name>}. */
    public String commandPrefixes = CommandParser.DEFAULT_PREFIXES;
    /** Dateiformat-Version für Umstellungen alter streamer.json-Dateien. */
    public int formatVersion;
    public int cooldownSeconds = 30;
    /** 0 = unbegrenzt. */
    public int lifetimeMinutes = 0;
    public boolean onePerViewer = true;
    public String roleFilter = "ALL";
    public boolean approvalMode = false;
    public boolean approvalForMessages = false;
    public boolean useWhitelist = false;
    public int maxSpawnsPerSecond = 2;
    public int spawnQueueMax = 20;
    /** Unbekannter Minecraft-Name: Standard-Skin statt gar keiner Figur. */
    public boolean unknownNameDefaultSkin = false;
    public boolean spawnGreeting = true;
    public boolean newViewerBubble = true;
    /** Zuschauer merken: schreibt jemand ohne Figur (z. B. nach einem Neustart), kommt seine Figur zurück. */
    public boolean rememberViewers = true;

    // --- Zuschauer-Nachrichten über den Figuren ---
    public boolean showMessages = true;
    public int messageSeconds = 6;
    public int maxChars = 100;
    /** 0 = jede neue Nachricht wird sofort gezeigt. */
    public int minBubbleIntervalSeconds = 0;
    public int bubbleOpacity = 92;
    public int bubbleScale = 100;
    public int bubbleColor = 0xFFFFFFFF;
    public int textColor = 0xFF202020;
    public boolean roleColors = true;
    /** 1 = eine neue Nachricht ersetzt sofort die alte Blase; größer = Nachrichten warten nacheinander. */
    public int bubbleQueueSize = 1;
    public boolean showSkinCommandBubble = false;
    public String censorMode = "CENSOR";
    public int maxMessagesPerSecond = 8;
    public boolean bubblesRegular = true;
    public boolean bubblesSubs = true;
    public boolean bubblesVips = true;
    public boolean bubblesMods = true;

    /** Werte in gültige Bereiche bringen (nach dem Laden einer evtl. manipulierten Datei). */
    public void validate() {
        viewerLimit = clamp(viewerLimit, 0, 500);
        cooldownSeconds = clamp(cooldownSeconds, 0, 3600);
        lifetimeMinutes = clamp(lifetimeMinutes, 0, 24 * 60);
        maxSpawnsPerSecond = clamp(maxSpawnsPerSecond, 1, 20);
        spawnQueueMax = clamp(spawnQueueMax, 1, 200);
        messageSeconds = clamp(messageSeconds, 1, 60);
        maxChars = clamp(maxChars, 10, 300);
        minBubbleIntervalSeconds = clamp(minBubbleIntervalSeconds, 0, 60);
        bubbleOpacity = clamp(bubbleOpacity, 10, 100);
        bubbleScale = clamp(bubbleScale, 50, 300);
        bubbleQueueSize = clamp(bubbleQueueSize, 1, 5);
        maxMessagesPerSecond = clamp(maxMessagesPerSecond, 1, 50);
        commandName = CommandParser.sanitizeCommandName(commandName);
        if (formatVersion < 2) {
            // Früherer Standard "/!" -> jetzt nur noch !skin
            if ("/!".equals(commandPrefixes)) {
                commandPrefixes = CommandParser.DEFAULT_PREFIXES;
            }
        }
        if (formatVersion < 3) {
            // Frühere Standards (2 s Mindestabstand, Warteschlange 3) ließen schnelle Nachrichten verschwinden
            // oder stark verzögert erscheinen. Jetzt ersetzt jede neue Nachricht sofort die alte Blase.
            if (minBubbleIntervalSeconds == 2) {
                minBubbleIntervalSeconds = 0;
            }
            if (bubbleQueueSize == 3) {
                bubbleQueueSize = 1;
            }
        }
        if (formatVersion < 4) {
            // Verbinden passiert jetzt automatisch, sobald der Streamer-Modus an ist
            autoConnect = true;
        }
        formatVersion = 4;
        kickChannel = cleanInput(kickChannel);
        youtubeChannel = cleanInput(youtubeChannel);
        tiktokChannel = cleanInput(tiktokChannel);
        tiktokApiKey = tiktokApiKey == null ? "" : tiktokApiKey.replaceAll("[^A-Za-z0-9_.-]", "");
        if (tiktokApiKey.length() > 200) {
            tiktokApiKey = tiktokApiKey.substring(0, 200);
        }
        if (!"OFF".equals(platformNameColors) && !"ALWAYS".equals(platformNameColors)) {
            platformNameColors = "MULTI";
        }
        if (commandPrefixes == null || commandPrefixes.isBlank()) {
            commandPrefixes = CommandParser.DEFAULT_PREFIXES;
        }
        commandPrefixes = commandPrefixes.replaceAll("[^/!#?.$%&*+~-]", "");
        if (commandPrefixes.isEmpty()) {
            commandPrefixes = CommandParser.DEFAULT_PREFIXES;
        }
        if (channel == null) {
            channel = "";
        }
        channel = channel.trim().replaceAll("[^A-Za-z0-9_]", "");
        if (channel.length() > 25) {
            channel = channel.substring(0, 25);
        }
        if (!"REPLACE_OLDEST".equals(limitPolicy)) {
            limitPolicy = "IGNORE";
        }
        if (!"DROP".equals(censorMode)) {
            censorMode = "CENSOR";
        }
        try {
            ChatRoles.Filter.valueOf(roleFilter);
        } catch (RuntimeException e) {
            roleFilter = "ALL";
        }
        platform = ChatPlatform.parse(platform == null ? "TWITCH" : platform).name();
    }

    /** Eingabefeld für Kanal/Link: nur sichtbare Zeichen ohne Leerzeichen, begrenzte Länge. */
    private static String cleanInput(String s) {
        if (s == null) {
            return "";
        }
        String c = s.strip().replaceAll("[^\\x21-\\x7E]", "");
        return c.length() > 120 ? c.substring(0, 120) : c;
    }

    /** Eingetragene und eingeschaltete Plattformen mit ihrem Kanal. */
    public java.util.Map<ChatPlatform, String> activePlatforms() {
        java.util.Map<ChatPlatform, String> m = new java.util.EnumMap<>(ChatPlatform.class);
        if (twitchOn && channel != null && !channel.isBlank()) {
            m.put(ChatPlatform.TWITCH, channel.strip());
        }
        if (kickOn && kickChannel != null && !kickChannel.isBlank()) {
            m.put(ChatPlatform.KICK, kickChannel.strip());
        }
        if (youtubeOn && youtubeChannel != null && !youtubeChannel.isBlank()) {
            m.put(ChatPlatform.YOUTUBE, youtubeChannel.strip());
        }
        if (tiktokOn && tiktokChannel != null && !tiktokChannel.isBlank()) {
            m.put(ChatPlatform.TIKTOK, tiktokChannel.strip());
        }
        return m;
    }

    public String channelOf(ChatPlatform p) {
        return switch (p) {
            case TWITCH -> channel;
            case KICK -> kickChannel;
            case YOUTUBE -> youtubeChannel;
            case TIKTOK -> tiktokChannel;
        };
    }

    public void setChannel(ChatPlatform p, String v) {
        switch (p) {
            case TWITCH -> channel = v == null ? "" : v.strip().replaceAll("[^A-Za-z0-9_]", "");
            case KICK -> kickChannel = cleanInput(v);
            case YOUTUBE -> youtubeChannel = cleanInput(v);
            case TIKTOK -> tiktokChannel = cleanInput(v);
        }
    }

    public boolean isOn(ChatPlatform p) {
        return switch (p) {
            case TWITCH -> twitchOn;
            case KICK -> kickOn;
            case YOUTUBE -> youtubeOn;
            case TIKTOK -> tiktokOn;
        };
    }

    public void setOn(ChatPlatform p, boolean on) {
        switch (p) {
            case TWITCH -> twitchOn = on;
            case KICK -> kickOn = on;
            case YOUTUBE -> youtubeOn = on;
            case TIKTOK -> tiktokOn = on;
        }
    }

    public ChatRoles.Filter roleFilter() {
        try {
            return ChatRoles.Filter.valueOf(roleFilter);
        } catch (RuntimeException e) {
            return ChatRoles.Filter.ALL;
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
