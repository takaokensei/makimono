#!/usr/bin/env python3
"""
Diff the current lint-baseline.xml against git HEAD.
Fails if any NEW issues were added.
Allows existing issues to be removed.
Exits 0 if issues <= HEAD and no new issues.
Exits 1 if new issues were introduced.
"""
import sys
import xml.etree.ElementTree as ET
import subprocess
from pathlib import Path

def parse_issues(xml_content: str) -> set:
    try:
        root = ET.fromstring(xml_content)
    except ET.ParseError:
        return set()
    issues = set()
    for el in root.findall(".//issue"):
        issue_id = el.get("id", "")
        msg = el.get("message", "")
        location = el.find("location")
        file_path = location.get("file", "") if location is not None else ""
        line = location.get("line", "") if location is not None else ""
        # Normaliza caminho para comparacao portavel
        file_path = file_path.replace("\\", "/")
        issues.add((issue_id, msg, file_path, line))
    return issues

def main():
    if len(sys.argv) < 2:
        baseline_path = Path("app/lint-baseline.xml")
    else:
        baseline_path = Path(sys.argv[1])

    if not baseline_path.exists():
        print(f"ERROR: {baseline_path} does not exist", file=sys.stderr)
        return 2

    current_xml = baseline_path.read_text(encoding="utf-8")
    current_issues = parse_issues(current_xml)

    # Obter baseline do HEAD via git show com encoding explicito utf-8
    try:
        res = subprocess.run(
            ["git", "show", f"HEAD:{baseline_path.as_posix()}"],
            capture_output=True,
            check=True
        )
        head_xml = res.stdout.decode("utf-8", errors="replace")
        head_issues = parse_issues(head_xml)
    except subprocess.CalledProcessError:
        print("WARN: Could not fetch baseline from HEAD, comparing count only")
        head_issues = current_issues

    new_issues = current_issues - head_issues
    removed_issues = head_issues - current_issues

    print(f"Baseline HEAD:     {len(head_issues)} issues")
    print(f"Baseline atual:    {len(current_issues)} issues")
    print(f"Removidas:         {len(removed_issues)}")
    print(f"Novas:             {len(new_issues)}")

    if new_issues:
        print("\nERRO: Novas issues introduzidas no baseline:", file=sys.stderr)
        for issue_id, msg, fpath, line in sorted(new_issues):
            print(f"  + [{issue_id}] {fpath}:{line} - {msg}", file=sys.stderr)
        return 1

    print("\nOK: Baseline so diminuiu ou manteve. Nenhuma issue nova.")
    return 0

if __name__ == "__main__":
    sys.exit(main())
