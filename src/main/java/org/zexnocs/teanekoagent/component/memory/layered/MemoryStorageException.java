package org.zexnocs.teanekoagent.component.memory.layered;

import org.zexnocs.teanekocore.logger.IFailureReason;
import tools.jackson.core.JacksonException;

import java.io.Serial;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 持久记忆格式错误的安全诊断，只公开受控结构字段，不记录原始 JSON 或 Jackson 消息。
 * <br>2026/11/06：旧数据兼容以外的读取失败保留原异常及可公开的类型、字段定位。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.3-l
 * @version 4.8.3-l
 */
public final class MemoryStorageException extends IllegalStateException implements IFailureReason {
    @Serial
    private static final long serialVersionUID = 1L;
    private static final Set<String> FIELDS = Set.of("through", "lease", "until", "retryAt", "failures",
            "claimedThrough", "fingerprint", "trouble", "revision", "invalidAttempts", "code", "at",
            "sequence", "message", "source", "role", "occurredAt", "expiresAt", "stream", "reference",
            "namespaceId", "generation", "bindingRevision", "segments", "quotes", "kind", "status",
            "createdAt", "updatedAt", "digest", "time", "text", "brief", "details");
    private final String recordType;
    private final String field;

    /**
     * @param type 调用方固定的领域类型，不从持久正文推断
     * @param cause 原解析异常，只向诊断器开放原因链
     */
    public MemoryStorageException(Class<?> type, JacksonException cause) {
        super("持久记忆数据与当前结构不匹配。", cause);
        recordType = type.getSimpleName();
        field = cause.getPath().stream().limit(8).map(reference -> {
            var name = reference.getPropertyName();
            return name != null && FIELDS.contains(name) ? name : "[隐藏字段]";
        }).collect(Collectors.joining("."));
    }

    /** @return 不含原文、动态映射键或第三方异常消息的结构定位 */
    @Override
    public String failureReason() {
        return "MEMORY_STORAGE_FORMAT（持久记忆数据与当前结构不匹配），记录类型=" + recordType
                + "，字段=" + (field.isEmpty() ? "未定位" : field)
                + "；请核对程序版本及数据格式，不会自动重置原记录。";
    }
}
