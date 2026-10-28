package org.zexnocs.teanekoagent.llm.framework.message.content;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMContentPart;
import java.util.Map;

/**
 * 无法解析的 Content Part 使用该类。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.4.0
 * @version 4.5.0-l
 */
@Getter
@Setter
@SuperBuilder
public class DefaultContentPart implements ILLMContentPart {
    /** 尚未支持的内容数据；适配器需显式实现映射，不能用 toString 发送。 */
    private Map<String, Object> dataMap;
}
