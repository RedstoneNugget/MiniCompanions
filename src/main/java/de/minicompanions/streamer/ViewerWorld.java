package de.minicompanions.streamer;

/**
 * Schnittstelle zwischen der (reinen) Streamer-Logik und den Figuren auf dem Bildschirm.
 * Die echte Umsetzung ist {@link ViewerFigureManager}; Tests verwenden eine einfache Attrappe.
 */
public interface ViewerWorld {
    boolean hasFigure(String login);

    /** Anzahl aktiver + gerade ladender Zuschauer-Figuren. */
    int viewerFigureCount();

    /** Startet das (asynchrone) Spawnen. Ersetzt eine vorhandene Figur desselben Zuschauers. */
    void spawn(SpawnQueue.Request request);

    void remove(String login);

    /** Entfernt die älteste Zuschauer-Figur. @return true wenn eine entfernt wurde. */
    boolean removeOldest();

    void removeAll();

    /** Sprechblase über der Figur des Zuschauers (Text ist bereits bereinigt). */
    void showMessage(String login, String text, ChatRoles roles, Integer colorRgb);

    void hideMessages(String login);

    void hideAllMessages();

    /** Entfernt alle Figuren, die den gegebenen Minecraft-Skin tragen. */
    void removeBySkin(String mcName);

    /** Hinweis für das Streamer-Fenster (z. B. "Limit erreicht"). */
    void notice(String key, Object... args);
}
