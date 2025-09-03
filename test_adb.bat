@echo off
echo Testing ADB setup...

call adb_config.bat
echo ADB_PATH variable: %ADB_PATH%

set ADB_CMD="%ADB_PATH%"
echo ADB_CMD variable: %ADB_CMD%

echo Testing ADB command:
%ADB_CMD% version

pause
