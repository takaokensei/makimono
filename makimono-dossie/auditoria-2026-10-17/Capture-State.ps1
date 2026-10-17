param(
    [Parameter(Mandatory=$true)][string]$Name,
    [string[]]$Keys = @(),
    [int]$SettleMilliseconds = 2200
)
$ErrorActionPreference = 'Stop'
if ($Name -notmatch '^[a-z0-9_-]+$') { throw 'Use a simple lowercase capture name.' }
$serial = 'emulator-5554'
$avd = (& adb -s $serial shell getprop ro.boot.qemu.avd_name | Out-String).Trim()
if ($avd -ne 'MakimonoTVAscii') { throw "Unexpected AVD: $avd" }
$captures = Join-Path $PSScriptRoot 'capturas'
$ui = Join-Path $PSScriptRoot 'ui'
New-Item -ItemType Directory -Path $captures,$ui -Force | Out-Null
foreach ($key in $Keys) {
    & adb -s $serial shell input keyevent $key | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Input failed: $key" }
    Start-Sleep -Milliseconds 350
}
Start-Sleep -Milliseconds $SettleMilliseconds
$png = Join-Path $captures "$Name.png"
$asciiCaptures = 'C:\Temp\MakimonoAudit-2026-10-17'
New-Item -ItemType Directory -Path $asciiCaptures -Force | Out-Null
$rawPng = Join-Path $asciiCaptures "$Name.png"
& adb -s $serial emu screenrecord screenshot $rawPng | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Emulator screenshot failed.' }
Copy-Item -LiteralPath $rawPng -Destination $png -Force
$bytes = [IO.File]::ReadAllBytes($png)
$signature = [byte[]](137,80,78,71,13,10,26,10)
if ($bytes.Length -lt 24) { throw 'Screenshot is too short.' }
for ($i=0; $i -lt 8; $i++) {
    if ($bytes[$i] -ne $signature[$i]) { throw 'Invalid PNG signature.' }
}
& adb -s $serial shell uiautomator dump /sdcard/makimono-audit.xml | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'UI dump failed.' }
$xmlPath = Join-Path $ui "$Name.xml"
& adb -s $serial pull /sdcard/makimono-audit.xml $xmlPath 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'UI dump pull failed.' }
$entry = [ordered]@{
    capturedAt=(Get-Date).ToString('o'); name=$Name; avd=$avd; serial=$serial;
    keys=$Keys; png="capturas/$Name.png"; ui="ui/$Name.xml";
    sha256=(Get-FileHash -LiteralPath $png -Algorithm SHA256).Hash
}
Add-Content -LiteralPath (Join-Path $PSScriptRoot 'manifest.jsonl') -Value ($entry | ConvertTo-Json -Compress)
[xml]$tree=Get-Content -LiteralPath $xmlPath -Raw
$nodes=$tree.SelectNodes('//node')
Write-Output "CAPTURE $Name ($($bytes.Length) bytes)"
foreach ($node in $nodes) {
    if ($node.focused -eq 'true' -or (($node.focusable -eq 'true' -or $node.clickable -eq 'true') -and ($node.text -or $node.'content-desc' -or $node.'resource-id'))) {
        Write-Output ("{0} | {1} | {2} | {3} | focused={4}" -f $node.'resource-id',$node.text,$node.'content-desc',$node.bounds,$node.focused)
    }
}
