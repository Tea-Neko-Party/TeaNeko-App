package org.zexnocs.teanekoagent.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.NonNull;

import java.util.Objects;

/**
 * 宿主从当前原始用户消息提取的文本凭据，与包含历史、引用或模型内容的消息列表分开。
 * 只保存授权判定所需数据，不替代 App 原生消息；平台身份和原文真实性由可信入口保证。
 * @param sourceId 当前原消息标识，必须与请求 actor 一致
 * @param text 当前原消息的未改写文本，不含转发或引用正文
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
@JsonIgnoreType
public record AgentUserText(String sourceId, String text) {
    /**
     * 保持原文不变并限制附加凭据大小，不从该数据自行推断用户已授权。
     * @param sourceId 原消息标识
     * @param text 真实原文，最多 65536 个 Java 字符
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
