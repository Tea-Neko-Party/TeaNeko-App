package org.zexnocs.teanekoagent.llm_api_framework.message.content;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.zexnocs.teanekoagent.llm_api_framework.message.interfaces.ILLMContentPart;
import org.zexnocs.teanekoapp.message.content.DefaultTeaNekoContentPart;

/**
 * 无法解析的 Content Part 使用该类。
 *
 * @author zExNocs
 * @date 2026/03/20
 * @since 4.4.0
 */
@Getter
@Setter
@SuperBuilder
public class DefaultContentPart extends DefaultTeaNekoContentPart implements ILLMContentPart {
}
