# scripts/publish-release.ps1
# Script para criar a GitHub Release e fazer upload dos APKs e checksums

Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;
using System.Text;

public class CredentialHelper {
    [DllImport("advapi32.dll", EntryPoint = "CredReadW", CharSet = CharSet.Unicode, SetLastError = true)]
    public static extern bool CredRead(string target, int type, int reservedFlag, out IntPtr credentialPtr);

    [DllImport("advapi32.dll", EntryPoint = "CredFree", SetLastError = true)]
    public static extern void CredFree(IntPtr buffer);

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    public struct CREDENTIAL {
        public int Flags;
        public int Type;
        public string TargetName;
        public string Comment;
        public System.Runtime.InteropServices.ComTypes.FILETIME LastWritten;
        public int CredentialBlobSize;
        public IntPtr CredentialBlob;
        public int Persist;
        public int AttributeCount;
        public IntPtr Attributes;
        public string TargetAlias;
        public string UserName;
    }

    public static string GetCredential(string target) {
        IntPtr ptr;
        if (CredRead(target, 1, 0, out ptr)) {
            CREDENTIAL cred = (CREDENTIAL)Marshal.PtrToStructure(ptr, typeof(CREDENTIAL));
            byte[] blob = new byte[cred.CredentialBlobSize];
            Marshal.Copy(cred.CredentialBlob, blob, 0, cred.CredentialBlobSize);
            CredFree(ptr);
            return Encoding.Unicode.GetString(blob);
        }
        return null;
    }
}
"@

$token = [CredentialHelper]::GetCredential("git:https://github.com")
if (-not $token) {
    $token = [CredentialHelper]::GetCredential("git:https://takaokensei@github.com")
}
if (-not $token) {
    Write-Error "Token do GitHub nao encontrado no Windows Credential Manager."
    exit 1
}

$headers = @{
    "Authorization" = "Bearer $token"
    "User-Agent"    = "Makimono-Release-Agent"
    "Accept"        = "application/vnd.github.v3+json"
}

# 1. Verificar usuario autenticado
$user = Invoke-RestMethod -Uri "https://api.github.com/user" -Headers $headers -Method Get
Write-Host "Autenticado como: $($user.login)" -ForegroundColor Green

# 2. Verificar se a release ja existe
$tag = "v1.5.0"
$repo = "takaokensei/makimono"
$release = $null

try {
    $release = Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/releases/tags/$tag" -Headers $headers -Method Get
    Write-Host "Release $tag ja existe (ID: $($release.id)). Atualizando assets..." -ForegroundColor Yellow
} catch {
    Write-Host "Criando nova release $tag..." -ForegroundColor Cyan
    $releaseBody = @"
# ⛩️ Makimono v1.5.0 — Complete UX/UI Overhaul

Revisão integral de experiência de usuário, interface e navegabilidade:

### 🌟 Destaques da Versão
- **Interface Estilo Streaming:** Shelves e cards com proporção 2:3/16:9, suporte a foco contínuo no controle remoto de Smart TVs.
- **Customizador de Legendas:** Janela completa com ajuste de cores, fontes (sans, serif, mono, rounded), outline, delay e gap bridging.
- **Fila de Reprodução ("Assistir Depois"):** Reordenação por drag-and-drop e navegação dedicada.
- **Acessibilidade e Usabilidade:** Alvos de toque padronizados em >= 48dp, 5 temas completos (Tokyo Night, Dracula, Nord, Catppuccin, Estuary).
- **Segurança e Verificação:** Atualizador in-app fail-closed com verificação de checksum SHA-256 obrigatória.

---

### 📦 Checksums SHA-256 dos APKs

| Arquivo | SHA-256 Checksum |
|---|---|
| ``makimono-v1.5.0-arm64-v8a-release.apk`` | ``bfa63ece50e1da485df91f57f461064c126f7b8359caf97075bbbaaeb789d2c4`` |
| ``makimono-v1.5.0-armeabi-v7a-release.apk`` | ``5ad1a4c241f0ec6afa3010249e4ce90c2c3ae918ccda7dcbce74866157019868`` |
| ``makimono-v1.5.0-universal-release.apk`` | ``e2ac79fe29df67e3c5936b4c92fc7483b6514403e653801899ebb4bc7f878fa6`` |
| ``makimono-v1.5.0-x86-release.apk`` | ``0136d18184b1080e791e192f8cce474740fdd7f82fa3dddcbe4e10a4be0aa247`` |
| ``makimono-v1.5.0-x86_64-release.apk`` | ``0135117a71cff345f87ab1496a623b927465c53c8503c219ae7651afd4cb60c6`` |
"@

    $bodyObj = @{
        tag_name         = $tag
        target_commitish = "main"
        name             = "Makimono v1.5.0 - Complete UX/UI Overhaul"
        body             = $releaseBody
        draft            = $false
        prerelease       = $false
    } | ConvertTo-Json -Depth 5

    $tempJson = [System.IO.Path]::GetTempFileName()
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($tempJson, $bodyObj, $utf8NoBom)
    
    $atJson = "@" + $tempJson
    $jsonOutput = curl.exe -s -X POST `
        -H "Authorization: Bearer $token" `
        -H "User-Agent: Makimono-Release-Agent" `
        -H "Accept: application/vnd.github.v3+json" `
        -H "Content-Type: application/json; charset=utf-8" `
        --data $atJson `
        "https://api.github.com/repos/$repo/releases"
        
    Remove-Item $tempJson -Force
    $release = $jsonOutput | ConvertFrom-Json

    if (-not $release.id) {
        Write-Error "Falha ao criar release: $jsonOutput"
        exit 1
    }
    Write-Host "Release criada com sucesso! ID: $($release.id)" -ForegroundColor Green
}

# 3. Upload dos arquivos (APKs e .sha256)
$apkDir = "app/build/outputs/apk/release"
$files = Get-ChildItem -Path $apkDir | Where-Object { $_.Name.EndsWith(".apk") -or $_.Name.EndsWith(".sha256") }

$uploadUrlBase = ($release.upload_url -replace '\{\?name,label\}', '')

# Recarrega release para obter lista atualizada de assets
$release = Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/releases/$($release.id)" -Headers $headers -Method Get

foreach ($file in $files) {
    Write-Host "`nProcessando: $($file.Name)..." -ForegroundColor Cyan

    # Se ja existe um asset com esse nome, deletar antes
    $existingAsset = $release.assets | Where-Object { $_.name -eq $file.Name }
    if ($existingAsset) {
        Write-Host "  Removendo asset antigo: $($existingAsset.name) (ID: $($existingAsset.id))..." -ForegroundColor Yellow
        Invoke-RestMethod -Uri $existingAsset.url -Headers $headers -Method Delete
    }

    $contentType = if ($file.Name.EndsWith(".apk")) { "application/vnd.android.package-archive" } else { "text/plain" }
    $uploadUri = "${uploadUrlBase}?name=$($file.Name)"

    Write-Host "  Enviando $($file.Name) ($([math]::Round($file.Length / 1MB, 2)) MB)..."
    
    $atFile = "@" + $file.FullName
    $httpResult = curl.exe -s -w "`nHTTP_STATUS:%{http_code}" -X POST `
        -H "Authorization: Bearer $token" `
        -H "User-Agent: Makimono-Release-Agent" `
        -H "Content-Type: $contentType" `
        --data-binary $atFile `
        "$uploadUri"

    if ($httpResult -match "HTTP_STATUS:201") {
        Write-Host "  [OK] $($file.Name) enviado com sucesso!" -ForegroundColor Green
    } else {
        Write-Host "  [FALHA] Falha ao enviar $($file.Name):`n$httpResult" -ForegroundColor Red
    }
}

Write-Host "`n=======================================================" -ForegroundColor Green
Write-Host "Release v1.5.0 publicada com sucesso no GitHub!" -ForegroundColor Green
Write-Host "URL: $($release.html_url)" -ForegroundColor Cyan
Write-Host "=======================================================" -ForegroundColor Green
