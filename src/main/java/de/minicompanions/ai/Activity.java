package de.minicompanions.ai;

/** Zustände der Figuren-Zustandsmaschine (jeder Zustand hat eine eigene Animation). */
public enum Activity {
    IDLE(true),
    WALK(true),
    RUN(true),
    JUMP(false),
    FALL(false),
    CLIMB(false),
    HANG(false),
    SIT(true),
    SIT_EDGE(true),
    SLEEP(true),
    DANCE(true),
    WAVE(true),
    CHEER(true),
    SCARED(true),
    SAD(true),
    EAT(true),
    TALK(true),
    FIGHT(true),
    HUG(true),
    CARRIED(false),
    THROWN(false),
    PETTED(true),
    TICKLED(true),
    WORK(true),
    HIDE(true),
    POSE(true),
    SPAWN(true),
    BOOST(true),
    RIDE(false),
    PLAY(true),
    YAWN(true),
    LOOK(true);

    /** Darf die KI aus diesem Zustand heraus frei einen neuen wählen? */
    public final boolean interruptible;

    Activity(boolean interruptible) {
        this.interruptible = interruptible;
    }
}
