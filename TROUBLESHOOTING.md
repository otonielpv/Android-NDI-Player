## Guía de Solución de Problemas - NDI Player

### Problemas Conocidos y Soluciones

#### 1. La aplicación se cierra inesperadamente (Crash)

**Causas posibles:**
- Error en la librería nativa NDI
- Permisos insuficientes
- Problemas de red/WiFi
- Error en la inicialización de componentes

**Soluciones implementadas:**
1. **Manejo robusto de errores**: Se añadió try-catch en todos los métodos críticos
2. **Verificación de permisos**: La app verifica automáticamente los permisos necesarios
3. **Sistema de fallback**: Si la librería NDI nativa falla, se usa un descubrimiento alternativo
4. **Logging detallado**: Se añadieron logs extensivos para diagnosticar problemas

**Para diagnosticar:**
- Conecta tu TV/dispositivo Android al PC vía ADB
- Ejecuta: `adb logcat | findstr NDI` para ver los logs específicos de NDI
- Busca mensajes de error que indiquen la causa del problema

#### 2. No se encuentran fuentes NDI

**Causas posibles:**
- Fuentes NDI no están en la misma red
- Firewall bloqueando el tráfico multicast
- WiFi no permite multicast
- Error en la librería NDI

**Soluciones:**
1. Verificar que el dispositivo NDI y la TV estén en la misma red
2. La app ahora incluye un sistema de fallback que simula fuentes para probar la UI
3. El botón "Actualizar Fuentes" permite reintentar la búsqueda

#### 3. Permisos de red

**Permisos necesarios (incluidos en el manifest):**
- `INTERNET`: Para comunicación de red
- `ACCESS_NETWORK_STATE`: Para detectar estado de red
- `ACCESS_WIFI_STATE`: Para acceso a información WiFi
- `CHANGE_WIFI_MULTICAST_STATE`: Para recibir paquetes multicast NDI
- `WAKE_LOCK`: Para mantener la conexión activa

### Mejoras Implementadas

1. **Manejo de errores más robusto**
2. **Sistema de logging detallado**
3. **Verificación de permisos en tiempo de ejecución**
4. **Sistema de fallback cuando NDI nativo falla**
5. **Botón de refrescar fuentes NDI**
6. **Mejor feedback visual al usuario**

### Comandos de Diagnóstico

```bash
# Ver logs de la aplicación
adb logcat | findstr "NDIPlayer\|MainActivity\|NDIDiscovery"

# Ver logs específicos de errores
adb logcat | findstr "ERROR\|FATAL"

# Limpiar logs y ver solo nuevos
adb logcat -c && adb logcat | findstr NDI
```

### Próximos Pasos

Si la aplicación sigue crasheando, los logs detallados ahora te ayudarán a identificar la causa exacta del problema.
