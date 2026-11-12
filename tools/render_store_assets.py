"""Grafiki do Google Play i strony (ikona 512/1024 px, grafika 1024x500 PL i EN, ikony strony).

Rysuje ten sam znak co app/src/nonRoot/res/drawable/thumbcast_launcher_foreground.xml (punkt dotyku i dwa kręgi
fali, siatka 108x108) na tle ic_launcher_background (#1D6B5F). Widoczna część ikony adaptacyjnej (72x72 w środku
siatki) wypełnia cały kwadrat, jak w szablonie ikony Google Play (pełny kwadrat, maskę nakłada Play).

Uruchomienie (z katalogu repo): python -I tools/render_store_assets.py
Wymaga Pillow i czcionek Segoe UI z Windows (tylko grafika 1024x500). Wynik: store/ i site/.
"""

import os
import sys

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BG = (0x1D, 0x6B, 0x5F)
DARK = (0x0F, 0x11, 0x15)
WHITE = (255, 255, 255)
SS = 4  # nadpróbkowanie (wygładzanie krawędzi)

# Znak w siatce 108x108: (promień, grubość obrysu albo None = wypełnienie, krycie).
MARK = [(26.0, 3.0, 0.55), (17.0, 4.0, 1.0), (8.0, None, 1.0)]


def draw_mark(img, cx, cy, unit):
    """Rysuje znak o środku (cx, cy); unit = piksele na jednostkę siatki 108."""
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for r, stroke, alpha in MARK:
        a = int(round(255 * alpha))
        if stroke is None:
            R = r * unit
            d.ellipse((cx - R, cy - R, cx + R, cy + R), fill=WHITE + (a,))
        else:
            R = (r + stroke / 2) * unit
            w = max(1, int(round(stroke * unit)))
            d.ellipse((cx - R, cy - R, cx + R, cy + R), outline=WHITE + (a,), width=w)
    img.alpha_composite(layer)


def icon(size):
    big = size * SS
    img = Image.new("RGBA", (big, big), BG + (255,))
    draw_mark(img, big / 2, big / 2, big / 72.0)
    return img.resize((size, size), Image.LANCZOS)


def feature_graphic(title, tagline, path):
    w, h = 1024 * SS, 500 * SS
    img = Image.new("RGBA", (w, h), DARK + (255,))
    # Poziomy gradient od koloru ikony do tła aplikacji.
    grad = Image.new("RGBA", (w, 1))
    for x in range(w):
        t = min(1.0, x / (w * 0.85))
        grad.putpixel((x, 0), tuple(int(BG[i] * (1 - t) + DARK[i] * t) for i in range(3)) + (255,))
    img = grad.resize((w, h))
    draw_mark(img, 250 * SS, h / 2, 5.0 * SS)
    d = ImageDraw.Draw(img)
    fonts = "C:/Windows/Fonts"
    x = 430 * SS
    max_w = (1024 - 48) * SS - x  # prawy margines 48 px

    def fit(font_file, size):
        # Największy rozmiar do `size`, przy którym tekst mieści się w max_w (dłuższe teksty, np. angielski).
        while True:
            f = ImageFont.truetype(os.path.join(fonts, font_file), size * SS)
            if size <= 20 or d.textlength(title if font_file == "segoeuib.ttf" else tagline, font=f) <= max_w:
                return f
            size -= 1

    f_title = fit("segoeuib.ttf", 92)
    f_tag = fit("segoeui.ttf", 38)
    d.text((x, 175 * SS), title, font=f_title, fill=WHITE)
    d.text((x, 295 * SS), tagline, font=f_tag, fill=(0xC9, 0xD1, 0xDE))
    # Play: grafika bez przezroczystości (JPEG albo 24-bitowy PNG).
    img.resize((1024, 500), Image.LANCZOS).convert("RGB").save(path, optimize=True)


def main():
    store = os.path.join(ROOT, "store")
    site = os.path.join(ROOT, "site")
    os.makedirs(store, exist_ok=True)
    os.makedirs(site, exist_ok=True)
    # Ikona Google Play: 512x512, 32-bitowy PNG; 1024 do innych zastosowań (strona, prasa).
    icon(512).save(os.path.join(store, "icon-512.png"), optimize=True)
    icon(1024).save(os.path.join(store, "icon-1024.png"), optimize=True)
    icon(192).save(os.path.join(site, "icon-192.png"), optimize=True)
    icon(32).save(os.path.join(site, "favicon.png"), optimize=True)
    feature_graphic("Thumbcast", "Play your PC games on your phone", os.path.join(store, "feature-graphic-en.png"))
    feature_graphic("Thumbcast", "Graj w gry z PC na telefonie", os.path.join(store, "feature-graphic-pl.png"))
    for name in sorted(os.listdir(store)):
        print(os.path.join("store", name))
    return 0


if __name__ == "__main__":
    sys.exit(main())
