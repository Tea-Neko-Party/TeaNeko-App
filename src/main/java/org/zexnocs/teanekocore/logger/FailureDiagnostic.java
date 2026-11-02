package org.zexnocs.teanekocore.logger;

import org.springframework.web.reactive.function.client.WebClientResponseException;
import java.util.*;

/**
 * 可安全记录的异常结构，不读取异常消息、请求、响应正文、请求头或抑制异常。
 * @param types 有界原因链中的异常类型，按外层到内层排列
 * @param location 最深可观察异常的代码位置
 * @param httpStatus 原因链中可直接取得的 HTTP 状态码，非 HTTP 失败为空
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.0.0
 * @version 4.6.0-l
 */
public record FailureDiagnostic(List<String> types, String location, Integer httpStatus) {
    /**
     * 固定类型链，不保留原 Throwable 或其他运行资源。
     * @param types 异常类型链
     * @param location 代码位置
     * @param httpStatus 可选 HTTP 状态码
     */
    public FailureDiagnostic { types = List.copyOf(types); Objects.requireNonNull(location); }

    /**
     * 遍历至多十六层原因，识别循环且不调用 getMessage 或异常 toString。
     * @param failure 实际观察到的失败
     * @return 只含类型、状态和位置的结构
     */
    public static FailureDiagnostic from(Throwable failure) {
        Objects.requireNonNull(failure, "诊断异常不能为空。");
        var visited = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        var types = new ArrayList<String>(); var current = failure; var deepest = failure; Integer status = null;
        while (current != null && types.size() < 16 && visited.add(current)) {
            types.add(current.getClass().getName()); deepest = current;
            if (current instanceof WebClientResponseException response) { status = response.getStatusCode().value(); }
            current = current.getCause();
        }
        var stack = deepest.getStackTrace(); String location = "未知代码位置";
        if (stack.length > 0) {
            var selected = stack[0];
            for (var frame : stack) {
                if (frame.getClassName().startsWith("org.zexnocs.")) { selected = frame; break; }
            }
            location = selected.getClassName() + "." + selected.getMethodName() + ":" + selected.getLineNumber();
        }
        return new FailureDiagnostic(types, location, status);
    }

    /** @return 不包含原异常文本的中文诊断摘要 */
    @Override public String toString() {
        return "异常类型=" + String.join(" → ", types) + "，代码位置=" + location + "，HTTP状态=" + (httpStatus == null ? "未取得" : httpStatus);
    }
}
