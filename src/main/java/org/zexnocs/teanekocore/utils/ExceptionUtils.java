package org.zexnocs.teanekocore.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 异常工具类，提供处理和格式化异常信息的方法。
 *
 * <br>2026/11/06：统一生成有界安全根因链及栈，不复制第三方异常正文。
 *
 * @author zExNocs
 * @date 2026/02/10
 * @version 4.8.4-l
 */
public enum ExceptionUtils {
    instance;

    /**
     * 构建异常信息字符串，包含异常类型、信息和堆栈跟踪。
     * @param throwable 要处理的异常对象。
     * @return 格式化的异常信息字符串。
     */
    public List<String> buildExceptionMessageList(@org.jspecify.annotations.Nullable Throwable throwable) {
        if (throwable == null) {
            return List.of();
        }
        // 2026/11/06：日志与监护报告共用安全原因，第三方异常正文可能含凭据或整段对话。
        var diagnostic = org.zexnocs.teanekocore.logger.FailureDiagnostic.from(throwable);
        var messages = new ArrayList<String>();
        messages.add(diagnostic.toString());
        Set<Throwable> visited = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        var queue = new java.util.ArrayDeque<Throwable>();
        queue.add(throwable);
        while (!queue.isEmpty() && visited.size() < 16) {
            var current = queue.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            var text = new StringBuilder("异常类型：").append(current.getClass().getName()).append("\n");
            for (var frame : java.util.Arrays.stream(current.getStackTrace()).limit(24).toList()) {
                text.append("    at ").append(frame).append("\n");
            }
            messages.add(text.toString());
            // 根因优先，资源关闭失败按同样安全规则保留有限结构。
            if (current.getCause() != null) {
                queue.addFirst(current.getCause());
            }
            java.util.Arrays.stream(current.getSuppressed()).limit(4).forEach(queue::addLast);
        }
        return List.copyOf(messages);
    }

    /**
     * 构建异常信息字符串，包含异常类型、信息和堆栈跟踪。
     * @param throwable 要处理的异常对象。
     * @return 格式化的异常信息字符串。
     */
    public String buildExceptionMessage(@org.jspecify.annotations.Nullable Throwable throwable) {
        return String.join("\n", buildExceptionMessageList(throwable));
    }
}
