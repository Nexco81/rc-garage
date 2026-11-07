#!/usr/bin/env python3
"""Generate launcher resources from the unchanged official PNG. Requires Pillow."""
from hashlib import sha256
from math import sqrt
from pathlib import Path

from PIL import Image, ImageDraw

ANDROID = Path(__file__).resolve().parents[1]
RES = ANDROID / "app/src/main/res"
SOURCE = RES / "drawable-nodpi/rc_garage_icon_artwork.png"
SOURCE_SHA256 = "3fff8cc0a7e67919197efffe8569c245ce4d12d2e6d8476f48db169c173c4728"
# A 46 dp square fits entirely inside Android's 66 dp safe-zone circle.
ARTWORK_DP = 46
LAYER_DP = 108
VISIBLE_DP = 72
DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def render(source, size, rounded=False):
    image = Image.new("RGBA", (size, size), (0, 0, 0, 255))
    side = round(size * ARTWORK_DP / VISIBLE_DP)
    artwork = source.resize((side, side), Image.Resampling.LANCZOS)
    offset = (size - side) // 2
    image.alpha_composite(artwork, (offset, offset))
    alpha = Image.new("L", (size, size), 0)
    draw = ImageDraw.Draw(alpha)
    if rounded:
        draw.ellipse((0, 0, size - 1, size - 1), fill=255)
    else:
        draw.rounded_rectangle((0, 0, size - 1, size - 1), radius=round(size * 0.2), fill=255)
    image.putalpha(alpha)
    return image


def main():
    assert sha256(SOURCE.read_bytes()).hexdigest() == SOURCE_SHA256, "Official source PNG changed"
    assert ARTWORK_DP * sqrt(2) < 66, "Artwork exceeds adaptive-icon safe zone"
    with Image.open(SOURCE) as original:
        assert original.format == "PNG" and original.size == (1254, 1254)
        source = original.convert("RGBA")
    for density, size in DENSITIES.items():
        folder = RES / f"mipmap-{density}"
        folder.mkdir(parents=True, exist_ok=True)
        for name, rounded in [("ic_launcher", False), ("ic_launcher_round", True)]:
            render(source, size, rounded).save(folder / f"{name}.png", optimize=True)
    # This sheet simulates masks/backgrounds for review; it is not a device screenshot.
    sheet = Image.new("RGB", (512, 288), "white")
    draw = ImageDraw.Draw(sheet)
    for column, rounded in enumerate([False, True]):
        for row, background in enumerate(["#f5f5f5", "#202124"]):
            x, y = column * 256, row * 144
            draw.rectangle((x, y, x + 255, y + 143), fill=background)
            tile = render(source, 112, rounded)
            sheet.paste(tile, (x + 72, y + 16), tile)
    sheet.save(ANDROID / "docs/APP_ICON_MASK_PREVIEW.png", optimize=True)
    assert sha256(SOURCE.read_bytes()).hexdigest() == SOURCE_SHA256
    print("Generated 10 density icons and mask preview; original PNG unchanged.")


if __name__ == "__main__":
    main()
