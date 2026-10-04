#!/usr/bin/env python3
"""Check text contrast for Makimono's six semantic color palettes."""

from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
COLORS = ROOT / "app/src/main/res/values/colors.xml"
PALETTES = ("obsidian", "tn", "dracula", "nord", "mocha", "estuary")


def parse_color(value: str) -> tuple[int, int, int]:
    if not re.fullmatch(r"#[0-9A-Fa-f]{6}", value):
        raise ValueError(f"Expected opaque #RRGGBB token, got {value!r}")
    return tuple(int(value[index:index + 2], 16) for index in (1, 3, 5))


def luminance(rgb: tuple[int, int, int]) -> float:
    channels = [value / 255 for value in rgb]
    linear = [value / 12.92 if value <= 0.04045 else ((value + 0.055) / 1.055) ** 2.4 for value in channels]
    return sum(channel * weight for channel, weight in zip(linear, (0.2126, 0.7152, 0.0722)))


def contrast(first: tuple[int, int, int], second: tuple[int, int, int]) -> float:
    light, dark = sorted((luminance(first), luminance(second)), reverse=True)
    return (light + 0.05) / (dark + 0.05)


def token(xml_colors: dict[str, str], palette: str, role: str) -> tuple[int, int, int]:
    return parse_color(xml_colors[f"{palette}_{role}"])


def main() -> int:
    root = ET.parse(COLORS).getroot()
    colors = {node.attrib["name"]: (node.text or "").strip() for node in root.findall("color")}
    failures: list[str] = []
    print("Palette       role              base    surface")
    for palette in PALETTES:
        base = token(colors, palette, "bg_base")
        surface = token(colors, palette, "bg_surface")
        for role in ("text_primary", "text_secondary"):
            foreground = token(colors, palette, role)
            against_base = contrast(foreground, base)
            against_surface = contrast(foreground, surface)
            print(f"{palette:13} {role:17} {against_base:5.2f}:1  {against_surface:5.2f}:1")
            minimum = 7.0
            if min(against_base, against_surface) < minimum:
                failures.append(f"{palette}.{role} contrast is below {minimum:.1f}:1")

        accent = token(colors, palette, "accent_primary")
        on_primary = token(colors, "obsidian", "on_primary")
        ratio = contrast(accent, on_primary)
        print(f"{palette:13} {'primary_button_text':17} {ratio:5.2f}:1  (accent fill)")
        if ratio < 4.5:
            failures.append(f"{palette}.accent_primary with on_primary is below 4.5:1")

        focused_ratio = contrast(accent, surface)
        print(f"{palette:13} {'focused_cta_text':17} {focused_ratio:5.2f}:1  (surface fill)")
        if focused_ratio < 4.5:
            failures.append(f"{palette}.accent_primary with bg_surface is below 4.5:1")

    if failures:
        print("\nContrast failures:")
        for failure in failures:
            print(f"- {failure}")
        return 1
    print("\nPrimary/secondary text meets 7:1; default and focused action text meet 4.5:1.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
