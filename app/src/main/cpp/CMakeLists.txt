
cmake_minimum_required(VERSION 3.22.1)

project(AndroidSampler LANGUAGES C CXX)

add_library(
    fluidsynth_bridge
    SHARED
    fluidsynth_bridge.cpp
)

find_package(fluidsynth-android REQUIRED CONFIG)

target_link_libraries(
    fluidsynth_bridge
    fluidsynth-android::fluidsynth
)
