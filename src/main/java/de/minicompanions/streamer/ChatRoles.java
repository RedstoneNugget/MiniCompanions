package de.minicompanions.streamer;

/**
 * Rollen eines Stream-Chat-Autors. Twitch-Auswertung der Badges nach dem Vorbild von
 * "Take Your Stream Chat" (ijustseen, MIT) - angepasst und um die IRC-Tags mod/subscriber/vip ergänzt.
 */
public record ChatRoles(boolean subscriber, boolean vip, boolean moderator, boolean broadcaster) {
    public static final ChatRoles NONE = new ChatRoles(false, false, false, false);

    /** Rollen-Filter für den Spawn-Befehl. */
    public enum Filter { ALL, SUBS, VIPS, MODS, SUBS_VIPS_MODS }

    public boolean passes(Filter filter) {
        if (filter == null || filter == Filter.ALL || broadcaster) {
            return true;
        }
        return switch (filter) {
            case SUBS -> subscriber;
            case VIPS -> vip;
            case MODS -> moderator;
            case SUBS_VIPS_MODS -> subscriber || vip || moderator;
            default -> true;
        };
    }

    public boolean isRegular() {
        return !subscriber && !vip && !moderator && !broadcaster;
    }

    /** Twitch IRC: Tag "badges" (z. B. "subscriber/12,vip/1") plus Einzel-Tags. */
    public static ChatRoles fromTwitch(String badges, String modTag, String subTag, String vipTag) {
        boolean sub = "1".equals(subTag);
        boolean vip = vipTag != null && !vipTag.isEmpty() && !"0".equals(vipTag);
        boolean mod = "1".equals(modTag);
        boolean bc = false;
        if (badges != null && !badges.isBlank()) {
            for (String part : badges.split(",")) {
                int slash = part.indexOf('/');
                String name = (slash >= 0 ? part.substring(0, slash) : part).trim().toLowerCase(java.util.Locale.ROOT);
                switch (name) {
                    case "subscriber", "founder" -> sub = true;
                    case "vip" -> vip = true;
                    case "moderator" -> mod = true;
                    case "broadcaster" -> bc = true;
                    default -> {
                    }
                }
            }
        }
        return new ChatRoles(sub, vip, mod, bc);
    }
}
