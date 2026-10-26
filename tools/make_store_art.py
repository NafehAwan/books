#!/usr/bin/env python3
"""Builds the Google Play feature graphic (1024x500) from the car cut-outs and the Fredoka font.

Run from the repo root after tools/process_art.py:  python3 tools/make_store_art.py
Output: store/feature_graphic_1024x500.png
"""
import os

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CARS = os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi")
OUT = os.path.join(ROOT, "store")
W, H = 1024, 500


def car(i: int, width: int) -> Image.Image:
    name = sorted(f for f in os.listdir(CARS) if f.startswith(f"car_{i:02d}_"))[0]
    img = Image.open(os.path.join(CARS, name)).convert("RGBA")
    img = img.crop(img.getbbox())
    return img.resize((width, int(img.height * width / img.width)), Image.LANCZOS)


def main() -> None:
    os.makedirs(OUT, exist_ok=True)
    bg = Image.new("RGB", (W, H))
    d = ImageDraw.Draw(bg)
    for y in range(H):
        t = y / (H - 1)
        d.line([(0, y), (W, y)], fill=tuple(int(a * (1 - t) + b * t) for a, b in zip((191, 230, 255), (239, 246, 251))))
    clouds = Image.new("L", (W, H), 0)
    cd = ImageDraw.Draw(clouds)
    for cx, cy, r in ((120, 70, 60), (190, 60, 80), (260, 75, 55), (820, 90, 60), (900, 80, 75), (970, 95, 50)):
        cd.ellipse([cx - r, cy - r * 0.6, cx + r, cy + r * 0.6], fill=170)
    bg.paste((255, 255, 255), (0, 0), clouds.filter(ImageFilter.GaussianBlur(8)))
    d.rectangle([0, 420, W, H], fill=(185, 215, 168))
    d.rectangle([0, 420, W, 426], fill=(156, 196, 138))
    canvas = bg.convert("RGBA")

    # the logo and the waving Towy, arranged like the home screen design
    logo = Image.open(os.path.join(CARS, "ui_logo_big.webp")).convert("RGBA")
    lw = 420
    logo = logo.resize((lw, int(logo.height * lw / logo.width)), Image.LANCZOS)
    lx, ly = 40, 12
    canvas.alpha_composite(logo, (lx, ly))
    # Towy sits over the logo's lower-right corner, exactly as on the home screen design
    towy = Image.open(os.path.join(CARS, "ui_towy_wave.webp")).convert("RGBA")
    tw = int(lw * 528 / 760)
    towy = towy.resize((tw, int(towy.height * tw / towy.width)), Image.LANCZOS)
    canvas.alpha_composite(towy, (lx + int(lw * (588 - 112) / 760), ly + int(logo.height * 252 / 445)))

    # a parade of cars from small to big
    lineup = [(1, 115), (7, 130), (11, 150)]
    total = sum(w for _, w in lineup) + 18 * (len(lineup) - 1)
    px = W - total - 24
    for level, width in lineup:
        img = car(level, width)
        sh = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        ImageDraw.Draw(sh).ellipse([px + width * 0.1, 418, px + width * 0.9, 438], fill=(43, 58, 85, 80))
        canvas.alpha_composite(sh.filter(ImageFilter.GaussianBlur(5)))
        canvas.alpha_composite(img, (px, 430 - img.height))
        px += width + 18
    canvas.convert("RGB").save(os.path.join(OUT, "feature_graphic_1024x500.png"))
    print("store/feature_graphic_1024x500.png")


if __name__ == "__main__":
    main()
