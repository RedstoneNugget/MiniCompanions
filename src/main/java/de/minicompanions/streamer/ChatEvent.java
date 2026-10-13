package de.minicompanions.streamer;

/**
 * Ereignisse aus dem Stream-Chat. Alle Inhalte sind NICHT vertrauenswürdig und werden
 * erst im {@link StreamerPipeline} geprüft, gefiltert und gekürzt.
 */
public sealed interface ChatEvent {
    /** Eine normale Chat-Nachricht. */
    record Message(ChatPlatform platform, String login, String displayName, String text, ChatRoles roles,
                   Integer colorRgb, String messageId) implements ChatEvent {
    }

    /** Ein Moderator des Kanals hat einen Nutzer gebannt/getimeoutet (Twitch CLEARCHAT mit Nutzer). */
    record UserCleared(String login) implements ChatEvent {
    }

    /** Eine einzelne Nachricht wurde von der Kanal-Moderation gelöscht (Twitch CLEARMSG). */
    record MessageDeleted(String login, String messageId) implements ChatEvent {
    }

    /** Der ganze Chat wurde geleert (Twitch CLEARCHAT ohne Nutzer). */
    record ChatCleared() implements ChatEvent {
    }
}
