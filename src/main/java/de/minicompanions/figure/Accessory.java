package de.minicompanions.figure;

/**
 * Accessoires (frei kombinierbar, alle sofort verfügbar). {@link #slot} verhindert, dass zwei Hüte gleichzeitig getragen werden.
 * Eigene PNG-Accessoires liegen in config/minicompanions/accessories/ und werden als "custom:<datei>" gespeichert.
 */
public enum Accessory {
    TOP_HAT(Slot.HEAD),
    PARTY_HAT(Slot.HEAD),
    CROWN(Slot.HEAD),
    CAP(Slot.HEAD),
    HEADPHONES(Slot.HEAD),
    GLASSES(Slot.FACE),
    SUNGLASSES(Slot.FACE),
    CAPE(Slot.BACK),
    BACKPACK(Slot.BACK),
    SCARF(Slot.NECK),
    BOW_TIE(Slot.NECK);

    public enum Slot { HEAD, FACE, BACK, NECK }

    public final Slot slot;

    Accessory(Slot slot) {
        this.slot = slot;
    }

    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public static Accessory parse(String s) {
        try {
            return valueOf(s);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
