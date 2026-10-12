package com.spears;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** Saved to config/spears.json. Edit in-game with O, or by hand. */
public class SpearsConfig {
    public static boolean legitMode = true;   // ON = swap-hits/combos disabled
    public static int stunDelay = 3;          // ticks between axe hit and mace hit (1-5)
    public static int holdTicks = 5;          // ticks to hold a select key for its action (3-10)
    public static int spearMaceDelay = 1;     // ticks between spear hit and mace hit (0-4)
    public static int windMaceDelay = 4;      // ticks between wind charge and mace select (1-8)
    public static int minFood = 6;            // lunge swap pauses at/below this hunger (0 = never)

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("spears.json"); }

    private static class Data { boolean legitMode = true; int stunDelay = 3, holdTicks = 5, spearMaceDelay = 1, windMaceDelay = 4, minFood = 6; }

    public static void load() {
        try {
            if (!Files.exists(file())) { save(); return; }
            Data d = GSON.fromJson(Files.readString(file()), Data.class);
            legitMode = d.legitMode; stunDelay = clamp(d.stunDelay, 1, 5); holdTicks = clamp(d.holdTicks, 3, 10);
            spearMaceDelay = clamp(d.spearMaceDelay, 0, 4); windMaceDelay = clamp(d.windMaceDelay, 1, 8);
            minFood = clamp(d.minFood, 0, 20);
        } catch (Exception ignored) {}
    }

    public static void save() {
        try {
            Data d = new Data();
            d.legitMode = legitMode; d.stunDelay = stunDelay; d.holdTicks = holdTicks;
            d.spearMaceDelay = spearMaceDelay; d.windMaceDelay = windMaceDelay; d.minFood = minFood;
            Files.writeString(file(), GSON.toJson(d));
        } catch (Exception ignored) {}
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
