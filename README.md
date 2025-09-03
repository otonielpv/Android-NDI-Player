# NDI Player Android

Un reproductor de video NDI (Network Device Interface) nativo para Android que permite recibir y visualizar transmisiones de video NDI en tiempo real.

## 🚀 Características

- **Conexión NDI automática**: Detección y conexión automática a fuentes NDI en la red
- **Reproducción en pantalla completa**: Modo inmersivo para visualización de video
- **Navegación intuitiva**: Interface simplificada sin botones innecesarios
- **Desconexión automática**: Al salir de pantalla completa se desconecta automáticamente
- **Soporte de video en tiempo real**: Procesamiento de frames BGRA de 1920x1080
- **Gestión de orientación**: Cambio manual de orientación sin crashes

## 📱 Uso

1. **Abrir la aplicación**: Verás una lista de fuentes NDI disponibles en la red
2. **Seleccionar fuente**: Toca cualquier fuente NDI para conectarte automáticamente
3. **Modo pantalla completa**: La aplicación entra automáticamente en pantalla completa
4. **Salir**: Presiona el botón "Atrás" para salir y desconectarte automáticamente

## 🛠️ Tecnologías

- **Android SDK**: Desarrollo nativo para Android
- **NDI SDK**: Oficial NDI SDK para Android
- **JNI (Java Native Interface)**: Bridge entre Java/Kotlin y código nativo C++
- **OpenGL/Graphics**: Procesamiento de frames de video en tiempo real

## 📂 Estructura del Proyecto

```
app/
├── src/main/
│   ├── java/ndiplayer/oto/
│   │   └── MainActivity.java          # Lógica principal de la aplicación
│   ├── cpp/
│   │   └── ndi_wrapper.cpp           # Bridge JNI para NDI SDK
│   └── res/
│       └── layout/
│           └── activity_main.xml     # Layout de la interfaz
├── build.gradle.kts                  # Configuración de Gradle
└── proguard-rules.pro               # Reglas de ofuscación
```

## 🔧 Scripts de Desarrollo

- `install_testing.bat` - Compilar e instalar versión de testing
- `install_staging.bat` - Compilar e instalar versión de staging
- `watch_logs.bat` - Monitorear logs en tiempo real
- `test_adb.bat` - Verificar conexión ADB
- `setup_adb.bat` - Configurar ADB

## 📋 Requisitos

- **Android 7.0** (API level 24) o superior
- **Conexión de red** para detectar fuentes NDI
- **Fuentes NDI activas** en la misma red

## 🚀 Instalación y Desarrollo

### Compilar e Instalar
```bash
# Versión de testing
./install_testing.bat

# Versión de staging  
./install_staging.bat
```

### Monitorear Logs
```bash
./watch_logs.bat
```

### Verificar Conexión
```bash
./test_adb.bat
```

## 🐛 Solución de Problemas

### La aplicación no detecta fuentes NDI
- Verifica que el dispositivo Android esté en la misma red que las fuentes NDI
- Asegúrate que las fuentes NDI estén transmitiendo activamente
- Revisa los logs con `watch_logs.bat`

### Crashes al entrar en pantalla completa
- ✅ **RESUELTO**: Se implementó manejo de cambios de configuración
- El usuario debe cambiar la orientación manualmente si lo desea

### Video no se muestra
- Verifica los logs para errores de conexión NDI
- Asegúrate que la fuente NDI esté transmitiendo en formato compatible

## 📄 Licencia

Este proyecto está destinado para uso personal y de desarrollo.

---

**Desarrollado con ❤️ para streaming profesional con NDI**
- El rendimiento depende de la calidad de la red WiFi
