[CmdletBinding()]
param(
    [string]$SdkRoot = 'C:\Android\Sdk',
    [string]$AvdHome = 'C:\Temp\MakimonoAVD',
    [string]$AvdName = 'MakimonoTVAscii',
    [ValidateRange(30, 600)]
    [int]$BootTimeoutSeconds = 180
)

$ErrorActionPreference = 'Stop'
$emulator = Join-Path $SdkRoot 'emulator\emulator.exe'
$adb = Join-Path $SdkRoot 'platform-tools\adb.exe'
$avdConfig = Join-Path $AvdHome "$AvdName.avd\config.ini"

foreach ($requiredPath in @($emulator, $adb, $avdConfig)) {
    if (-not (Test-Path -LiteralPath $requiredPath)) {
        throw "Required Android TV emulator file not found: $requiredPath"
    }
}

$env:ANDROID_AVD_HOME = $AvdHome
$availableAvds = @(& $emulator -list-avds)
if ($LASTEXITCODE -ne 0 -or $availableAvds -notcontains $AvdName) {
    throw "AVD '$AvdName' is not registered under ANDROID_AVD_HOME '$AvdHome'. Available AVDs: $($availableAvds -join ', ')"
}

function Get-TargetEmulatorSerial {
    $deviceLines = @(& $adb devices)
    foreach ($line in $deviceLines) {
        if ($line -match '^(emulator-\d+)\s+device$') {
            $serial = $Matches[1]
            $runningAvd = (& $adb -s $serial shell getprop ro.boot.qemu.avd_name 2>$null | Out-String).Trim()
            if ($runningAvd -eq $AvdName) {
                return $serial
            }
        }
    }
    return $null
}

$serial = Get-TargetEmulatorSerial
if (-not $serial) {
    $lines = Get-Content -LiteralPath $avdConfig
    $updated = $false
    foreach ($flag in @('hw.keyboard', 'hw.dPad')) {
        $pattern = '^' + [regex]::Escape($flag) + '='
        $desiredLine = "$flag=yes"
        $existingLine = $lines | Where-Object { $_ -match $pattern } | Select-Object -First 1
        if ($existingLine) {
            if ($existingLine -eq $desiredLine) { continue }
            $lines = @($lines | ForEach-Object {
                if ($_ -match $pattern) { $desiredLine } else { $_ }
            })
            $updated = $true
        } else {
            $lines += $desiredLine
            $updated = $true
        }
    }

    if ($updated) {
        $backup = "$avdConfig.before-keyboard-$((Get-Date).ToString('yyyyMMdd-HHmmss'))"
        Copy-Item -LiteralPath $avdConfig -Destination $backup
        Set-Content -LiteralPath $avdConfig -Value $lines -Encoding ASCII
        Write-Output "Enabled host keyboard and D-pad; saved previous AVD config to $backup"
    }

    $arguments = @(
        '-avd', $AvdName,
        '-gpu', 'swangle',
        '-no-snapshot',
        '-no-boot-anim',
        '-no-audio',
        '-memory', '2048',
        '-cores', '2'
    )
    $process = Start-Process -FilePath $emulator -ArgumentList $arguments -WorkingDirectory (Join-Path $SdkRoot 'emulator') -PassThru
    Write-Output "Started Android TV AVD '$AvdName' (launcher PID $($process.Id)); waiting for boot."
}

$deadline = (Get-Date).AddSeconds($BootTimeoutSeconds)
do {
    Start-Sleep -Seconds 3
    $serial = Get-TargetEmulatorSerial
    if ($serial) {
        $boot = (& $adb -s $serial shell getprop sys.boot_completed 2>$null | Out-String).Trim()
        if ($boot -eq '1') { break }
    }
} while ((Get-Date) -lt $deadline)

if (-not $serial -or $boot -ne '1') {
    throw "Android TV AVD '$AvdName' did not reach boot-complete within $BootTimeoutSeconds seconds. Check emulator output and run '$adb devices'."
}

$sdkVersion = (& $adb -s $serial shell getprop ro.build.version.sdk | Out-String).Trim()
$resolution = (& $adb -s $serial shell wm size | Out-String).Trim()
$inputDevices = (& $adb -s $serial shell dumpsys input | Out-String)
$keyboardReady = $inputDevices.Contains('AT Translated Set 2 keyboard')
Write-Output "Ready: $serial, AVD=$AvdName, API=$sdkVersion, $resolution, hostKeyboard=$keyboardReady"
if (-not $keyboardReady) {
    Write-Warning 'Android is online, but the host keyboard device is not visible yet. Confirm hw.keyboard=yes in the AVD config and restart it.'
}
