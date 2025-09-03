@echo off
echo ==================================================
echo   NDI Player - Build Optimized for Low-End Devices
echo ==================================================

echo.
echo [1/3] Cleaning previous builds...
call gradlew clean

echo.
echo [2/3] Building optimized APK with performance settings...
call gradlew assembleDebug

echo.
echo [3/3] Installing optimized APK...
call adb install -r app\build\outputs\apk\debug\app-debug.apk

echo.
echo ==================================================
echo   Installation complete!
echo   Performance optimizations applied:
echo   - Native SIMD pixel conversion
echo   - Object pooling for memory efficiency  
echo   - Async frame processing
echo   - NDI bandwidth optimization
echo   - Hardware acceleration enabled
echo   - Cleaned unused files for smaller APK
echo ==================================================

pause
