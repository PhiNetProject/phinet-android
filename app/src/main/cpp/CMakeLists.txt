cmake_minimum_required(VERSION 3.22.1)
project("lockr")

add_library(lockr SHARED
    lockr-jni.c
    argon2.c
    spake2.c
    curve25519.c
)

find_library(log-lib log)

target_link_libraries(lockr ${log-lib})

# 16 KB page-size alignment (Android 15). liblockr.so is loaded via
# System.loadLibrary, so its segments must be 16 KB-aligned on such devices.
target_link_options(lockr PRIVATE "-Wl,-z,max-page-size=16384")
