$ErrorActionPreference = 'Stop'
$cacheRoot = Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1'
function Find-CachedJar([string]$group, [string]$artifact, [string]$version) {
    $artifactRoot = Join-Path $cacheRoot "$group\$artifact\$version"
    $jar = Get-ChildItem -LiteralPath $artifactRoot -Recurse -File -Filter "$artifact-$version.jar" | Select-Object -First 1
    if (-not $jar) { throw "Cached dependency missing: $group/$artifact/$version" }
    return $jar.FullName
}

$stdlib = Find-CachedJar 'org.jetbrains.kotlin' 'kotlin-stdlib' '1.9.24'
$annotations = Find-CachedJar 'org.jetbrains' 'annotations' '13.0'
$junit = Find-CachedJar 'junit' 'junit' '4.13.2'
$hamcrest = Find-CachedJar 'org.hamcrest' 'hamcrest-core' '1.3'
$compilerClasspath = @(
    (Find-CachedJar 'org.jetbrains.kotlin' 'kotlin-compiler-embeddable' '1.9.24'),
    $stdlib,
    (Find-CachedJar 'org.jetbrains.kotlin' 'kotlin-script-runtime' '1.9.24'),
    (Find-CachedJar 'org.jetbrains.kotlin' 'kotlin-reflect' '1.6.10'),
    (Find-CachedJar 'org.jetbrains.kotlin' 'kotlin-daemon-embeddable' '1.9.24'),
    (Find-CachedJar 'org.jetbrains.intellij.deps' 'trove4j' '1.0.20200330'),
    $annotations
) -join ';'
$proofRoot = Split-Path $PSScriptRoot -Parent
$sourceFiles = @(
    Get-ChildItem (Join-Path $proofRoot 'prova_01_rail_sidebar_back') -Filter '*.kt' -File
    Get-ChildItem (Join-Path $proofRoot 'prova_02_hero_shelves_focus_memory') -Filter '*.kt' -File
    Get-ChildItem (Join-Path $proofRoot 'prova_03_player_osd_skip_statemachine') -Filter '*.kt' -File
    Get-Item (Join-Path $PSScriptRoot 'ContractRegressionAuditTest.kt')
)
$compiledDir = Join-Path $PSScriptRoot 'classes'
New-Item -ItemType Directory -Path $compiledDir -Force | Out-Null
$targetClasspath = @($stdlib, $annotations, $junit, $hamcrest) -join ';'
$javaExe = (Get-Command java -ErrorAction Stop).Source
& $javaExe -cp $compilerClasspath org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 -classpath $targetClasspath -d $compiledDir @($sourceFiles.FullName) 2>&1 | Tee-Object (Join-Path $PSScriptRoot 'compile.log')
$compileExit = $LASTEXITCODE
if ($compileExit -ne 0) { throw "Proof compilation failed: $compileExit" }

$runtimeClasspath = @($compiledDir, $targetClasspath) -join ';'
$originalTests = @(
    'makimono.dossie.provas.rail.MakimonoRailCoordinatorTest',
    'makimono.dossie.provas.focus.MakimonoFocusMemoryManagerTest',
    'makimono.dossie.provas.player.MakimonoPlayerOsdSkipCoordinatorTest'
)
& $javaExe -cp $runtimeClasspath org.junit.runner.JUnitCore @originalTests 2>&1 | Tee-Object (Join-Path $PSScriptRoot 'original-tests.log')
$originalExit = $LASTEXITCODE
& $javaExe -cp $runtimeClasspath org.junit.runner.JUnitCore 'makimono.dossie.audit.ContractRegressionAuditTest' 2>&1 | Tee-Object (Join-Path $PSScriptRoot 'contract-audit.log')
$contractExit = $LASTEXITCODE
[ordered]@{
    recordedAt = (Get-Date).ToString('o')
    javaExecutable = $javaExe
    compilerVersion = '1.9.24'
    junitVersion = '4.13.2'
    compilationExitCode = $compileExit
    originalTestsExitCode = $originalExit
    contractTestsExitCode = $contractExit
    scope = 'JVM prototypes only; no Android app build, UI, emulator or playback validation'
    inputs = @($sourceFiles | ForEach-Object { @{file=$_.FullName; sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash} })
} | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'results.json') -Encoding utf8
Write-Output "Audit completed: originalTestsExitCode=$originalExit; contractTestsExitCode=$contractExit"
if ($originalExit -ne 0) { exit $originalExit }
if ($contractExit -ne 0) { exit $contractExit }
