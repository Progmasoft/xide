# SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
# SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.0

if(NOT CMAKE_SYSTEM_NAME STREQUAL "Linux")
  message(FATAL_ERROR "The first XIT runtime slice currently supports Linux with GNUstep Base")
endif()

find_path(
  XIT_GNUSTEP_INCLUDE_DIR
  Foundation/Foundation.h
  PATH_SUFFIXES GNUstep
  REQUIRED)

file(GLOB XIT_OBJC_RUNTIME_INCLUDE_CANDIDATES LIST_DIRECTORIES true "/usr/lib/gcc/*/*/include")
find_path(
  XIT_OBJC_RUNTIME_INCLUDE_DIR
  objc/objc.h
  PATHS ${XIT_OBJC_RUNTIME_INCLUDE_CANDIDATES}
  REQUIRED)

find_library(XIT_GNUSTEP_BASE_LIBRARY NAMES gnustep-base REQUIRED)
find_library(XIT_OBJC_RUNTIME_LIBRARY NAMES objc)

if(NOT XIT_OBJC_RUNTIME_LIBRARY)
  execute_process(
    COMMAND "${CMAKE_OBJC_COMPILER}" -print-file-name=libobjc.so
    OUTPUT_VARIABLE XIT_OBJC_RUNTIME_LIBRARY
    OUTPUT_STRIP_TRAILING_WHITESPACE
    COMMAND_ERROR_IS_FATAL ANY)

  if(NOT IS_ABSOLUTE "${XIT_OBJC_RUNTIME_LIBRARY}" OR NOT EXISTS "${XIT_OBJC_RUNTIME_LIBRARY}")
    find_program(XIT_GCC_EXECUTABLE NAMES gcc)
    if(XIT_GCC_EXECUTABLE)
      execute_process(
        COMMAND "${XIT_GCC_EXECUTABLE}" -print-file-name=libobjc.so
        OUTPUT_VARIABLE XIT_OBJC_RUNTIME_LIBRARY
        OUTPUT_STRIP_TRAILING_WHITESPACE
        COMMAND_ERROR_IS_FATAL ANY)
    endif()
  endif()

  if(NOT IS_ABSOLUTE "${XIT_OBJC_RUNTIME_LIBRARY}" OR NOT EXISTS "${XIT_OBJC_RUNTIME_LIBRARY}")
    message(FATAL_ERROR "The Objective-C runtime library could not be located")
  endif()
endif()

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
  INTERFACE -pthread
            "$<$<COMPILE_LANGUAGE:OBJC>:-fobjc-exceptions>"
            "$<$<COMPILE_LANGUAGE:OBJC>:-fconstant-string-class=NSConstantString>")
target_link_options(XitGNUstepBase INTERFACE -pthread)
target_link_libraries(
  XitGNUstepBase
  INTERFACE "${XIT_GNUSTEP_BASE_LIBRARY}"
            "${XIT_OBJC_RUNTIME_LIBRARY}"
            m)
