package de.minicompanions.ai;

/**
 * Persönlichkeiten beeinflussen Tempo, Gesprächigkeit, Aktivitäten und Sprüche.
 */
public enum Personality {
    //            speed  talk  energyUse brave  social chaos  work
    HAPPY(1.05f, 1.3f, 1.0f, 0.6f, 1.3f, 0.3f, 0.6f),
    LAZY(0.65f, 0.7f, 1.6f, 0.5f, 0.7f, 0.1f, 0.2f),
    CURIOUS(1.0f, 1.1f, 1.0f, 0.7f, 1.0f, 0.4f, 0.5f),
    SHY(0.85f, 0.5f, 1.0f, 0.2f, 0.4f, 0.1f, 0.6f),
    BRAVE(1.15f, 1.0f, 0.9f, 1.0f, 1.0f, 0.4f, 0.7f),
    CHAOTIC(1.3f, 1.2f, 1.2f, 0.9f, 1.1f, 1.0f, 0.3f),
    DILIGENT(1.0f, 0.8f, 0.8f, 0.6f, 0.8f, 0.1f, 1.0f);

    public final float speed;
    public final float talk;
    public final float energyUse;
    public final float brave;
    public final float social;
    public final float chaos;
    public final float work;

    Personality(float speed, float talk, float energyUse, float brave, float social, float chaos, float work) {
        this.speed = speed;
        this.talk = talk;
        this.energyUse = energyUse;
        this.brave = brave;
        this.social = social;
        this.chaos = chaos;
        this.work = work;
    }

    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public static Personality parse(String s) {
        try {
            return valueOf(s);
        } catch (RuntimeException e) {
            return HAPPY;
        }
    }

    public static Personality random(java.util.Random r) {
        Personality[] v = values();
        return v[r.nextInt(v.length)];
    }
}
