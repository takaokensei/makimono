#!/usr/bin/env bash
set -euo pipefail

echo "==> Running design system and parity verifications..."

python3 tools/checks/check_strings.py
python3 tools/checks/check_contrast.py
python3 tools/checks/check_layout_colors.py
python3 tools/checks/check_typography.py
python3 tools/checks/check_no_direct_log.py
python3 tools/checks/check_readme_claims.py

echo "==> All static verification checks passed!"
