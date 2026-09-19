@ECHO off
GOTO start
:find_dp0
SET dp0=%~dp0
EXIT /b
:start
SETLOCAL
CALL :find_dp0

powershell -NoProfile -ExecutionPolicy Bypass -Command "$c = Get-NetTCPConnection -LocalPort 4000 -ErrorAction SilentlyContinue; if (-not $c) { Write-Host '[NVIDIA NIM] Iniciando LiteLLM proxy...' -ForegroundColor Cyan; Start-Process powershell -ArgumentList '-NoProfile -ExecutionPolicy Bypass -File \"\"%USERPROFILE%\.claude\run_proxy.ps1\"\"' -WindowStyle Minimized; Start-Sleep -Seconds 3 }"

"%dp0%\node_modules\@anthropic-ai\claude-code\bin\claude.exe"   %*
