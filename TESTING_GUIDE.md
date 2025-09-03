## 🚀 Guía de Testing sin TV Física

### **Opción 1: Emulador de Android TV (RECOMENDADO)**

#### Paso 1: Abrir Android Studio
1. Abre Android Studio
2. Ve a **Tools > AVD Manager**
3. Haz clic en **Create Virtual Device**

#### Paso 2: Seleccionar Android TV
1. En la pestaña **TV**, selecciona **Android TV (1080p)**
2. Haz clic en **Next**

#### Paso 3: Seleccionar imagen del sistema
1. Descarga **API Level 34** (Android 14) para TV
2. Haz clic en **Next**

#### Paso 4: Configurar el emulador
1. Nombre: `Android_TV_NDI_Test`
2. RAM: 2048 MB (mínimo)
3. Internal Storage: 6 GB
4. Haz clic en **Finish**

#### Paso 5: Ejecutar
```bash
# Desde Android Studio
Click en "Run" > "Run 'app'" > Selecciona el emulador de TV

# Desde terminal
.\gradlew installDebug
```

---

### **Opción 2: Testing con Smartphone/Tablet**

#### Modificar la app para funcionar en móvil:
- La app funcionará en cualquier dispositivo Android
- Solo necesitas conectar tu móvil por USB
- Activar "Depuración USB" en opciones de desarrollador

---

### **Opción 3: Usar ADB WiFi (Sin cables)**

#### Configurar ADB por WiFi:
```bash
# Conectar por USB primera vez
adb tcpip 5555

# Desconectar USB y conectar por WiFi
adb connect [IP_DE_TU_TV]:5555

# Instalar app por WiFi
adb install app-debug.apk

# Ver logs en tiempo real
adb logcat | findstr NDI
```

---

### **Opción 4: Simular Fuentes NDI**

Crear fuentes NDI falsas para testing:

#### En Windows (para simular):
```bash
# Usar OBS Studio con plugin NDI
# O usar NDI Test Patterns (gratuito)
```

---

### **Opción 5: Debug Remoto**

#### Ver logs desde cualquier dispositivo:
```bash
# Conectar dispositivo
adb devices

# Ver logs en tiempo real
adb logcat -s "MainActivity" "NDIDiscovery" "NDIPlayer"

# Filtrar solo errores
adb logcat *:E
```

---

### **🎯 Recomendación**

**Para desarrollo rápido**: Usa el **emulador de Android TV**
- ✅ No necesitas TV física
- ✅ Debugging completo
- ✅ Puedes simular controles remotos
- ✅ Instanánea instalación/actualización

**Para testing final**: Usa **ADB WiFi** con tu TV
- ✅ Testing en hardware real
- ✅ Sin cables
- ✅ Logs remotos

---

### **🛠️ Comandos Útiles**

```bash
# Compilar e instalar automáticamente
.\gradlew installDebug

# Compilar versión con logs extra (staging)
.\gradlew assembleStaging

# Ver dispositivos conectados
adb devices

# Desinstalar app
adb uninstall ndiplayer.oto

# Ver logs específicos de la app
adb logcat | findstr "ndiplayer.oto"

# Limpiar logs y empezar fresh
adb logcat -c
```
