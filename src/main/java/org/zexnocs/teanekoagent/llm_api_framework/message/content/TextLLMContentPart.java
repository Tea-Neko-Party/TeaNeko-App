package org.zexnocs.teanekoagent.llm_api_framework.message.content;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.zexnocs.teanekoagent.llm_api_framework.message.interfaces.ILLMContentPart;
import org.zexnocs.teanekoapp.message.api.content.ITextTeaNekoContentPart;

/**
 * 纯文本信息
 *
 * @author zExNocs
 * @date 2026/03/20
 * @since 4.4.0
 */
@Setter
@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TextLLMContentPart implements ILLMContentPart, ITextTeaNekoContentPart {
    /**
     * 文本内容。
     */
    private String text;
}
