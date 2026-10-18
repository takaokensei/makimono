[CmdletBinding()]
param(
    [string]$SdkRoot = 'C:\Android\Sdk',
    [string]$AvdHome = 'C:\Temp\MakimonoAVD',
    [string]$AvdName = 'MakimonoTVAscii',
    [ValidateRange(15, 600)]
    [int]$BootTimeoutSeconds = 180,
    [switch]$LaunchApp
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

# 1. Ensure ANDROID_AVD_HOME is set in both Process and User environment
$env:ANDROID_AVD_HOME = $AvdHome
try {
    [Environment]::SetEnvironmentVariable("ANDROID_AVD_HOME", $AvdHome, "User")
} catch {}

# 2. Mirror AVD pointer to default .android\avd directory so emulator can always resolve it
$userAvdDir = Join-Path $env:USERPROFILE '.android\avd'
if (Test-Path $userAvdDir) {
    $sourceIni = Join-Path $AvdHome "$AvdName.ini"
    if (Test-Path $sourceIni) {
        Copy-Item -LiteralPath $sourceIni -Destination (Join-Path $userAvdDir "$AvdName.ini") -Force -ErrorAction SilentlyContinue
    }
}

# 3. Check if emulator is already running and ready
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
$isAlreadyRunning = $false

if ($serial) {
    $boot = (& $adb -s $serial shell getprop sys.boot_completed 2>$null | Out-String).Trim()
    if ($boot -eq '1') {
        $isAlreadyRunning = $true
        Write-Output "Android TV emulator '$AvdName' ($serial) is already running and ready."
    }
}

if (-not $isAlreadyRunning) {
    # 4. Clean up any stale locks from previous crashes
    $avdDir = Join-Path $AvdHome "$AvdName.avd"
    Get-ChildItem -Path $avdDir -Filter "*.lock" -Recurse -ErrorAction SilentlyContinue |
        Remove-Item -Force -Recurse -ErrorAction SilentlyContinue

    # 5. Ensure host keyboard and D-pad are enabled in config.ini
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
        Set-Content -LiteralPath $avdConfig -Value $lines -Encoding ASCII
        Write-Output "Enabled host keyboard and D-pad in $avdConfig"
    }

    # 6. Launch interactively on user's active desktop (WinSta0\Default)
    # Using schtasks /it ensures the window is visible on the interactive desktop
    # and detached from any caller job object or isolated virtual sandbox desktop.
    $launchBat = Join-Path $env:TEMP "launch_makimono_avd.bat"
    $batContent = @"
@echo off
set ANDROID_AVD_HOME=$AvdHome
start "" "$emulator" -avd $AvdName -gpu swangle -no-snapshot -no-boot-anim -no-audio -memory 2048 -cores 2
"@
    Set-Content -LiteralPath $launchBat -Value $batContent -Encoding ASCII

    $taskName = "MakimonoTVEmulatorLaunch"
    & schtasks /create /tn $taskName /tr "`"$launchBat`"" /sc once /st 23:59 /f /it | Out-Null
    & schtasks /run /tn $taskName | Out-Null
    Start-Sleep -Seconds 1
    & schtasks /delete /tn $taskName /f | Out-Null
    Write-Output "Started Android TV AVD '$AvdName' on interactive screen; waiting for boot."

    # 7. Wait for boot completion
    $deadline = (Get-Date).AddSeconds($BootTimeoutSeconds)
    do {
        Start-Sleep -Seconds 2
        $serial = Get-TargetEmulatorSerial
        if ($serial) {
            $boot = (& $adb -s $serial shell getprop sys.boot_completed 2>$null | Out-String).Trim()
            if ($boot -eq '1') { break }
        }
    } while ((Get-Date) -lt $deadline)

    if (-not $serial -or $boot -ne '1') {
        throw "Android TV AVD '$AvdName' did not reach boot-complete within $BootTimeoutSeconds seconds. Run '$adb devices'."
    }
}

# 8. Report status
$sdkVersion = (& $adb -s $serial shell getprop ro.build.version.sdk | Out-String).Trim()
$resolution = (& $adb -s $serial shell wm size | Out-String).Trim()
$inputDevices = (& $adb -s $serial shell dumpsys input | Out-String)
$keyboardReady = $inputDevices.Contains('AT Translated Set 2 keyboard')
Write-Output "Ready: $serial, AVD=$AvdName, API=$sdkVersion, $resolution, hostKeyboard=$keyboardReady"

# 9. If Makimono is installed, launch or bring MainActivity to front
$installed = & $adb -s $serial shell pm list packages zechs.drive.stream.visualreview 2>$null
if ($installed -match 'zechs\.drive\.stream\.visualreview') {
    & $adb -s $serial shell am start -n zechs.drive.stream.visualreview/zechs.drive.stream.ui.main.MainActivity | Out-Null
    Write-Output "Launched Makimono on $serial."
}
