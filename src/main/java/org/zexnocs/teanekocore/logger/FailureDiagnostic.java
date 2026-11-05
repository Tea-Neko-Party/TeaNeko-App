package org.zexnocs.teanekocore.logger;

import lombok.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import java.util.*;

/**
 * 可安全记录的异常结构，不读取异常消息、请求、响应正文、请求头或抑制异常。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/05：提供共享的 HTTP、网络及业务安全原因，未知错误保留代码位置。
 * <br>2026/11/05：继承业务异常携带的安全诊断，不将已有 HTTP 状态和原始位置丢失为未知异常。
 *
 * @param types 有界原因链中的异常类型，按外层到内层排列
 * @param location 最深可观察异常的代码位置
 * @param httpStatus 原因链中可直接取得的 HTTP 状态码，非 HTTP 失败为空
 * @param reason 可信异常声明的公开原因，未声明时为空
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.0-l
 * @version 4.8.3-l
 */
public record FailureDiagnostic(List<String> types, String location, @Nullable Integer httpStatus,
                                @Nullable String reason) {
    /**
     * 固定类型链，不保留原 Throwable 或其他运行资源。
     * @param types 异常类型链
     * @param location 代码位置
     * @param httpStatus 可选 HTTP 状态码
     * @param reason 可选的可信业务原因
     */
    public FailureDiagnostic {
        // 2026/11/05：新增原因字段仍不持有异常对象，类型链保持不可变。
        types = List.copyOf(types);
        Objects.requireNonNull(location);
    }

    /**
     * 2026/11/05：保留既有结构化诊断构造，不虚构业务原因。
     * @param types 原因链类型
     * @param location 已知代码位置
     * @param httpStatus 可选 HTTP 状态码
     */
    public FailureDiagnostic(List<String> types, String location, @Nullable Integer httpStatus) {
        this(types, location, httpStatus, null);
    }

    /**
     * 遍历至多十六层原因，识别循环且不调用 getMessage 或异常 toString。
     * 2026/11/05：跨结果传播时合并原脱敏快照，仍保留异常类型的有界长度。
     * @param failure 实际观察到的失败
     * @return 只含类型、状态和位置的结构
     */
    public static FailureDiagnostic from(Throwable failure) {
        Objects.requireNonNull(failure, "诊断异常不能为空。");
        var visited = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        var types = new ArrayList<String>();
        var current = failure;
        var deepest = failure;
        Integer status = null;
        // 2026/11/05：只读取显式安全契约，不调用第三方异常的消息或响应正文。
        String reason = null;
        FailureDiagnostic inherited = null;
        while (current != null && types.size() < 16 && visited.add(current)) {
            types.add(current.getClass().getName());
            deepest = current;
            if (current instanceof WebClientResponseException response) {
                status = response.getStatusCode().value();
            }
            if (current instanceof IFailureReason known) {
                reason = known.failureReason();
                var diagnostic = known.failureDiagnostic();
                if (diagnostic != null) {
                    inherited = diagnostic;
                    if (diagnostic.httpStatus() != null) {
                        status = diagnostic.httpStatus();
                    }
                }
            }
            if (current instanceof org.zexnocs.teanekocore.actuator.execution.ExecutionStoppedException stopped) {
                reason = stopped.reason() == org.zexnocs.teanekocore.actuator.execution.ExecutionControl.StopReason.DEADLINE_EXCEEDED
                        ? "请求超过允许的等待或执行时限。" : "请求已取消或所属服务已关闭。";
            }
            current = current.getCause();
        }
        var stack = deepest.getStackTrace();
        String location = "未知代码位置";
        if (stack.length > 0) {
            var selected = stack[0];
            for (var frame : stack) {
                if (frame.getClassName().startsWith("org.zexnocs.")) {
                    selected = frame;
                    break;
                }
            }
            location = selected.getClassName() + "." + selected.getMethodName() + ":" + selected.getLineNumber();
        }
        // 2026/11/05：使用实际观察过的原错误类型和位置，不伪造 Throwable 或响应对象。
        if (inherited != null) {
            location = inherited.location();
            for (var type : inherited.types()) {
                if (types.size() == 16) {
                    break;
                }
                if (!types.contains(type)) {
                    types.add(type);
                }
            }
        }
        return new FailureDiagnostic(types, location, status, reason);
    }

    /**
     * 2026/11/05：统一模型检测、聊天回执和日志的可读原因，不把 403 错称为确定的密钥无效。
     * @return 固定提示或未知异常的代码位置
     */
    public String description() {
        if (reason != null) {
            return reason;
        }
        if (httpStatus != null) {
            return "HTTP " + httpStatus + "：" + switch (httpStatus) {
                case 400, 422 -> "请求参数或模型协议不被接受，请检查模型名称和参数。";
                case 401 -> "认证失败，请检查 API key 是否缺失、失效或填写错误。";
                case 402 -> "账户余额或付费资格不足。";
                case 403 -> "访问被拒绝，请检查 API key 的模型权限、IP 或地区限制。";
                case 404 -> "接口或模型不存在，请检查服务地址、接口路径及模型名称。";
                case 408, 504 -> "模型服务或上游网关超时。";
                case 413 -> "请求内容超过服务限制。";
                case 429 -> "请求过于频繁或额度不足，请检查供应商限额。";
                default -> httpStatus >= 500 ? "模型服务暂时不可用。" : "模型请求被拒绝；位置：" + location;
            };
        }
        if (types.stream().anyMatch(type -> type.endsWith("UnknownHostException"))) {
            return "模型服务域名解析失败，请检查服务地址与 DNS。";
        }
        if (types.stream().anyMatch(type -> type.endsWith("TimeoutException"))) {
            return "模型连接或响应超时。";
        }
        if (types.stream().anyMatch(type -> type.endsWith("SSLHandshakeException") || type.endsWith("SSLException"))) {
            return "模型 HTTPS 握手失败，请检查证书和协议。";
        }
        if (types.stream().anyMatch(type -> type.endsWith("ConnectException") || type.endsWith("WebClientRequestException"))) {
            return "无法连接模型服务，请检查地址、端口、网络和代理。";
        }
        // 2026/11/05：业务原因由 IFailureReason 主动声明，Core 不按上层类型名称反向分派。
        return "未识别异常；位置：" + location;
    }

    /** @return 不包含原异常文本的中文诊断摘要 */
    @Override
    @NonNull
    public String toString() {
        // 2026/11/05：原因与原结构一起输出，供请求关联且不泄露第三方正文。
        return "原因=" + description() + "，异常类型=" + String.join(" → ", types)
                + "，代码位置=" + location + "，HTTP状态=" + (httpStatus == null ? "未取得" : httpStatus);
    }
}
