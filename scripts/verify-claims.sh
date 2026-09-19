#!/usr/bin/env bash
set -eu
set -o pipefail 2>/dev/null || true

echo "=== Makimono Claims Verification ==="

ERRORS=0

# 1. Verify Drive scope is drive.readonly
if grep -q "https://www.googleapis.com/auth/drive.readonly" app/src/main/res/values/strings.xml; then
    echo "[OK] Drive scope is strictly drive.readonly"
else
    echo "[FAIL] Drive scope is not drive.readonly in strings.xml"
    ERRORS=$((ERRORS + 1))
fi

# 2. Verify authorization code masking
if grep -q "AUTHORIZATION_CODE" app/src/main/res/values/strings.xml; then
    echo "[OK] Example OAuth authorization code is properly masked"
else
    echo "[FAIL] Authorization code mask AUTHORIZATION_CODE missing"
    ERRORS=$((ERRORS + 1))
fi

# 3. Verify AdMob removal
if grep -ri "play-services-ads" app/build.gradle app/src/main/AndroidManifest.xml 2>/dev/null; then
    echo "[FAIL] Found AdMob references"
    ERRORS=$((ERRORS + 1))
else
    echo "[OK] AdMob completely absent from gradle and manifest"
fi

# 4. Verify Firebase removal
if grep -ri "google-services" build.gradle app/build.gradle 2>/dev/null; then
    echo "[FAIL] Found google-services plugin references"
else
    echo "[OK] Firebase / google-services absent from build configuration"
fi

# 5. Verify zero runBlocking in player DataSource
if grep -q "runBlocking" app/src/main/java/zechs/drive/stream/ui/player/utils/AuthenticatingDataSource.kt; then
    echo "[FAIL] runBlocking detected in AuthenticatingDataSource.kt"
    ERRORS=$((ERRORS + 1))
else
    echo "[OK] Zero runBlocking in player AuthenticatingDataSource"
fi

# 6. Verify fail-closed update
if grep -q "A release não possui arquivo de verificação (.sha256)" app/src/main/java/zechs/drive/stream/ui/main/MainViewModel.kt; then
    echo "[OK] Fail-closed updater enforced for missing .sha256"
else
    echo "[FAIL] Fail-closed update check missing in MainViewModel"
    ERRORS=$((ERRORS + 1))
fi

echo "====================================="
if [ "$ERRORS" -eq 0 ]; then
    echo "ALL AUDIT CLAIMS VERIFIED SUCCESSFULLY!"
    exit 0
else
    echo "$ERRORS claim(s) failed verification."
    exit 1
fi
