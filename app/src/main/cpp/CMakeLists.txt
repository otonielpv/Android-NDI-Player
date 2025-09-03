cmake_minimum_required(VERSION 3.22.1)

project("ndiplayer")

# Configuración de C++
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Agregar directorio de headers NDI
include_directories(ndi/include)

# Crear librería nativa
add_library(
    ndiplayer
    SHARED
    native-lib.cpp
    ndi_wrapper.cpp
    pixel_converter.cpp
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

# Enlazar librerías
target_link_libraries(
    ndiplayer
    ndi
    ${log-lib}
    ${android-lib}
)
