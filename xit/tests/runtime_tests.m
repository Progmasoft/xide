/*
 * SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
 * SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.0
 */

#import <xide/xit.h>

#include <stdio.h>
#include <string.h>

static int check(bool condition, const char *message)
{
  if(condition)
  {
    return 0;
  }

  fprintf(stderr, "check failed: %s\n", message);
  return 1;
}

int main(void)
{
  int failures = 0;
  XitRuntimeInfo info = {.struct_size = sizeof(info)};

  failures += check(xit_runtime_info(&info) == XIT_STATUS_OK, "runtime info succeeds");
  failures += check(info.abi_version == XIT_ABI_VERSION, "runtime reports the public ABI version");
  failures += check(info.runtime_major == 0 && info.runtime_minor == 0 && info.runtime_patch == 1,
                    "runtime reports version 0.0.1");

  size_t required_size = 0;
  failures += check(xit_runtime_description(nullptr, 0, &required_size) == XIT_STATUS_OK,
                    "description size query succeeds");
  failures += check(required_size > 1, "description is not empty");

  char description[256] = {0};
  failures += check(required_size <= sizeof(description), "description fits the test buffer");
  failures += check(xit_runtime_description(description, sizeof(description), &required_size) ==
                        XIT_STATUS_OK,
                    "description copy succeeds");
  failures +=
      check(strstr(description, "GNUstep Base") != nullptr, "description identifies GNUstep Base");

  const XitWindowConfig short_config = {.struct_size = 1, .width = 640, .height = 480};
  failures += check(xit_window_run(&short_config) == XIT_STATUS_INCOMPATIBLE_STRUCT,
                    "short window configuration is rejected before platform access");

  const XitWindowConfig empty_config = {.struct_size = sizeof(empty_config)};
  failures += check(xit_window_run(&empty_config) == XIT_STATUS_INVALID_ARGUMENT,
                    "empty window dimensions are rejected before platform access");

  char too_small[2] = {0};
  failures += check(xit_runtime_description(too_small, sizeof(too_small), &required_size) ==
                        XIT_STATUS_BUFFER_TOO_SMALL,
                    "small destination is rejected");

  return failures == 0 ? 0 : 1;
}
