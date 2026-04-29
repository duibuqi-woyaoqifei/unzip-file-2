cmake_minimum_required(VERSION 3.22.1)

project("unzipfile")

add_library(
        unzip-lib
        SHARED
        native-lib.cpp
)

find_library(
        log-lib
        log
)

target_link_libraries(
        unzip-lib
        ${log-lib}
)
