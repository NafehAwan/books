#!/usr/bin/env python3
"""Tiny Motors art pipeline.

Turns the car sheet in art-source/ into:
  * app/src/main/res/drawable-nodpi/car_XX_name.webp  (512x512, transparent, car ~88% wide)
  * app/src/main/res/values/car_colors.xml            (tile tint, tile edge, main colour per car)
  * launcher icon layers (adaptive foreground/monochrome + legacy PNG mipmaps)
  * tools/out/preview_*.png                           (cut-outs on white and dark, for checking)

Run from the repo root:  python3 tools/process_art.py
Needs: Pillow, NumPy, OpenCV (pip install pillow numpy opencv-python-headless)
"""
from __future__ import annotations

import colorsys
import json
import os
import sys

import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SHEET = os.path.join(ROOT, "art-source", "car_sheet.webp")
RES = os.path.join(ROOT, "app", "src", "main", "res")
OUT = os.path.join(ROOT, "tools", "out")

NAMES = [
    "kei", "city_hatch", "retro_bug", "sedan", "estate", "suv",
    "pickup", "coupe", "sports_car", "supercar", "hypercar",
]
CANVAS = 512
CAR_WIDTH_FRACTION = 0.88
ICON_CAR = 0  # Kei car: the friendliest face for the launcher icon
SKY_TOP = (0xBF, 0xE6, 0xFF)
SKY_BOTTOM = (0x8F, 0xD0, 0xFA)


def load_sheet() -> np.ndarray:
    img = Image.open(SHEET).convert("RGBA")
    return np.array(img)


def find_cars(rgba: np.ndarray) -> list[np.ndarray]:
    """Returns one boolean mask per car, ordered left-to-right, top-to-bottom."""
    alpha = rgba[..., 3]
    n, labels, stats, cents = cv2.connectedComponentsWithStats((alpha > 8).astype(np.uint8), 8)
    comps = sorted(range(1, n), key=lambda i: -stats[i][cv2.CC_STAT_AREA])
    big = [i for i in comps if stats[i][cv2.CC_STAT_AREA] > 2000]
    if len(big) != 11:
        sys.exit(f"Expected 11 cars on the sheet, found {len(big)}")
    masks = {i: labels == i for i in big}
    # Attach any small detached parts (mirror tips, spoiler bits) to the nearest car.
    for i in comps:
        if i in big or stats[i][cv2.CC_STAT_AREA] < 30:
            continue
        cx, cy = cents[i]
        nearest = min(big, key=lambda b: (cents[b][0] - cx) ** 2 + (cents[b][1] - cy) ** 2)
        masks[nearest] |= labels == i
    # Order into rows by vertical centre, then left to right.
    items = sorted(big, key=lambda i: cents[i][1])
    rows: list[list[int]] = []
    for i in items:
        if rows and abs(cents[rows[-1][0]][1] - cents[i][1]) < 90:
            rows[-1].append(i)
        else:
            rows.append([i])
    ordered = [i for row in rows for i in sorted(row, key=lambda j: cents[j][0])]
    return [masks[i] for i in ordered]


def clean_cutout(rgba: np.ndarray, mask: np.ndarray) -> np.ndarray:
    """Crops one car, removes halo/fringe and returns a tight RGBA crop."""
    ys, xs = np.where(mask)
    pad = 6
    y0, y1 = max(ys.min() - pad, 0), min(ys.max() + pad + 1, rgba.shape[0])
    x0, x1 = max(xs.min() - pad, 0), min(xs.max() + pad + 1, rgba.shape[1])
    crop = rgba[y0:y1, x0:x1].copy().astype(np.float32)
    m = mask[y0:y1, x0:x1]
    # Keep the soft edge pixels that belong to this car (they sit just outside the >8 mask).
    near = cv2.dilate(m.astype(np.uint8), np.ones((5, 5), np.uint8)) > 0
    alpha = crop[..., 3] * near

    # De-fringe: recolour every semi-transparent pixel with its nearest solid neighbour,
    # so no glow or background colour survives in the edge.
    solid = (alpha >= 250).astype(np.uint8)
    if solid.sum() > 0:
        _, lbl = cv2.distanceTransformWithLabels(1 - solid, cv2.DIST_L2, 5,
                                                 labelType=cv2.DIST_LABEL_PIXEL)
        sy, sx = np.where(solid > 0)
        # label k (1-based) corresponds to the k-th zero pixel of (1 - solid) in row-major order
        lut_y = np.zeros(lbl.max() + 1, np.int32)
        lut_x = np.zeros(lbl.max() + 1, np.int32)
        zy, zx = np.where((1 - solid) == 0)
        lut_y[1:len(zy) + 1] = zy
        lut_x[1:len(zx) + 1] = zx
        ny, nx = lut_y[lbl], lut_x[lbl]
        edge = alpha < 250
        for c in range(3):
            ch = crop[..., c]
            ch[edge] = ch[ny[edge], nx[edge]]

    # Erode by one pixel and feather, removing any thin glow ring.
    eroded = cv2.erode(alpha, np.ones((3, 3), np.uint8))
    feathered = cv2.GaussianBlur(eroded, (3, 3), 0.8)
    alpha = np.minimum(alpha, feathered)
    alpha[alpha < 6] = 0
    crop[..., 3] = alpha

    ys, xs = np.where(alpha > 0)
    crop = crop[ys.min():ys.max() + 1, xs.min():xs.max() + 1]
    return np.clip(crop, 0, 255).astype(np.uint8)


def place_on_canvas(car: np.ndarray, size: int = CANVAS, width_fraction: float = CAR_WIDTH_FRACTION) -> Image.Image:
    img = Image.fromarray(car, "RGBA")
    target_w = int(size * width_fraction)
    scale = target_w / img.width
    target_h = int(round(img.height * scale))
    if target_h > size * width_fraction:  # very tall car: fit height instead
        scale = size * width_fraction / img.height
        target_w, target_h = int(img.width * scale), int(size * width_fraction)
    img = img.resize((target_w, target_h), Image.LANCZOS)
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    canvas.alpha_composite(img, ((size - target_w) // 2, (size - target_h) // 2))
    return canvas


def dominant_colour(car: np.ndarray) -> tuple[int, int, int]:
    """Most common saturated hue of the body paint."""
    # Sample the door band only: below the windows (which are blue glass) and above the wheels.
    h = car.shape[0]
    band = car[int(h * 0.45):int(h * 0.78)]
    px = band[band[..., 3] > 240][:, :3].astype(np.float32) / 255.0
    hsv = np.array([colorsys.rgb_to_hsv(*p) for p in px[:: max(1, len(px) // 20000)]])
    good = hsv[(hsv[:, 1] > 0.35) & (hsv[:, 2] > 0.45)]
    if len(good) == 0:
        good = hsv
    hist, edges = np.histogram(good[:, 0], bins=36, range=(0, 1))
    k = hist.argmax()
    sel = good[(good[:, 0] >= edges[k]) & (good[:, 0] < edges[k + 1])]
    h, s, v = np.median(sel, axis=0)
    r, g, b = colorsys.hsv_to_rgb(h, s, v)
    return int(r * 255), int(g * 255), int(b * 255)


def mix(c: tuple[int, int, int], other: tuple[int, int, int], t: float) -> tuple[int, int, int]:
    return tuple(int(round(a * (1 - t) + b * t)) for a, b in zip(c, other))


def hexc(c: tuple[int, int, int]) -> str:
    return "#FF%02X%02X%02X" % c


def composite_preview(cars: list[Image.Image]) -> None:
    os.makedirs(OUT, exist_ok=True)
    for name, bg in (("white", (255, 255, 255)), ("dark", (24, 28, 40)), ("sky", (191, 230, 255))):
        cell = 256
        sheet = Image.new("RGB", (cell * 4, cell * 3), bg)
        for i, car in enumerate(cars):
            thumb = car.resize((cell, cell), Image.LANCZOS)
            base = Image.new("RGBA", (cell, cell), bg + (255,))
            base.alpha_composite(thumb)
            sheet.paste(base.convert("RGB"), ((i % 4) * cell, (i // 4) * cell))
        sheet.save(os.path.join(OUT, f"preview_{name}.png"))
    # Full-size zoom of edges for the hardest cases.
    for i in (5, 9, 10):
        bg = Image.new("RGBA", (CANVAS, CANVAS), (24, 28, 40, 255))
        bg.alpha_composite(cars[i])
        bg.convert("RGB").save(os.path.join(OUT, f"zoom_dark_{i + 1:02d}.png"))


def write_colours(cols: list[tuple[int, int, int]]) -> dict:
    white = (255, 255, 255)
    data = {}
    lines = ['<?xml version="1.0" encoding="utf-8"?>',
             "<!-- Generated by tools/process_art.py from each car's dominant paint colour. Do not edit by hand. -->",
             "<resources>"]
    for i, (name, c) in enumerate(zip(NAMES, cols)):
        if name == "hypercar":
            tile, edge = (0x1F, 0x2A, 0x52), (0x10, 0x18, 0x36)
        else:
            tile, edge = mix(c, white, 0.82), mix(c, white, 0.45)
        data[name] = {"main": hexc(c), "tile": hexc(tile), "edge": hexc(edge)}
        n = f"car_{i + 1:02d}"
        lines += [f'    <color name="{n}_main">{hexc(c)}</color>',
                  f'    <color name="{n}_tile">{hexc(tile)}</color>',
                  f'    <color name="{n}_edge">{hexc(edge)}</color>']
    lines.append("</resources>")
    with open(os.path.join(RES, "values", "car_colors.xml"), "w") as f:
        f.write("\n".join(lines) + "\n")
    return data


def sky_background(size: int) -> Image.Image:
    grad = Image.new("RGB", (size, size))
    d = ImageDraw.Draw(grad)
    for y in range(size):
        d.line([(0, y), (size, y)], fill=mix(SKY_TOP, SKY_BOTTOM, y / (size - 1)))
    # two soft clouds
    cloud = Image.new("L", (size, size), 0)
    cd = ImageDraw.Draw(cloud)
    for cx, cy, r in ((0.25, 0.22, 0.10), (0.35, 0.20, 0.13), (0.45, 0.23, 0.09),
                      (0.68, 0.78, 0.08), (0.78, 0.76, 0.11), (0.88, 0.79, 0.07)):
        cd.ellipse([(cx - r) * size, (cy - r * 0.7) * size, (cx + r) * size, (cy + r * 0.7) * size], fill=150)
    cloud = cloud.filter(ImageFilter.GaussianBlur(size / 90))
    grad.paste((255, 255, 255), (0, 0), cloud)
    return grad


def icon_foreground(car: np.ndarray, size: int, fraction: float) -> Image.Image:
    """Car inside the adaptive-icon safe zone, with a soft ground shadow."""
    fg = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    car_img = place_on_canvas(car, size, fraction)
    bbox = car_img.getbbox()
    shadow = Image.new("L", (size, size), 0)
    sd = ImageDraw.Draw(shadow)
    w = (bbox[2] - bbox[0]) * 0.42
    cx, by = size / 2, bbox[3] - size * 0.01
    sd.ellipse([cx - w, by - size * 0.035, cx + w, by + size * 0.035], fill=90)
    shadow = shadow.filter(ImageFilter.GaussianBlur(size / 70))
    fg.paste((20, 60, 110, 255), (0, 0), shadow)
    fg.alpha_composite(car_img)
    return fg


def write_icons(car: np.ndarray) -> None:
    # Adaptive icon layers live in drawable-nodpi (108dp canvas; safe zone is the centre 66dp).
    nodpi = os.path.join(RES, "drawable-nodpi")
    fg = icon_foreground(car, 432, 0.60)
    fg.save(os.path.join(nodpi, "ic_launcher_foreground.webp"), "WEBP", lossless=True, quality=100)
    sky_background(432).save(os.path.join(nodpi, "ic_launcher_background.webp"), "WEBP", quality=92)
    # Monochrome (themed icon): flat silhouette of the car.
    mono = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
    a = place_on_canvas(car, 432, 0.60).getchannel("A")
    mono.paste((0, 0, 0, 255), (0, 0), a)
    mono.save(os.path.join(nodpi, "ic_launcher_monochrome.webp"), "WEBP", lossless=True)
    # Splash icon: the Android 12 splash crops the icon to a circle of 2/3 the size, so keep the car smaller.
    icon_foreground(car, 288, 0.56).save(os.path.join(nodpi, "splash_car.webp"), "WEBP", lossless=True)

    # Legacy icons for Android 7.x (API 24/25), rounded square and round.
    dens = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    big = 768
    bg = sky_background(big).convert("RGBA")
    bg.alpha_composite(icon_foreground(car, big, 0.80))
    for name, px in dens.items():
        d = os.path.join(RES, f"mipmap-{name}")
        os.makedirs(d, exist_ok=True)
        for kind in ("square", "round"):
            m = Image.new("L", (big, big), 0)
            md = ImageDraw.Draw(m)
            inset = big * 0.04
            if kind == "round":
                md.ellipse([inset, inset, big - inset, big - inset], fill=255)
            else:
                md.rounded_rectangle([inset, inset, big - inset, big - inset], radius=big * 0.2, fill=255)
            out = Image.new("RGBA", (big, big), (0, 0, 0, 0))
            out.paste(bg, (0, 0), m)
            out = out.resize((px, px), Image.LANCZOS)
            fname = "ic_launcher.webp" if kind == "square" else "ic_launcher_round.webp"
            out.save(os.path.join(d, fname), "WEBP", quality=92)
    # Play Store icon (512x512, full square; Play applies its own mask).
    store = sky_background(512).convert("RGBA")
    store.alpha_composite(icon_foreground(car, 512, 0.80))
    os.makedirs(os.path.join(ROOT, "store"), exist_ok=True)
    store.convert("RGB").save(os.path.join(ROOT, "store", "play_icon_512.png"))


def main() -> None:
    os.makedirs(os.path.join(RES, "drawable-nodpi"), exist_ok=True)
    os.makedirs(os.path.join(RES, "values"), exist_ok=True)
    sheet = load_sheet()
    masks = find_cars(sheet)
    cutouts = [clean_cutout(sheet, m) for m in masks]
    canvases = []
    for i, (name, car) in enumerate(zip(NAMES, cutouts)):
        img = place_on_canvas(car)
        path = os.path.join(RES, "drawable-nodpi", f"car_{i + 1:02d}_{name}.webp")
        img.save(path, "WEBP", quality=88, method=6)
        canvases.append(img)
        print(f"{path}  {os.path.getsize(path) // 1024} KB  crop={car.shape[1]}x{car.shape[0]}")
    colours = write_colours([dominant_colour(c) for c in cutouts])
    print(json.dumps(colours, indent=1))
    write_icons(cutouts[ICON_CAR])
    composite_preview(canvases)


if __name__ == "__main__":
    main()
