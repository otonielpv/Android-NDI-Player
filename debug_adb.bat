@echo off
setlocal enabledelayedexpansion

echo === DEBUG ADB SETUP ===
echo.

echo 1. Llamando adb_config.bat...
call adb_config.bat

echo 2. ADB_PATH from config: "%ADB_PATH%"

echo 3. Setting ADB_CMD...
set ADB_CMD=%ADB_PATH%

echo 4. ADB_CMD without quotes: !ADB_CMD!

echo 5. Testing direct ADB path...
"%ADB_PATH%" version

echo 6. Testing ADB_CMD variable...
!ADB_CMD! version

pause
