# Ensure JAVA_HOME is set
if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    $androidStudioJbr = "C:\Program Files\Android\Android Studio\jbr"
    if (Test-Path "$androidStudioJbr\bin\java.exe") {
        $env:JAVA_HOME = $androidStudioJbr
        $env:PATH = "$androidStudioJbr\bin;$env:PATH"
    }
}

Write-Host "[1/3] Cleaning previous build artifacts..." -ForegroundColor Cyan
Set-Location -Path "$PSScriptRoot/.."
flutter clean

Write-Host "[2/3] Fetching Flutter dependencies..." -ForegroundColor Cyan
flutter pub get

Write-Host "[3/3] Compiling release Android App Bundle (AAB)..." -ForegroundColor Cyan
Push-Location android
.\gradlew.bat bundleRelease
$buildCode = $LASTEXITCODE
Pop-Location

if ($buildCode -eq 0) {
    Write-Host "==============================================================================" -ForegroundColor Green
    Write-Host "Build finished successfully!" -ForegroundColor Green
    Write-Host "Production Bundle Location: $PWD/build/app/outputs/bundle/release/app-release.aab" -ForegroundColor White
    Write-Host "==============================================================================" -ForegroundColor Green
} else {
    Write-Host "Build failed with exit code $LASTEXITCODE" -ForegroundColor Red
}
