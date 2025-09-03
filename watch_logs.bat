@echo off
echo ================================
echo  NDI Player - Logs Only
echo ================================

:: Configurar ADB
call :SETUP_ADB
if %errorlevel% neq 0 (
    echo ❌ ADB no encontrado. Ejecuta setup_adb.bat primero.
    pause
    exit /b 1
)

echo Conectando para ver logs...
echo Presiona Ctrl+C para detener

%ADB_CMD% logcat -c
%ADB_CMD% logcat | findstr "MainActivity\|NDIDiscovery\|NDIPlayer\|ERROR\|FATAL"

goto END

:: Función para configurar ADB
:SETUP_ADB
set ADB_CMD=""

if exist "adb_config.bat" (
    call adb_config.bat
    set ADB_CMD=%ADB_PATH%
    exit /b 0
)

if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" (
    set ADB_CMD="%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
    exit /b 0
)

if exist "C:\Android\Sdk\platform-tools\adb.exe" (
    set ADB_CMD="C:\Android\Sdk\platform-tools\adb.exe"
    exit /b 0
)

adb version >nul 2>&1
if %errorlevel% equ 0 (
    set ADB_CMD="adb"
    exit /b 0
)

exit /b 1

:END
