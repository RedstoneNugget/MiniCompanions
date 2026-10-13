**Mini-Begleiter 1.0.1** für Minecraft 1.21.11 (Fabric, nur Client).

### Neu & behoben
- **Zuschauer werden gemerkt:** Ist die Figur eines Zuschauers nicht mehr da (z. B. nach einem Neustart des Spiels oder nachdem der Streamer-Modus aus war), kommt sie mit demselben Skin zurück, sobald er wieder schreibt, und sagt seine Nachricht. Abschaltbar unter „Befehl & Limits“ → „Zuschauer merken“. Gekickte und gesperrte Zuschauer werden vergessen.
- **Gleiche Nachricht später wieder erlaubt:** Der Spam-Schutz blockiert dieselbe Nachricht nur noch innerhalb von 30 Sekunden (vorher für immer).
- **Twitch:** Eine still abgerissene Verbindung wird nach ca. 90 Sekunden erkannt und neu aufgebaut (vorher erst nach 6 Minuten).
- Sprechblasen bleiben sichtbar, wenn eine Figur ganz oben am Bildschirmrand ist.

### Installation
1. [Fabric Loader](https://fabricmc.net/use/) für 1.21.11 und die [Fabric API](https://modrinth.com/mod/fabric-api) installieren
2. `minicompanions-1.0.1.jar` (unten bei „Assets“) in den `mods`-Ordner legen (alte Version löschen)
3. Im Spiel **K** drücken

**English:** Viewers are now remembered: when a viewer whose figure is gone (e.g. after a game restart) writes again, their figure comes back with the same skin and says the message. The same message is only treated as spam within 30 seconds, and dropped Twitch connections are detected after about 90 seconds instead of 6 minutes.
