@echo off
echo ================================
echo  NDI Player - WiFi Connection
echo ================================

:: Configurar ADB
call :SETUP_ADB
if %errorlevel% neq 0 (
    echo ❌ ADB no encontrado. Ejecuta setup_adb.bat primero.
    pause
    exit /b 1
)

echo.
echo Para conectar por WiFi necesitas:
echo 1. Habilitar "Depuración inalámbrica" en tu Android TV
echo 2. Anotar la IP y puerto que aparece
echo.

set /p tv_ip="Introduce la IP de tu TV: "
set /p tv_port="Introduce el puerto (o presiona Enter para usar 5555): "

if "%tv_port%"=="" set tv_port=5555

echo.
echo Conectando a %tv_ip%:%tv_port%...
%ADB_CMD% connect %tv_ip%:%tv_port%

echo.
echo Verificando conexión...
%ADB_CMD% devices

echo.
echo Si ves tu dispositivo "device" está listo para usar.
echo Si ves "unauthorized" acepta la conexión en tu TV.
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
