#!/usr/bin/env python3
"""Renders a ~20 s promo video for Tiny Motors with the game's own art, music and sound effects.

  python3 tools/make_promo_video.py                 -> promo/tiny_motors_ad_16x9.mp4 (1920 x 1080, YouTube)
  python3 tools/make_promo_video.py --vertical      -> promo/tiny_motors_ad_9x16.mp4 (1080 x 1920, Shorts)
  python3 tools/make_promo_video.py --end "Coming soon on Google Play"

Needs: ffmpeg, pillow, numpy. Everything else comes from the app's resources.
Storyboard: logo + Towy -> two cars merge -> merge combo -> all 11 cars -> "New car unlocked!"
-> real gameplay on a phone -> end card.
"""
from __future__ import annotations

import argparse
import math
import os
import random
import subprocess
import tempfile
from functools import lru_cache

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
ART = os.path.join(RES, "drawable-nodpi")
RAW = os.path.join(RES, "raw")
FONT = os.path.join(RES, "font", "fredoka_bold.ttf")
SHOT = os.path.join(ROOT, "store", "screenshots", "2_game.png")

FPS = 30
DURATION = 20.0
SR = 44100

CARS = ["car_01_kei", "car_02_city_hatch", "car_03_retro_bug", "car_04_sedan", "car_05_estate",
        "car_06_suv", "car_07_pickup", "car_08_coupe", "car_09_sports_car", "car_10_supercar",
        "car_11_hypercar"]
CAR_NAMES = ["Kei car", "City hatch", "Retro bug", "Sedan", "Estate", "SUV", "Pickup", "Coupe",
             "Sports car", "Supercar", "Hypercar"]

BLUE = (31, 92, 214)
WHITE = (255, 255, 255)
PURPLE = (124, 77, 230)
PURPLE_DARK = (88, 50, 180)
YELLOW = (255, 196, 40)
ORANGE = (255, 150, 30)


# ---------------------------------------------------------------- helpers

def clamp(x, a=0.0, b=1.0):
    return max(a, min(b, x))


def prog(t, start, dur):
    return clamp((t - start) / dur)


def ease_out_cubic(p):
    return 1 - (1 - p) ** 3


def ease_in_out(p):
    return 3 * p * p - 2 * p * p * p


def ease_out_back(p, s=1.7):
    p -= 1
    return 1 + (s + 1) * p ** 3 + s * p ** 2


@lru_cache(maxsize=None)
def load(name: str) -> Image.Image:
    path = name if os.path.isabs(name) else os.path.join(ART, name + ".webp")
    return Image.open(path).convert("RGBA")


@lru_cache(maxsize=4096)
def sprite(name: str, height: int, alpha_q: int = 255) -> Image.Image:
    """`name` scaled to `height` px (aspect kept), with overall opacity alpha_q/255."""
    im = load(name)
    height = max(1, height)
    w = max(1, round(im.width * height / im.height))
    out = im.resize((w, height), Image.LANCZOS)
    if alpha_q < 255:
        a = out.getchannel("A").point(lambda v: v * alpha_q // 255)
        out.putalpha(a)
    return out


def paste_center(frame: Image.Image, im: Image.Image, cx: float, cy: float):
    frame.alpha_composite(im, (round(cx - im.width / 2), round(cy - im.height / 2)))


def put(frame, name, cx, cy, height, alpha=1.0, rotate=0.0):
    if height < 2 or alpha <= 0.01:
        return
    im = sprite(name, int(height), int(clamp(alpha) * 255))
    if rotate:
        im = im.rotate(rotate, resample=Image.BICUBIC, expand=True)
    paste_center(frame, im, cx, cy)


@lru_cache(maxsize=64)
def font(size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(FONT, max(8, size))


def text(frame, s, cx, cy, size, fill=WHITE, outline=BLUE, alpha=1.0, stroke=None, shadow=True):
    """Chunky outlined title text centred at (cx, cy)."""
    if alpha <= 0.01:
        return
    probe = ImageDraw.Draw(Image.new("L", (1, 1)))
    stroke_arg = stroke
    for _ in range(8):  # shrink until it fits in 92 % of the frame width
        f = font(int(size))
        stroke = stroke_arg if stroke_arg is not None else max(2, int(size * 0.12))
        l, t, r, b = probe.textbbox((0, 0), s, font=f, stroke_width=stroke)
        w, h = r - l, b - t
        if w <= frame.width * 0.92:
            break
        size *= frame.width * 0.9 / w
    layer = Image.new("RGBA", (w + 20, h + 26), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    if shadow:
        d.text((10 - l, 10 - t + max(3, int(size * 0.08))), s, font=f, fill=(0, 0, 0, 70),
               stroke_width=stroke, stroke_fill=(0, 0, 0, 70))
    d.text((10 - l, 10 - t), s, font=f, fill=fill + (255,), stroke_width=stroke, stroke_fill=outline + (255,))
    if alpha < 1:
        layer.putalpha(layer.getchannel("A").point(lambda v: int(v * alpha)))
    paste_center(frame, layer, cx, cy)


def pill(frame, cx, cy, w, h, color, alpha=1.0, shade=None):
    layer = Image.new("RGBA", (int(w) + 8, int(h) + 14), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    a = int(255 * alpha)
    if shade:
        d.rounded_rectangle((4, 10, w + 4, h + 10), radius=h / 2, fill=shade + (a,))
    d.rounded_rectangle((4, 4, w + 4, h + 4), radius=h / 2, fill=color + (a,))
    paste_center(frame, layer, cx, cy + 3)


# ---------------------------------------------------------------- background

def make_background(W, H):
    bg = Image.new("RGBA", (W, H))
    top, bottom = np.array((150, 214, 255)), np.array((222, 244, 255))
    grad = (top[None, :] + (bottom - top)[None, :] * np.linspace(0, 1, H)[:, None]).astype(np.uint8)
    bg = Image.fromarray(np.repeat(grad[:, None, :], W, axis=1), "RGB").convert("RGBA")
    d = ImageDraw.Draw(bg)
    # rolling hills
    d.ellipse((-W * 0.3, H * 0.80, W * 0.75, H * 1.5), fill=(132, 206, 112))
    d.ellipse((W * 0.35, H * 0.83, W * 1.35, H * 1.55), fill=(116, 196, 98))
    d.rectangle((0, H * 0.93, W, H), fill=(108, 188, 92))
    # little trees
    u = min(W, H) / 1080
    rng = random.Random(7)
    for i in range(9):
        x = W * (0.04 + i * 0.115) + rng.uniform(-20, 20) * u
        y = H * 0.875 + rng.uniform(-10, 10) * u
        r = rng.uniform(34, 48) * u
        d.rectangle((x - 5 * u, y, x + 5 * u, y + r * 1.1), fill=(150, 100, 60))
        d.ellipse((x - r, y - r * 1.4, x + r, y + r * 0.4), fill=(78, 168, 80))
        d.ellipse((x - r * 0.6, y - r * 1.25, x + r * 0.1, y - r * 0.6), fill=(110, 196, 104))
    return bg


def make_cloud(width):
    h = int(width * 0.45)
    im = Image.new("RGBA", (width, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    for (x, y, r) in [(0.28, 0.62, 0.22), (0.5, 0.45, 0.28), (0.72, 0.62, 0.22), (0.5, 0.7, 0.24)]:
        d.ellipse(((x - r) * width, (y - r * 1.6) * h, (x + r) * width, (y + r * 1.6) * h), fill=(255, 255, 255, 235))
    return im.filter(ImageFilter.GaussianBlur(1.2))


# ---------------------------------------------------------------- effects

class Burst:
    """A one-off ring of coloured confetti / sparkles."""

    def __init__(self, x, y, t0, n, spread, seed, colors=None, life=0.9, gravity=0.0):
        rng = random.Random(seed)
        self.t0, self.life, self.gravity = t0, life, gravity
        cols = colors or [(255, 214, 64), (255, 120, 120), (110, 200, 255), (140, 230, 120), (200, 140, 255)]
        self.parts = []
        for _ in range(n):
            a = rng.uniform(0, 2 * math.pi)
            v = rng.uniform(0.45, 1.0) * spread
            self.parts.append((x, y, math.cos(a) * v, math.sin(a) * v - spread * 0.3 * (gravity > 0),
                               rng.choice(cols), rng.uniform(0.5, 1.0), rng.random() < 0.5))

    def draw(self, d, t, u):
        p = (t - self.t0) / self.life
        if p < 0 or p > 1:
            return
        e = ease_out_cubic(p)
        for (x, y, vx, vy, c, s, star) in self.parts:
            px = x + vx * e
            py = y + vy * e + self.gravity * p * p
            r = (12 * s * (1 - p) + 2) * u
            a = int(255 * (1 - p))
            if star:
                pts = []
                for k in range(10):
                    rr = r * (1.0 if k % 2 == 0 else 0.45)
                    ang = k * math.pi / 5 + p * 3
                    pts.append((px + rr * math.cos(ang), py + rr * math.sin(ang)))
                d.polygon(pts, fill=c + (a,))
            else:
                d.ellipse((px - r, py - r, px + r, py + r), fill=c + (a,))


def ribbon(frame, cx, cy, w, h, title, sub, alpha, u):
    layer = Image.new("RGBA", (int(w * 1.3), int(h * 1.6)), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    ox, oy = (layer.width - w) / 2, (layer.height - h) / 2
    tail = h * 0.55
    for side in (-1, 1):
        x0 = ox - tail * 0.6 if side < 0 else ox + w + tail * 0.6
        pts = [(x0, oy + h * 0.25), (ox + (w / 2) * (1 + side) - side * 0.0, oy + h * 0.25),
               (ox + (w / 2) * (1 + side), oy + h * 1.1), (x0, oy + h * 1.1), (x0 + side * tail * 0.45, oy + h * 0.68)]
        d.polygon(pts, fill=PURPLE_DARK + (255,))
    d.rounded_rectangle((ox, oy, ox + w, oy + h), radius=h * 0.25, fill=PURPLE + (255,))
    d.rounded_rectangle((ox + w * 0.04, oy + h * 0.08, ox + w * 0.96, oy + h * 0.32), radius=h * 0.12,
                        fill=(160, 120, 245, 255))
    if alpha < 1:
        layer.putalpha(layer.getchannel("A").point(lambda v: int(v * alpha)))
    paste_center(frame, layer, cx, cy)
    text(frame, title, cx, cy - h * 0.12, h * 0.42, outline=PURPLE_DARK, alpha=alpha)
    text(frame, sub, cx, cy + h * 0.28, h * 0.2, outline=PURPLE_DARK, alpha=alpha, stroke=2, shadow=False)


def phone(frame, cx, cy, h, shot: Image.Image, alpha):
    w = h * 0.5
    bez = h * 0.03
    layer = Image.new("RGBA", (int(w + 40), int(h + 40)), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.rounded_rectangle((20 + 6, 20 + 10, 20 + w + 6, 20 + h + 10), radius=h * 0.07, fill=(0, 0, 0, 60))
    d.rounded_rectangle((20, 20, 20 + w, 20 + h), radius=h * 0.07, fill=(32, 38, 60, 255))
    sw, sh = int(w - 2 * bez), int(h - 2 * bez)
    scr = shot.resize((sw, sh), Image.LANCZOS)
    mask = Image.new("L", (sw, sh), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, sw - 1, sh - 1), radius=h * 0.055, fill=255)
    layer.paste(scr, (int(20 + bez), int(20 + bez)), mask)
    if alpha < 1:
        layer.putalpha(layer.getchannel("A").point(lambda v: int(v * alpha)))
    paste_center(frame, layer, cx, cy)


# ---------------------------------------------------------------- the ad

def render(W, H, end_line, out_path):
    u = min(W, H) / 1080
    vertical = H > W
    bg = make_background(W, H)
    clouds = [(make_cloud(int(r * u)), x, y, sp) for (r, x, y, sp) in
              [(420, 0.08, 0.16, 18), (300, 0.62, 0.10, 12), (360, 0.86, 0.30, 22), (260, 0.36, 0.33, 15)]]
    shot = Image.open(SHOT).convert("RGB")
    cx, cy = W / 2, H / 2
    car_h = 230 * u

    # merge scene positions (two cars meet in the middle)
    gap = (300 if not vertical else 230) * u
    stage_y = cy + (40 if not vertical else 0) * u

    bursts = []
    merges = [  # (time the cars meet, level index of the pair)
        (4.1, 0), (5.25, 1), (6.25, 2),
    ]
    for i, (tm, lv) in enumerate(merges):
        bursts.append(Burst(cx, stage_y, tm, 26 + 8 * i, 260 * u, seed=i + 1))

    # car parade 7.3 .. 10.6: each car pops in
    parade_t0, parade_dt = 7.35, 0.27
    if not vertical:
        cols, rows = 11, 1
    else:
        cols, rows = 4, 3
    parade_pos = []
    for i in range(11):
        if not vertical:
            x = W * (0.07 + i * (0.86 / 10))
            y = cy + 70 * u + (math.sin(i * 0.9) * 40 * u)
            size = 150 * u
        else:
            r, c = divmod(i, 4)
            n_in_row = 4 if r < 2 else 3
            x = W * (0.5 + (c - (n_in_row - 1) / 2) * 0.235)
            y = cy - 80 * u + r * 250 * u
            size = 200 * u
        parade_pos.append((x, y, size))
    for i, (x, y, _) in enumerate(parade_pos):
        bursts.append(Burst(x, y, parade_t0 + i * parade_dt, 10, 90 * u, seed=100 + i, life=0.6))

    unlock_t = 11.0
    bursts.append(Burst(cx, cy, unlock_t + 0.1, 120, 900 * u, seed=500, life=1.8, gravity=500 * u))
    end_t = 15.6
    bursts.append(Burst(cx, cy - 160 * u, end_t + 0.35, 90, 700 * u, seed=900, life=1.6, gravity=420 * u))

    cmd = ["ffmpeg", "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}",
           "-r", str(FPS), "-i", "-", "-i", out_path + ".wav", "-c:v", "libx264", "-preset", "medium",
           "-crf", "18", "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "192k", "-shortest",
           "-movflags", "+faststart", out_path]
    enc = subprocess.Popen(cmd, stdin=subprocess.PIPE)

    n_frames = int(DURATION * FPS)
    for fi in range(n_frames):
        t = fi / FPS
        f = bg.copy()
        for (im, x, y, sp) in clouds:
            xx = (x * W + sp * u * t * 3) % (W + im.width) - im.width / 2
            paste_center(f, im, xx, y * H)

        # ---------- scene 1: logo + Towy (0 .. 3.1)
        if t < 3.3:
            out = 1 - prog(t, 2.9, 0.4)
            p = ease_out_back(prog(t, 0.15, 0.75))
            logo_h = (430 if not vertical else 470) * u
            ly = -logo_h + (cy - (60 if not vertical else 260) * u + logo_h) * p
            put(f, "ui_logo_big", cx - (150 if not vertical else 0) * u, ly, logo_h * (0.96 + 0.04 * math.sin(t * 6)), out)
            q = ease_out_back(prog(t, 0.95, 0.6))
            tx = W + 300 * u - (W + 300 * u - (cx + (520 if not vertical else 230) * u)) * q
            ty = cy + (40 if not vertical else 180) * u
            put(f, "ui_towy_wave", tx, ty, (430 if not vertical else 460) * u, out, rotate=math.sin(t * 5) * 4)
            a = prog(t, 1.7, 0.3) * out
            text(f, "The cutest car merge game!", cx, H * (0.88 if not vertical else 0.78), 76 * u, alpha=a)

        # ---------- scene 2: merges (3.2 .. 7.0)
        if 3.2 <= t < 7.2:
            fade = prog(t, 3.2, 0.3) * (1 - prog(t, 6.85, 0.35))
            text(f, "Match 2 cars...", cx, H * (0.17 if not vertical else 0.22), 90 * u, alpha=prog(t, 3.3, 0.3) * fade)
            if t > 4.6:
                text(f, "...to make a BIGGER car!", cx, H * (0.84 if not vertical else 0.74), 74 * u,
                     fill=YELLOW, outline=(170, 90, 10), alpha=prog(t, 4.6, 0.3) * fade)
            # Each merge: the pair appears at `start`, slides together and pops at `tm`; the new car
            # stays until the next pair appears (the new car becomes one of the next pair).
            starts = [3.4] + [m[0] - 0.95 for m in merges[1:]]
            current = max(i for i, s0 in enumerate(starts) if t >= s0 or i == 0)
            tm, lv = merges[current]
            start = starts[current]
            meet = prog(t, tm - 0.6, 0.6)
            if t < tm:
                e = ease_in_out(meet)
                appear = ease_out_back(prog(t, start, 0.35))
                for side in (-1, 1):
                    # the left car is the one just made: it is already there; the right one pops in
                    grow = 1.0 if (current > 0 and side < 0) else appear
                    slide_in = 1.0 if (current > 0 and side < 0) else 1.0
                    x = cx + side * gap * (1 - e) * slide_in
                    if current > 0 and side < 0:
                        x = cx - gap * ease_out_cubic(prog(t, start, 0.3)) * (1 - e)
                    put(f, CARS[lv], x, stage_y - abs(math.sin(e * math.pi)) * 40 * u, car_h * grow, fade,
                        rotate=-side * 8 * math.sin(e * math.pi))
            else:
                pop = ease_out_back(prog(t, tm, 0.35), 3.0)
                put(f, CARS[lv + 1], cx, stage_y, car_h * (0.4 + 0.75 * pop), fade)
                if prog(t, tm, 0.6) < 1:
                    text(f, CAR_NAMES[lv + 1] + "!", cx, stage_y - car_h * 0.85, 58 * u,
                         alpha=(1 - prog(t, tm + 0.35, 0.3)) * fade, outline=PURPLE_DARK)
            if current >= 1 and t >= tm - 0.75:
                combo = current + 1
                text(f, f"x{combo} COMBO!", cx + (520 if not vertical else 260) * u,
                     stage_y - (170 if not vertical else 330) * u, 64 * u, fill=YELLOW, outline=(200, 80, 0),
                     alpha=prog(t, tm, 0.15) * (1 - prog(t, tm + 0.7, 0.2)) * fade)

        # ---------- scene 3: all 11 cars (7.2 .. 10.9)
        if 7.2 <= t < 11.0:
            fade = prog(t, 7.2, 0.25) * (1 - prog(t, 10.7, 0.3))
            text(f, "11 cars to collect!", cx, H * (0.18 if not vertical else 0.2), 92 * u, alpha=fade)
            for i, (x, y, size) in enumerate(parade_pos):
                p = ease_out_back(prog(t, parade_t0 + i * parade_dt, 0.35), 2.2)
                bob = math.sin(t * 4 + i) * 6 * u
                put(f, CARS[i], x, y + bob, size * p * (1.25 if i == 10 else 1.0), fade)
            if t > 10.0:
                text(f, "...all the way to the HYPERCAR!", cx, H * (0.85 if not vertical else 0.86), 66 * u,
                     fill=YELLOW, outline=(170, 90, 10), alpha=prog(t, 10.0, 0.25) * fade)

        # ---------- scene 4: new car unlocked (11.0 .. 13.0) + phone (12.9 .. 15.7)
        if 11.0 <= t < 13.2:
            fade = 1 - prog(t, 12.85, 0.3)
            p = ease_out_back(prog(t, unlock_t, 0.45), 2.0)
            put(f, "car_11_hypercar", cx, cy + 40 * u, 470 * u * p * (1 + 0.03 * math.sin(t * 8)), fade)
            rp = ease_out_back(prog(t, unlock_t + 0.25, 0.4))
            ribbon(f, cx, cy - (300 if not vertical else 420) * u, 700 * u * rp + 1, 150 * u * rp + 1,
                   "Hypercar", "New Car Unlocked!", fade * clamp(rp), u)
        if 12.9 <= t < 15.9:
            fade = prog(t, 12.9, 0.2) * (1 - prog(t, 15.55, 0.35))
            p = ease_out_cubic(prog(t, 12.9, 0.55))
            ph = (900 if not vertical else 1250) * u
            px = cx + (330 if not vertical else 0) * u
            py = H + ph - (H + ph - (cy + (30 if not vertical else 120) * u)) * p
            phone(f, px, py, ph, shot, fade)
            if not vertical:
                text(f, "Easy for kids", cx - 470 * u, cy - 120 * u, 86 * u, alpha=prog(t, 13.3, 0.3) * fade)
                text(f, "ages 5 to 10", cx - 470 * u, cy - 15 * u, 70 * u, fill=YELLOW, outline=(170, 90, 10),
                     alpha=prog(t, 13.6, 0.3) * fade)
                put(f, "ui_towy_happy", cx - 470 * u, cy + 190 * u, 230 * u, prog(t, 13.9, 0.3) * fade,
                    rotate=math.sin(t * 6) * 6)
            else:
                text(f, "Easy for kids 5 to 10", cx, H * 0.1, 76 * u, alpha=prog(t, 13.3, 0.3) * fade)

        # ---------- scene 5: end card (15.6 .. 20)
        if t >= 15.6:
            a = prog(t, 15.6, 0.3)
            p = ease_out_back(prog(t, 15.65, 0.6))
            logo_h = (400 if not vertical else 440) * u
            put(f, "ui_logo_big", cx, cy - (150 if not vertical else 380) * u, logo_h * p, a)
            put(f, "ui_towy_wave", cx + (560 if not vertical else 250) * u, cy + (40 if not vertical else 120) * u,
                (360 if not vertical else 380) * u * ease_out_back(prog(t, 16.0, 0.5)), a, rotate=math.sin(t * 5) * 5)
            pp = ease_out_back(prog(t, 16.3, 0.45))
            pw, phh = (760 if not vertical else 900) * u, 130 * u
            py = cy + (150 if not vertical else 470) * u
            if pp > 0.01:
                pill(f, cx, py, pw * pp, phh * pp, (52, 168, 83), shade=(30, 120, 55))
                text(f, end_line, cx, py, 62 * u * pp, outline=(20, 100, 45), shadow=False)
            text(f, "No timers  ·  No lives  ·  Parent-safe", cx, py + (120 if not vertical else 140) * u, 44 * u,
                 outline=BLUE, alpha=prog(t, 16.9, 0.3), stroke=4)
            # cars drive across the bottom
            for i, lv in enumerate((0, 2, 5, 8, 10)):
                x = -300 * u + (t - 16.2 - i * 0.35) * 620 * u
                if -300 * u < x < W + 300 * u:
                    put(f, CARS[lv], x, H * 0.905 - abs(math.sin(t * 9 + i)) * 8 * u, 150 * u)

        d = ImageDraw.Draw(f)
        for b in bursts:
            b.draw(d, t, u)
        enc.stdin.write(f.convert("RGB").tobytes())
    enc.stdin.close()
    if enc.wait() != 0:
        raise SystemExit("ffmpeg failed")


# ---------------------------------------------------------------- sound

def decode(path, rate=1.0):
    """Decodes an audio file to float32 stereo at SR (optionally pitch-shifted by `rate`)."""
    af = f"asetrate={int(SR * rate)},aresample={SR}" if rate != 1.0 else "anull"
    raw = subprocess.run(["ffmpeg", "-loglevel", "error", "-i", path, "-af", af, "-f", "f32le", "-ac", "2",
                          "-ar", str(SR), "-"], capture_output=True, check=True).stdout
    return np.frombuffer(raw, dtype=np.float32).reshape(-1, 2).copy()


def make_audio(path):
    n = int(DURATION * SR)
    mix = np.zeros((n, 2), np.float32)

    def add(name, at, gain=1.0, rate=1.0):
        s = decode(os.path.join(RAW, name + ".ogg"), rate) * gain
        i = int(at * SR)
        j = min(n, i + len(s))
        if i < n:
            mix[i:j] += s[: j - i]

    music = decode(os.path.join(RAW, "music_loop.ogg"))
    reps = int(math.ceil(n / len(music)))
    bed = np.tile(music, (reps, 1))[:n] * 0.33
    env = np.ones(n, np.float32)
    env[: int(0.6 * SR)] = np.linspace(0, 1, int(0.6 * SR))
    env[-int(1.2 * SR):] = np.linspace(1, 0, int(1.2 * SR))
    mix += bed * env[:, None]

    # scene 1
    add("sfx_whoosh", 0.15, 0.8)
    add("sfx_merge_04", 0.75, 0.9)
    add("sfx_whoosh", 0.95, 0.5, 1.2)
    add("sfx_honk", 1.35, 0.9)
    # scene 2: two cars slide together and pop
    add("sfx_drop", 3.45, 0.6)
    for i, (tm, lv) in enumerate([(4.1, 0), (5.25, 1), (6.25, 2)]):
        add("sfx_whoosh", tm - 0.55, 0.45, 1.3)
        add(f"sfx_merge_{lv + 2:02d}", tm, 1.0)
        if i >= 1:
            add(f"sfx_combo_{i}", tm + 0.05, 0.8)
    add("sfx_brum", 4.25, 0.5)
    # scene 3: each car pops in with a rising note
    for i in range(11):
        add(f"sfx_merge_{i + 1:02d}", 7.35 + i * 0.27, 0.55)
    add("sfx_beep", 10.1, 0.7)
    # scene 4
    add("sfx_unlock", 11.05, 1.0)
    add("sfx_gift", 11.6, 0.6)
    add("sfx_whoosh", 12.9, 0.7)
    add("sfx_tap", 13.3, 0.6)
    add("sfx_tap", 13.6, 0.6, 1.2)
    add("sfx_honk", 14.0, 0.7)
    # scene 5
    add("sfx_win", 15.65, 1.0)
    add("sfx_brum", 16.3, 0.6)
    add("sfx_honk", 17.4, 0.6)
    add("sfx_brum", 17.9, 0.5)

    peak = float(np.abs(mix).max()) or 1.0
    mix = np.clip(mix * (0.89 / peak), -1, 1)
    pcm = (mix * 32767).astype("<i2").tobytes()
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-f", "s16le", "-ar", str(SR), "-ac", "2", "-i", "-",
                    path], input=pcm, check=True)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--vertical", action="store_true", help="1080 x 1920 for YouTube Shorts")
    ap.add_argument("--end", default="Free on Google Play", help="text on the end-card button")
    ap.add_argument("--out", default=None)
    args = ap.parse_args()
    W, H = (1080, 1920) if args.vertical else (1920, 1080)
    out = args.out or os.path.join(ROOT, "promo", "tiny_motors_ad_9x16.mp4" if args.vertical else "tiny_motors_ad_16x9.mp4")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    make_audio(out + ".wav")
    try:
        render(W, H, args.end, out)
    finally:
        if os.path.exists(out + ".wav"):
            os.remove(out + ".wav")
    print("wrote", out)


if __name__ == "__main__":
    main()
