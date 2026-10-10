#!/usr/bin/env python3
"""
Verify that no direct android.util.Log calls exist outside of AppLog.kt.
Exits 0 if zero occurrences found.
Exits 1 if direct Log calls exist, printing file and line number.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SRC_DIR = ROOT / "app" / "src" / "main" / "java"
ALLOWLIST = {"AppLog.kt"}

PATTERN = re.compile(r'\bLog\.[vdiwe]\s*\(')

def main():
    violations = []
    for kt_file in SRC_DIR.rglob("*.kt"):
        if kt_file.name in ALLOWLIST:
            continue
        try:
            lines = kt_file.read_text(encoding="utf-8").splitlines()
        except UnicodeDecodeError:
            continue
        for idx, line in enumerate(lines, 1):
            # Ignora comentarios
            trimmed = line.strip()
            if trimmed.startswith("//") or trimmed.startswith("*"):
                continue
            if PATTERN.search(line):
                rel_path = kt_file.relative_to(ROOT)
                violations.append((rel_path, idx, line.strip()))

    if violations:
        print(f"[FAIL] Found {len(violations)} direct android.util.Log calls outside AppLog.kt:", file=sys.stderr)
        for path, line_no, content in violations:
            print(f"  {path}:{line_no}: {content}", file=sys.stderr)
        return 1

    print("[OK] Zero direct android.util.Log calls outside AppLog.kt.")
    return 0

if __name__ == "__main__":
    sys.exit(main())
