#!/usr/bin/env python3
"""Optional: generate extra artwork with OpenAI's image model (e.g. GPT Image 2).

Not used by the build. Needs your own OpenAI API key and internet access:
    pip install openai
    export OPENAI_API_KEY=sk-...
    python3 tools/gpt_image.py "a cute cartoon garage, bright pastel colours, no text" store/extra.png

The model name can be changed with OPENAI_IMAGE_MODEL (default: gpt-image-2). Anything you
generate is your responsibility to review before using it in a children's app.
"""
import base64
import os
import sys


def main() -> None:
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    prompt, out = sys.argv[1], sys.argv[2]
    if not os.environ.get("OPENAI_API_KEY"):
        sys.exit("Set OPENAI_API_KEY first.")
    from openai import OpenAI  # imported here so the rest of the tools work without it

    client = OpenAI()
    result = client.images.generate(
        model=os.environ.get("OPENAI_IMAGE_MODEL", "gpt-image-2"),
        prompt=prompt,
        size=os.environ.get("OPENAI_IMAGE_SIZE", "1024x1024"),
    )
    os.makedirs(os.path.dirname(out) or ".", exist_ok=True)
    with open(out, "wb") as f:
        f.write(base64.b64decode(result.data[0].b64_json))
    print("saved", out)


if __name__ == "__main__":
    main()
