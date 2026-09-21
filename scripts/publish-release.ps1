# scripts/publish-release.ps1
# Script para criar a GitHub Release e fazer upload dos APKs e checksums SHA-256

param(
    [string]$tag = "v1.5.2",
    [string]$repo = "takaokensei/makimono",
    [string]$apkDir = "app/build/outputs/apk/debug"
)

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

# 2. Gerar .sha256 se nao existirem
$apks = Get-ChildItem -Path $apkDir | Where-Object { $_.Name.EndsWith(".apk") -and -not $_.Name.Contains("androidTest") }
foreach ($apk in $apks) {
    $shaPath = "$($apk.FullName).sha256"
    if (-not (Test-Path $shaPath)) {
        $hash = (Get-FileHash -Path $apk.FullName -Algorithm SHA256).Hash.ToLower()
        "$hash  $($apk.Name)" | Out-File -FilePath $shaPath -Encoding utf8
        Write-Host "Gerado checksum para $($apk.Name): $hash" -ForegroundColor DarkGray
    }
}

# 3. Verificar se a release ja existe
$release = $null

try {
    $release = Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/releases/tags/$tag" -Headers $headers -Method Get
    Write-Host "Release $tag ja existe (ID: $($release.id)). Atualizando assets..." -ForegroundColor Yellow
} catch {
    Write-Host "Criando nova release $tag..." -ForegroundColor Cyan
    $releaseBody = @"
# Makimono $tag - UI Modernization and Subtitle Engine v4

Esta versao traz a modernizacao visual do Makimono no nivel streaming profissional (Netflix/Crunchyroll), com menu flutuante em Frosted Glass no Player, gerenciamento avancado de perfis com Room Database e novo motor de legendas v4 com eliminacao total de sobreposicoes de falas simultaneas.

### Destaques da Versao
- **Motor de Legendas v4:** Algoritmo de resolucao de sobreposicoes simultaneas que divide o tempo em intervalos elementares e empilha as falas em quebra de linha unica, sem colisoes de texto na tela.
- **Player OSD & Menu Glass:** Hierarquia com Progressive Disclosure - transporte central ergonomico com glow ciano neon em foco e menu rapido de configuracoes em vidro translucido (Velocidade, Proporcao, Capitulos, Info Tecnica).
- **Modulo de Perfis (Quem esta assistindo?):** Gerenciador de multiplos perfis com persistencia via Room (ProfileEntity), catalogo remoto de avatares/wallpapers de animes e editor de perfil com foco D-Pad para Android TV.
- **Explorador de Arquivos & Breadcrumbs:** Nova organizacao com subpastas no topo e grid de videos com badges de resolucao (1080p/4K), formato (MKV), audio (5.1) e tamanho.
- **Identidade Visual Torii:** Novo icone vetorizado e paleta neon dark consistente em todas as telas e dialogos.

### APKs Incluidos (Debug Build):
- makimono-v1.5.2-arm64-v8a-debug.apk (TV Box moderna, Fire TV Stick 4K, Smartphones)
- makimono-v1.5.2-armeabi-v7a-debug.apk (TVs e dispositivos 32-bit)
- makimono-v1.5.2-x86_64-debug.apk (Emuladores PC 64-bit)
- makimono-v1.5.2-x86-debug.apk (Emuladores PC 32-bit)
"@

    $bodyObj = @{
        tag_name         = $tag
        target_commitish = "main"
        name             = "Makimono $tag - UI Modernization & Subtitle Engine v4"
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

# 4. Upload dos arquivos (APKs e .sha256)
$files = Get-ChildItem -Path $apkDir | Where-Object { ($_.Name.EndsWith(".apk") -or $_.Name.EndsWith(".sha256")) -and -not $_.Name.Contains("androidTest") }

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

    $fileMb = [math]::Round($file.Length / 1048576, 2)
    Write-Host "  Enviando $($file.Name) ($fileMb MB)..."
    
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
Write-Host "Release $tag publicada com sucesso no GitHub!" -ForegroundColor Green
Write-Host "URL: $($release.html_url)" -ForegroundColor Cyan
Write-Host "=======================================================" -ForegroundColor Green
