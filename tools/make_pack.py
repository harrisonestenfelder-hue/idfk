#!/usr/bin/env python3
"""Builds the "Spears Theme" resource pack from YOUR own Minecraft jar (Mojang textures can't be redistributed).

Usage:  python make_pack.py <path to 1.21.11.jar> [output.zip] [--ui]
Output: Spears_Theme.zip  -> drop it in .minecraft/resourcepacks

  * Netherite armor: night-sky black/navy with scattered stars (worn model + inventory icons).
    Icons twinkle (animated). Worn armor can't animate in vanilla, so the *moving* stars on worn
    gear come from the glint below.
  * All other armor: black.
  * Enchant glint: pure white, scrolling star specks + faint white shimmer.
  * --ui : also grayscale hotbar + buttons (optional).
Needs: pip install pillow
"""
import io, json, os, random, sys, zipfile, zlib
from PIL import Image

args = [a for a in sys.argv[1:] if not a.startswith("--")]
UI = "--ui" in sys.argv
if not args:
    sys.exit(__doc__)
jar = args[0]
out_zip = args[1] if len(args) > 1 else "Spears_Theme.zip"
EQ = "assets/minecraft/textures/entity/equipment/"
ITEM = "assets/minecraft/textures/item/"
files = {}

def png(im):
    b = io.BytesIO(); im.save(b, "PNG"); return b.getvalue()

def shade(im, f):                       # plain black armor
    im = im.convert("RGBA"); px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            l = int((r*.3 + g*.59 + b*.11) * f)
            px[x, y] = (l, l, l, a)
    return im

def night(im, seed, density, bright=0.25):   # night-sky navy + stars, keeps the armor's shading
    im = im.convert("RGBA"); px = im.load(); rnd = random.Random(seed)
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a == 0: continue
            l = (r*.3 + g*.59 + b*.11) / 255.0
            px[x, y] = (int(4 + 14*l), int(6 + 20*l), int(14 + 48*l), a)
            if rnd.random() < density:
                v = rnd.choice((255, 255, 230, 190, 150))
                tint = rnd.choice((0, 0, 1))        # a few pale-blue stars
                px[x, y] = (v - 30*tint, v - 15*tint, v, a)
    return im

with zipfile.ZipFile(jar) as z:
    names = z.namelist()
    # --- worn armor models ---
    for n in names:
        if n.startswith(EQ) and n.endswith(".png") and "humanoid" in n and "leather" not in n:
            im = Image.open(io.BytesIO(z.read(n)))
            if os.path.basename(n) == "netherite.png":
                files[n] = png(night(im, zlib.crc32(n.encode()) & 0xffff, 0.045))
            else:
                files[n] = png(shade(im, 0.18))
    # --- netherite inventory icons, 4-frame twinkle ---
    for piece in ("helmet", "chestplate", "leggings", "boots"):
        n = f"{ITEM}netherite_{piece}.png"
        if n in names:
            base = Image.open(io.BytesIO(z.read(n))).convert("RGBA")
            sheet = Image.new("RGBA", (base.width, base.height * 4))
            for f in range(4):
                sheet.paste(night(base, 1000 + f, 0.07), (0, base.height * f))
            files[n] = png(sheet)
            files[n + ".mcmeta"] = json.dumps({"animation": {"frametime": 12}}).encode()
    # --- optional UI ---
    if UI:
        for n in names:
            if n.endswith(".png") and (n.startswith("assets/minecraft/textures/gui/sprites/widget/")
                                       or "textures/gui/sprites/hud/hotbar" in n):
                files[n] = png(shade(Image.open(io.BytesIO(z.read(n))), 1.0))

# --- pure white scrolling glint: black background (no tint), white shimmer + white star specks ---
import math
g = Image.new("RGBA", (64, 64)); px = g.load(); rnd = random.Random(7)
for y in range(64):
    for x in range(64):
        v = (math.sin((x + y) / 64 * 2 * math.pi * 2) + 1) / 2
        w = int(70 * v ** 3)                         # faint white band
        px[x, y] = (w, w, w, 255)
for _ in range(34):                                   # pure white stars, tile-safe
    x, y = rnd.randrange(64), rnd.randrange(64)
    px[x, y] = (255, 255, 255, 255)
    if rnd.random() < 0.3:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            px[(x+dx) % 64, (y+dy) % 64] = (170, 170, 170, 255)
for n in ("enchanted_glint_item.png", "enchanted_glint_entity.png"):
    files["assets/minecraft/textures/misc/" + n] = png(g)

files["pack.mcmeta"] = json.dumps({"pack": {
    "description": "Spears Theme: night-sky netherite, pure white glint",
    "pack_format": 75, "min_format": 75, "max_format": 75}}).encode()

with zipfile.ZipFile(out_zip, "w", zipfile.ZIP_DEFLATED) as zo:
    for n, d in files.items(): zo.writestr(n, d)
print(f"Wrote {out_zip} with {len(files)} files")
