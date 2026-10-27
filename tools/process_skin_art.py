#!/usr/bin/env python3
"""Cuts the car skins out of the two skin designs in art-source/ui (skins_design*.png).

Each skin is cut out with rembg (isnet-general-use), then placed on the same 448 x 448 canvas as
the base cars (car body 394 px wide, centred), so a skin lines up exactly with the car it
replaces on the board. Writes app/src/main/res/drawable-nodpi/skin_*.webp and a preview sheet
tools/out/skins_preview.png.

Run from the repo root:  python3 tools/process_skin_art.py
Needs: pip install pillow numpy opencv-python-headless "rembg[cpu]"
"""
from __future__ import annotations

import os

import cv2
import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi")
OUT = os.path.join(ROOT, "tools", "out")

CANVAS = 448
CAR_WIDTH = 394

D1 = os.path.join(ROOT, "art-source", "ui", "skins_design.png")   # first Car Skins design
D2 = os.path.join(ROOT, "art-source", "ui", "skins_design2.png")  # Skin Collection design

# skin drawable: (design, box (x0, y0, x1, y1)). Lock-free pictures only.
_COLS = [(372, 522), (526, 676), (678, 828), (830, 980), (983, 1133)]
_ROWS = [(140, 282), (288, 438), (444, 592), (598, 752)]


def _grid(r, c):
    return (D2, (_COLS[c][0], _ROWS[r][0], _COLS[c][1], _ROWS[r][1]))


def _more(i, top=826):
    x0, x1 = [(78, 248), (254, 424), (430, 598), (602, 772), (776, 946), (950, 1122), (1126, 1298), (1302, 1470)][i]
    return (D2, (x0, top, x1, 990))


SKINS = {
    "skin_racing_red": (D2, (1185, 295, 1505, 565)),
    "skin_cow": _more(1),
    "skin_police": _grid(1, 0),
    "skin_bunny": _more(2, 818),
    "skin_taxi": (D1, (60, 232, 425, 505)),
    "skin_kitty": _more(3, 832),
    "skin_mint": _grid(1, 3),
    "skin_dino": _more(4, 832),
    "skin_stars": _grid(1, 4),
    "skin_flower_van": _grid(2, 2),
    "skin_santa": _more(5, 812),
    "skin_crown": _grid(3, 0),
    "skin_rescue": _grid(2, 3),
    "skin_pumpkin": _more(6, 812),
    "skin_pink_bow": _more(0, 818),
    "skin_unicorn": _more(7, 812),
    "skin_rally": _grid(1, 2),
    "skin_rainbow": _grid(3, 4),
    "skin_night": _grid(2, 4),
    "skin_neon": _grid(3, 1),
    "skin_galaxy": _grid(3, 2),
    "skin_champion": _grid(3, 3),
}

# areas (design coordinates) that are background art stuck to a car, removed after cutting
ERASE = {
    # the gold card's glow and sparkle: above the spoiler and left of the hood
    "skin_rainbow": [(983, 598, 1082, 628), (983, 598, 1036, 652), (983, 645, 1016, 663)],
}
_session = None


def cut(img: Image.Image, erase_px=()) -> Image.Image:
    global _session
    from rembg import new_session, remove

    if _session is None:
        _session = new_session("isnet-general-use")
    out = np.array(remove(img, session=_session, post_process_mask=True))
    alpha = out[..., 3].astype(np.float32)
    alpha = np.clip((alpha - 24) * 255.0 / (255 - 24), 0, 255)
    for (x0, y0, x1, y1) in erase_px:
        alpha[max(y0, 0):max(y1, 0), max(x0, 0):max(x1, 0)] = 0
    # keep only the biggest shape (drops sparkles and bits of the card background)
    mask = (alpha > 40).astype(np.uint8)
    n, lab, st, _ = cv2.connectedComponentsWithStats(mask, 8)
    if n > 2:
        keep = 1 + int(np.argmax(st[1:, cv2.CC_STAT_AREA]))
        alpha[(lab != keep) & (lab != 0)] = 0
    # fill enclosed holes (windows the model made see-through) with the original colours
    solid = (alpha > 128).astype(np.uint8)
    flood = solid.copy()
    cv2.floodFill(flood, np.zeros((flood.shape[0] + 2, flood.shape[1] + 2), np.uint8), (0, 0), 1)
    holes = flood == 0
    alpha[holes] = 255
    rgb = np.array(img.convert("RGB"))
    out[..., :3] = np.where((out[..., 3:4] == 0) | holes[..., None], rgb, out[..., :3])
    out[..., 3] = alpha.astype(np.uint8)
    return Image.fromarray(out, "RGBA")


def place(cutout: Image.Image) -> Image.Image:
    bbox = cutout.getchannel("A").point(lambda v: 255 if v > 8 else 0).getbbox()
    car = cutout.crop(bbox)
    scale = CAR_WIDTH / car.width
    car = car.resize((CAR_WIDTH, max(1, round(car.height * scale))), Image.LANCZOS)
    if car.height > CANVAS - 20:
        s2 = (CANVAS - 20) / car.height
        car = car.resize((max(1, round(car.width * s2)), CANVAS - 20), Image.LANCZOS)
    canvas = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    canvas.alpha_composite(car, ((CANVAS - car.width) // 2, (CANVAS - car.height) // 2))
    return canvas


def main() -> None:
    os.makedirs(RES, exist_ok=True)
    os.makedirs(OUT, exist_ok=True)
    designs = {path: Image.open(path).convert("RGB") for path in (D1, D2)}
    results = {}
    for name, (design, box) in SKINS.items():
        crop = designs[design].crop(box)
        crop = crop.resize((crop.width * 3, crop.height * 3), Image.LANCZOS)  # model works better large
        erase = [((x0 - box[0]) * 3, (y0 - box[1]) * 3, (x1 - box[0]) * 3, (y1 - box[1]) * 3)
                 for (x0, y0, x1, y1) in ERASE.get(name, [])]
        im = place(cut(crop, erase))
        im.save(os.path.join(RES, name + ".webp"), "WEBP", quality=92, method=6)
        results[name] = im
        print(f"{name:14s} {im.size}")
    for label, bg in (("white", (255, 255, 255)), ("sky", (191, 230, 255))):
        cell, cols = 220, 6
        rows = (len(results) + cols - 1) // cols
        sheet = Image.new("RGBA", (cell * cols, cell * rows), bg + (255,))
        for i, im in enumerate(results.values()):
            t = im.copy()
            t.thumbnail((cell - 10, cell - 10), Image.LANCZOS)
            sheet.alpha_composite(t, ((i % cols) * cell + (cell - t.width) // 2, (i // cols) * cell + (cell - t.height) // 2))
        sheet.convert("RGB").save(os.path.join(OUT, f"skins_preview_{label}.png"))


if __name__ == "__main__":
    main()
