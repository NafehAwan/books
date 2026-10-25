#!/usr/bin/env python3
"""Builds the Google Play feature graphic (1024x500) from the car cut-outs and the Fredoka font.

Run from the repo root after tools/process_art.py:  python3 tools/make_store_art.py
Output: store/feature_graphic_1024x500.png
"""
import os

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CARS = os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi")
FONT = os.path.join(ROOT, "app", "src", "main", "res", "font", "fredoka_bold.ttf")
OUT = os.path.join(ROOT, "store")
W, H = 1024, 500
COLOURS = [(255, 166, 43), (255, 123, 172), (69, 173, 247), (76, 208, 125), (160, 132, 240)]


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

    # title with letter colours, like the app
    font = ImageFont.truetype(FONT, 120)
    text = "Tiny Motors"
    x = (W - d.textlength(text, font=font)) / 2
    shadow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow)
    cx = x
    for ch in text:
        sd.text((cx, 48 + 8), ch, font=font, fill=(43, 58, 85, 70))
        cx += sd.textlength(ch, font=font)
    canvas.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(3)))
    td = ImageDraw.Draw(canvas)
    k = 0
    for ch in text:
        if ch != " ":
            td.text((x, 48), ch, font=font, fill=COLOURS[k % len(COLOURS)] + (255,), stroke_width=6, stroke_fill=(255, 255, 255, 255))
            k += 1
        x += td.textlength(ch, font=font)

    # a parade of cars from small to big
    lineup = [(1, 150), (3, 165), (6, 185), (9, 200), (11, 235)]
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
