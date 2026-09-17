"""Generate web derivatives without changing approved PNG originals.

Requires Pillow with WebP support. Run from any directory with Python.
Bump VERSION and the app URLs when changing the generated assets; published
version directories are immutable and may be cached for a year.
"""
from pathlib import Path
from PIL import Image

VERSION = "v1"
ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "src/main/resources/static/assets/choco"
OUTPUT = SOURCE / "web" / VERSION
MAX_EDGE = 660  # Largest 220 CSS-pixel slot at 3x device pixel ratio.


def main():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    original_bytes = web_bytes = 0
    sources = sorted(SOURCE.glob("*.png"))
    for source in sources:
        with Image.open(source) as image:
            image = image.convert("RGBA")
            image.thumbnail((MAX_EDGE, MAX_EDGE), Image.Resampling.LANCZOS)
            target = OUTPUT / (source.stem + ".webp")
            image.save(target, "WEBP", quality=85, alpha_quality=100, method=6)
        original_bytes += source.stat().st_size
        web_bytes += target.stat().st_size
    print(f"{len(sources)} images: {original_bytes:,} -> {web_bytes:,} bytes "
          f"({100 * (1 - web_bytes / original_bytes):.1f}% smaller)")


if __name__ == "__main__":
    main()
