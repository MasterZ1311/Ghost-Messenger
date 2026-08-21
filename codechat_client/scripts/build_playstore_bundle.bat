@echo off
REM ==============================================================================
REM Ghost Messenger - Production Android App Bundle Build Script
REM ==============================================================================

if "%JAVA_HOME%"=="" (
    if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" (
        set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
        set "PATH=C:\Program Files\Android\Android Studio\jbr\bin;%PATH%"
    )
)

echo [1/3] Cleaning previous build artifacts...
cd ..
call flutter clean

echo [2/3] Fetching Flutter dependencies...
call flutter pub get

echo [3/3] Compiling release Android App Bundle (AAB)...
cd android
call gradlew.bat bundleRelease
cd ..

echo ==============================================================================
echo Build finished successfully!
echo Production Bundle Location:
echo %CD%\build\app\outputs\bundle\release\app-release.aab
echo ==============================================================================
pause
