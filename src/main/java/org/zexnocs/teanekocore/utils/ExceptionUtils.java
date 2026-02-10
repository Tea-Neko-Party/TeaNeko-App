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

    public String buildExceptionMessage(Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        Set<Throwable> visited = new HashSet<>();

        while (throwable != null && !visited.contains(throwable)) {
            visited.add(throwable);

            sb.append("====> 异常类型: ").append(throwable.getClass().getName()).append("\n");
            sb.append("-> 异常信息: ").append(throwable.getMessage()).append("\n");
            sb.append("-> 异常堆栈:\n");
            for (StackTraceElement element : throwable.getStackTrace()) {
                sb.append("\tat ").append(element).append("\n");
            }

            throwable = throwable.getCause();
            if (throwable != null && !visited.contains(throwable)) {
                sb.append("-> 源头信息:\n");
            }
        }

        return sb.toString();
    }
}
