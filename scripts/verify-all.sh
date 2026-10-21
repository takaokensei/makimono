#!/usr/bin/env bash
set -euo pipefail

QUICK_MODE=false
for arg in "$@"; do
    case "$arg" in
        --quick|-q)
            QUICK_MODE=true
            ;;
    esac
done

START_TIME=$(date +%s)

echo "====================================================="
echo " [Makimono] Iniciando Verificação Completa do Projeto"
if [ "$QUICK_MODE" = true ]; then
    echo " Modo Rápido (--quick): assembleDebug será ignorado"
fi
echo "====================================================="

# 1. Testes Unitários
echo -e "\n==> [1/6] Executando: Testes Unitários (testDebugUnitTest)..."
./gradlew testDebugUnitTest --no-daemon

# 2. Android Lint
echo -e "\n==> [2/6] Executando: Android Lint (lintDebug)..."
./gradlew lintDebug --no-daemon

# 3. Compilação Debug
if [ "$QUICK_MODE" = false ]; then
    echo -e "\n==> [3/6] Executando: Compilação do APK (assembleDebug)..."
    ./gradlew assembleDebug --no-daemon
else
    echo -e "\n==> [3/6] Compilação do APK (assembleDebug) [IGNORADO via --quick]"
fi

# 4. Alegações do Repositório
echo -e "\n==> [4/6] Executando: Verificação de Alegações do Repositório..."
if [ -f "scripts/verify-claims.sh" ]; then
    bash scripts/verify-claims.sh
fi

# 5. Verificações estáticas e de design (9 guards)
echo -e "\n==> [5/6] Executando: Verificações Estáticas e de Design..."
if [ -f "scripts/verify-checks.sh" ]; then
    bash scripts/verify-checks.sh
fi

# 6. Testes do Cloudflare Worker (Vitest)
if command -v npm >/dev/null 2>&1; then
    echo -e "\n==> [6/6] Executando: Testes do Cloudflare Worker (Vitest)..."
    npm --prefix backend/cloudflare-worker test
else
    echo -e "\n==> [6/6] Testes do Cloudflare Worker [IGNORADO: npm não encontrado no PATH]"
fi

END_TIME=$(date +%s)
TOTAL_TIME=$((END_TIME - START_TIME))

echo -e "\n====================================================="
echo -e " \033[0;32m✅ TODAS AS VERIFICAÇÕES PASSARAM COM SUCESSO!\033[0m"
echo -e " \033[0;32mTempo total decorrido: ${TOTAL_TIME}s\033[0m"
echo -e "====================================================="
