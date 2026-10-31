"""Bind a locally built APK to its revision and effective, non-secret app sources."""
import argparse
import hashlib
import json
import subprocess
from datetime import datetime, timezone
from pathlib import Path


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("apk", type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    apk = args.apk.resolve(strict=True)
    # An allowlist avoids opening credentials, local configuration, signing files,
    # generated BuildConfig, dossier packages, or any ignored artifacts.
    tracked = subprocess.check_output(
        ["git", "ls-files", "-co", "--exclude-standard", "app/src", "gradle", "scripts",
         "app/build.gradle", "build.gradle", "settings.gradle", "gradle.properties"],
        cwd=root, text=True).splitlines()
    files = {}
    for name in sorted(set(tracked)):
        path = root / name
        if path.suffix in {".kt", ".java", ".xml", ".gradle", ".properties", ".toml", ".ps1", ".sh"} and path.is_file():
            if path.name not in {"local.properties", "gradle.properties"}:
                files[name] = digest(path)
    source_digest = hashlib.sha256(json.dumps(files, sort_keys=True).encode()).hexdigest()
    payload = {
        "revision": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip(),
        "createdAt": datetime.now(timezone.utc).isoformat(),
        "apk": apk.name,
        "apkSha256": digest(apk),
        "effectiveSourceSha256": source_digest,
        "sourceFiles": files,
    }
    output = apk.with_suffix(apk.suffix + ".provenance.json")
    output.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    apk.with_suffix(apk.suffix + ".sha256").write_text(payload["apkSha256"] + "  " + apk.name + "\n", encoding="utf-8")
    print(output)


if __name__ == "__main__":
    main()
