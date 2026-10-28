package org.zexnocs.teanekoagent.llm.framework.message.content;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 统一的图像细节请求；具体模型可只支持其中一部分。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
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
