$errors = 0

Write-Host "=== Makimono Claims Verification ===" -ForegroundColor Cyan

# 1. Drive scope
if (Select-String -Path "app/src/main/res/values/strings.xml" -Pattern "https://www.googleapis.com/auth/drive.readonly" -Quiet) {
    Write-Host "[OK] Drive scope is strictly drive.readonly" -ForegroundColor Green
} else {
    Write-Host "[FAIL] Drive scope is not drive.readonly" -ForegroundColor Red
    $errors++
}

# 2. Authorization code mask
if (Select-String -Path "app/src/main/res/values/strings.xml" -Pattern "AUTHORIZATION_CODE" -Quiet) {
    Write-Host "[OK] Example OAuth authorization code is properly masked" -ForegroundColor Green
} else {
    Write-Host "[FAIL] Authorization code mask AUTHORIZATION_CODE missing" -ForegroundColor Red
    $errors++
}

# 3. AdMob
$admobFound = Select-String -Path @("app/build.gradle", "app/src/main/AndroidManifest.xml") -Pattern "play-services-ads" -Quiet
if (-not $admobFound) {
    Write-Host "[OK] AdMob completely absent from gradle and manifest" -ForegroundColor Green
} else {
    Write-Host "[FAIL] Found AdMob references" -ForegroundColor Red
    $errors++
}

# 4. Firebase
$firebaseFound = Select-String -Path @("build.gradle", "app/build.gradle") -Pattern "google-services" -Quiet
if (-not $firebaseFound) {
    Write-Host "[OK] Firebase / google-services absent from build configuration" -ForegroundColor Green
} else {
    Write-Host "[FAIL] Found google-services plugin references" -ForegroundColor Red
    $errors++
}

# 5. Zero runBlocking in player DataSource
$playerRunBlocking = Select-String -Path "app/src/main/java/zechs/drive/stream/ui/player/utils/AuthenticatingDataSource.kt" -Pattern "runBlocking" -Quiet
if (-not $playerRunBlocking) {
    Write-Host "[OK] Zero runBlocking in player AuthenticatingDataSource" -ForegroundColor Green
} else {
    Write-Host "[FAIL] runBlocking detected in AuthenticatingDataSource.kt" -ForegroundColor Red
    $errors++
}

# 6. Fail-closed update
if (Select-String -Path "app/src/main/java/zechs/drive/stream/ui/main/MainViewModel.kt" -Pattern "A release não possui arquivo de verificação \(\.sha256\)" -Quiet) {
    Write-Host "[OK] Fail-closed updater enforced for missing .sha256" -ForegroundColor Green
} else {
    Write-Host "[FAIL] Fail-closed update check missing in MainViewModel" -ForegroundColor Red
    $errors++
}

Write-Host "=====================================" -ForegroundColor Cyan
if ($errors -eq 0) {
    Write-Host "ALL AUDIT CLAIMS VERIFIED SUCCESSFULLY!" -ForegroundColor Green
    exit 0
} else {
    Write-Host "$errors claim(s) failed verification." -ForegroundColor Red
    exit 1
}
