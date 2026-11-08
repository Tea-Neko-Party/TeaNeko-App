package org.zexnocs.teanekoagent.llm.framework.message.content;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 统一的图像细节请求；具体模型可只支持其中一部分。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：迁入独立 LLM 子模块，保留公开协议与原配置语义。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
public enum LLMImageDetail {
    AUTO, LOW, HIGH, ORIGINAL;

    /**
     * 生成协议使用的固定小写细节级别，不受运行环境语言设置影响。
     *
     * @return 按本方法契约生成的文本或标识
     */
    @JsonValue
    public String getValue() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
