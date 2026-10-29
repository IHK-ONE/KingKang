cmake_minimum_required(VERSION 3.22.1)
project(kingkang_pty C)

add_library(kingkang_pty SHARED pty_bridge.c)
target_link_libraries(kingkang_pty log)
