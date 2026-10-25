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

    # the logo and Towy from the design sheets (tools/process_ui_art.py)
    logo = Image.open(os.path.join(CARS, "ui_logo.webp")).convert("RGBA")
    logo = logo.resize((440, int(logo.height * 440 / logo.width)), Image.LANCZOS)
    canvas.alpha_composite(logo, ((W - logo.width) // 2 - 90, 6))
    towy = Image.open(os.path.join(CARS, "ui_towy_front.webp")).convert("RGBA")
    towy = towy.resize((200, int(towy.height * 200 / towy.width)), Image.LANCZOS)
    canvas.alpha_composite(towy, (W - 300, 30))

    # a parade of cars from small to big
    lineup = [(1, 150), (3, 160), (7, 175), (9, 185), (11, 205)]
    total = sum(w for _, w in lineup) + 18 * (len(lineup) - 1)
    px = (W - total) // 2
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
