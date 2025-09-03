@echo off
echo ================================
echo  NDI Player - Staging Build
echo ================================

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
adb devices

echo.
echo Instalando version de staging...
adb install -r app\build\outputs\apk\staging\app-staging.apk

echo.
echo Iniciando aplicacion de staging...
adb shell am start -n ndiplayer.oto.staging/.MainActivity

echo.
echo Version de staging instalada y ejecutada!
echo Esta version incluye:
echo - Logs extensivos
echo - Fuentes NDI simuladas
echo - Informacion de debug adicional
echo.

pause
