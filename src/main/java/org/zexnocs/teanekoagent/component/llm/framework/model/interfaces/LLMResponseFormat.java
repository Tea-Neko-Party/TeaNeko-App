package org.zexnocs.teanekoagent.component.llm.framework.model.interfaces;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

/**
 * LLM 回复的格式。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/03/24
 * @since 4.4.0
 * @version 4.8.0-l
 */
public enum LLMResponseFormat {
    /**
     * 纯文本形式
     */
    TEXT,

    /**
     * JSON 形式
     */
    JSON
}
