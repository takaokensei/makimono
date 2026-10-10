import re
from collections import Counter
from pathlib import Path

def main():
    root = Path(__file__).resolve().parents[2]
    baseline_path = root / "app" / "lint-baseline.xml"
    if not baseline_path.exists():
        print(f"Error: {baseline_path} not found")
        return

    content = baseline_path.read_text(encoding="utf-8")
    issues = re.findall(r'<issue\s+id="([^"]+)"', content)
    counts = Counter(issues)

    print(f"Total issues in baseline: {len(issues)}")
    print("\n| Categoria (Issue ID) | Ocorrências |")
    print("|---|---|")
    for issue_id, count in counts.most_common():
        print(f"| `{issue_id}` | {count} |")
    print(f"| **Total** | **{sum(counts.values())}** |")

if __name__ == "__main__":
    main()
