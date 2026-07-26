# SPDX-FileCopyrightText: 2026 Leitwolf <xs-lang.chess031@slmails.com>
# SPDX-License-Identifier: MPL-2.0

cmake_minimum_required(VERSION 3.31)

project(XIT VERSION 0.0.1 LANGUAGES C OBJC)

list(APPEND CMAKE_MODULE_PATH "${CMAKE_CURRENT_SOURCE_DIR}/cmake")

include(XitToolchain)
include(XitGNUstep)
include(XitWarnings)
include(CTest)
include(GNUInstallDirs)

option(XIT_ENABLE_CLANG_TIDY "Run clang-tidy while compiling XIT" ON)

if(XIT_ENABLE_CLANG_TIDY)
  find_program(XIT_CLANG_TIDY_EXECUTABLE NAMES clang-tidy REQUIRED)
  set(XIT_CLANG_TIDY
      "${XIT_CLANG_TIDY_EXECUTABLE};--config-file=${CMAKE_CURRENT_SOURCE_DIR}/.clang-tidy;--warnings-as-errors=*")
endif()

add_library(xit SHARED sources/runtime.m)
add_library(Xide::XIT ALIAS xit)

target_include_directories(
  xit
  PUBLIC "$<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include>"
         "$<INSTALL_INTERFACE:${CMAKE_INSTALL_INCLUDEDIR}>")
target_compile_definitions(xit PRIVATE XIT_BUILDING_LIBRARY)
target_link_libraries(xit PRIVATE Xit::GNUstepBase)

set_target_properties(
  xit
  PROPERTIES C_STANDARD 23
             C_STANDARD_REQUIRED ON
             C_EXTENSIONS OFF
             OBJC_STANDARD 23
             OBJC_STANDARD_REQUIRED ON
             OBJC_EXTENSIONS OFF
             C_VISIBILITY_PRESET hidden
             OBJC_VISIBILITY_PRESET hidden
             VISIBILITY_INLINES_HIDDEN ON
             VERSION "${PROJECT_VERSION}"
             SOVERSION 0)

xit_enable_strict_warnings(xit)

if(XIT_ENABLE_CLANG_TIDY)
  set_target_properties(xit PROPERTIES OBJC_CLANG_TIDY "${XIT_CLANG_TIDY}")
endif()

if(BUILD_TESTING)
  add_executable(xit_runtime_tests tests/runtime_tests.c)
  target_link_libraries(xit_runtime_tests PRIVATE Xide::XIT)
  set_target_properties(
    xit_runtime_tests
    PROPERTIES C_STANDARD 23
               C_STANDARD_REQUIRED ON
               C_EXTENSIONS OFF)
  xit_enable_strict_warnings(xit_runtime_tests)

  if(XIT_ENABLE_CLANG_TIDY)
    set_target_properties(xit_runtime_tests PROPERTIES C_CLANG_TIDY "${XIT_CLANG_TIDY}")
  endif()

  add_test(NAME xit.runtime COMMAND xit_runtime_tests)
endif()

install(
  TARGETS xit
  LIBRARY DESTINATION "${CMAKE_INSTALL_LIBDIR}"
  RUNTIME DESTINATION "${CMAKE_INSTALL_BINDIR}")
install(DIRECTORY include/ DESTINATION "${CMAKE_INSTALL_INCLUDEDIR}")
