#!/usr/bin/env python3
"""Makes small static Fredoka font files (OFL licence) for the app.

Source: tools/font-source/Fredoka[wdth,wght].ttf from github.com/google/fonts (ofl/fredoka).
Run from the repo root:  python3 tools/make_font.py   (needs: pip install fonttools)
"""
import os
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont
from fontTools import subset

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "tools", "font-source", "Fredoka[wdth,wght].ttf")
OUT = os.path.join(ROOT, "app", "src", "main", "res", "font")

for name, weight in (("fredoka_medium", 500), ("fredoka_bold", 700)):
    font = instantiateVariableFont(TTFont(SRC), {"wght": weight, "wdth": 100}, updateFontNames=True)
    opts = subset.Options()
    opts.layout_features = ["*"]
    opts.name_IDs = ["*"]
    opts.notdef_outline = True
    sub = subset.Subsetter(opts)
    sub.populate(unicodes=list(range(0x20, 0x7F)) + list(range(0xA0, 0x180)) + [0x2019, 0x2026, 0x201C, 0x201D, 0x2013, 0x2014, 0xD7])
    sub.subset(font)
    path = os.path.join(OUT, name + ".ttf")
    font.save(path)
    print(path, os.path.getsize(path) // 1024, "KB")
