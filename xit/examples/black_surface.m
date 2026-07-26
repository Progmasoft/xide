/*
 * SPDX-FileCopyrightText: 2026 Leitwolf <xs-lang.chess031@slmails.com>
 * SPDX-License-Identifier: MPL-2.0
 */

#import <xide/xit.h>

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static uint32_t xit_example_frame_limit(int argument_count, const char *const *arguments)
{
  if(argument_count != 3 || strcmp(arguments[1], "--frames") != 0)
  {
    return 0;
  }

  char *end = nullptr;
  const unsigned long value = strtoul(arguments[2], &end, 10);
  if(end == arguments[2] || *end != '\0' || value > UINT32_MAX)
  {
    return 0;
  }
  return (uint32_t)value;
}

int main(int argument_count, const char *const *arguments)
{
  const XitWindowConfig config = {
      .struct_size = sizeof(config),
      .width = 960,
      .height = 600,
      .title = "XIT — native black surface",
      .frame_limit = xit_example_frame_limit(argument_count, arguments),
      .backend = XIT_WINDOW_BACKEND_AUTO,
  };
  const XitStatus status = xit_window_run(&config);
  if(status != XIT_STATUS_OK)
  {
    fprintf(stderr, "XIT black surface failed with status %d\n", status);
    return 1;
  }
  return 0;
}
