#!/usr/bin/env python3
"""Validate parity between values/strings.xml and values-pt/strings.xml."""

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BASE_STRINGS = ROOT / "app/src/main/res/values/strings.xml"
PT_STRINGS = ROOT / "app/src/main/res/values-pt/strings.xml"


def get_string_keys(path: Path) -> dict[str, str]:
    if not path.exists():
        print(f"Error: {path} does not exist", file=sys.stderr)
        sys.exit(1)
    tree = ET.parse(path)
    root = tree.getroot()
    return {
        el.get("name"): el.text or ""
        for el in root.findall("string")
        if el.get("name") and el.get("translatable", "true").lower() != "false"
    }


def main() -> int:
    base_keys = get_string_keys(BASE_STRINGS)
    pt_keys = get_string_keys(PT_STRINGS)

    missing_in_pt = sorted(set(base_keys.keys()) - set(pt_keys.keys()))
    missing_in_base = sorted(set(pt_keys.keys()) - set(base_keys.keys()))

    has_errors = False

    if missing_in_pt:
        has_errors = True
        print(f"[FAIL] {len(missing_in_pt)} string keys present in values/ but missing in values-pt/:")
        for key in missing_in_pt:
            print(f"  - {key}")

    if missing_in_base:
        has_errors = True
        print(f"[FAIL] {len(missing_in_base)} string keys present in values-pt/ but missing in values/:")
        for key in missing_in_base:
            print(f"  + {key}")

    if has_errors:
        return 1

    print(f"[OK] String parity verified: {len(base_keys)} keys match between values and values-pt.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
