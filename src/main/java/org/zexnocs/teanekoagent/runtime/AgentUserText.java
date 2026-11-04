package org.zexnocs.teanekoagent.runtime;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/**
 * 宿主从当前原始用户消息提取的文本凭据，与包含历史、引用或模型内容的消息列表分开。
 * 只保存授权判定所需数据，不替代 App 原生消息；平台身份和原文真实性由可信入口保证。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @param sourceId 当前原消息标识，必须与请求 actor 一致
 * @param text 当前原消息的未改写文本，不含转发或引用正文
 * @param occurredAt 平台原消息时刻，旧独立宿主未知时为空，不能由模型提供
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.5.0-l
 * @version 4.8.0-l
 */
@JsonIgnoreType
public record AgentUserText(String sourceId, String text, @Nullable Instant occurredAt) {
    /**
     * 保留不知道真实平台时间的旧宿主入口，历史须明确标注为处理时间估算。
     * @param sourceId 真实原消息 ID
     * @param text 当前原文
     */
    public AgentUserText(String sourceId, String text) { this(sourceId, text, null); }
    /**
     * 保持原文不变并限制附加凭据大小，不从该数据自行推断用户已授权。
     * @param sourceId 原消息标识
     * @param text 真实原文，最多 65536 个 Java 字符
     * @param occurredAt 真实平台时刻或未知
     * @throws NullPointerException 当来源或文本为空时
     * @throws IllegalArgumentException 当来源标识或文本长度非法时
     */
    public AgentUserText {
        AgentScope.requireIdentity(sourceId); Objects.requireNonNull(text, "原始用户文本不能为空。");
        if (text.length() > 65536) { throw new IllegalArgumentException("原始用户文本超出凭据大小上限。"); }
    }

    /**
     * 避免通用日志或快照输出授权原文，不影响原消息按正常协议进入对话。
     * @return 固定脱敏说明
     */
    @Override @JsonValue @NonNull
    public String toString() { return "原始用户文本[已隐藏来源与正文]"; }
}
