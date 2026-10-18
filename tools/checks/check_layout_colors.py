#!/usr/bin/env python3
"""Prevent new raw hex colors from bypassing Android layout color resources."""

import json
from pathlib import Path
import re
import sys


ROOT = Path(__file__).resolve().parents[2]
LAYOUT_ROOT = ROOT / "app/src/main/res"
BASELINE_FILE = ROOT / "config/layout-colors-baseline.json"
HEX_COLOR = re.compile(r"#[0-9A-Fa-f]{6,8}")


def scan_violations() -> dict[str, list[int]]:
    findings: dict[str, list[int]] = {}
    for path in sorted(LAYOUT_ROOT.rglob("*.xml")):
        if not path.parent.name.startswith("layout"):
            continue
        rel_path = path.relative_to(ROOT).as_posix()
        lines = path.read_text(encoding="utf-8").splitlines()
        file_issues = [
            line_no for line_no, line in enumerate(lines, 1)
            if HEX_COLOR.search(line)
        ]
        if file_issues:
            findings[rel_path] = file_issues
    return findings


def main() -> int:
    current = scan_violations()
    if "--write-baseline" in sys.argv:
        BASELINE_FILE.parent.mkdir(parents=True, exist_ok=True)
        BASELINE_FILE.write_text(
            json.dumps(current, indent=2, sort_keys=True) + "\n",
            encoding="utf-8"
        )
        total = sum(len(v) for v in current.values())
        print(f"[OK] Baseline escrita em {BASELINE_FILE.relative_to(ROOT)} ({total} itens em {len(current)} arquivos).")
        return 0

    baseline: dict[str, list[int]] = {}
    if BASELINE_FILE.exists():
        try:
            baseline = json.loads(BASELINE_FILE.read_text(encoding="utf-8"))
        except Exception:
            baseline = {}

    new_violations: list[str] = []
    for file, line_numbers in current.items():
        base_lines = set(baseline.get(file, []))
        for line_no in line_numbers:
            if line_no not in base_lines:
                new_violations.append(f"{file}:{line_no}: raw layout color")

    if new_violations:
        print("Novas cores hex brutas encontradas em layout XML (não catalogadas na baseline):")
        print("\n".join(f"- {item}" for item in new_violations))
        return 1

    total_current = sum(len(v) for v in current.values())
    total_baseline = sum(len(v) for v in baseline.values())
    print(f"[OK] Layout colors check passou: 0 novas violações (atuais: {total_current} <= baseline: {total_baseline}).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
