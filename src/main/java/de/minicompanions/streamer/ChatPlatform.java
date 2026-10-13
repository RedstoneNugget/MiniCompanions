package de.minicompanions.streamer;

import java.util.Locale;

/**
 * Unterstützte Stream-Plattformen. Jede hat eine eigene lesende {@link ChatSource}, eine Markenfarbe
 * (für Namensschilder, wenn mehrere Plattformen gleichzeitig laufen) und ein Login-Präfix, damit
 * gleichnamige Zuschauer verschiedener Plattformen nicht verwechselt werden.
 */
public enum ChatPlatform {
    TWITCH("Twitch", 0xFF9146FF, ""),
    YOUTUBE("YouTube", 0xFFFF3030, "yt:"),
    KICK("Kick", 0xFF53FC18, "kick:"),
    TIKTOK("TikTok", 0xFF25F4EE, "tt:");

    private final String displayName;
    private final int color;
    private final String loginPrefix;

    ChatPlatform(String displayName, int color, String loginPrefix) {
        this.displayName = displayName;
        this.color = color;
        this.loginPrefix = loginPrefix;
    }

    public String displayName() {
        return displayName;
    }

    /** Markenfarbe (ARGB). */
    public int color() {
        return color;
    }

    public String loginPrefix() {
        return loginPrefix;
    }

    /** Eindeutiger Login-Schlüssel für Zuschauer dieser Plattform (Twitch ohne Präfix wie bisher). */
    public String login(String name) {
        return loginPrefix + name.toLowerCase(Locale.ROOT);
    }

    public boolean isAvailable() {
        return true;
    }

    /** Neue Verbindung für diese Plattform. */
    public ChatSource create() {
        return switch (this) {
            case TWITCH -> new TwitchChatSource();
            case YOUTUBE -> new YouTubeChatSource();
            case KICK -> new KickChatSource();
            case TIKTOK -> new TikTokChatSource();
        };
    }

    /** Plattform eines Login-Schlüssels (z. B. "kick:name" -> KICK). */
    public static ChatPlatform ofLogin(String login) {
        if (login != null) {
            for (ChatPlatform p : values()) {
                if (!p.loginPrefix.isEmpty() && login.startsWith(p.loginPrefix)) {
                    return p;
                }
            }
        }
        return TWITCH;
    }

    /** Login ohne Plattform-Präfix (für die Anzeige). */
    public static String plainLogin(String login) {
        ChatPlatform p = ofLogin(login);
        return login == null ? "" : login.substring(p.loginPrefix.length());
    }

    public static ChatPlatform parse(String s) {
        try {
            return valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            return TWITCH;
        }
    }
}
