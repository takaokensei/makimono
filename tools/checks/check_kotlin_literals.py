#!/usr/bin/env python3
"""
Verifies that zero hardcoded UI string literals exist in Kotlin calls matching the v6 audit regex (T-720):
- Toast.makeText(..., "...")
- setTitle("...")
- setMessage("...")
- setText("...")
"""

import pathlib
import re
import sys

PATTERN = re.compile(r'Toast\.makeText\([^,]+,\s*"|setTitle\("|setMessage\("|setText\("')


def main() -> int:
    root = pathlib.Path(__file__).resolve().parents[2]
    java_dir = root / "app" / "src" / "main" / "java"
    matches = []
    for kt_file in sorted(java_dir.rglob("*.kt")):
        lines = kt_file.read_text(encoding="utf-8").splitlines()
        for idx, line in enumerate(lines, start=1):
            if PATTERN.search(line):
                rel = kt_file.relative_to(root)
                matches.append((str(rel), idx, line.strip()))

    if matches:
        print(f"[ERRO] {len(matches)} literais Kotlin encontrados (T-720):", file=sys.stderr)
        for rel, line_no, text in matches:
            print(f"  {rel}:{line_no}: {text}", file=sys.stderr)
        return 1

    print("[OK] Zero literais de UI Kotlin (Toast/setTitle/setMessage/setText) encontrados (T-720).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
