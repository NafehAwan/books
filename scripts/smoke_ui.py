#!/usr/bin/env python3
"""Helpers for scripts/smoke-test.sh: reads a `uiautomator dump` XML file.

  smoke_ui.py center <xml> <content-desc>      -> "x y" of the first node whose description starts with it
  smoke_ui.py pair <xml>                         -> "x1 y1 x2 y2" of the lowest two board cars with the same name
  smoke_ui.py cars <xml>                         -> number of cars on the board
  smoke_ui.py count <xml> <content-desc>         -> number of nodes with exactly that description
  smoke_ui.py anycar <xml>                       -> "x y" of the first car on the board
  smoke_ui.py bounds <xml> <content-desc>        -> "[x1,y1][x2,y2]" of the first node starting with it
"""
import re
import sys
import xml.etree.ElementTree as ET

CARS = ["Kei car", "City hatch", "Retro bug", "Sedan", "Estate", "SUV", "Pickup", "Coupe",
        "Sports car", "Supercar", "Hypercar"]


def nodes(path):
    for n in ET.parse(path).iter("node"):
        desc = n.get("content-desc") or n.get("text") or ""
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.get("bounds", ""))
        if m:
            x1, y1, x2, y2 = map(int, m.groups())
            yield desc, ((x1 + x2) // 2, (y1 + y2) // 2)


def main():
    cmd, path = sys.argv[1], sys.argv[2]
    all_nodes = list(nodes(path))
    if cmd == "bounds":
        for n in ET.parse(path).iter("node"):
            if (n.get("content-desc") or n.get("text") or "").startswith(sys.argv[3]):
                print(n.get("bounds"))
                return
        sys.exit(f"not found: {sys.argv[3]}")
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
    elif cmd == "count":
        print(sum(1 for d, _ in all_nodes if d == sys.argv[3]))
    elif cmd == "anycar":
        if not cars:
            sys.exit("no cars")
        print(*cars[0][1])
    elif cmd == "pair":
        # the lowest matching pair, so a fresh board always merges two Kei cars into a City hatch
        for name in CARS[:-1]:
            same = [c for d, c in cars if d == name]
            if len(same) >= 2:
                print(*same[0], *same[1])
                return
        sys.exit("no matching pair")


if __name__ == "__main__":
    main()
