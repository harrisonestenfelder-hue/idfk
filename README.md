# Spears (Fabric, Minecraft 1.21.11)
Build: python tools/make_armor.py <1.21.11.jar>, then ./gradlew build (run `gradle wrapper` once). Jar: build/libs/spears-1.0.0.jar
Defaults: Z sword, X axe, E wind charge, R mace, C shield, V ender pearl, Q food, Mouse4 spear, Mouse5 stamina food (golden apples).
Press a key = jump to that item's hotbar slot instantly; press again = next matching slot.
Rebind vanilla Drop (Q) and Inventory (E) in Controls to avoid conflicts. White glint + black armor via built-in "Spears Theme" pack.
Swap-hit: G = spear, H = mace (one press: switch, hit target, switch back, same tick).
Combos: B = stun slam (axe hit, 2-3 tick wait, mace hit; N toggles 2/3), J = spear then mace (1 tick).
Legit mode: K toggles. ON (default) disables swap-hits and combos; item-select keys still work.
Training tracker (always on, passive): misses show their reason on the action bar. L = print stats, U = reset.
Training: your real clicks are counted as hits/misses on the action bar; L resets.
Lunge swap: hold Y = spear jab/lunge via same-tick swap (needs Lunge spear in hotbar; legit mode must be OFF). Drains hunger fast.

## Fitted to your binds (tap = select, hold 0.25s = action; needs legit mode OFF via K)
Mouse4 (spear): hold = lunge swap, repeats. R (mace): hold = mace swap-hit. X (axe): hold = stun slam (axe -> mace, delay toggled with N).
Dedicated keys G/H/B/J/Y still work and can be rebound or cleared in Controls.
Training stats: L = print hits/misses with reasons, U = reset.

## v2: settings, more combos
O = settings screen (saved to config/spears.json). M = axe>spear>mace, P = wind charge>mace (aim down first), hold E = wind>mace.
Lunge swap pauses at low hunger (configurable). All macro actions need legit mode OFF (K).

## Latest
- Settings screen: key O (see Controls -> Spears). Saved to config/spears.json: legit mode, stun slam delay (1-5), spear>mace delay, wind charge>mace delay, hold time, lunge hunger floor.
- New combos: Axe > Spear > Mace, Wind Charge > Mace (see Controls for keys).
- Theme pack (white glint, black armor, UI) is NOT registered for now. Files stay in resourcepacks/ and tools/make_armor.py.

## Theme pack (armor + glint)
Run: python tools/make_pack.py <.minecraft/versions/1.21.11/1.21.11.jar>  (pip install pillow)
Put the resulting Spears_Theme.zip in .minecraft/resourcepacks, then Options > Resource Packs > enable, move to top.

## Easiest install
- Pack: open spears_pack_maker.html in a browser, pick the 1.21.11.jar, download Spears_Theme.zip -> .minecraft/resourcepacks.
- Mod: upload this folder to a GitHub repo (keep .github), Actions tab -> run "build" -> download spears-mod artifact -> .minecraft/mods.

## Lunge swap (hold Mouse4 / Y)
Cycle is wind charge -> spear (jab) -> back to wind charge. Uses the game's own slot sync so no duplicate packets are sent, and runs at most once per 2 ticks.
