# ─────────────────────────────────────────────────────────────────────────────
#  Ghost Messenger - Developer Environment Setup Script
#  Run this script once in a new PowerShell session to set up all environment
#  variables needed to build and run the app.
#
#  Usage:
#    . .\setup-dev-env.ps1           (dot-source to set vars in current shell)
#    .\setup-dev-env.ps1             (run standalone — opens a new shell)
# ─────────────────────────────────────────────────────────────────────────────

$JAVA_HOME    = "C:\Program Files\Android\Android Studio\jbr"
$ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$SDK_TOOLS    = "$ANDROID_HOME\cmdline-tools\latest\bin"
$PLATFORM_TOOLS = "$ANDROID_HOME\platform-tools"
$BUILD_TOOLS  = "$ANDROID_HOME\build-tools\36.0.0"

# Set for current session
$env:JAVA_HOME        = $JAVA_HOME
$env:ANDROID_HOME     = $ANDROID_HOME
$env:ANDROID_SDK_ROOT = $ANDROID_HOME
$env:PATH             = "$JAVA_HOME\bin;$SDK_TOOLS;$PLATFORM_TOOLS;$BUILD_TOOLS;$env:PATH"

# ── Verification ─────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "=================================================" -ForegroundColor Cyan
Write-Host "  Ghost Messenger - Dev Environment Ready" -ForegroundColor Green
Write-Host "=================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "JAVA_HOME     : $env:JAVA_HOME" -ForegroundColor Yellow
Write-Host "ANDROID_HOME  : $env:ANDROID_HOME" -ForegroundColor Yellow
Write-Host ""

$javaVer = & java -version 2>&1 | Select-String "version"
Write-Host "Java          : $javaVer" -ForegroundColor White

$flutterVer = & flutter --version 2>&1 | Select-String "Flutter"
Write-Host "Flutter       : $flutterVer" -ForegroundColor White

$nodeVer = node --version 2>&1
Write-Host "Node.js       : $nodeVer" -ForegroundColor White

Write-Host ""
Write-Host "─────────────────────────────────────────────────" -ForegroundColor DarkGray
Write-Host "  Quick Commands" -ForegroundColor Cyan
Write-Host "─────────────────────────────────────────────────" -ForegroundColor DarkGray
Write-Host ""
Write-Host "  [Flutter App - Android Emulator]" -ForegroundColor Green
Write-Host "    cd codechat_client" -ForegroundColor White
Write-Host "    flutter run" -ForegroundColor White
Write-Host ""
Write-Host "  [Flutter App - Windows Desktop]" -ForegroundColor Green
Write-Host "    cd codechat_client" -ForegroundColor White
Write-Host "    flutter run -d windows" -ForegroundColor White
Write-Host ""
Write-Host "  [Flutter App - Release APK]" -ForegroundColor Green
Write-Host "    cd codechat_client" -ForegroundColor White
Write-Host "    flutter build apk --release" -ForegroundColor White
Write-Host ""
Write-Host "  [Signaling Server]" -ForegroundColor Green
Write-Host "    cd codechat_signaling" -ForegroundColor White
Write-Host "    npm start                  (production)" -ForegroundColor White
Write-Host "    npm run dev                (with auto-reload)" -ForegroundColor White
Write-Host ""
Write-Host "  [Install/refresh Dart packages]" -ForegroundColor Green
Write-Host "    cd codechat_client" -ForegroundColor White
Write-Host "    flutter pub get" -ForegroundColor White
Write-Host ""
Write-Host "  [Install/refresh Node packages]" -ForegroundColor Green
Write-Host "    cd codechat_signaling" -ForegroundColor White
Write-Host "    npm ci" -ForegroundColor White
Write-Host ""
Write-Host "  [Full flutter doctor check]" -ForegroundColor Green
Write-Host "    flutter doctor -v" -ForegroundColor White
Write-Host ""
Write-Host "  [Accept Android licenses (if prompted)]" -ForegroundColor Green
Write-Host "    flutter doctor --android-licenses" -ForegroundColor White
Write-Host ""
Write-Host "─────────────────────────────────────────────────" -ForegroundColor DarkGray
Write-Host ""
Write-Host "  SDK Locations" -ForegroundColor Cyan
Write-Host "─────────────────────────────────────────────────" -ForegroundColor DarkGray
Write-Host "  Flutter SDK  : C:\Users\$env:USERNAME\flutter" -ForegroundColor White
Write-Host "  Android SDK  : $ANDROID_HOME" -ForegroundColor White
Write-Host "  Java (JBR)   : $JAVA_HOME" -ForegroundColor White
Write-Host "  cmdline-tools: $SDK_TOOLS" -ForegroundColor White
Write-Host "  platform-tools: $PLATFORM_TOOLS" -ForegroundColor White
Write-Host ""
