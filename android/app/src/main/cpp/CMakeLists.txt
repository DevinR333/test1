cmake_minimum_required(VERSION 3.22.1)
project(oracle C)

# The repository root, four levels up from here.
get_filename_component(REPO "${CMAKE_CURRENT_SOURCE_DIR}/../../../../.." ABSOLUTE)

set(GEN "${REPO}/build/src")
set(WORLD "${REPO}/build/world_data.c")

file(GLOB TRANSLATED "${GEN}/bank_*.c")
if(NOT TRANSLATED OR NOT EXISTS "${WORLD}")
    message(FATAL_ERROR
        "The translated game has not been generated yet.\n"
        "Run this once, from the repository root:\n"
        "    bash scripts/generate-sources.sh /path/to/seasons.gbc\n"
        "It writes build/src/*.c and build/world_data.c, which this build reads.")
endif()

add_library(oracle SHARED
    android_main.c
    ${REPO}/runtime/alu.c
    ${REPO}/runtime/memory.c
    ${REPO}/runtime/ppu.c
    ${REPO}/runtime/machine.c
    ${REPO}/runtime/interp.c
    ${REPO}/runtime/interp_gen.c
    ${REPO}/runtime/present.c
    ${REPO}/runtime/io_masks.c
    ${REPO}/runtime/diag.c
    ${REPO}/runtime/display.c
    ${REPO}/runtime/worldmap.c
    ${WORLD}
    ${TRANSLATED}
    ${GEN}/dispatch.c
    # Shipped with the NDK: the plumbing that lets a native activity run a
    # normal main loop instead of living inside callbacks.
    ${ANDROID_NDK}/sources/android/native_app_glue/android_native_app_glue.c
)

target_include_directories(oracle PRIVATE
    ${REPO}/runtime
    ${GEN}
    ${ANDROID_NDK}/sources/android/native_app_glue
)

# The translated banks are enormous and already straight-line; optimising them
# is slow and buys nothing worth the wait. The runtime is ordinary code.
set_source_files_properties(${TRANSLATED} ${GEN}/dispatch.c ${WORLD}
    PROPERTIES COMPILE_OPTIONS "-O1")

target_compile_options(oracle PRIVATE -Wall -O2 -fno-strict-aliasing)
target_link_libraries(oracle android log EGL GLESv2 m)
