# SPDX-FileCopyrightText: 2026 Leitwolf <xs-lang.chess031@slmails.com>
# SPDX-License-Identifier: MPL-2.0

if(NOT CMAKE_SYSTEM_NAME STREQUAL "Linux")
  message(FATAL_ERROR "The first XIT runtime slice currently supports Linux with GNUstep Base")
endif()

find_package(Threads REQUIRED)
find_path(XIT_GNUSTEP_INCLUDE_DIR Foundation/Foundation.h REQUIRED)

file(GLOB XIT_OBJC_RUNTIME_INCLUDE_CANDIDATES LIST_DIRECTORIES true "/usr/lib/gcc/*/*/include")
find_path(
  XIT_OBJC_RUNTIME_INCLUDE_DIR
  objc/objc.h
  PATHS ${XIT_OBJC_RUNTIME_INCLUDE_CANDIDATES}
  REQUIRED)

find_library(XIT_GNUSTEP_BASE_LIBRARY NAMES gnustep-base REQUIRED)
find_library(XIT_OBJC_RUNTIME_LIBRARY NAMES objc REQUIRED)

add_library(XitGNUstepBase INTERFACE)
add_library(Xit::GNUstepBase ALIAS XitGNUstepBase)

target_include_directories(
  XitGNUstepBase
  SYSTEM INTERFACE "${XIT_GNUSTEP_INCLUDE_DIR}" "${XIT_OBJC_RUNTIME_INCLUDE_DIR}")
target_compile_definitions(
  XitGNUstepBase
  INTERFACE GNUSTEP
            GNUSTEP_BASE_LIBRARY=1
            GNU_RUNTIME=1
            GS_USE_LIBDISPATCH=0)
target_compile_options(
  XitGNUstepBase
  INTERFACE "$<$<COMPILE_LANGUAGE:OBJC>:-fobjc-exceptions>"
            "$<$<COMPILE_LANGUAGE:OBJC>:-fconstant-string-class=NSConstantString>")
target_link_libraries(
  XitGNUstepBase
  INTERFACE "${XIT_GNUSTEP_BASE_LIBRARY}"
            "${XIT_OBJC_RUNTIME_LIBRARY}"
            Threads::Threads
            m)
