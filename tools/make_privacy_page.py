#!/usr/bin/env python3
"""Builds the public web pages for Google Play from PRIVACY_POLICY.md.

Writes site/index.html (a small page for the app) and site/privacy-policy.html. Those two files
are what the gh-pages branch holds, so GitHub Pages serves them at
https://<user>.github.io/<repo>/privacy-policy.html

Run from the repo root:  python3 tools/make_privacy_page.py
Handles the small Markdown subset the policy uses: #/## headings, paragraphs, "* " lists,
**bold**, _italic_ and <https://links>.
"""
from __future__ import annotations

import html
import os
import re
import shutil

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "site")

STYLE = """
:root { color-scheme: light; --ink:#1d2a44; --muted:#4b5a78; --sky:#dff1ff; --card:#ffffff; --blue:#2f6fe4; }
* { box-sizing: border-box; }
body { margin:0; background:var(--sky); color:var(--ink);
       font: 17px/1.6 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; }
main { max-width: 760px; margin: 0 auto; padding: 24px 16px 48px; }
.card { background:var(--card); border-radius:20px; padding:24px; box-shadow:0 6px 24px rgba(30,60,120,.12); }
header { display:flex; align-items:center; gap:14px; margin-bottom:18px; }
header img { width:64px; height:64px; border-radius:16px; }
h1 { font-size:1.7rem; margin:0; line-height:1.2; }
h2 { font-size:1.2rem; margin:1.6em 0 .4em; color:var(--blue); }
p, li { color:var(--ink); }
.muted, em { color:var(--muted); }
a { color:var(--blue); word-break: break-word; }
ul { padding-left: 1.2em; }
footer { margin-top:20px; font-size:.9rem; color:var(--muted); text-align:center; }
"""


def inline(text: str) -> str:
    text = html.escape(text, quote=False)
    text = re.sub(r"&lt;(https?://[^&\s]+)&gt;", r'<a href="\1">\1</a>', text)
    text = re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", text)
    text = re.sub(r"(?<![\w*])_(.+?)_(?!\w)", r"<em>\1</em>", text)
    return text


def markdown_to_html(md: str) -> tuple[str, str]:
    title, out, para, items = "Privacy policy", [], [], []

    def flush():
        if para:
            out.append(f"<p>{inline(' '.join(para))}</p>")
            para.clear()
        if items:
            out.append("<ul>" + "".join(f"<li>{inline(i)}</li>" for i in items) + "</ul>")
            items.clear()

    for raw in md.splitlines():
        line = raw.rstrip()
        if line.startswith("# "):
            flush()
            title = line[2:].strip()
        elif line.startswith("## "):
            flush()
            out.append(f"<h2>{inline(line[3:].strip())}</h2>")
        elif line.startswith("* "):
            if para:
                flush()
            items.append(line[2:].strip())
        elif line.startswith("  ") and items:
            items[-1] += " " + line.strip()          # continuation of a list item
        elif not line.strip():
            flush()
        else:
            if items:
                flush()
            para.append(line.strip())
    flush()
    return title, "\n".join(out)


def page(title: str, body: str) -> str:
    return f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{html.escape(title)}</title>
<link rel="icon" href="icon.png">
<style>{STYLE}</style>
</head>
<body><main>
<div class="card">
<header><img src="icon.png" alt=""><h1>{html.escape(title)}</h1></header>
{body}
</div>
<footer>Tiny Motors: a merge game for children aged 5 to 10.</footer>
</main></body>
</html>
"""


def main() -> None:
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(ROOT, "PRIVACY_POLICY.md"), encoding="utf-8") as f:
        title, body = markdown_to_html(f.read())
    with open(os.path.join(OUT, "privacy-policy.html"), "w", encoding="utf-8") as f:
        f.write(page(title, body))
    index_body = """
<p>Tiny Motors is a calm, cheerful car merge puzzle for kids aged 5 to 10. Drag two matching cars
together to build a bigger car, fill the Garage sticker book and meet Towy the tow truck.</p>
<h2>Links</h2>
<ul>
<li><a href="privacy-policy.html">Privacy policy</a></li>
</ul>
"""
    with open(os.path.join(OUT, "index.html"), "w", encoding="utf-8") as f:
        f.write(page("Tiny Motors", index_body))
    shutil.copy(os.path.join(ROOT, "store", "play_icon_512.png"), os.path.join(OUT, "icon.png"))
    # GitHub Pages: serve the files as they are (no Jekyll processing)
    open(os.path.join(OUT, ".nojekyll"), "w").close()
    print("wrote", sorted(os.listdir(OUT)))


if __name__ == "__main__":
    main()
