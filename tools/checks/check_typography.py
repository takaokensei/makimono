#!/usr/bin/env python3
"""Typography gate (T1.1).

Counts literal `android:textSize` values in layout XML to track migration
towards the unified `TextAppearance.Makimono.*` design system tokens.

Usage:
    python tools/check_typography.py
    python tools/check_typography.py --max N   # exit 1 if literals > N
"""
from __future__ import annotations

import argparse
import re
import sys
from collections import Counter
from pathlib import Path

RES = Path(__file__).resolve().parent.parent.parent / "app" / "src" / "main" / "res"
LITERAL = re.compile(r'android:textSize="(\d+(?:\.\d+)?)sp"')
TOKEN = re.compile(r"TextAppearance\.Makimono\.")


def scan() -> tuple[Counter[str], Counter[str], int]:
    per_file: Counter[str] = Counter()
    sizes: Counter[str] = Counter()
    tokens = 0
    for xml in sorted(RES.glob("layout*/*.xml")):
        text = xml.read_text(encoding="utf-8")
        found = LITERAL.findall(text)
        if found:
            per_file[f"{xml.parent.name}/{xml.name}"] = len(found)
            sizes.update(found)
        tokens += len(TOKEN.findall(text))
    return per_file, sizes, tokens


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--max", type=int, default=None)
    args = parser.parse_args()

    per_file, sizes, tokens = scan()
    total = sum(per_file.values())
    print(f"Literal textSize: {total} in {len(per_file)} files | token usages: {tokens}")
    print("Distinct sizes (sp: count):",
          ", ".join(f"{s}:{c}" for s, c in sorted(sizes.items(), key=lambda i: float(i[0]))))
    for name, count in per_file.most_common(10):
        print(f"  {count:4d}  {name}")

    if args.max is not None and total > args.max:
        print(f"FAIL: {total} literal sizes exceeds ratchet {args.max}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
