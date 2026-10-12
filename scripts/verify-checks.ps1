# scripts/verify-checks.ps1
$ErrorActionPreference = "Stop"

Write-Host "==> Running design system and parity verifications..." -ForegroundColor Cyan

python tools/checks/check_strings.py
if ($LASTEXITCODE -ne 0) { throw "check_strings failed" }

python tools/checks/check_contrast.py
if ($LASTEXITCODE -ne 0) { throw "check_contrast failed" }

python tools/checks/check_layout_colors.py
if ($LASTEXITCODE -ne 0) { throw "check_layout_colors failed" }

python tools/checks/check_typography.py
if ($LASTEXITCODE -ne 0) { throw "check_typography failed" }

python tools/checks/check_no_direct_log.py
if ($LASTEXITCODE -ne 0) { throw "check_no_direct_log failed" }

python tools/checks/check_readme_claims.py
if ($LASTEXITCODE -ne 0) { throw "check_readme_claims failed" }

python tools/checks/check_kotlin_literals.py
if ($LASTEXITCODE -ne 0) { throw "check_kotlin_literals failed" }

python tools/checks/check_kotlin_style.py
if ($LASTEXITCODE -ne 0) { throw "check_kotlin_style failed" }

python tools/checks/check_immutable_tag.py --self-test
if ($LASTEXITCODE -ne 0) { throw "check_immutable_tag self-test failed" }

Write-Host "==> All static verification checks passed!" -ForegroundColor Green
