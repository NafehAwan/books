#!/usr/bin/env python3
"""Cuts the UI art (icons, Towy the mascot, logo, win badge) out of the design sheets.

Sources (1536 x 1024 mock-ups) live in art-source/ui/:
  icons_sheet.png, game_design.png, screens_design.png

Two methods:
  * key:   glyph on a flat button face. Alpha comes from the colour distance to the face colour
           (sampled at the crop border), then colours are un-mixed so edges stay clean.
  * rembg: characters and logo on busy backgrounds, using the isnet-general-use model.

Writes transparent WebP files to app/src/main/res/drawable-nodpi/ (prefix ui_) and preview
sheets on white and dark to tools/out/ui_preview_*.png.

Run from the repo root:  python3 tools/process_ui_art.py
Needs: pip install pillow numpy opencv-python-headless "rembg[cpu]"
"""
from __future__ import annotations

import os

import cv2
import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "art-source", "ui")
RES = os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi")
OUT = os.path.join(ROOT, "tools", "out")

# name: (sheet, box (x0, y0, x1, y1), method, max output side)
ASSETS = {
    # icon sheet: glyphs keyed off their button faces
    "ui_gift": ("icons_sheet", (512, 38, 630, 162), "key", 160),
    "ui_gear": ("icons_sheet", (712, 48, 828, 158), "key", 128),
    "ui_sound": ("icons_sheet", (905, 55, 1012, 150), "key", 128),
    "ui_music": ("icons_sheet", (1080, 52, 1175, 150), "key", 128),
    "ui_vibrate": ("icons_sheet", (1242, 58, 1340, 146), "key", 128),
    "ui_close": ("icons_sheet", (1406, 62, 1488, 138), "key", 128),
    "ui_plus": ("icons_sheet", (100, 235, 230, 360), "key", 160),
    "ui_play": ("icons_sheet", (680, 245, 790, 355), "key", 128),
    "ui_garage": ("icons_sheet", (902, 242, 1030, 358), "key", 160),
    "ui_book": ("icons_sheet", (1130, 250, 1270, 350), "key", 160),
    "ui_back": ("icons_sheet", (65, 452, 155, 525), "key", 128),
    "ui_home": ("icons_sheet", (225, 445, 310, 525), "key", 128),
    "ui_refresh": ("icons_sheet", (385, 445, 475, 535), "key", 128),
    "ui_tow": ("icons_sheet", (545, 440, 675, 535), "key", 160),
    "ui_crown": ("icons_sheet", (1070, 450, 1180, 530), "key", 128),
    "ui_star_small": ("icons_sheet", (1390, 440, 1490, 535), "key", 128),
    # Towy the tow truck and the logo
    "ui_logo": ("game_design", (58, 92, 395, 305), "rembg", 640),
    "ui_towy_front": ("game_design", (588, 498, 802, 708), "rembg", 320),
    "ui_towy_happy": ("game_design", (1238, 528, 1372, 630), "rembg", 220),
    "ui_towy_laugh": ("game_design", (1378, 528, 1512, 630), "rembg", 220),
    "ui_towy_squint": ("game_design", (1238, 630, 1372, 728), "rembg", 220),
    "ui_towy_surprised": ("game_design", (1378, 630, 1512, 728), "rembg", 220),
    "ui_towy_bounce": ("game_design", (592, 810, 742, 925), "rembg", 240),
    "ui_win_badge": ("game_design", (1240, 760, 1520, 990), "rembg", 420),
    "ui_towy_wink": ("screens_design", (96, 784, 338, 976), "rembg", 420),
    # settings row icons from the screens sheet
    "ui_shield": ("screens_design", (98, 692, 160, 755), "key", 96),
    "ui_lock": ("screens_design", (492, 702, 534, 748), "key", 72),
}

_rembg_session = None


def rembg_cut(img: Image.Image) -> Image.Image:
    global _rembg_session
    from rembg import new_session, remove

    if _rembg_session is None:
        _rembg_session = new_session("isnet-general-use")
    out = remove(img, session=_rembg_session, post_process_mask=True)
    a = np.array(out)
    # tighten soft halo: drop near-transparent pixels and lift the core
    alpha = a[..., 3].astype(np.float32)
    alpha = np.clip((alpha - 24) * 255.0 / (255 - 24), 0, 255)
    # drop small detached specks (bits of background the model kept)
    mask = (alpha > 40).astype(np.uint8)
    n, lab, st, _ = cv2.connectedComponentsWithStats(mask, 8)
    if n > 2:
        biggest = st[1:, cv2.CC_STAT_AREA].max()
        for i in range(1, n):
            if st[i, cv2.CC_STAT_AREA] < biggest * 0.01:
                alpha[lab == i] = 0
    a[..., 3] = alpha.astype(np.uint8)
    return Image.fromarray(a, "RGBA")


def key_cut(img: Image.Image) -> Image.Image:
    """Removes a flat-ish face colour sampled from the crop border."""
    a = np.array(img.convert("RGB")).astype(np.float32)
    border = np.concatenate([a[0], a[-1], a[:, 0], a[:, -1]])
    bg = np.median(border, axis=0)
    dist = np.linalg.norm(a - bg, axis=2)
    lo, hi = 18.0, 70.0
    alpha = np.clip((dist - lo) / (hi - lo), 0, 1)
    # keep only the biggest connected shapes (drop face-gradient noise)
    mask = (alpha > 0.5).astype(np.uint8)
    n, lab, st, _ = cv2.connectedComponentsWithStats(mask, 8)
    keep = np.zeros_like(mask)
    if n > 1:
        biggest = st[1:, cv2.CC_STAT_AREA].max()
        for i in range(1, n):
            if st[i, cv2.CC_STAT_AREA] >= max(20, biggest * 0.02):
                keep[lab == i] = 1
    keep = cv2.dilate(keep, np.ones((5, 5), np.uint8))
    alpha = alpha * keep
    # un-mix the face colour from semi-transparent edge pixels
    safe = np.maximum(alpha, 1e-3)[..., None]
    rgb = np.clip((a - (1 - alpha[..., None]) * bg) / safe, 0, 255)
    rgba = np.dstack([rgb, alpha * 255]).astype(np.uint8)
    return Image.fromarray(rgba, "RGBA")


def drop_bluish(img: Image.Image) -> Image.Image:
    """The gift's bow overlaps the button border; remove those light-blue border pixels."""
    a = np.array(img).astype(np.int32)
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    blue = (b > r + 25) & (b > g + 5)
    a[..., 3][blue] = 0
    return Image.fromarray(a.astype(np.uint8), "RGBA")


def tighten(img: Image.Image, pad: int = 4) -> Image.Image:
    bbox = img.getchannel("A").point(lambda v: 255 if v > 8 else 0).getbbox()
    if bbox is None:
        return img
    img = img.crop(bbox)
    out = Image.new("RGBA", (img.width + pad * 2, img.height + pad * 2), (0, 0, 0, 0))
    out.paste(img, (pad, pad))
    return out


def main() -> None:
    os.makedirs(RES, exist_ok=True)
    os.makedirs(OUT, exist_ok=True)
    sheets = {n: Image.open(os.path.join(SRC, n + ".png")).convert("RGB") for n in ("icons_sheet", "game_design", "screens_design")}
    results = {}
    for name, (sheet, box, method, max_side) in ASSETS.items():
        crop = sheets[sheet].crop(box)
        if method == "rembg":
            # upscale first: the model segments small artwork better at larger sizes
            crop = crop.resize((crop.width * 2, crop.height * 2), Image.LANCZOS)
            cut = rembg_cut(crop)
        else:
            cut = key_cut(crop.resize((crop.width * 2, crop.height * 2), Image.LANCZOS))
        if name == "ui_gift":
            cut = drop_bluish(cut)
        cut = tighten(cut)
        scale = min(1.0, max_side / max(cut.size))
        if scale < 1:
            cut = cut.resize((max(1, int(cut.width * scale)), max(1, int(cut.height * scale))), Image.LANCZOS)
        cut.save(os.path.join(RES, name + ".webp"), "WEBP", quality=92, method=6)
        results[name] = cut
        print(f"{name:20s} {cut.size}")
    for label, bg in (("white", (255, 255, 255)), ("dark", (30, 40, 60)), ("sky", (191, 230, 255))):
        cols = 6
        cell = 230
        rows = (len(results) + cols - 1) // cols
        sheet = Image.new("RGBA", (cols * cell, rows * cell), bg + (255,))
        for i, (n, im) in enumerate(results.items()):
            t = im.copy()
            t.thumbnail((cell - 20, cell - 20), Image.LANCZOS)
            sheet.alpha_composite(t, ((i % cols) * cell + (cell - t.width) // 2, (i // cols) * cell + (cell - t.height) // 2))
        sheet.convert("RGB").save(os.path.join(OUT, f"ui_preview_{label}.png"))


if __name__ == "__main__":
    main()
