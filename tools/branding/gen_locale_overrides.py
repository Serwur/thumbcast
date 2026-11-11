#!/usr/bin/env python3
# THUMBCAST: generator nadpisań tekstów z marką upstreamu dla lokalizacji innych niż domyślna i polska.
#
# Czyta app/src/main/res/values-*/strings.xml (upstream, nie edytujemy) i zapisuje
# app/src/nonRoot/res/values-*/strings_branding.xml z tymi samymi tekstami, w których
# „Moonlight” w znaczeniu „ta aplikacja” zamieniono na „Thumbcast”. Atrybucja i nazwy
# narzędzi upstreamu zostają (patrz docs/branding.md).
#
# values/ i values-pl/ są pisane ręcznie i pomijane.
# Uruchom po każdym scaleniu upstreamu:  python tools/branding/gen_locale_overrides.py
# Wynik: lista zmienionych kluczy; klucze z „Moonlight” spoza listy są zgłaszane do decyzji.

import glob
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
MAIN_RES = os.path.join(ROOT, "app", "src", "main", "res")
FLAVOR_RES = os.path.join(ROOT, "app", "src", "nonRoot", "res")
OUT_NAME = "strings_branding.xml"
HAND_WRITTEN = {"values", "values-pl"}

# Teksty, w których „Moonlight” oznacza tę aplikację (kategoria a).
REBRAND_KEYS = [
    "nettest_text_waiting",
    "nettest_text_success",
    "nettest_text_failure",
    "nettest_text_blocked",
    "message_decoding_error",
    "summary_seekbar_deadzone",
    "summary_checkbox_usb_bind_all",
    "summary_language_list",
    "local_network_rationale",
    "error_manager_not_running",  # tylko values-ja
    "text_native_res_dialog",  # tylko values-ko (zastrzeżenie odpowiedzialności)
]
# Teksty, w których „Moonlight” to atrybucja albo usługa upstreamu (kategoria b): zostają.
KEEP_KEYS = {"nettest_text_inconclusive", "summary_privacy_policy"}

# Nazwa narzędzia upstreamu (Moonlight Internet Hosting Tool i tłumaczenia) zostaje.
TOOL_NAME = re.compile(r"Moonlight(?=\s*(?:Internet|İnternet|Internett|互联网))")
STRING_RE = re.compile(r'<string name="([^"]+)"([^>]*)>(.*?)</string>', re.S)

HEADER = """<?xml version="1.0" encoding="utf-8"?>
<!-- THUMBCAST: plik wygenerowany przez tools/branding/gen_locale_overrides.py, nie edytować ręcznie.
     Nadpisuje teksty upstreamu (app/src/main/res/{dir}/strings.xml), w których „Moonlight”
     oznaczało tę aplikację, żeby nie sugerować oficjalnej aplikacji Moonlight.
     Atrybucja i nazwy narzędzi upstreamu zostają. Uzasadnienie: docs/branding.md. -->
<resources>
"""


def rebrand(value: str) -> str:
    protected = TOOL_NAME.sub("\0", value)
    return protected.replace("Moonlight", "Thumbcast").replace("\0", "Moonlight")


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8")
    unknown = []
    for path in sorted(glob.glob(os.path.join(MAIN_RES, "values-*", "strings.xml"))):
        res_dir = os.path.basename(os.path.dirname(path))
        if res_dir in HAND_WRITTEN:
            continue
        with open(path, encoding="utf-8") as f:
            text = f.read()
        found = {m.group(1): m for m in STRING_RE.finditer(text)}
        lines = []
        for key in REBRAND_KEYS:
            m = found.get(key)
            if not m or "Moonlight" not in m.group(3):
                continue
            new_value = rebrand(m.group(3))
            if new_value != m.group(3):
                lines.append(f'    <string name="{key}"{m.group(2)}>{new_value}</string>\n')
        for key, m in found.items():
            if "Moonlight" in m.group(3) and key not in REBRAND_KEYS and key not in KEEP_KEYS:
                unknown.append(f"{res_dir}: {key}")

        out_dir = os.path.join(FLAVOR_RES, res_dir)
        out_path = os.path.join(out_dir, OUT_NAME)
        if not lines:
            if os.path.exists(out_path):
                os.remove(out_path)
            continue
        os.makedirs(out_dir, exist_ok=True)
        with open(out_path, "w", encoding="utf-8", newline="\n") as f:
            f.write(HEADER.format(dir=res_dir))
            f.writelines(lines)
            f.write("</resources>\n")
        print(f"{res_dir}: {len(lines)}")

    if unknown:
        print("DO DECYZJI (klucze z „Moonlight” spoza list):", *unknown, sep="\n  ")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
