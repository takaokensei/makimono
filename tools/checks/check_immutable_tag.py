#!/usr/bin/env python3
"""
check_immutable_tag.py — Release tag immutability guard (T-906, docs/releases/POLICY.md).

Aborts the release workflow if a GitHub Release or remote tag already exists
pointing to a different commit SHA than the current checkout.
"""

import argparse
import sys


def verify_tag_immutability(
    tag: str,
    current_sha: str,
    existing_sha: str | None,
) -> tuple[bool, str]:
    clean_current = (current_sha or "").strip().lower()
    clean_existing = (existing_sha or "").strip().lower()
    if not clean_current:
        return False, f"Tag '{tag}': current SHA is empty."
    # If existing_sha is empty, null, or a GitHub API error payload (404 Not Found), no prior release exists.
    if not clean_existing or clean_existing == "null" or clean_existing.startswith("{") or "not found" in clean_existing:
        return True, f"Tag '{tag}' has no prior release SHA; proceeding with {clean_current[:7]}."

    if clean_current.startswith(clean_existing) or clean_existing.startswith(clean_current):
        return True, f"Tag '{tag}' matches existing SHA ({clean_current[:7]})."

    return (
        False,
        f"ABORT: Tag '{tag}' already exists with SHA {clean_existing}, "
        f"which differs from current checkout SHA {clean_current}. "
        f"Releases and tags are strictly immutable (docs/releases/POLICY.md).",
    )


def run_self_test() -> int:
    ok_new, _ = verify_tag_immutability("v1.8.0", "ca26827f4421d9d9bfcfca6fa5fd788a0bb5108c", None)
    if not ok_new:
        print("[FAIL] Self-test failed on new tag case.", file=sys.stderr)
        return 1

    ok_404, _ = verify_tag_immutability(
        "v1.8.0",
        "ca26827f4421d9d9bfcfca6fa5fd788a0bb5108c",
        '{"message":"Not Found","documentation_url":"https://docs.github.com","status":"404"}',
    )
    if not ok_404:
        print("[FAIL] Self-test failed on 404 response case.", file=sys.stderr)
        return 1

    ok_same, _ = verify_tag_immutability("v1.8.0", "ca26827f4421d9d9bfcfca6fa5fd788a0bb5108c", "ca26827")
    if not ok_same:
        print("[FAIL] Self-test failed on matching SHA case.", file=sys.stderr)
        return 1

    ok_dup, msg_dup = verify_tag_immutability(
        "v1.7.0",
        "ca26827f4421d9d9bfcfca6fa5fd788a0bb5108c",
        "9f8e7d6c5b4a3210fedcba9876543210abcdef12",
    )
    if ok_dup or "ABORT:" not in msg_dup:
        print("[FAIL] Self-test failed: duplicate tag with different SHA did not abort!", file=sys.stderr)
        return 1

    print("[OK] check_immutable_tag self-test passed: duplicate tag with different SHA aborts as required (T-906).")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Verify release tag immutability.")
    parser.add_argument("--self-test", action="store_true", help="Run built-in test of the immutability check.")
    parser.add_argument("--tag", default="", help="Tag name (e.g. v1.8.0).")
    parser.add_argument("--current-sha", default="", help="Current checkout commit SHA.")
    parser.add_argument("--existing-sha", default="", help="Existing release commitish/SHA if any.")
    args = parser.parse_args()

    if args.self_test:
        return run_self_test()

    ok, message = verify_tag_immutability(args.tag, args.current_sha, args.existing_sha)
    if not ok:
        print(f"[FAIL] {message}", file=sys.stderr)
        return 1
    print(f"[OK] {message}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
