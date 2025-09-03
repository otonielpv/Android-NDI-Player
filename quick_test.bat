@echo off
echo ================================
echo  NDI Player - Quick Development
echo ================================

:: Configurar ADB
call :SETUP_ADB
if %errorlevel% neq 0 (
    echo ❌ ADB no encontrado. Ejecuta setup_adb.bat primero.
    pause
    exit /b 1
)

echo.
echo 1. Compilando aplicacion...
call gradlew assembleDebug
if %errorlevel% neq 0 (
    echo ERROR: Compilacion fallida
    pause
    exit /b 1
)

echo.
echo 2. Buscando dispositivos conectados...
%ADB_CMD% devices

echo.
echo 3. Instalando aplicacion...
%ADB_CMD% install -r app\build\outputs\apk\debug\app-debug.apk
if %errorlevel% neq 0 (
    echo ERROR: Instalacion fallida
    pause
    exit /b 1
)

echo.
echo 4. Iniciando aplicacion...
%ADB_CMD% shell am start -n ndiplayer.oto/.MainActivity

echo.
echo 5. Mostrando logs en tiempo real...
echo Presiona Ctrl+C para detener los logs
%ADB_CMD% logcat -s "MainActivity" "NDIDiscovery" "NDIPlayer"

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
pause
