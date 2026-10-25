# Builds the game and serves it on a local web server.
# Usage: .\run.ps1 [-Port 8765] [-Level 1] [-NoBrowser] [-NoBuild]
param(
    [int]$Port = 8765,
    [int]$Level = 1,
    [switch]$NoBrowser,
    [switch]$NoBuild
)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

# Find a JDK if JAVA_HOME isn't set
if (-not $env:JAVA_HOME -or -not (Test-Path $env:JAVA_HOME)) {
    $jdk = Get-ChildItem "C:\Program Files\Eclipse Adoptium", "C:\Program Files\Java" -Directory -Filter "jdk*" -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending | Select-Object -First 1
    if (-not $jdk) { throw "JAVA_HOME is not set and no JDK was found. Install a JDK (11 works) and set JAVA_HOME." }
    $env:JAVA_HOME = $jdk.FullName
}
Write-Host "Using JDK: $env:JAVA_HOME"

if (-not $NoBuild) {
    Write-Host "Building..."
    & .\gradlew.bat browserDevelopmentWebpack --offline --console=plain -q
    if ($LASTEXITCODE -ne 0) { throw "Build failed (exit code $LASTEXITCODE)" }
}

$dist = Join-Path $PSScriptRoot "build\distributions"
if (-not (Test-Path (Join-Path $dist "pacman3d.js"))) { throw "Build output not found in $dist" }

$python = (Get-Command python -ErrorAction SilentlyContinue)
if (-not $python) { $python = Get-Command py -ErrorAction Stop }

$url = "http://localhost:$Port/index.html?level=$Level"
Write-Host "Serving $dist at $url (Ctrl+C to stop)"
if (-not $NoBrowser) { Start-Process $url }

Push-Location $dist
try {
    & $python.Source -m http.server $Port
} finally {
    Pop-Location
}
