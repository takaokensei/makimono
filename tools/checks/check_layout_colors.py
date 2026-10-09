#!/usr/bin/env python3
"""Prevent new raw hex colors from bypassing Android layout color resources."""

from pathlib import Path
import re
import sys


ROOT = Path(__file__).resolve().parents[2]
LAYOUT_ROOT = ROOT / "app/src/main/res"
HEX_COLOR = re.compile(r"#[0-9A-Fa-f]{6,8}")


def main() -> int:
    violations: list[str] = []
    for path in LAYOUT_ROOT.rglob("*.xml"):
        if not path.parent.name.startswith("layout"):
            continue
        for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            if HEX_COLOR.search(line):
                violations.append(f"{path.relative_to(ROOT)}:{line_number}: raw layout color")
    if violations:
        print("Raw hex colors found in layout XML:")
        print("\n".join(f"- {item}" for item in violations))
        return 1
    print("No raw hex color literals remain in layout XML.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
