# SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
# SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.0

find_path(XIT_XCB_INCLUDE_DIR xcb/xcb.h REQUIRED)
find_library(XIT_XCB_LIBRARY NAMES xcb REQUIRED)

find_path(XIT_EGL_INCLUDE_DIR EGL/egl.h REQUIRED)
find_library(XIT_EGL_LIBRARY NAMES EGL REQUIRED)

find_path(XIT_OPENGL_INCLUDE_DIR GL/gl.h REQUIRED)
find_library(XIT_OPENGL_LIBRARY NAMES GL REQUIRED)

add_library(XitLinuxGraphics INTERFACE)
add_library(Xit::LinuxGraphics ALIAS XitLinuxGraphics)

target_include_directories(
  XitLinuxGraphics
  SYSTEM INTERFACE "${XIT_XCB_INCLUDE_DIR}" "${XIT_EGL_INCLUDE_DIR}" "${XIT_OPENGL_INCLUDE_DIR}")
target_link_libraries(
  XitLinuxGraphics
  INTERFACE "${XIT_XCB_LIBRARY}" "${XIT_EGL_LIBRARY}" "${XIT_OPENGL_LIBRARY}")
