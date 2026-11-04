package org.zexnocs.teanekoagent.component.llm.framework.message.content;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.zexnocs.teanekoagent.component.llm.framework.message.interfaces.ILLMContentPart;
import java.util.Map;

/**
 * 无法解析的 Content Part 使用该类。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.4.0
 * @version 4.8.0-l
 */
@Getter
@Setter
@SuperBuilder
public class DefaultContentPart implements ILLMContentPart {
    /** 尚未支持的内容数据；适配器需显式实现映射，不能用 toString 发送。 */
    private Map<String, Object> dataMap;
}
