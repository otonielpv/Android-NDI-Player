@echo off
echo ================================
echo  NDI Player - ADB Setup Check
echo ================================

:: Buscar ADB en ubicaciones comunes
set ADB_PATH=""

echo Buscando ADB en ubicaciones comunes...

:: Android Studio
if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" (
    set ADB_PATH="%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
    echo Encontrado: Android Studio SDK
    goto FOUND
)

:: Android SDK manual
if exist "C:\Android\Sdk\platform-tools\adb.exe" (
    set ADB_PATH="C:\Android\Sdk\platform-tools\adb.exe"
    echo Encontrado: Android SDK manual
    goto FOUND
)

:: Program Files
if exist "%PROGRAMFILES%\Android\Android Studio\platform-tools\adb.exe" (
    set ADB_PATH="%PROGRAMFILES%\Android\Android Studio\platform-tools\adb.exe"
    echo Encontrado: Program Files
    goto FOUND
)

:: Verificar si está en PATH
adb version >nul 2>&1
if %errorlevel% equ 0 (
    set ADB_PATH="adb"
    echo Encontrado: En PATH del sistema
    goto FOUND
)

:NOT_FOUND
echo.
echo ❌ ADB no encontrado!
echo.
echo SOLUCION 1 - Instalar Android Studio:
echo 1. Descarga Android Studio desde: https://developer.android.com/studio
echo 2. Durante la instalacion, asegurate de incluir Android SDK
echo 3. Ejecuta este script nuevamente
echo.
echo SOLUCION 2 - Solo Android SDK:
echo 1. Descarga solo el SDK desde: https://developer.android.com/studio#command-tools
echo 2. Extracto en C:\Android\Sdk\
echo 3. Ejecuta este script nuevamente
echo.
echo SOLUCION 3 - Agregar al PATH:
echo 1. Encuentra tu carpeta platform-tools (ej: %LOCALAPPDATA%\Android\Sdk\platform-tools)
echo 2. Agrégala al PATH del sistema
echo 3. Reinicia PowerShell/CMD
echo.
pause
exit /b 1

:FOUND
echo.
echo ✅ ADB encontrado en: %ADB_PATH%
echo.

:: Crear archivo de configuración
echo set ADB_PATH=%ADB_PATH% > adb_config.bat
echo Configuracion guardada en adb_config.bat

:: Probar ADB
echo Probando conexion ADB...
%ADB_PATH% version
echo.

echo Dispositivos conectados:
%ADB_PATH% devices
echo.

echo ✅ ADB configurado correctamente!
echo Ahora puedes usar los otros scripts de desarrollo.
echo.
pause
