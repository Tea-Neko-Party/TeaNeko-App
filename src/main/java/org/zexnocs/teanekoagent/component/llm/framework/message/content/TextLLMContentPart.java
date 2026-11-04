package org.zexnocs.teanekoagent.component.llm.framework.message.content;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.zexnocs.teanekoagent.component.llm.framework.message.interfaces.ILLMContentPart;

/**
 * 纯文本信息
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.4.0
 * @version 4.8.0-l
 */
@Setter
@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TextLLMContentPart implements ILLMContentPart {
    /**
     * 文本内容。
     */
    private String text;
}
