
cmake_minimum_required(VERSION 3.22.1)

project(AndroidSampler LANGUAGES C CXX)

add_library(
    fluidsynth_bridge
    SHARED
    fluidsynth_bridge.cpp
)

find_package(fluidsynth REQUIRED CONFIG)

target_link_libraries(
    fluidsynth_bridge
    fluidsynth::fluidsynth
)
