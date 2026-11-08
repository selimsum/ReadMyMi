<#
.SYNOPSIS
    Bumps version in ReadMyMi (app/build.gradle) and optionally commits and pushes to trigger release.

.DESCRIPTION
    Updates versionCode and versionName in app/build.gradle.
    When pushed to the main branch on GitHub, GitHub Actions will automatically
    run tests, compile signed release & debug APKs, and publish a new GitHub Release.

.EXAMPLE
    .\bump-version.ps1 -Patch
    Bumps patch version (e.g. 1.7.0 -> 1.7.1) and increments versionCode (9 -> 10).

.EXAMPLE
    .\bump-version.ps1 -Minor -Commit -Push
    Bumps minor version (e.g. 1.7.0 -> 1.8.0), commits, and pushes to origin main.

.EXAMPLE
    .\bump-version.ps1 1.8.0 -Commit -Push
    Sets version to 1.8.0, commits, and pushes to origin main.
#>
[CmdletBinding(DefaultParameterSetName = 'Custom')]
param(
    [Parameter(Position = 0, ParameterSetName = 'Custom')]
    [string]$Version,

    [Parameter(Mandatory = $true, ParameterSetName = 'Patch')]
    [switch]$Patch,

    [Parameter(Mandatory = $true, ParameterSetName = 'Minor')]
    [switch]$Minor,

    [Parameter(Mandatory = $true, ParameterSetName = 'Major')]
    [switch]$Major,

    [Parameter()]
    [int]$VersionCode = 0,

    [Parameter()]
    [switch]$Commit,

    [Parameter()]
    [switch]$Push
)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$gradleFile = Join-Path $root 'app\build.gradle'

if (-not (Test-Path -LiteralPath $gradleFile)) {
    Write-Error "Could not find app\build.gradle at $gradleFile"
    exit 1
}

$content = [System.IO.File]::ReadAllText($gradleFile, [System.Text.Encoding]::UTF8)

# Extract current versionCode and versionName
$codeMatch = [regex]::Match($content, 'versionCode\s+(?<codeDefault>\d+)')
$nameMatch = [regex]::Match($content, 'versionName\s*=?\s*["''](?<nameDefault>[^"'']+)["'']')

if (-not $codeMatch.Success) {
    Write-Error "Could not find versionCode in $gradleFile"
    exit 1
}
if (-not $nameMatch.Success) {
    Write-Error "Could not find versionName in $gradleFile"
    exit 1
}

$oldCode = [int]$codeMatch.Groups['codeDefault'].Value
$oldVersion = $nameMatch.Groups['nameDefault'].Value

# Calculate new versionCode
$newCode = if ($VersionCode -gt 0) { $VersionCode } else { $oldCode + 1 }

# Calculate new versionName
$newVersion = ''

if ($Patch -or $Minor -or $Major) {
    $semverParts = $oldVersion.Split('.')
    $maj = if ($semverParts.Length -gt 0) { [int]$semverParts[0] } else { 0 }
    $min = if ($semverParts.Length -gt 1) { [int]$semverParts[1] } else { 0 }
    $pat = if ($semverParts.Length -gt 2) { [int]$semverParts[2] } else { 0 }

    if ($Major) {
        $maj++
        $min = 0
        $pat = 0
    }
    elseif ($Minor) {
        $min++
        $pat = 0
    }
    elseif ($Patch) {
        $pat++
    }
    $newVersion = "$maj.$min.$pat"
}
elseif (-not [string]::IsNullOrWhiteSpace($Version)) {
    $newVersion = $Version.Trim().TrimStart('v')
}
else {
    Write-Host "Current version: $oldVersion (versionCode: $oldCode)"
    Write-Host "`nUsage:"
    Write-Host "  .\bump-version.ps1 -Patch [-Commit] [-Push]"
    Write-Host "  .\bump-version.ps1 -Minor [-Commit] [-Push]"
    Write-Host "  .\bump-version.ps1 -Major [-Commit] [-Push]"
    Write-Host "  .\bump-version.ps1 <new_version> [-Commit] [-Push]"
    exit 0
}

if ($newVersion -eq $oldVersion -and $newCode -eq $oldCode) {
    Write-Warning "Version is already $newVersion and versionCode is $newCode. Nothing changed."
    exit 0
}

# Update content
$newContent = [regex]::Replace($content, 'versionCode\s+\d+', "versionCode $newCode", 1)
$newContent = [regex]::Replace($newContent, 'versionName\s*=?\s*["''][^"'']+["'']', "versionName `"$newVersion`"", 1)

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($gradleFile, $newContent, $utf8NoBom)

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host " Successfully bumped ReadMyMi version:   " -ForegroundColor Green
Write-Host "   versionName: $oldVersion -> $newVersion" -ForegroundColor Yellow
Write-Host "   versionCode: $oldCode -> $newCode" -ForegroundColor Yellow
Write-Host "==========================================" -ForegroundColor Cyan

if ($Commit -or $Push) {
    Write-Host "==> Staging app/build.gradle and creating commit..." -ForegroundColor Cyan
    git -C $root add app/build.gradle
    git -C $root commit -m "chore(release): bump version to $newVersion"
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Git commit failed."
        exit 1
    }

    if ($Push) {
        Write-Host "==> Pushing to origin main..." -ForegroundColor Cyan
        git -C $root push origin main
        if ($LASTEXITCODE -ne 0) {
            Write-Error "Git push failed."
            exit 1
        }
        Write-Host "==> Pushed successfully! GitHub Actions is now publishing release v$newVersion." -ForegroundColor Green
    }
}
else {
    Write-Host "`nTo publish the release on GitHub, commit and push:" -ForegroundColor Cyan
    Write-Host "  git commit -am `"chore(release): bump version to $newVersion`""
    Write-Host "  git push origin main"
    Write-Host "`nGitHub Actions will automatically build APKs and publish release v$newVersion on push." -ForegroundColor Green
}
