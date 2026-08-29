# SPDX-FileCopyrightText: 2026 Progmasoft <support@progmasoft.com>
# SPDX-License-Identifier: MPL-2.0 WITH AdditionRef-Progmasoft-Exception-1.0

function(xit_enable_strict_warnings target)
  target_compile_options(
    "${target}"
    PRIVATE "$<$<COMPILE_LANGUAGE:C,OBJC>:-Wall>"
            "$<$<COMPILE_LANGUAGE:C,OBJC>:-Wextra>"
            "$<$<COMPILE_LANGUAGE:C,OBJC>:-Wpedantic>"
            "$<$<COMPILE_LANGUAGE:C,OBJC>:-Werror>"
            "$<$<COMPILE_LANGUAGE:C,OBJC>:-Wconversion>"
            "$<$<COMPILE_LANGUAGE:C,OBJC>:-Wsign-conversion>"
            "$<$<COMPILE_LANGUAGE:C,OBJC>:-Wshadow>"
            "$<$<COMPILE_LANGUAGE:C,OBJC>:-Wstrict-prototypes>"
            "$<$<COMPILE_LANGUAGE:C,OBJC>:-Wmissing-prototypes>"
            "$<$<COMPILE_LANGUAGE:OBJC>:-Wobjc-interface-ivars>"
            "$<$<COMPILE_LANGUAGE:OBJC>:-Wselector>")
endfunction()
