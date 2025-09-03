@echo off
echo ================================
echo  NDI Player - Staging Build
echo ================================

:: Configurar ADB
call :SETUP_ADB
if %errorlevel% neq 0 (
    echo ❌ ADB no encontrado. Ejecuta setup_adb.bat primero.
    pause
    exit /b 1
)

echo.
echo Compilando version de staging...
call gradlew assembleStaging

if %errorlevel% neq 0 (
    echo ERROR: Compilacion fallida
    pause
    exit /b 1
)

echo.
echo Dispositivos disponibles:
%ADB_CMD% devices

echo.
echo Instalando version de staging...
%ADB_CMD% install -r app\build\outputs\apk\staging\app-staging.apk

echo.
echo Iniciando aplicacion de staging...
%ADB_CMD% shell am start -n ndiplayer.oto.staging/.MainActivity

echo.
echo Version de staging instalada y ejecutada!
echo Esta version incluye:
echo - Logs extensivos
echo - Fuentes NDI simuladas
echo - Informacion de debug adicional
echo.

pause
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
