@echo off
echo === Building TuTuTV ===
call gradlew.bat assembleDebug
if errorlevel 1 (
    echo BUILD FAILED
    pause
    exit /b 1
)

echo.
echo === Build successful ===
echo.
echo APK download URL:
echo   http://192.168.1.98:9090/app-debug.apk
echo.
echo Press Ctrl+C to stop the server.
echo.

cd app\build\outputs\apk\debug
python -m http.server 9090
