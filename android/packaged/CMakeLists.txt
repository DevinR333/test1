cmake_minimum_required(VERSION 3.22.1)
project(oracle C)

# Everything this needs is in this folder. The game's code was translated from
# the cartridge before the project was packaged, and the world outside the
# screen was built from the disassembly at the same time, so there is nothing
# to generate here and no Python, no ROM hunting and no second checkout in the
# way of pressing Run.

file(GLOB TRANSLATED "${CMAKE_CURRENT_SOURCE_DIR}/game/bank_*.c")
list(LENGTH TRANSLATED BANK_COUNT)
if(BANK_COUNT LESS 1)
    message(FATAL_ERROR "game/ has no translated banks - the zip is incomplete.")
endif()
message(STATUS "oracle: ${BANK_COUNT} translated banks")

add_library(oracle SHARED
    android_main.c
    runtime/alu.c
    runtime/memory.c
    runtime/ppu.c
    runtime/machine.c
    runtime/interp.c
    runtime/interp_gen.c
    runtime/present.c
    runtime/io_masks.c
    runtime/diag.c
    runtime/display.c
    runtime/worldmap.c
    game/world_data.c
    game/dispatch.c
    ${TRANSLATED}
    # Shipped with the NDK: the plumbing that lets a native activity run a
    # normal main loop instead of living inside callbacks.
    ${ANDROID_NDK}/sources/android/native_app_glue/android_native_app_glue.c
)

target_include_directories(oracle PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/runtime
    ${CMAKE_CURRENT_SOURCE_DIR}/game
    ${ANDROID_NDK}/sources/android/native_app_glue
)

# The translated banks are enormous and already straight-line; optimising them
# is slow and buys nothing worth the wait, the machine being emulated running
# at four megahertz. The runtime is ordinary code and is built normally.
set_source_files_properties(${TRANSLATED} game/dispatch.c game/world_data.c
    PROPERTIES COMPILE_OPTIONS "-O1")

target_compile_options(oracle PRIVATE -Wall -O2 -fno-strict-aliasing)
target_link_libraries(oracle android log EGL GLESv2 m)
