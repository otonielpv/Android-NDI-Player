@echo off
REM Script de compilación optimizada para NDI Player
REM Ultra-optimizado para dispositivos de bajo rendimiento

echo =================================================
echo    NDI Player - Compilacion Ultra-Optimizada
echo =================================================

set SCRIPT_DIR=%~dp0
cd /d "%SCRIPT_DIR%"

echo.
echo [1/6] Limpiando builds anteriores...
call gradlew clean
if errorlevel 1 (
    echo ERROR: Falló la limpieza
    goto :error
)

echo.
echo [2/6] Verificando dependencias NDI...
if not exist "app\src\main\jniLibs" (
    echo ERROR: Directorio jniLibs no encontrado
    echo Asegurate de que las librerías NDI estén en app\src\main\jniLibs\
    goto :error
)

echo.
echo [3/6] Compilando versión para dispositivos de gama baja...
call gradlew assembleLowend -Pandroid.enableR8.fullMode=true --parallel --daemon
if errorlevel 1 (
    echo ERROR: Falló compilación lowend
    goto :error
)

echo.
echo [4/6] Compilando versión debug optimizada...
call gradlew assembleDebug -Pandroid.enableR8=true --parallel --daemon
if errorlevel 1 (
    echo ERROR: Falló compilación debug
    goto :error
)

echo.
echo [5/6] Compilando versión release optimizada...
call gradlew assembleRelease -Pandroid.enableR8.fullMode=true --parallel --daemon
if errorlevel 1 (
    echo ERROR: Falló compilación release
    goto :error
)

echo.
echo [6/6] Generando reporte de optimización...
call gradlew bundleRelease --scan
if errorlevel 1 (
    echo ADVERTENCIA: Falló generación de bundle
)

echo.
echo =================================================
echo          COMPILACION COMPLETADA EXITOSAMENTE
echo =================================================
echo.
echo APKs generados:
echo - Gama Baja:  app\build\outputs\apk\lowend\release\
echo - Debug:      app\build\outputs\apk\debug\
echo - Release:    app\build\outputs\apk\release\
echo.
echo Instalación rápida para testing:
echo   adb install -r app\build\outputs\apk\lowend\release\app-lowend-release.apk
echo.
echo Rendimiento optimizado para:
echo   ✓ Dispositivos Android 5.0+ (API 21)
echo   ✓ RAM limitada (1-2GB)
echo   ✓ CPU de 1-4 cores
echo   ✓ Resolución adaptativa (480p-1080p)
echo   ✓ FPS adaptativo (20-60fps)
echo.
pause
goto :end

:error
echo.
echo =================================================
echo                ERROR EN COMPILACION
echo =================================================
echo.
echo Soluciones posibles:
echo 1. Verificar que Android SDK esté configurado
echo 2. Verificar que NDK esté instalado
echo 3. Verificar que las librerías NDI estén presentes
echo 4. Limpiar completamente el proyecto: gradlew clean
echo.
pause
exit /b 1

:end
echo Compilación completada. Presiona cualquier tecla para salir.
pause >nul
