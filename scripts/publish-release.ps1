# scripts/publish-release.ps1
# Script para criar a GitHub Release e fazer upload dos APKs e checksums SHA-256

param(
    [Parameter(Mandatory=$true)][string]$tag,
    [Parameter(Mandatory=$true)][string]$targetCommit,
    [Parameter(Mandatory=$true)][string]$notesFile,
    [string]$repo = "takaokensei/makimono",
    [string]$title = "",
    [string]$apkDir = "app/build/outputs/apk/release"
)
$ErrorActionPreference = "Stop"


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

# Only this version's signed release APKs; never upload stale builds or test APKs.
$apks = @(Get-ChildItem -LiteralPath $apkDir -File | Where-Object { $_.Name -like "makimono-$tag-*-release.apk" })
if ($apks.Count -ne 4) { throw "Expected four release APKs for $tag, got $($apks.Count)" }
$notes = [System.IO.File]::ReadAllText((Resolve-Path $notesFile).Path, [System.Text.Encoding]::UTF8)
if (-not $title) { $title = "Makimono $tag" }
$body = @{tag_name=$tag; target_commitish=$targetCommit; name=$title; body=$notes; draft=$true; prerelease=$false} | ConvertTo-Json
$release = Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/releases" -Headers $headers -Method Post -ContentType "application/json; charset=utf-8" -Body ([System.Text.Encoding]::UTF8.GetBytes($body))
$uploadBase = $release.upload_url -replace '\{\?name,label\}', ''
foreach ($apk in $apks) {
    $hash = (Get-FileHash -LiteralPath $apk.FullName -Algorithm SHA256).Hash.ToLower()
    $checksum = "$($apk.FullName).sha256"
    [System.IO.File]::WriteAllText($checksum, "$hash  $($apk.Name)`n", [System.Text.UTF8Encoding]::new($false))
    foreach ($path in @($apk.FullName, $checksum)) {
        $file = Get-Item -LiteralPath $path
        $uploadUri = "${uploadBase}?name=$([uri]::EscapeDataString($file.Name))"
        $asset = Invoke-RestMethod -Uri $uploadUri -Headers $headers -Method Post -ContentType "application/octet-stream" -InFile $file.FullName
        if ($asset.size -ne $file.Length -or $asset.state -ne "uploaded") { throw "Asset validation failed: $($file.Name)" }
        Write-Host "Uploaded $($file.Name) ($($file.Length) bytes)"
    }
}
$verified = Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/releases/$($release.id)" -Headers $headers
if (@($verified.assets).Count -ne 8) { throw "Release must contain four APKs and four checksums" }
$published = Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/releases/$($release.id)" -Headers $headers -Method Patch -ContentType "application/json" -Body '{"draft":false}'
Write-Host "Published: $($published.html_url)"
