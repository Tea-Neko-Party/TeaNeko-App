package org.zexnocs.teanekocore.utils;

import java.util.HashSet;
import java.util.Set;

/**
 * 异常工具类，提供处理和格式化异常信息的方法。
 *
 * @author zExNocs
 * @date 2026/02/10
 */
public enum ExceptionUtils {
    instance;

    /**
     * 构建异常信息字符串，包含异常类型、信息和堆栈跟踪。
     * @param throwable 要处理的异常对象。
     * @return 格式化的异常信息字符串。
     */
    public String buildExceptionMessage(Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        Set<Throwable> visited = new HashSet<>();

        while (throwable != null && !visited.contains(throwable)) {
            visited.add(throwable);

            // 堆栈信息
            sb.append("====> 异常类型: ").append(throwable.getClass().getName()).append("\n");
            sb.append("-> 异常信息: ").append(throwable.getMessage()).append("\n");
            sb.append("-> 异常堆栈:\n");
            for (StackTraceElement element : throwable.getStackTrace()) {
                sb.append("\tat ").append(element).append("\n");
            }

            // 如果存在被掩盖的异常，尝试处理被掩盖的异常
            var suppressed = throwable.getSuppressed();
            if (suppressed != null) {
                for (Throwable sup : suppressed) {
                    if (!visited.contains(sup)) {
                        sb.append("""
                                -> 被掩盖的异常:
                                    -> 异常类型: %s
                                    -> 异常信息: %s
                                    -> 异常堆栈:""".formatted(sup.getClass().getName(), sup.getMessage()));
                        for (StackTraceElement element : sup.getStackTrace()) {
                            sb.append("\t\tat ").append(element).append("\n");
                        }
                        visited.add(sup);
                    }
                }
            }

            // 源头信息
            throwable = throwable.getCause();
            if (throwable != null && !visited.contains(throwable)) {
                sb.append("-> 源头信息:\n");
            }
        }

        return sb.toString();
    }
}
