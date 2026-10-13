package de.minicompanions.events;

import de.minicompanions.Mini;
import de.minicompanions.ai.Activity;
import de.minicompanions.figure.BubbleQueue;
import de.minicompanions.figure.Emote;
import de.minicompanions.figure.Figure;
import de.minicompanions.streamer.MessageSanitizer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;

import java.util.Locale;

/**
 * Reaktionen auf den Minecraft-Chat (nur lesend): eigener Name, "gg", "lol", Todesnachrichten,
 * eigene Schlüsselwörter und kurze Sprechblasen mit Nachrichten anderer Spieler (abschaltbar).
 */
public final class McChatWatcher {
    private long lastBubble;
    private long lastReaction;

    public void register() {
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, time) -> {
            String name = sender == null ? null : sender.name();
            onMessage(message, name, false);
        });
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) {
                onMessage(message, null, true);
            }
        });
    }

    private void onMessage(Text message, String sender, boolean system) {
        if (Mini.configs == null || !Mini.cfg().enabled || !Mini.cfg().mcChatReactions) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        String self = mc.getSession() == null ? "" : mc.getSession().getUsername();
        String text = message.getString();
        String lower = text.toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();

        if (system && message.getContent() instanceof TranslatableTextContent tc && tc.getKey().startsWith("death.")) {
            if (now - lastReaction > 5000) {
                lastReaction = now;
                Mini.figures.reactAll("death_msg", Activity.SAD, 2f, Emote.TEAR, 1);
            }
            return;
        }
        boolean fromSelf = sender != null && sender.equalsIgnoreCase(self);
        if (!fromSelf && !self.isEmpty() && lower.contains(self.toLowerCase(Locale.ROOT)) && now - lastReaction > 3000) {
            lastReaction = now;
            Mini.figures.reactAll("ownname", Activity.LOOK, 1.5f, Emote.EXCLAMATION, 1);
        } else if (containsWord(lower, "gg") && now - lastReaction > 3000) {
            lastReaction = now;
            Mini.figures.reactAll("gg", Activity.CHEER, 1.5f, Emote.STAR, 1);
        } else if ((containsWord(lower, "lol") || containsWord(lower, "xd") || lower.contains("haha")) && now - lastReaction > 3000) {
            lastReaction = now;
            Mini.figures.reactAll("lol", Activity.TICKLED, 1.2f, null, 1);
        } else if (now - lastReaction > 5000) {
            for (String kw : Mini.cfg().chatKeywords) {
                if (kw != null && kw.length() >= 2 && containsWord(lower, kw.toLowerCase(Locale.ROOT))) {
                    lastReaction = now;
                    Mini.figures.reactAll("keyword", Activity.WAVE, 1.2f, null, 1);
                    break;
                }
            }
        }
        // Nachrichten anderer Spieler kurz in Sprechblasen zeigen
        if (!system && !fromSelf && sender != null && Mini.cfg().mcChatBubbles && now - lastBubble > 1500) {
            Figure f = Mini.figures.randomOnScreen(true);
            if (f != null) {
                lastBubble = now;
                String clean = MessageSanitizer.sanitize(sender + ": " + stripSender(text, sender), 90);
                f.bubbles.push(new BubbleQueue.Bubble(clean, false, 0xFFE8F4FF, 0xFF203040, 0xFF5A8AD0, 4000, now), 2, now);
            }
        }
    }

    private static String stripSender(String text, String sender) {
        String prefix = "<" + sender + "> ";
        return text.startsWith(prefix) ? text.substring(prefix.length()) : text;
    }

    private static boolean containsWord(String text, String word) {
        int i = text.indexOf(word);
        while (i >= 0) {
            boolean startOk = i == 0 || !Character.isLetterOrDigit(text.charAt(i - 1));
            int end = i + word.length();
            boolean endOk = end >= text.length() || !Character.isLetterOrDigit(text.charAt(end));
            if (startOk && endOk) {
                return true;
            }
            i = text.indexOf(word, i + 1);
        }
        return false;
    }
}
