# Guía de Testing para NDI Player Optimizado

## 🧪 Plan de Testing Completo

### 1. **Testing de Rendimiento por Tipo de Dispositivo**

#### Dispositivos de Gama Baja (RAM < 3GB, CPU < 4 cores)
```bash
# Instalar versión optimizada para gama baja
adb install -r app/build/outputs/apk/lowend/release/app-lowend-release.apk

# Métricas objetivo:
- FPS estable: 18-22 fps
- Latencia: < 120ms
- Uso de RAM: < 80MB
- CPU usage: < 65%
- Sin frame drops críticos
```

#### Dispositivos de Gama Media (RAM 3-6GB, CPU 4-6 cores)
```bash
# Usar build regular con configuración automática
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Métricas objetivo:
- FPS estable: 28-32 fps
- Latencia: < 80ms
- Uso de RAM: < 120MB  
- CPU usage: < 55%
- Resolución: 720p estable
```

#### Dispositivos de Gama Alta (RAM > 6GB, CPU > 6 cores)
```bash
# Usar build release completo
adb install -r app/build/outputs/apk/release/app-release.apk

# Métricas objetivo:
- FPS estable: 55-60 fps
- Latencia: < 50ms
- Uso de RAM: < 150MB
- CPU usage: < 45%
- Resolución: 1080p sin problemas
```

### 2. **Casos de Prueba Funcionales**

#### Caso 1: Discovery Automático
```
DADO que la app se inicia por primera vez
CUANDO el servicio NDI Discovery se activa
ENTONCES debería encontrar fuentes NDI en < 10 segundos
Y mostrar al menos 1 fuente si hay NDI activo en la red
Y permitir búsqueda manual con botón "🔍 Buscar Fuentes"
```

#### Caso 2: Conexión Optimizada  
```
DADO que hay fuentes NDI disponibles
CUANDO usuario toca una fuente de la lista
ENTONCES debería conectar en < 3 segundos
Y mostrar video en modo fullscreen automáticamente
Y mantener conexión estable por > 5 minutos
```

#### Caso 3: Adaptación de Calidad
```
DADO que está conectado a una fuente NDI
CUANDO el dispositivo está bajo carga (CPU > 80%)
ENTONCES debería reducir automáticamente la calidad
Y mantener FPS estable
Y recuperar calidad cuando la carga baje
```

#### Caso 4: Gestión de Memoria
```
DADO que la app está en uso por > 10 minutos
CUANDO se revisa el uso de memoria
ENTONCES no debería exceder los límites configurados
Y no debería tener memory leaks
Y los object pools deberían funcionar correctamente
```

### 3. **Testing de Stress**

#### Test de Resistencia (Endurance)
```bash
# Dejar la app corriendo por 1+ horas
# Monitorear:
- Uso de memoria (no debe incrementar constantemente)
- Temperatura del dispositivo
- Estabilidad de FPS
- Posibles crashes o ANRs
```

#### Test de Múltiples Conexiones
```bash
# Conectar y desconectar repetidamente
for i in {1..50}; do
    echo "Ciclo de conexión $i"
    # Conectar a fuente
    # Esperar 30 segundos  
    # Desconectar
    # Esperar 10 segundos
done
```

#### Test de Cambio de Orientación
```bash
# Probar rotación de pantalla múltiples veces
# Durante conexión activa
# Verificar que no crashee
# Y que mantenga la conexión
```

### 4. **Testing de Configuración**

#### Test de Detección de Dispositivo
```java
// Verificar clasificación correcta
@Test
public void testDeviceClassification() {
    PerformanceManager pm = new PerformanceManager(context);
    DeviceClass detected = pm.getDeviceClass();
    
    // Verificar que la clasificación sea razonable
    // según las specs del dispositivo de testing
    assertNotNull(detected);
    
    PerformanceConfig config = pm.getCurrentConfig();
    assertNotNull(config);
    assertTrue(config.targetFPS > 0);
    assertTrue(config.maxResolutionWidth > 0);
}
```

#### Test de Configuración Manual
```
DADO que el usuario accede a configuración de rendimiento
CUANDO selecciona "Calidad Baja"
ENTONCES debería aplicar configuración de gama baja
Y mostrar cambios inmediatamente
Y persistir la configuración entre sesiones
```

### 5. **Testing de Network**

#### Test de Network Discovery
```bash
# Probar en diferentes redes:
- WiFi doméstica típica
- Red corporativa con firewall
- Hotspot móvil
- Red con múltiples VLANs

# Verificar:
- Discovery funciona en cada red
- Timeout apropiado cuando no hay fuentes
- Retry logic funciona correctamente
```

#### Test de Bandwidth Adaptation
```
DADO que hay conexión NDI activa
CUANDO el bandwidth de red se reduce
ENTONCES la app debería adaptarse automáticamente
Y reducir calidad para mantener fluídez
```

### 6. **Comandos de Testing Automatizado**

#### Testing con ADB
```bash
# Monitoreo de memoria
adb shell dumpsys meminfo ndiplayer.oto

# Monitoreo de CPU
adb shell top -p $(adb shell ps | grep ndiplayer.oto | awk '{print $2}')

# Logs en tiempo real
adb logcat -s NDIPlayer:* NDI_Optimized:* NDIStreamProcessor:*

# Stats de performance
adb shell am broadcast -a android.intent.action.GET_PERFORMANCE_STATS
```

#### Profiling Avanzado
```bash
# Memory profiling
adb shell am profile start ndiplayer.oto /data/local/tmp/ndi_profile.trace

# CPU profiling con simpleperf
adb shell simpleperf record -p $(adb shell ps | grep ndiplayer.oto | awk '{print $2}') -o /data/local/tmp/perf.data
```

### 7. **Testing de Edge Cases**

#### Test de Baja Memoria
```bash
# Simular baja memoria del sistema
adb shell am send-trim-memory ndiplayer.oto RUNNING_LOW
adb shell am send-trim-memory ndiplayer.oto RUNNING_CRITICAL

# Verificar que la app:
- No crashee
- Libere memoria apropiadamente  
- Continue funcionando
```

#### Test de Interrupciones
```bash
# Simular llamadas telefónicas
adb shell am start -a android.intent.action.CALL -d tel:123456789

# Simular notificaciones
adb shell am broadcast -a android.intent.action.BATTERY_LOW

# Verificar comportamiento apropiado
```

### 8. **Criterios de Aceptación**

#### Performance Mínimo Aceptable
```
✓ Dispositivos Gama Baja: 
  - FPS ≥ 18, Latencia ≤ 150ms, RAM ≤ 100MB

✓ Dispositivos Gama Media:
  - FPS ≥ 25, Latencia ≤ 100ms, RAM ≤ 140MB

✓ Dispositivos Gama Alta:
  - FPS ≥ 50, Latencia ≤ 60ms, RAM ≤ 180MB
```

#### Estabilidad Requerida
```
✓ Sin crashes por 2+ horas de uso continuo
✓ Sin memory leaks detectables
✓ Reconexión automática tras pérdida de red
✓ Graceful degradation bajo carga extrema
```

### 9. **Reporting de Resultados**

#### Template de Reporte
```
## Resultados de Testing - Dispositivo: [MODELO]

### Specs del Dispositivo:
- RAM: [X]GB
- CPU: [X] cores, [Y]GHz
- GPU: [MODELO]
- Android: [VERSION]
- Clasificación automática: [LOW_END/MID_RANGE/HIGH_END]

### Métricas Obtenidas:
- FPS promedio: [X] fps
- Latencia promedio: [X] ms
- Uso de RAM pico: [X] MB
- CPU usage promedio: [X]%
- Duración de testing: [X] minutos
- Crashes detectados: [X]

### Observaciones:
[Comentarios específicos sobre comportamiento]

### Recomendaciones:
[Ajustes sugeridos si es necesario]
```

## 🎯 Quick Start Testing

### Testing Básico (5 minutos)
1. Instalar APK apropiado para el dispositivo
2. Iniciar app y verificar clasificación automática
3. Buscar fuentes NDI (debe encontrar al menos 1)
4. Conectar y verificar que entra en fullscreen
5. Verificar FPS estable por 2 minutos

### Testing Completo (30 minutos)
1. Ejecutar testing básico
2. Probar configuración manual de calidad
3. Test de rotación de pantalla
4. Test de reconexión tras pérdida de red
5. Monitorear memoria y CPU
6. Test de múltiples ciclos de conexión

### Testing de Stress (2+ horas)
1. Ejecutar testing completo
2. Dejar conectado por 1+ hora
3. Monitorear estabilidad long-term
4. Verificar no degradación de performance
5. Test bajo diferentes condiciones de carga

Esta guía de testing asegura que la aplicación optimizada funcione correctamente en todos los tipos de dispositivos y condiciones de uso.
