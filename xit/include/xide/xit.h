/*
 * SPDX-FileCopyrightText: 2026 Leitwolf <xs-lang.chess031@slmails.com>
 * SPDX-License-Identifier: MPL-2.0
 */

#ifndef XIDE_XIT_H
#define XIDE_XIT_H

#include <stddef.h>
#include <stdint.h>

#if defined(_WIN32)
#if defined(XIT_BUILDING_LIBRARY)
#define XIT_API __declspec(dllexport)
#else
#define XIT_API __declspec(dllimport)
#endif
#else
#define XIT_API __attribute__((visibility("default")))
#endif

#define XIT_ABI_VERSION 0u

typedef enum XitStatus
{
  XIT_STATUS_OK = 0,
  XIT_STATUS_INVALID_ARGUMENT = 1,
  XIT_STATUS_INCOMPATIBLE_STRUCT = 2,
  XIT_STATUS_BUFFER_TOO_SMALL = 3,
  XIT_STATUS_PLATFORM_UNAVAILABLE = 4,
  XIT_STATUS_GRAPHICS_UNAVAILABLE = 5
} XitStatus;

typedef struct XitRuntimeInfo
{
  size_t struct_size;
  uint32_t abi_version;
  uint32_t runtime_major;
  uint32_t runtime_minor;
  uint32_t runtime_patch;
} XitRuntimeInfo;

XIT_API XitStatus xit_runtime_info(XitRuntimeInfo *out_info);

XIT_API XitStatus xit_runtime_description(char *buffer, size_t buffer_size, size_t *out_required_size);

#include <xide/xit_window.h>

#endif
