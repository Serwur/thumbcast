#!/usr/bin/env python3
"""Strażnik presetów: odrzuca zakazane typy wyjść/kontrolek (plan sekcja 5.3, FAIR_PLAY.md).

Sprawdza strukturę JSON, a nie dowolne napisy. Naruszeniem jest:
  - pole "type" (lub "behavior") o wartości z listy zakazanych,
  - klucz obiektu o nazwie z listy zakazanych (np. {"sequence": [...]}).
Etykiety i inne wartości tekstowe (np. "label": "Delay") są dozwolone.

Użycie: check_presets.py [katalog_lub_plik ...]   (domyślnie: presets)
Kod wyjścia: 0 = OK, 1 = naruszenie lub błąd JSON, 2 = brak plików.
"""
import json
import sys
from pathlib import Path

FORBIDDEN = {"sequence", "macro", "repeat", "turbo", "delay", "toggle_auto"}
VALUE_FIELDS = ("type", "behavior")


def walk(node, path, errors):
    if isinstance(node, dict):
        for key, value in node.items():
            here = f"{path}.{key}" if path else key
            if key.strip().lower() in FORBIDDEN:
                errors.append(f"{here}: zakazany klucz '{key}'")
            if key in VALUE_FIELDS and isinstance(value, str) and value.strip().lower() in FORBIDDEN:
                errors.append(f"{here}: zakazana wartość '{value}'")
            walk(value, here, errors)
    elif isinstance(node, list):
        for i, item in enumerate(node):
            walk(item, f"{path}[{i}]", errors)


def collect(args):
    files = []
    for arg in args or ["presets"]:
        p = Path(arg)
        if p.is_dir():
            files.extend(sorted(p.rglob("*.json")))
        elif p.is_file():
            files.append(p)
    return files


def main(argv):
    files = collect(argv[1:])
    if not files:
        print("Brak plików JSON do sprawdzenia")
        return 2
    bad = False
    for f in files:
        try:
            data = json.loads(f.read_text(encoding="utf-8"))
        except (OSError, ValueError) as e:
            print(f"{f}: nie da się wczytać JSON: {e}")
            bad = True
            continue
        errors = []
        walk(data, "", errors)
        for e in errors:
            print(f"{f}: {e}")
            bad = True
    if bad:
        print("Preset zawiera zakazany typ (makro/sekwencja/turbo)")
        return 1
    print(f"OK: {len(files)} plik(ów) presetów bez zakazanych typów")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
