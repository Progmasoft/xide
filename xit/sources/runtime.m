/*
 * SPDX-FileCopyrightText: 2026 Leitwolf <xs-lang.chess031@slmails.com>
 * SPDX-License-Identifier: MPL-2.0
 */

#import <Foundation/Foundation.h>

#include <xide/xit.h>

#include <string.h>

XitStatus xit_runtime_info(XitRuntimeInfo *out_info)
{
  if(out_info == nullptr)
  {
    return XIT_STATUS_INVALID_ARGUMENT;
  }

  if(out_info->struct_size < sizeof(*out_info))
  {
    return XIT_STATUS_INCOMPATIBLE_STRUCT;
  }

  *out_info = (XitRuntimeInfo){
      .struct_size = sizeof(*out_info),
      .abi_version = XIT_ABI_VERSION,
      .runtime_major = 0,
      .runtime_minor = 0,
      .runtime_patch = 1,
  };
  return XIT_STATUS_OK;
}

XitStatus xit_runtime_description(char *buffer, size_t buffer_size, size_t *out_required_size)
{
  if(out_required_size == nullptr || (buffer == nullptr && buffer_size != 0))
  {
    return XIT_STATUS_INVALID_ARGUMENT;
  }

  @autoreleasepool
  {
    NSProcessInfo *process_info = [NSProcessInfo processInfo];
    NSString *description = [NSString stringWithFormat:@"XIT 0.0.1 | GNUstep Base | %@",
                                                       [process_info operatingSystemVersionString]];
    NSData *encoded = [description dataUsingEncoding:NSUTF8StringEncoding];

    if(encoded == nil)
    {
      return XIT_STATUS_INVALID_ARGUMENT;
    }

    const size_t encoded_size = [encoded length];
    const size_t required_size = encoded_size + 1;
    *out_required_size = required_size;

    if(buffer == nullptr)
    {
      return XIT_STATUS_OK;
    }

    if(buffer_size < required_size)
    {
      return XIT_STATUS_BUFFER_TOO_SMALL;
    }

    memcpy(buffer, [encoded bytes], encoded_size);
    buffer[encoded_size] = '\0';
  }

  return XIT_STATUS_OK;
}
