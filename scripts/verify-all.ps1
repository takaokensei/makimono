# scripts/verify-all.ps1
param(
    [switch]$Quick
)

$isQuick = $Quick -or ($args -contains "--quick") -or ($args -contains "-q")

$ErrorActionPreference = "Stop"
$sw = [System.Diagnostics.Stopwatch]::StartNew()

Write-Host "=====================================================" -ForegroundColor Cyan
Write-Host " [Makimono] Iniciando Verificação Completa do Projeto" -ForegroundColor Cyan
if ($isQuick) {
    Write-Host " Modo Rápido (--quick): assembleDebug será ignorado" -ForegroundColor Yellow
}
Write-Host "=====================================================" -ForegroundColor Cyan

function Run-Step($name, $commandBlock) {
    $stepSw = [System.Diagnostics.Stopwatch]::StartNew()
    Write-Host "`n==> Executando: $name..." -ForegroundColor Cyan
    try {
        & $commandBlock
        if ($LASTEXITCODE -ne 0) {
            throw "O comando retornou código de erro $LASTEXITCODE"
        }
        $stepSw.Stop()
        Write-Host "  -> Aprovado em $([math]::Round($stepSw.Elapsed.TotalSeconds, 1))s" -ForegroundColor Green
    } catch {
        $stepSw.Stop()
        Write-Host "`n❌ FALHA NA ETAPA: $name" -ForegroundColor Red
        Write-Host "Detalhe do erro: $_" -ForegroundColor Red
        exit 1
    }
}

# 1. Testes unitários
Run-Step "1/6: Testes Unitários (testDebugUnitTest)" {
    if (Test-Path "gradlew.bat") {
        .\gradlew.bat testDebugUnitTest --no-daemon
    } else {
        ./gradlew testDebugUnitTest --no-daemon
    }
}

# 2. Linter Android
Run-Step "2/6: Android Lint (lintDebug)" {
    if (Test-Path "gradlew.bat") {
        .\gradlew.bat lintDebug --no-daemon
    } else {
        ./gradlew lintDebug --no-daemon
    }
}

# 3. Compilação do APK Debug
if (-not $isQuick) {
    Run-Step "3/6: Compilação do APK (assembleDebug)" {
        if (Test-Path "gradlew.bat") {
            .\gradlew.bat assembleDebug --no-daemon
        } else {
            ./gradlew assembleDebug --no-daemon
        }
    }
} else {
    Write-Host "`n==> 3/6: Compilação do APK (assembleDebug) [IGNORADO via --quick]" -ForegroundColor Yellow
}

# 4. Alegações do repositório
Run-Step "4/6: Verificação de Alegações (scripts/verify-claims.ps1)" {
    powershell -ExecutionPolicy Bypass -File scripts/verify-claims.ps1
}

# 5. Verificações estáticas e de design (9 guards)
Run-Step "5/6: Verificações Estáticas e de Design (scripts/verify-checks.ps1)" {
    powershell -ExecutionPolicy Bypass -File scripts/verify-checks.ps1
}

# 6. Testes do Cloudflare Worker (Vitest)
if (Get-Command npm -ErrorAction SilentlyContinue) {
    Run-Step "6/6: Testes do Cloudflare Worker (Vitest)" {
        npm --prefix backend/cloudflare-worker test
    }
} else {
    Write-Host "`n==> 6/6: Testes do Cloudflare Worker (Vitest) [IGNORADO: npm não encontrado no PATH]" -ForegroundColor Yellow
}

$sw.Stop()
$totalSec = [math]::Round($sw.Elapsed.TotalSeconds, 1)
Write-Host "`n=====================================================" -ForegroundColor Green
Write-Host " ✅ TODAS AS VERIFICAÇÕES PASSARAM COM SUCESSO!" -ForegroundColor Green
Write-Host " Tempo total decorrido: ${totalSec}s" -ForegroundColor Green
Write-Host "=====================================================" -ForegroundColor Green
