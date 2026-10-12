package com.spears;

import java.util.function.Predicate;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.text.Text;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import org.lwjgl.glfw.GLFW;

/** Spears: one key per item. Press it and the hotbar jumps to that item instantly (same tick, no delay). */
public class SpearsClient implements ClientModInitializer {
    public static final String ID = "spears";
    private static final KeyBinding.Category CATEGORY = KeyBinding.Category.create(Identifier.of(ID, "main"));

    // "Stamina food" = golden apples by default. Edit this predicate to change it.
    private static final Predicate<ItemStack> STAMINA_FOOD =
        s -> s.isOf(Items.GOLDEN_APPLE) || s.isOf(Items.ENCHANTED_GOLDEN_APPLE);
    private static final Predicate<ItemStack> FOOD =
        s -> s.contains(DataComponentTypes.FOOD) && !STAMINA_FOOD.test(s);

    private static final class Bind {
        final KeyBinding key; final Predicate<ItemStack> match;
        java.util.function.Consumer<MinecraftClient> holdAction; boolean repeat; int held;
        Bind(KeyBinding k, Predicate<ItemStack> m) { key = k; match = m; }
        KeyBinding key() { return key; } Predicate<ItemStack> match() { return match; }
    }
    private record SwapHit(KeyBinding key, Predicate<ItemStack> match) {}
    // ---- Combos (timed multi-step swap hits) ----
    // kind: 0 = hit target, 1 = use item (e.g. throw wind charge) then swap back, 2 = just select and stay
    private record Step(Predicate<ItemStack> match, int delayTicks, int kind) {
        Step(Predicate<ItemStack> match, int delayTicks) { this(match, delayTicks, 0); }
    }
    private KeyBinding axeSpearMaceKey, windMaceKey, settingsKey;
    private KeyBinding stunKey, spearMaceKey, stunDelayKey;
    // ---- Training tracker (passive: observes your own clicks, never acts for you) ----
    private static int hits, misses;
    private static final java.util.Map<String, Integer> missReasons = new java.util.LinkedHashMap<>();
    private KeyBinding statsKey, resetStatsKey;
    private KeyBinding lungeKey, legitKey;
    private final java.util.ArrayDeque<Step> queue = new java.util.ArrayDeque<>();
    private Entity comboTarget;
    private int countdown;
    private final java.util.List<SwapHit> swapHits = new java.util.ArrayList<>();
    private final java.util.List<Bind> binds = new java.util.ArrayList<>();

    private Bind add(String name, InputUtil.Type type, int code, Predicate<ItemStack> match) {
        KeyBinding k = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.spears." + name, type, code, CATEGORY));
        Bind b = new Bind(k, match);
        binds.add(b);
        return b;
    }

    @Override
    public void onInitializeClient() {
        InputUtil.Type K = InputUtil.Type.KEYSYM, M = InputUtil.Type.MOUSE;
        add("sword",        K, GLFW.GLFW_KEY_Z, s -> s.isIn(ItemTags.SWORDS));
        Bind axe = add("axe", K, GLFW.GLFW_KEY_X, s -> s.isIn(ItemTags.AXES));
        Bind wind = add("wind_charge", K, GLFW.GLFW_KEY_E, s -> s.isOf(Items.WIND_CHARGE));
        Bind mace = add("mace", K, GLFW.GLFW_KEY_R, s -> s.isOf(Items.MACE));
        add("shield",       K, GLFW.GLFW_KEY_C, s -> s.isOf(Items.SHIELD));
        add("ender_pearl",  K, GLFW.GLFW_KEY_V, s -> s.isOf(Items.ENDER_PEARL));
        add("food",         K, GLFW.GLFW_KEY_Q, FOOD);
        Bind spear = add("spear", M, GLFW.GLFW_MOUSE_BUTTON_4, s -> s.isIn(ItemTags.SPEARS));
        // Fit to your binds: tap = select, HOLD = action (needs legit mode OFF, press K).
        spearBind = spear;
        spear.holdAction = mc -> lungeSwap(mc); spear.repeat = true;               // hold Mouse4: lunge swap
        wind.holdAction  = mc -> { if (queue.isEmpty()) startWindMace(mc); };      // hold E: wind charge > mace
        mace.holdAction  = mc -> swapHit(mc, st -> st.isOf(Items.MACE));           // hold R: mace swap-hit
        axe.holdAction   = mc -> { if (queue.isEmpty()) startCombo(mc,             // hold X: stun slam
            new Step(st -> st.isIn(ItemTags.AXES), 0), new Step(st -> st.isOf(Items.MACE), SpearsConfig.stunDelay)); };
        add("stamina_food", M, GLFW.GLFW_MOUSE_BUTTON_5, STAMINA_FOOD);

        // Swap-hit: ONE press = switch to item, hit the targeted entity, switch back, all in one tick.
        // The server only refreshes attributes once per tick, so the hit uses the swapped item's
        // enchants/attributes with no cooldown wait. (Many PvP servers treat this as a macro.)
        swapHits.add(new SwapHit(KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.spears.swaphit_spear", K, GLFW.GLFW_KEY_G, CATEGORY)), s -> s.isIn(ItemTags.SPEARS)));
        swapHits.add(new SwapHit(KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.spears.swaphit_mace", K, GLFW.GLFW_KEY_H, CATEGORY)), s -> s.isOf(Items.MACE)));

        stunKey = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.spears.stun_slam", K, GLFW.GLFW_KEY_B, CATEGORY));
        spearMaceKey = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.spears.spear_mace", K, GLFW.GLFW_KEY_J, CATEGORY));
        stunDelayKey = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.spears.stun_delay", K, GLFW.GLFW_KEY_N, CATEGORY));

        statsKey = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.spears.stats", K, GLFW.GLFW_KEY_L, CATEGORY));
        resetStatsKey = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.spears.reset_stats", K, GLFW.GLFW_KEY_U, CATEGORY));
        legitKey = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.spears.legit_mode", K, GLFW.GLFW_KEY_K, CATEGORY));

        lungeKey = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.spears.lunge_swap", K, GLFW.GLFW_KEY_Y, CATEGORY));

        axeSpearMaceKey = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.spears.axe_spear_mace", K, GLFW.GLFW_KEY_M, CATEGORY));
        windMaceKey = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.spears.wind_mace", K, GLFW.GLFW_KEY_P, CATEGORY));
        settingsKey = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.spears.settings", K, GLFW.GLFW_KEY_O, CATEGORY));
        SpearsConfig.load();

        // Start of tick = before the game handles clicks, so key + attack in the same tick works.
        ClientTickEvents.START_CLIENT_TICK.register(this::onTick);

        // Theme pack registration is OFF for now (no resource/texture packs). The files remain in resourcepacks/.
    }

    private void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        for (Bind b : binds) {
            if (b.holdAction == null) {
                boolean pressed = false;
                while (b.key().wasPressed()) pressed = true;
                if (pressed && mc.currentScreen == null) select(mc, b.match());
                continue;
            }
            // TAP = select the item. HOLD (0.25s+) = that item's swap action, if legit mode is off.
            while (b.key().wasPressed()) {}
            if (mc.currentScreen == null && b.key().isPressed()) {
                b.held++;
                if (!SpearsConfig.legitMode && (b.held == SpearsConfig.holdTicks || (b.repeat && b.held > SpearsConfig.holdTicks))) b.holdAction.accept(mc);
            } else {
                if (b.held > 0 && mc.currentScreen == null && (b.held < SpearsConfig.holdTicks || SpearsConfig.legitMode)) select(mc, b.match());
                b.held = 0;
            }
        }
        // key released (or legit mode on) while the spear is out -> go back to the wind charge
        if (lungePhase == 1 && (spearBind == null || spearBind.held < SpearsConfig.holdTicks || SpearsConfig.legitMode)) finishLunge(mc);
        while (statsKey.wasPressed()) printStats(mc);
        while (resetStatsKey.wasPressed()) {
            hits = 0; misses = 0; missReasons.clear();
            mc.player.sendMessage(Text.literal("Spears stats reset"), true);
        }
        while (legitKey.wasPressed()) {
            SpearsConfig.legitMode = !SpearsConfig.legitMode; SpearsConfig.save();
            if (SpearsConfig.legitMode) queue.clear();
            mc.player.sendMessage(Text.literal("Spears legit mode: " + (SpearsConfig.legitMode ? "ON (swap-hits/combos off)" : "OFF")), true);
        }
        if (SpearsConfig.legitMode) {
            // Drain macro keys so presses don't queue up; item-select keys below still work.
            while (stunKey.wasPressed()) {}
            while (spearMaceKey.wasPressed()) {}
            while (axeSpearMaceKey.wasPressed()) {}
            while (windMaceKey.wasPressed()) {}
            for (SwapHit h : swapHits) while (h.key().wasPressed()) {}
        } else {
            tickCombo(mc);
        }
        while (settingsKey.wasPressed()) if (mc.currentScreen == null) mc.setScreen(new SpearsScreen());
        while (stunDelayKey.wasPressed()) {
            SpearsConfig.stunDelay = SpearsConfig.stunDelay >= 5 ? 1 : SpearsConfig.stunDelay + 1; SpearsConfig.save();
            mc.player.sendMessage(Text.literal("Stun slam delay: " + SpearsConfig.stunDelay + " ticks"), true);
        }
        if (!SpearsConfig.legitMode && mc.currentScreen == null && queue.isEmpty()) {
            while (stunKey.wasPressed())      startCombo(mc, new Step(s -> s.isIn(ItemTags.AXES), 0), new Step(s -> s.isOf(Items.MACE), SpearsConfig.stunDelay));
            while (axeSpearMaceKey.wasPressed()) startCombo(mc, new Step(x -> x.isIn(ItemTags.AXES), 0),
                new Step(x -> x.isIn(ItemTags.SPEARS), 1), new Step(x -> x.isOf(Items.MACE), SpearsConfig.spearMaceDelay));
            while (windMaceKey.wasPressed()) startWindMace(mc);
            while (spearMaceKey.wasPressed()) startCombo(mc, new Step(s -> s.isIn(ItemTags.SPEARS), 0), new Step(s -> s.isOf(Items.MACE), SpearsConfig.spearMaceDelay));
        }
        if (!SpearsConfig.legitMode) for (SwapHit h : swapHits) {
            boolean pressed = false;
            while (h.key().wasPressed()) pressed = true;
            if (pressed && mc.currentScreen == null) swapHit(mc, h.match());
        }
    }

    private void startCombo(MinecraftClient mc, Step... steps) {
        if (!(mc.crosshairTarget instanceof EntityHitResult hit)) return;
        comboTarget = hit.getEntity();
        queue.clear();
        for (Step st : steps) queue.add(st);
        countdown = 0;
        tickCombo(mc); // first step fires this same tick
    }

    private void tickCombo(MinecraftClient mc) {
        if (queue.isEmpty()) return;
        if (comboTarget == null || !comboTarget.isAlive() || mc.player.squaredDistanceTo(comboTarget) > 64.0) {
            queue.clear(); return;
        }
        if (countdown-- > 0) return;
        Step st = queue.poll();
        runStep(mc, st);
        if (!queue.isEmpty()) countdown = queue.peek().delayTicks() - 1;
    }

    private static long lastLungeTick = -100;
    private static final int LUNGE_MIN_TICKS = 3; // one swap cycle per 3 ticks max = few packets
    private static int lungePhase;        // 0 = idle/on wind charge, 1 = spear is out (return next tick)
    private static int lungeBack = -1;    // slot to return to (the wind charge)
    private static Bind spearBind;

    /** Selects a slot and sends the slot packet only if it actually changed (never a duplicate). */
    private static void selectSlot(MinecraftClient mc, int slot) {
        PlayerInventory inv = mc.player.getInventory();
        if (inv.getSelectedSlot() == slot) return;
        inv.setSelectedSlot(slot);
        ((com.spears.mixin.InteractionManagerAccessor) mc.interactionManager).spears$syncSelectedSlot();
    }

    /** Lunge swap: wind charge -> spear (jab/lunge) -> back to wind charge. 2 slot packets per cycle, max. */
    /**
     * Hold the spear key. Visible 2-step cycle, repeated while held:
     *   tick N   : wind charge -> spear, jab (same tick = no attribute delay)
     *   tick N+1 : spear -> back to wind charge
     * then waits until LUNGE_MIN_TICKS have passed, so packets stay low.
     */
    private static void lungeSwap(MinecraftClient mc) {
        if (lungePhase == 1) { finishLunge(mc); return; }
        if (SpearsConfig.minFood > 0 && mc.player.getHungerManager().getFoodLevel() <= SpearsConfig.minFood) return;
        long now = mc.world.getTime();
        if (now - lastLungeTick < LUNGE_MIN_TICKS) return;
        PlayerInventory inv = mc.player.getInventory();
        int cur = inv.getSelectedSlot();
        int spear = -1, wind = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack st = inv.getStack(i);
            if (spear < 0 && st.isIn(ItemTags.SPEARS)) spear = i;
            if (wind < 0 && st.isOf(Items.WIND_CHARGE)) wind = i;
        }
        if (spear < 0) return;
        lungeBack = wind >= 0 ? wind : cur;
        lastLungeTick = now;
        if (cur != lungeBack && cur != spear) { selectSlot(mc, lungeBack); return; }   // first, visibly go to the wind charge
        selectSlot(mc, spear);                                                         // wind charge -> spear
        inMacro = true;
        try { ((com.spears.mixin.MinecraftClientAccessor) mc).spears$doAttack(); }     // jab / lunge, same tick
        finally { inMacro = false; }
        lungePhase = 1;
    }

    private static void finishLunge(MinecraftClient mc) {
        if (lungeBack >= 0) selectSlot(mc, lungeBack);                                 // spear -> back to wind charge
        lungePhase = 0;
    }

    private void runStep(MinecraftClient mc, Step st) {
        if (st.kind() == 0) { hitWith(mc, st.match(), comboTarget); return; }
        PlayerInventory inv = mc.player.getInventory();
        int orig = inv.getSelectedSlot(), slot = -1;
        for (int i = 0; i < 9; i++) if (st.match().test(inv.getStack(i))) { slot = i; break; }
        if (slot < 0) return;
        if (slot != orig) {
            inv.setSelectedSlot(slot);
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        }
        if (st.kind() == 1) {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            if (slot != orig) {
                inv.setSelectedSlot(orig);
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(orig));
            }
        }
    }

    private void startWindMace(MinecraftClient mc) {
        startCombo(mc, new Step(s -> s.isOf(Items.WIND_CHARGE), 0, 1),
                       new Step(s -> s.isOf(Items.MACE), SpearsConfig.windMaceDelay, 0));
    }

    private static void swapHit(MinecraftClient mc, Predicate<ItemStack> match) {
        if (!(mc.crosshairTarget instanceof EntityHitResult hit)) return;
        hitWith(mc, match, hit.getEntity());
    }

    private static void hitWith(MinecraftClient mc, Predicate<ItemStack> match, Entity target) {
        PlayerInventory inv = mc.player.getInventory();
        int orig = inv.getSelectedSlot();
        int slot = -1;
        if (match.test(inv.getStack(orig))) slot = orig;
        else for (int i = 0; i < 9; i++) if (match.test(inv.getStack(i))) { slot = i; break; }
        if (slot < 0) return;
        selectSlot(mc, slot);
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
        selectSlot(mc, orig);
    }

    /** Selects the matching hotbar slot; if already on one, cycles to the next match. */
    private static void select(MinecraftClient mc, Predicate<ItemStack> match) {
        PlayerInventory inv = mc.player.getInventory();
        int cur = inv.getSelectedSlot();
        for (int i = 1; i <= 9; i++) {
            int slot = (cur + i) % 9;
            if (match.test(inv.getStack(slot))) {
                if (slot != cur) {
                    inv.setSelectedSlot(slot);
                    mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
                }
                return;
            }
        }
    }

    // Called (observe-only) from MinecraftClientMixin on every left-click.
    private static boolean inMacro;
    public static void onAttackAttempt(MinecraftClient mc) {
        if (inMacro) return;
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;
        if (mc.crosshairTarget instanceof EntityHitResult) { hits++; return; }
        misses++;
        String reason = explainMiss(mc);
        missReasons.merge(reason.split(" \\(")[0], 1, Integer::sum);
        mc.player.sendMessage(Text.literal("Miss: " + reason), true);
    }

    private static String explainMiss(MinecraftClient mc) {
        var p = mc.player;
        float charge = p.getAttackCooldownProgress(0.5f);
        if (charge < 0.9f) return "attack not charged (" + Math.round(charge * 100) + "%)";
        Box box = p.getBoundingBox().expand(6.0);
        LivingEntity best = null; double bestD = 1e9;
        for (Entity e : mc.world.getOtherEntities(p, box, x -> x instanceof LivingEntity && x.isAlive())) {
            double d = p.squaredDistanceTo(e);
            if (d < bestD) { bestD = d; best = (LivingEntity) e; }
        }
        if (best == null) return "nothing in range (swung at air)";
        Vec3d to = best.getBoundingBox().getCenter().subtract(p.getEyePos()).normalize();
        double dot = Math.max(-1, Math.min(1, to.dotProduct(p.getRotationVec(1.0f))));
        long deg = Math.round(Math.toDegrees(Math.acos(dot)));
        String extra = "";
        if (p.getMainHandStack().isOf(Items.MACE) && p.fallDistance < 1.5f)
            extra = ", no fall (" + String.format("%.1f", p.fallDistance) + " blocks)";
        return "aim off by " + deg + " deg, target " + String.format("%.1f", Math.sqrt(bestD)) + " blocks away" + extra;
    }

    private static void printStats(MinecraftClient mc) {
        int total = hits + misses;
        String rate = total == 0 ? "-" : Math.round(100.0 * hits / total) + "%";
        mc.player.sendMessage(Text.literal("Spears: " + hits + " hits / " + misses + " misses (" + rate + " hit rate)"), false);
        missReasons.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(4)
            .forEach(e -> mc.player.sendMessage(Text.literal("  " + e.getValue() + "x " + e.getKey()), false));
    }
}
