package de.minicompanions.gui;

import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/** Ganzzahl-Schieberegler mit Schrittweite und Beschriftung "Name: Wert". */
public class IntSlider extends SliderWidget {
    private final int min;
    private final int max;
    private final int step;
    private final Text label;
    private final IntConsumer onChange;
    private final IntFunction<String> format;

    public IntSlider(int x, int y, int w, int h, Text label, int min, int max, int step, int value, IntFunction<String> format, IntConsumer onChange) {
        super(x, y, w, h, Text.empty(), toFraction(value, min, max));
        this.min = min;
        this.max = Math.max(min + 1, max);
        this.step = Math.max(1, step);
        this.label = label;
        this.onChange = onChange;
        this.format = format == null ? Integer::toString : format;
        updateMessage();
    }

    private static double toFraction(int v, int min, int max) {
        return Math.max(0, Math.min(1, (v - min) / (double) Math.max(1, max - min)));
    }

    public int intValue() {
        int v = (int) Math.round(min + value * (max - min));
        v = Math.round(v / (float) step) * step;
        return Math.max(min, Math.min(max, v));
    }

    public void setIntValue(int v) {
        value = toFraction(v, min, max);
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        setMessage(label.copy().append(": " + format.apply(intValue())));
    }

    @Override
    protected void applyValue() {
        onChange.accept(intValue());
    }
}
