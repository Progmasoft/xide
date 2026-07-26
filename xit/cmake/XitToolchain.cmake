# SPDX-FileCopyrightText: 2026 Leitwolf <xs-lang.chess031@slmails.com>
# SPDX-License-Identifier: MPL-2.0

if(NOT CMAKE_OBJC_COMPILER_ID MATCHES "^(Apple)?Clang$")
  message(FATAL_ERROR "XIT requires Clang for Objective-C23 sources")
endif()

find_program(XIT_LLD_EXECUTABLE NAMES ld.lld REQUIRED)

add_link_options("-fuse-ld=lld")
