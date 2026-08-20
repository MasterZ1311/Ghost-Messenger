# Ghost Messenger - Production Android App Bundle Build Script (PowerShell)

Write-Host "[1/3] Cleaning previous build artifacts..." -ForegroundColor Cyan
Set-Location -Path "$PSScriptRoot/.."
flutter clean

Write-Host "[2/3] Fetching Flutter dependencies..." -ForegroundColor Cyan
flutter pub get

Write-Host "[3/3] Compiling release Android App Bundle (AAB)..." -ForegroundColor Cyan
flutter build appbundle --release

if ($LASTEXITCODE -eq 0) {
    Write-Host "==============================================================================" -ForegroundColor Green
    Write-Host "Build finished successfully!" -ForegroundColor Green
    Write-Host "Production Bundle Location: $PWD/build/app/outputs/bundle/release/app-release.aab" -ForegroundColor White
    Write-Host "==============================================================================" -ForegroundColor Green
} else {
    Write-Host "Build failed with exit code $LASTEXITCODE" -ForegroundColor Red
}
