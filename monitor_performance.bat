@echo off
echo ==================================================
echo   NDI Player - Performance Monitor
echo ==================================================

echo.
echo Monitoring performance on device...
echo Press Ctrl+C to stop monitoring
echo.

adb logcat -s NDIPlayer:* PixelConverter:* NDI_Native:* | findstr /i "performance\|frame\|dropped\|fps\|memory\|optimization"

pause
