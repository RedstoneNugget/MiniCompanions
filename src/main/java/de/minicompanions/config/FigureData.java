package de.minicompanions.config;

import de.minicompanions.ai.Personality;
import de.minicompanions.figure.FigureEnums;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gespeicherte Daten einer eigenen Figur (Zuschauer-Figuren werden nie gespeichert).
 */
public final class FigureData {
    public String id = UUID.randomUUID().toString();
    public String name = "Mini";
    public String skinType = FigureEnums.SkinType.DEFAULT.name();
    public String skinValue = "";
    public String model = FigureEnums.Model.AUTO.name();
    /** null = globale Größe verwenden; sonst Prozent (hat Vorrang). */
    public Integer sizePercent;
    public String personality = Personality.HAPPY.name();
    public boolean behavior = true;
    public int alpha = 100;
    public int speed = 100;
    public int volume = 100;
    public int talkFrequency = 100;
    /** Tonhöhe der Mini-Sounds in Prozent (50..200). */
    public int pitch = 100;
    public List<String> accessories = new ArrayList<>();
    public String pet = FigureEnums.PetType.NONE.name();
    public String colorFilter = FigureEnums.ColorFilter.NONE.name();
    public String infoBoard = FigureEnums.InfoBoard.NONE.name();
    public boolean showName = true;
    /** Rüstung dieser Figur: "" = wie in den Einstellungen, sonst NONE, PLAYER, LEATHER, IRON ... */
    public String armor = "";

    // --- Leben ---
    public float energy = 80;
    public float hunger = 20;
    public float mood = 70;
    /** Beziehungspunkte zu anderen Figuren (id -> Punkte, negativ = Rivale). */
    public Map<String, Float> relations = new HashMap<>();
    /** id des Partners (verheiratet) oder null. */
    public String partner;

    // --- Statistik / Bestenliste ---
    public int collected = 0;
    public int gamesWon = 0;
    public int tasksDone = 0;

    /** Figur wohnt im Zimmer-Fenster. */
    public boolean inRoom;


    public FigureData copy() {
        FigureData d = new FigureData();
        d.name = name;
        d.skinType = skinType;
        d.skinValue = skinValue;
        d.model = model;
        d.sizePercent = sizePercent;
        d.personality = personality;
        d.behavior = behavior;
        d.alpha = alpha;
        d.speed = speed;
        d.volume = volume;
        d.talkFrequency = talkFrequency;
        d.pitch = pitch;
        d.accessories = new ArrayList<>(accessories);
        d.pet = pet;
        d.colorFilter = colorFilter;
        d.infoBoard = infoBoard;
        d.showName = showName;
        d.inRoom = inRoom;
        d.armor = armor;
        return d;
    }

    public void validate() {
        if (armor == null || !armor.isEmpty() && !de.minicompanions.figure.ArmorMode.isValid(armor)) {
            armor = "";
        }
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        if (name == null || name.isBlank()) {
            name = "Mini";
        }
        name = name.replaceAll("[\\p{Cntrl}§]", "");
        if (name.length() > 24) {
            name = name.substring(0, 24);
        }
        if (skinValue == null) {
            skinValue = "";
        }
        skinType = FigureEnums.parse(FigureEnums.SkinType.class, skinType, FigureEnums.SkinType.DEFAULT).name();
        model = FigureEnums.parse(FigureEnums.Model.class, model, FigureEnums.Model.AUTO).name();
        personality = Personality.parse(personality).name();
        pet = FigureEnums.parse(FigureEnums.PetType.class, pet, FigureEnums.PetType.NONE).name();
        colorFilter = FigureEnums.parse(FigureEnums.ColorFilter.class, colorFilter, FigureEnums.ColorFilter.NONE).name();
        infoBoard = FigureEnums.parse(FigureEnums.InfoBoard.class, infoBoard, FigureEnums.InfoBoard.NONE).name();
        if (sizePercent != null) {
            sizePercent = Math.max(10, Math.min(1000, sizePercent));
        }
        alpha = clamp(alpha, 10, 100);
        speed = clamp(speed, 10, 400);
        volume = clamp(volume, 0, 200);
        talkFrequency = clamp(talkFrequency, 0, 400);
        pitch = clamp(pitch, 50, 200);
        energy = Math.max(0, Math.min(100, energy));
        hunger = Math.max(0, Math.min(100, hunger));
        mood = Math.max(0, Math.min(100, mood));
        if (accessories == null) {
            accessories = new ArrayList<>();
        }
        if (relations == null) {
            relations = new HashMap<>();
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
