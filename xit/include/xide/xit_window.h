/*
 * SPDX-FileCopyrightText: 2026 Leitwolf <xs-lang.chess031@slmails.com>
 * SPDX-License-Identifier: MPL-2.0
 */

#ifndef XIDE_XIT_WINDOW_H
#define XIDE_XIT_WINDOW_H

#include <xide/xit.h>

typedef enum XitWindowBackend
{
  XIT_WINDOW_BACKEND_AUTO = 0,
  XIT_WINDOW_BACKEND_X11 = 1,
  XIT_WINDOW_BACKEND_WAYLAND = 2
} XitWindowBackend;

typedef struct XitWindowConfig
{
  size_t struct_size;
  uint32_t width;
  uint32_t height;
  const char *title;
  uint32_t frame_limit;
  XitWindowBackend backend;
} XitWindowConfig;

XIT_API XitStatus xit_window_run(const XitWindowConfig *config);

#endif
