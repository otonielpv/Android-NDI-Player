@echo off
setlocal enabledelayedexpansion

echo ================================
echo   NDI Player - Development Tools
echo ================================

:: Configurar ADB
call adb_config.bat
set ADB_CMD=%ADB_PATH%

echo.
echo Dispositivos disponibles:
!ADB_CMD! devices

echo.
echo Opciones de Build:
echo 1. Debug normal (compilar e instalar)
echo 2. Staging con logs extra (compilar e instalar)
echo 3. Solo instalar debug (sin compilar)
echo 4. Solo instalar staging (sin compilar)
echo.
echo Opciones de Testing:
echo 5. Ejecutar app debug
echo 6. Ejecutar app staging
echo 7. Ver logs en tiempo real
echo 8. Limpiar y ver logs
echo.

set /p choice="Selecciona una opcion (1-8): "

if "%choice%"=="1" goto BUILD_DEBUG
if "%choice%"=="2" goto BUILD_STAGING
if "%choice%"=="3" goto INSTALL_DEBUG
if "%choice%"=="4" goto INSTALL_STAGING
if "%choice%"=="5" goto RUN_DEBUG
if "%choice%"=="6" goto RUN_STAGING
if "%choice%"=="7" goto LOGS_ONLY
if "%choice%"=="8" goto LOGS_CLEAN

:BUILD_DEBUG
echo.
echo Compilando version debug...
call gradlew assembleDebug
if %errorlevel% neq 0 (
    echo ERROR: Compilacion fallida
    pause
    exit /b 1
)
goto INSTALL_DEBUG

:BUILD_STAGING
echo.
echo Compilando version staging (con logs extra)...
call gradlew assembleStaging
if %errorlevel% neq 0 (
    echo ERROR: Compilacion fallida
    pause
    exit /b 1
)
goto INSTALL_STAGING

:INSTALL_DEBUG
echo.
echo Instalando version debug...
!ADB_CMD! install -r app\build\outputs\apk\debug\app-debug.apk
if %errorlevel% neq 0 (
    echo ERROR: Instalacion fallida
    pause
    exit /b 1
)
echo ✅ Debug instalado correctamente
goto RUN_DEBUG

:INSTALL_STAGING
echo.
echo Instalando version staging...
!ADB_CMD! install -r app\build\outputs\apk\staging\app-staging.apk
if %errorlevel% neq 0 (
    echo ERROR: Instalacion fallida
    pause
    exit /b 1
)
echo ✅ Staging instalado correctamente
goto RUN_STAGING

:RUN_DEBUG
echo.
echo Iniciando aplicacion debug...
!ADB_CMD! shell am start -n ndiplayer.oto.debug/ndiplayer.oto.MainActivity
echo ✅ App iniciada
goto LOGS_ONLY

:RUN_STAGING
echo.
echo Iniciando aplicacion staging...
!ADB_CMD! shell am start -n ndiplayer.oto.staging/ndiplayer.oto.MainActivity
echo ✅ App iniciada
goto LOGS_ONLY

:LOGS_CLEAN
echo.
echo Limpiando logs...
!ADB_CMD! logcat -c
echo ✅ Logs limpiados

:LOGS_ONLY
echo.
echo Mostrando logs de NDI Player (Ctrl+C para detener)...
echo ================================================================
!ADB_CMD! logcat | findstr "MainActivity\|NDIDiscovery\|NDIPlayer\|ERROR\|FATAL"

goto END

:END
pause
