cmake_minimum_required(VERSION 3.22.1)

project("ndiplayer")

# Configuraciones de optimización
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Flags de optimización para rendimiento máximo
set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -O3 -DNDEBUG -ffast-math")
set(CMAKE_CXX_FLAGS_DEBUG "${CMAKE_CXX_FLAGS_DEBUG} -O1 -g -DDEBUG")

# Optimizaciones específicas para ARM
if(${ANDROID_ABI} STREQUAL "armeabi-v7a")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -mfpu=neon")
elseif(${ANDROID_ABI} STREQUAL "arm64-v8a")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -march=armv8-a")
endif()

# Agregar directorio de headers NDI
include_directories(ndi/include)

# Crear librería nativa con archivos optimizados
add_library(
    ndiplayer
    SHARED
    native-lib.cpp
    ndi_wrapper_optimized.cpp
)

# Buscar librerías del sistema
find_library(log-lib log)
find_library(android-lib android)

# Agregar librería NDI precompilada
add_library(ndi SHARED IMPORTED)
set_target_properties(
    ndi
    PROPERTIES
    IMPORTED_LOCATION
    ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libndi.so
)

# Definiciones del compilador para optimización
target_compile_definitions(ndiplayer PRIVATE
    -DNDI_DEPRECATED=
    -DUSE_OPTIMIZED_IMPLEMENTATION=1
    -DENABLE_PERFORMANCE_LOGGING=1
    -DMAX_FRAME_CACHE_SIZE=3
)

# Opciones de compilación optimizadas
target_compile_options(ndiplayer PRIVATE
    -Wall
    -Wextra
    -Wno-unused-parameter
    -fvisibility=hidden
    -ffunction-sections
    -fdata-sections
    -funroll-loops
)

# Opciones del linker para optimización
set_target_properties(ndiplayer PROPERTIES
    LINK_FLAGS "-Wl,--gc-sections -Wl,--strip-debug"
)

# Enlazar librerías
target_link_libraries(
    ndiplayer
    ndi
    ${log-lib}
    ${android-lib}
)
