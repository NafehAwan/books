#!/usr/bin/env python3
"""Synthesises every Tiny Motors sound from scratch (no downloaded audio).

Writes WAV files with the standard `wave` module + NumPy into tools/out/audio/, then encodes
them to small OGG Vorbis files in app/src/main/res/raw/ with ffmpeg (if ffmpeg is missing the
WAV files are copied instead, which also play fine on Android).

Run from the repo root:  python3 tools/make_audio.py
"""
from __future__ import annotations

import os
import shutil
import subprocess
import wave

import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WAV_DIR = os.path.join(ROOT, "tools", "out", "audio")
RAW = os.path.join(ROOT, "app", "src", "main", "res", "raw")
SR = 44100
rng = np.random.default_rng(7)


# ---------- building blocks ----------
def t_axis(dur: float) -> np.ndarray:
    return np.arange(int(dur * SR)) / SR


def env_adsr(n: int, a=0.005, d=0.05, s=0.6, r=0.1) -> np.ndarray:
    a_n, d_n, r_n = int(a * SR), int(d * SR), int(r * SR)
    s_n = max(n - a_n - d_n - r_n, 0)
    e = np.concatenate([
        np.linspace(0, 1, a_n, endpoint=False),
        np.linspace(1, s, d_n, endpoint=False),
        np.full(s_n, s),
        np.linspace(s, 0, r_n),
    ])
    return np.pad(e, (0, max(0, n - len(e))))[:n]


def midi_hz(m: float) -> float:
    return 440.0 * 2 ** ((m - 69) / 12)


def mallet(freq: float, dur: float = 1.2, bright: float = 1.0, decay: float = 3.5) -> np.ndarray:
    """Marimba / xylophone style bar: fundamental + inharmonic overtones, soft attack."""
    t = t_axis(dur)
    tone = (np.sin(2 * np.pi * freq * t) * np.exp(-decay * t)
            + 0.35 * bright * np.sin(2 * np.pi * freq * 3.93 * t) * np.exp(-decay * 3.2 * t)
            + 0.12 * bright * np.sin(2 * np.pi * freq * 9.2 * t) * np.exp(-decay * 7 * t))
    attack = np.minimum(t / 0.003, 1.0)
    return tone * attack


def bell(freq: float, dur: float = 1.0) -> np.ndarray:
    t = t_axis(dur)
    partials = [(1, 1.0, 2.5), (2.0, 0.5, 3.5), (3.01, 0.28, 5), (4.2, 0.14, 7), (5.4, 0.08, 9)]
    out = sum(a * np.sin(2 * np.pi * freq * k * t) * np.exp(-dk * t) for k, a, dk in partials)
    return out * np.minimum(t / 0.002, 1.0)


def mix_at(buf: np.ndarray, snd: np.ndarray, start: float, gain: float = 1.0) -> None:
    i = int(start * SR)
    if i >= len(buf):
        return
    j = min(len(buf), i + len(snd))
    buf[i:j] += snd[: j - i] * gain


def lowpass(x: np.ndarray, cutoff: float) -> np.ndarray:
    # one-pole low-pass, run twice for a gentler slope
    a = np.exp(-2 * np.pi * cutoff / SR)
    for _ in range(2):
        y = np.empty_like(x)
        acc = 0.0
        for k in range(len(x)):
            acc = (1 - a) * x[k] + a * acc
            y[k] = acc
        x = y
    return x


def soft_reverb(x: np.ndarray, amount: float = 0.25) -> np.ndarray:
    out = x.copy()
    for delay, g in ((0.031, 0.5), (0.047, 0.4), (0.071, 0.3), (0.113, 0.22), (0.167, 0.15)):
        d = int(delay * SR)
        out[d:] += x[:-d] * g * amount
    return out


def normalise(x: np.ndarray, peak: float = 0.85) -> np.ndarray:
    m = np.max(np.abs(x))
    return x if m == 0 else x / m * peak


def fade(x: np.ndarray, fin: float = 0.002, fout: float = 0.03) -> np.ndarray:
    x = x.copy()
    a, b = int(fin * SR), int(fout * SR)
    if a:
        x[:a] *= np.linspace(0, 1, a)
    if b:
        x[-b:] *= np.linspace(1, 0, b)
    return x


def write(name: str, x: np.ndarray, peak: float = 0.85) -> None:
    os.makedirs(WAV_DIR, exist_ok=True)
    x = normalise(fade(x), peak)
    pcm = (np.clip(x, -1, 1) * 32767).astype("<i2")
    with wave.open(os.path.join(WAV_DIR, name + ".wav"), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


# ---------- the sounds ----------
def sfx_tap():
    t = t_axis(0.12)
    f = 900 * np.exp(-t * 12)
    write("sfx_tap", np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 35), 0.6)


def sfx_click():
    t = t_axis(0.08)
    write("sfx_click", (np.sin(2 * np.pi * 1400 * t) + 0.4 * np.sin(2 * np.pi * 2100 * t)) * np.exp(-t * 70), 0.55)


def sfx_drop():
    t = t_axis(0.25)
    f = 260 * np.exp(-t * 6) + 90
    body = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 18)
    write("sfx_drop", body + 0.05 * rng.standard_normal(len(t)) * np.exp(-t * 60), 0.7)


def sfx_brum():
    """Cute little engine: low buzzy tone revving up then away, with a putt-putt tremolo."""
    t = t_axis(0.9)
    f = 70 + 45 * np.sin(np.pi * np.minimum(t / 0.55, 1.0)) + 30 * t
    phase = 2 * np.pi * np.cumsum(f) / SR
    saw = sum(np.sin(k * phase) / k for k in range(1, 9))
    putt = 0.65 + 0.35 * np.sin(2 * np.pi * (14 + 10 * t) * t)
    x = lowpass(saw * putt, 900) * env_adsr(len(t), 0.03, 0.1, 0.8, 0.35)
    write("sfx_brum", x, 0.7)


def sfx_merges():
    # 11 chimes climbing a C-major pentatonic ladder, brighter as the cars get fancier.
    steps = [72, 74, 76, 79, 81, 84, 86, 88, 91, 93, 96]
    for i, m in enumerate(steps):
        f = midi_hz(m)
        x = mallet(f, 0.7, bright=0.8 + i * 0.05, decay=5.0)
        mix_at(x, mallet(f * 1.5, 0.6, decay=6.0), 0.0, 0.5)  # a fifth on top
        pop_t = t_axis(0.06)
        x[: len(pop_t)] += 0.25 * np.sin(2 * np.pi * (500 + 40 * i) * pop_t) * np.exp(-pop_t * 60)
        write(f"sfx_merge_{i + 1:02d}", soft_reverb(x, 0.3), 0.75)


def sfx_combos():
    base = [79, 81, 84, 86]
    for i, m in enumerate(base):
        buf = np.zeros(int(0.9 * SR))
        for k, step in enumerate((0, 4, 7, 12)):
            mix_at(buf, bell(midi_hz(m + step), 0.6), k * 0.06, 0.7)
        write(f"sfx_combo_{i + 1}", soft_reverb(buf, 0.35), 0.7)


def sfx_unlock():
    buf = np.zeros(int(1.8 * SR))
    notes = [(72, 0.0), (76, 0.12), (79, 0.24), (84, 0.36)]
    for m, s in notes:
        mix_at(buf, mallet(midi_hz(m), 0.8, decay=4), s, 0.8)
    for m in (84, 88, 91):
        mix_at(buf, bell(midi_hz(m), 1.3), 0.5, 0.45)
    t = t_axis(0.9)
    sparkle = sum(np.sin(2 * np.pi * midi_hz(96 + d) * t) * np.exp(-6 * t) for d in (0, 4, 7))
    mix_at(buf, sparkle * 0.25, 0.6)
    write("sfx_unlock", soft_reverb(buf, 0.35), 0.8)


def horn_note(f: float, dur: float) -> np.ndarray:
    t = t_axis(dur)
    phase = 2 * np.pi * f * t
    sq = sum(np.sin(k * phase) / k for k in (1, 3, 5, 7))
    x = sq + 0.6 * sum(np.sin(k * phase * 1.26) / k for k in (1, 3, 5))  # major-third two-tone horn
    return lowpass(x, 2200) * env_adsr(len(t), 0.01, 0.03, 0.85, 0.04)


def sfx_beep():
    buf = np.zeros(int(0.55 * SR))
    mix_at(buf, horn_note(440, 0.16), 0.0)
    mix_at(buf, horn_note(440, 0.2), 0.24)
    write("sfx_beep", buf, 0.6)


def sfx_honk():
    buf = np.zeros(int(0.45 * SR))
    mix_at(buf, horn_note(392, 0.32), 0.0)
    write("sfx_honk", buf, 0.6)


def sfx_whoosh():
    t = t_axis(0.7)
    noise = rng.standard_normal(len(t))
    sweep = np.zeros_like(noise)
    # band-ish filter that sweeps up: difference of two low-passes with moving cutoff (approx.)
    lp = lowpass(noise, 3000) - lowpass(noise, 400)
    env = np.sin(np.pi * np.clip(t / 0.7, 0, 1)) ** 2
    sweep = lp * env
    chain = 0.3 * np.sin(2 * np.pi * 1800 * t) * (np.sin(2 * np.pi * 22 * t) > 0.6) * np.exp(-t * 8)
    write("sfx_whoosh", sweep + chain, 0.65)


def sfx_win():
    buf = np.zeros(int(3.2 * SR))
    melody = [(72, 0.0), (76, 0.18), (79, 0.36), (84, 0.54), (79, 0.8), (84, 0.98), (88, 1.16), (91, 1.4)]
    for m, s in melody:
        mix_at(buf, mallet(midi_hz(m), 1.0, decay=3.5), s, 0.8)
    for m in (60, 64, 67, 72):
        mix_at(buf, mallet(midi_hz(m), 2.0, bright=0.5, decay=1.6), 1.4, 0.35)
    for k in range(6):
        mix_at(buf, bell(midi_hz(96 + (k % 3) * 4), 0.8), 1.5 + k * 0.12, 0.2)
    write("sfx_win", soft_reverb(buf, 0.4), 0.8)


def sfx_gift():
    buf = np.zeros(int(1.0 * SR))
    for k, m in enumerate((84, 88, 91, 96)):
        mix_at(buf, bell(midi_hz(m), 0.7), k * 0.07, 0.6)
    write("sfx_gift", soft_reverb(buf, 0.35), 0.7)


def music_loop():
    """Calm, happy marimba loop, 100 bpm, 30 bars of 4/4 = 72 s, seamless loop."""
    bpm = 100
    beat = 60 / bpm
    bars = 30
    length = bars * 4 * beat
    tail = 3.0
    buf = np.zeros(int((length + tail) * SR))
    prog = [(60, [0, 4, 7]), (57, [0, 3, 7]), (53, [0, 4, 7]), (55, [0, 4, 7])]  # C Am F G
    prog_b = [(53, [0, 4, 7]), (55, [0, 4, 7]), (60, [0, 4, 7]), (57, [0, 3, 7])]  # F G C Am
    pent = [0, 2, 4, 7, 9]
    mrng = np.random.default_rng(2024)

    def phrase(seed_chords):
        # one 4-bar melodic phrase built on chord tones and the pentatonic scale
        notes = []
        for bar, (root, chord) in enumerate(seed_chords):
            rhythm = [0, 1, 1.5, 2, 3] if bar % 2 == 0 else [0, 0.5, 1, 2, 2.5, 3]
            for r in rhythm:
                if mrng.random() < 0.18:
                    continue
                if r in (0, 2):
                    pitch = 72 + (root - 60) % 12 + chord[mrng.integers(0, 3)]
                else:
                    pitch = 72 + pent[mrng.integers(0, 5)]
                notes.append((bar * 4 + r, pitch))
        return notes

    phrase_a = phrase(prog)
    phrase_b = phrase(prog_b)
    form = ["A", "A", "B", "A", "A", "B", "A"]  # 7 phrases x 4 bars = 28 bars, +2 bar outro-to-loop
    for p, sec in enumerate(form):
        chords = prog if sec == "A" else prog_b
        mel = phrase_a if sec == "A" else phrase_b
        bar0 = p * 4
        for bar, (root, chord) in enumerate(chords):
            start = (bar0 + bar) * 4 * beat
            # bass on beats 1 and 3
            mix_at(buf, mallet(midi_hz(root - 12), 1.4, bright=0.3, decay=2.2), start, 0.55)
            mix_at(buf, mallet(midi_hz(root - 12 + 7), 1.0, bright=0.3, decay=2.8), start + 2 * beat, 0.4)
            # gentle broken chord on off-beats
            for k, c in enumerate(chord):
                mix_at(buf, mallet(midi_hz(root + c), 0.9, bright=0.5, decay=3.5), start + (0.5 + k) * beat, 0.22)
        for pos, pitch in mel:
            vel = 0.45 if sec == "A" else 0.4
            mix_at(buf, mallet(midi_hz(pitch), 1.0, bright=0.9, decay=3.2), (bar0 * 4 + pos) * beat, vel)
        # soft shaker on eighth notes
    sh = rng.standard_normal(int(0.05 * SR)) * np.exp(-np.arange(int(0.05 * SR)) / SR * 80)
    sh = sh - lowpass(sh, 3000)
    for e in range(int(bars * 8)):
        mix_at(buf, sh, e * beat / 2, 0.05 if e % 2 else 0.08)
    # last two bars: return to C, so the loop lands home
    for bar in range(28, 30):
        start = bar * 4 * beat
        mix_at(buf, mallet(midi_hz(48), 1.4, bright=0.3, decay=2.2), start, 0.55)
        for k, c in enumerate([0, 4, 7, 12]):
            mix_at(buf, mallet(midi_hz(72 + c), 1.0, decay=3.2), start + k * beat, 0.35)
    buf = soft_reverb(buf, 0.3)
    # wrap the tail around to the start so the loop is seamless
    n = int(length * SR)
    loop = buf[:n].copy()
    loop[: len(buf) - n] += buf[n:]
    os.makedirs(WAV_DIR, exist_ok=True)
    x = normalise(loop, 0.7)
    pcm = (np.clip(x, -1, 1) * 32767).astype("<i2")
    with wave.open(os.path.join(WAV_DIR, "music_loop.wav"), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


def encode_all() -> None:
    os.makedirs(RAW, exist_ok=True)
    ffmpeg = shutil.which("ffmpeg")
    for f in sorted(os.listdir(WAV_DIR)):
        if not f.endswith(".wav"):
            continue
        name = f[:-4]
        src = os.path.join(WAV_DIR, f)
        if ffmpeg:
            dst = os.path.join(RAW, name + ".ogg")
            q = "3" if name == "music_loop" else "4"
            subprocess.run([ffmpeg, "-y", "-loglevel", "error", "-i", src, "-ar", "44100" if name != "music_loop" else "32000",
                            "-c:a", "libvorbis", "-q:a", q, dst], check=True)
        else:
            dst = os.path.join(RAW, f)
            shutil.copy(src, dst)
        print(dst, os.path.getsize(dst) // 1024, "KB")


if __name__ == "__main__":
    for fn in (sfx_tap, sfx_click, sfx_drop, sfx_brum, sfx_merges, sfx_combos, sfx_unlock, sfx_beep,
               sfx_honk, sfx_whoosh, sfx_win, sfx_gift, music_loop):
        fn()
    encode_all()
