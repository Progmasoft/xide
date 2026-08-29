/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.0
 */

#include <xide/xit.h>

#include "linux/window_backend.h"

#include <stdlib.h>

static XitWindowBackend xit_linux_window_backend(void)
{
  const char *wayland_display = getenv("WAYLAND_DISPLAY");
  if(wayland_display != nullptr && wayland_display[0] != '\0')
  {
    return XIT_WINDOW_BACKEND_WAYLAND;
  }

  const char *x11_display = getenv("DISPLAY");
  if(x11_display != nullptr && x11_display[0] != '\0')
  {
    return XIT_WINDOW_BACKEND_X11;
  }

  return XIT_WINDOW_BACKEND_AUTO;
}

XitStatus xit_window_run(const XitWindowConfig *config)
{
  if(config == nullptr)
  {
    return XIT_STATUS_INVALID_ARGUMENT;
  }
  if(config->struct_size < sizeof(*config))
  {
    return XIT_STATUS_INCOMPATIBLE_STRUCT;
  }
  if(config->width == 0 || config->height == 0 || config->width > UINT16_MAX ||
     config->height > UINT16_MAX)
  {
    return XIT_STATUS_INVALID_ARGUMENT;
  }

  const XitWindowBackend backend =
      config->backend == XIT_WINDOW_BACKEND_AUTO ? xit_linux_window_backend() : config->backend;
  switch(backend)
  {
  case XIT_WINDOW_BACKEND_X11:
    return xit_linux_x11_window_run(config);
  case XIT_WINDOW_BACKEND_WAYLAND:
  case XIT_WINDOW_BACKEND_AUTO:
    return XIT_STATUS_PLATFORM_UNAVAILABLE;
  default:
    return XIT_STATUS_INVALID_ARGUMENT;
  }
}
