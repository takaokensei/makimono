#!/usr/bin/env python3
"""
check_kotlin_style.py — Static Kotlin style and hygiene checker with versioned baseline (T-901).

Checks Kotlin source files under app/src/main/java for:
- tabs instead of 4-space indentation (`no-tabs`)
- lines exceeding 160 characters (`max-line-length`)
- wildcard imports other than kotlinx/java.util (`no-wildcard-imports`)
- printStackTrace / println debugging calls (`no-debug-stdio`)

Violations recorded in `config/ktlint-baseline.json` are tolerated as legacy baseline;
any new violation fails the build with exit code 1.
Use `--write-baseline` to regenerate `config/ktlint-baseline.json`.
"""

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
KT_ROOT = ROOT / "app/src/main/java"
BASELINE_FILE = ROOT / "config/ktlint-baseline.json"
MAX_LINE_LENGTH = 160
ALLOWED_WILDCARDS = {
    "import java.util.*",
    "import kotlinx.coroutines.*",
    "import kotlinx.coroutines.flow.*",
}


def scan_violations() -> dict[str, list[str]]:
    findings: dict[str, list[str]] = {}
    for kt_file in sorted(KT_ROOT.rglob("*.kt")):
        rel_path = kt_file.relative_to(ROOT).as_posix()
        file_issues: list[str] = []
        lines = kt_file.read_text(encoding="utf-8").splitlines()
        for line_no, line in enumerate(lines, start=1):
            if "\t" in line:
                file_issues.append(f"no-tabs:{line_no}")
            if len(line) > MAX_LINE_LENGTH:
                file_issues.append(f"max-line-length:{line_no}")
            stripped = line.strip()
            if stripped.startswith("import ") and stripped.endswith(".*") and stripped not in ALLOWED_WILDCARDS:
                file_issues.append(f"no-wildcard-imports:{stripped}")
            if re.search(r"\b(printStackTrace|println)\s*\(", stripped) and not stripped.startswith("//"):
                file_issues.append(f"no-debug-stdio:{line_no}")
        if file_issues:
            findings[rel_path] = sorted(set(file_issues))
    return findings


def main() -> int:
    current = scan_violations()
    if "--write-baseline" in sys.argv:
        BASELINE_FILE.parent.mkdir(parents=True, exist_ok=True)
        BASELINE_FILE.write_text(json.dumps(current, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        total = sum(len(v) for v in current.values())
        print(f"[OK] Baseline escrita em {BASELINE_FILE.relative_to(ROOT)} ({total} itens em {len(current)} arquivos).")
        return 0

    if not BASELINE_FILE.exists():
        print(f"[FAIL] Baseline ausente: {BASELINE_FILE.relative_to(ROOT)}", file=sys.stderr)
        return 1

    baseline: dict[str, list[str]] = json.loads(BASELINE_FILE.read_text(encoding="utf-8"))
    new_violations: list[str] = []
    for rel_path, issues in current.items():
        allowed = set(baseline.get(rel_path, []))
        for issue in issues:
            if issue not in allowed:
                new_violations.append(f"{rel_path} -> {issue}")

    if new_violations:
        print(f"[FAIL] {len(new_violations)} novas violações de estilo Kotlin fora da baseline:", file=sys.stderr)
        for v in new_violations:
            print(f"  - {v}", file=sys.stderr)
        return 1

    total_baseline = sum(len(v) for v in baseline.values())
    total_current = sum(len(v) for v in current.values())
    print(
        f"[OK] Kotlin style check passou: 0 novas violações "
        f"(atuais: {total_current} <= baseline: {total_baseline})."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
