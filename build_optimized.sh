#!/bin/bash
# Script de compilación optimizada para NDI Player (Linux/Mac)
# Ultra-optimizado para dispositivos de bajo rendimiento

echo "================================================="
echo "   NDI Player - Compilación Ultra-Optimizada"
echo "================================================="

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
cd "$SCRIPT_DIR"

# Función para manejo de errores
handle_error() {
    echo ""
    echo "================================================="
    echo "                ERROR EN COMPILACIÓN"
    echo "================================================="
    echo ""
    echo "Soluciones posibles:"
    echo "1. Verificar que Android SDK esté configurado"
    echo "2. Verificar que NDK esté instalado"  
    echo "3. Verificar que las librerías NDI estén presentes"
    echo "4. Limpiar completamente el proyecto: ./gradlew clean"
    echo ""
    exit 1
}

echo ""
echo "[1/6] Limpiando builds anteriores..."
./gradlew clean || handle_error

echo ""
echo "[2/6] Verificando dependencias NDI..."
if [ ! -d "app/src/main/jniLibs" ]; then
    echo "ERROR: Directorio jniLibs no encontrado"
    echo "Asegúrate de que las librerías NDI estén en app/src/main/jniLibs/"
    handle_error
fi

echo ""
echo "[3/6] Compilando versión para dispositivos de gama baja..."
./gradlew assembleLowend -Pandroid.enableR8.fullMode=true --parallel --daemon || handle_error

echo ""
echo "[4/6] Compilando versión debug optimizada..."
./gradlew assembleDebug -Pandroid.enableR8=true --parallel --daemon || handle_error

echo ""
echo "[5/6] Compilando versión release optimizada..."
./gradlew assembleRelease -Pandroid.enableR8.fullMode=true --parallel --daemon || handle_error

echo ""
echo "[6/6] Generando reporte de optimización..."
./gradlew bundleRelease --scan || echo "ADVERTENCIA: Falló generación de bundle"

echo ""
echo "================================================="
echo "         COMPILACIÓN COMPLETADA EXITOSAMENTE"
echo "================================================="
echo ""
echo "APKs generados:"
echo "- Gama Baja:  app/build/outputs/apk/lowend/release/"
echo "- Debug:      app/build/outputs/apk/debug/"
echo "- Release:    app/build/outputs/apk/release/"
echo ""
echo "Instalación rápida para testing:"
echo "  adb install -r app/build/outputs/apk/lowend/release/app-lowend-release.apk"
echo ""
echo "Rendimiento optimizado para:"
echo "  ✓ Dispositivos Android 5.0+ (API 21)"
echo "  ✓ RAM limitada (1-2GB)"
echo "  ✓ CPU de 1-4 cores"
echo "  ✓ Resolución adaptativa (480p-1080p)"
echo "  ✓ FPS adaptativo (20-60fps)"
echo ""
