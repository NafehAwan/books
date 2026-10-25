#!/usr/bin/env python3
"""Helpers for scripts/smoke-test.sh: reads a `uiautomator dump` XML file.

  smoke_ui.py center <xml> <content-desc>      -> "x y" of the first node whose description starts with it
  smoke_ui.py pair <xml>                         -> "x1 y1 x2 y2" of two board cars with the same name
  smoke_ui.py cars <xml>                         -> number of cars on the board
"""
import re
import sys
import xml.etree.ElementTree as ET

CARS = ["Kei car", "City hatch", "Retro bug", "Sedan", "Estate", "SUV", "Pickup", "Coupe",
        "Sports car", "Supercar", "Hypercar"]


def nodes(path):
    for n in ET.parse(path).iter("node"):
        desc = n.get("content-desc") or ""
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.get("bounds", ""))
        if m:
            x1, y1, x2, y2 = map(int, m.groups())
            yield desc, ((x1 + x2) // 2, (y1 + y2) // 2)


def main():
    cmd, path = sys.argv[1], sys.argv[2]
    all_nodes = list(nodes(path))
    if cmd == "center":
        want = sys.argv[3]
        for desc, (x, y) in all_nodes:
            if desc.startswith(want):
                print(x, y)
                return
        sys.exit(f"not found: {want}")
    cars = [(d, c) for d, c in all_nodes if d in CARS]
    if cmd == "cars":
        print(len(cars))
    elif cmd == "pair":
        seen = {}
        for d, c in cars:
            if d in seen and d != "Hypercar":
                print(*seen[d], *c)
                return
            seen[d] = c
        sys.exit("no matching pair")


if __name__ == "__main__":
    main()
