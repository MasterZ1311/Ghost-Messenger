@echo off
REM ==============================================================================
REM Ghost Messenger - Production Android App Bundle Build Script
REM ==============================================================================

echo [1/3] Cleaning previous build artifacts...
cd ..
call flutter clean

echo [2/3] Fetching Flutter dependencies...
call flutter pub get

echo [3/3] Compiling release Android App Bundle (AAB)...
call flutter build appbundle --release

echo ==============================================================================
echo Build finished successfully!
echo Production Bundle Location:
echo %CD%\build\app\outputs\bundle\release\app-release.aab
echo ==============================================================================
pause
