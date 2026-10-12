package com.spears;

import java.util.function.IntSupplier;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class SpearsScreen extends Screen {
    public SpearsScreen() { super(Text.literal("Spears Settings")); }

    private void cycle(int x, int y, String name, int lo, int hi, IntSupplier get, IntConsumer set) {
        addDrawableChild(ButtonWidget.builder(Text.literal(name + ": " + get.getAsInt()), b -> {
            int v = get.getAsInt() + 1;
            set.accept(v > hi ? lo : v);
            SpearsConfig.save();
            b.setMessage(Text.literal(name + ": " + get.getAsInt()));
        }).dimensions(x, y, 200, 20).build());
    }

    @Override
    protected void init() {
        int x = width / 2 - 100, y = height / 6;
        addDrawableChild(ButtonWidget.builder(legitLabel(), b -> {
            SpearsConfig.legitMode = !SpearsConfig.legitMode;
            SpearsConfig.save();
            b.setMessage(legitLabel());
        }).dimensions(x, y, 200, 20).build());
        cycle(x, y + 24,  "Stun slam delay (ticks)",   1, 5,  () -> SpearsConfig.stunDelay,      v -> SpearsConfig.stunDelay = v);
        cycle(x, y + 48,  "Spear > Mace delay (ticks)", 0, 4,  () -> SpearsConfig.spearMaceDelay, v -> SpearsConfig.spearMaceDelay = v);
        cycle(x, y + 72,  "Wind > Mace delay (ticks)",  1, 8,  () -> SpearsConfig.windMaceDelay,  v -> SpearsConfig.windMaceDelay = v);
        cycle(x, y + 96,  "Hold time (ticks)",          3, 10, () -> SpearsConfig.holdTicks,      v -> SpearsConfig.holdTicks = v);
        cycle(x, y + 120, "Lunge pauses at hunger <=",  0, 20, () -> SpearsConfig.minFood,        v -> SpearsConfig.minFood = v);
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
            .dimensions(x, y + 150, 200, 20).build());
    }

    private static Text legitLabel() {
        return Text.literal("Legit mode: " + (SpearsConfig.legitMode ? "ON (macros off)" : "OFF (macros on)"));
    }
}
