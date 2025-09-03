# 🚀 NDI Player - Scripts de Desarrollo

## Scripts Disponibles

### 📱 `dev_tools.bat` - Herramienta Principal
Script interactivo con todas las opciones:
- Compilar e instalar (debug/staging)
- Solo instalar APKs existentes  
- Ejecutar aplicaciones
- Ver logs en tiempo real

```bash
.\dev_tools.bat
```

### ⚡ `quick_test.bat` - Testing Rápido
Compila, instala y ejecuta automáticamente la versión debug:
```bash
.\quick_test.bat
```

### 🔍 `watch_logs.bat` - Solo Logs
Ver logs en tiempo real sin instalar nada:
```bash
.\watch_logs.bat
```

### 🏗️ `install_staging.bat` - Versión con Logs Extra
Compila e instala la versión staging con logs extensivos:
```bash
.\install_staging.bat
```

## Versiones de la App

### 🐛 Debug (`app-debug.apk`)
- Package: `ndiplayer.oto`
- Logs normales
- Debugging habilitado

### 🚀 Staging (`app-staging.apk`)  
- Package: `ndiplayer.oto.staging`
- Logs extensivos
- Fuentes NDI simuladas
- Información de debug adicional

## Comandos Manuales

```bash
# Compilar versión debug
.\gradlew assembleDebug

# Compilar versión staging (con logs extra)
.\gradlew assembleStaging

# Instalar directamente
.\gradlew installDebug

# Ver dispositivos conectados
adb devices

# Ver logs específicos
adb logcat | findstr "NDI\|MainActivity"

# Ejecutar app debug
adb shell am start -n ndiplayer.oto/.MainActivity

# Ejecutar app staging  
adb shell am start -n ndiplayer.oto.staging/.MainActivity
```

## Workflow Recomendado

1. **Desarrollo inicial**: Usa `.\dev_tools.bat` opción 1 (debug)
2. **Testing con logs**: Usa `.\dev_tools.bat` opción 2 (staging)
3. **Solo ver qué pasa**: Usa `.\watch_logs.bat`
4. **Testing rápido**: Usa `.\quick_test.bat`

## Solución de Problemas

- Si adb no funciona, asegúrate de tener Android SDK instalado
- Si no aparecen dispositivos, activa "Depuración USB" en tu dispositivo
- Para conectar por WiFi: `adb connect IP_DISPOSITIVO:5555`
