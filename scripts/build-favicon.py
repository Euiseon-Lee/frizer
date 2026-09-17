"""Generate the browser tab icons from the dedicated Choco head cutout.

Requires Pillow. Run from any directory with Python. The source next to this
script (user-provided transparent head cutout) is trimmed to its alpha bounding
box and padded to a square, then written as favicon.ico (16/32/48, alpha kept)
and a 180px apple-touch-icon.png on a white background because iOS fills alpha
with black. Replace the source file and rerun to change the icons.
"""
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
STATIC = ROOT / "src/main/resources/static"
SOURCE = Path(__file__).resolve().parent / "choco-favicon.png"
MARGIN = 0.02  # Transparent breathing room around the head, per side.


def square_head(image):
    trimmed = image.crop(image.getchannel("A").getbbox())
    edge = round(max(trimmed.size) * (1 + 2 * MARGIN))
    square = Image.new("RGBA", (edge, edge), (0, 0, 0, 0))
    square.paste(trimmed, ((edge - trimmed.width) // 2, (edge - trimmed.height) // 2))
    return square


def main():
    with Image.open(SOURCE) as image:
        head = square_head(image.convert("RGBA"))
    favicon = head.resize((48, 48), Image.Resampling.LANCZOS)
    favicon.save(STATIC / "favicon.ico", sizes=[(16, 16), (32, 32), (48, 48)])
    touch = Image.new("RGBA", (180, 180), (255, 255, 255, 255))
    touch.alpha_composite(head.resize((180, 180), Image.Resampling.LANCZOS))
    touch.convert("RGB").save(STATIC / "apple-touch-icon.png", optimize=True)
    for name in ("favicon.ico", "apple-touch-icon.png"):
        print(f"{name}: {(STATIC / name).stat().st_size:,} bytes")


if __name__ == "__main__":
    main()
