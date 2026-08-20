param(
    [switch]$Release,
    [switch]$SkipTests,
    [switch]$Install,
    [string]$OutputDir = "$PSScriptRoot\dist"
)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$gradlew = Join-Path $root 'gradlew.bat'

if (-not (Test-Path -LiteralPath $gradlew)) {
    Write-Error "gradlew.bat not found. Run this script from the project root."
    exit 1
}

Push-Location $root
try {
    if (-not $SkipTests) {
        Write-Host "==> Running unit tests..."
        & $gradlew :app:testDebugUnitTest --console=plain
        if ($LASTEXITCODE -ne 0) { Write-Error "Unit tests failed."; exit 1 }
    }

    if ($Release) {
        Write-Host "==> Building release APK (unsigned)..."
        & $gradlew :app:assembleRelease --console=plain
        if ($LASTEXITCODE -ne 0) { Write-Error "Release build failed."; exit 1 }
        $apks = @(Get-ChildItem -Path "$root\app\build\outputs\apk\release" -Filter *.apk -File)
    }
    else {
        Write-Host "==> Building debug APK..."
        & $gradlew :app:assembleDebug --console=plain
        if ($LASTEXITCODE -ne 0) { Write-Error "Debug build failed."; exit 1 }
        $apks = @(Get-ChildItem -Path "$root\app\build\outputs\apk\debug" -Filter *.apk -File)
    }

    if ($apks.Count -eq 0) {
        Write-Error "No APK was produced."
        exit 1
    }

    New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
    foreach ($apk in $apks) {
        $dest = Join-Path $OutputDir $apk.Name
        Copy-Item -LiteralPath $apk.FullName -Destination $dest -Force
        Write-Host "==> APK ready: $dest"
    }

    if ($Install -and -not $Release) {
        Write-Host "==> Installing on connected device..."
        & $gradlew :app:installDebug --console=plain
        if ($LASTEXITCODE -ne 0) { Write-Error "Install failed."; exit 1 }
    }
}
finally {
    Pop-Location
}
