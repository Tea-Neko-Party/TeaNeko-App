package org.zexnocs.teanekoagent.llm_api.api.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.zexnocs.teanekoagent.llm_api.api.message.content.TextLLMContentPart;
import org.zexnocs.teanekoagent.llm_api.api.message.interfaces.ILLMContent;
import org.zexnocs.teanekoagent.llm_api.api.message.interfaces.ILLMMessage;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.annotation.JsonSerialize;

import java.util.List;

/**
 * LLM Message 的抽象类
 *
 * @author zExNocs
 * @date 2026/03/21
 * @since 4.4.0
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonPropertyOrder({"role", "content"})
public abstract class AbstractLLMMessage implements ILLMMessage {
    /**
     * content 列表
     *
     */
    @JsonProperty("content")
    @JsonSerialize(using = LLMContentSerializer.class)
    private List<ILLMContent> contents;

    /**
     * 提供一个默认的 Deserializer。
     *
     * @author zExNocs
     * @date 2026/03/21
     * @since 4.4.0
     */
    public static class LLMContentSerializer extends ValueSerializer<List<ILLMContent>> {
        /**
         * 如果只有一个 Text Content，则将 contents 翻译成纯 string 形式；
         * 否则就使用默认的 list object 模式
         *
         * @param contents Value to serialize; can <b>not</b> be null.
         * @param gen   Generator used to output resulting Json content
         * @param cTxt  Context that can be used to get serializers for
         *              serializing Objects value contains, if any.
         */
        @Override
        public void serialize(List<ILLMContent> contents, JsonGenerator gen, SerializationContext cTxt) throws JacksonException {
            // 1. null 或空 → 输出 null 或 []
            if (contents == null) {
                gen.writeNull();
                return;
            }
            // 2. 空，则返回 ""
            if (contents.isEmpty()) {
                gen.writeString("");
                return;
            }

            // 3. 如果全部都是 text 的话，就合并成一个大 String
            boolean allText = true;
            for (ILLMContent content : contents) {
                // 假设获取 Part 的逻辑，如果是 null 或者不是 TextLLMContentPart，则标记非全文本
                if (content == null || !(content.getContentPart() instanceof TextLLMContentPart)) {
                    allText = false;
                    break;
                }
            }

            if (allText) {
                // 3. 全文本合并模式：合并为单个 String
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < contents.size(); i++) {
                    if (contents.get(i).getContentPart() instanceof TextLLMContentPart textPart) {
                        if (i > 0) sb.append(" ");
                        sb.append(textPart.getText());
                    }
                }
                gen.writeString(sb.toString());
            } else {
                // 4. 混合模式：fallback
                gen.writeStartArray();
                for (ILLMContent content : contents) {
                    if (content == null) {
                        gen.writeNull();
                        continue;
                    }
                    ValueSerializer<Object> serializer = cTxt.findValueSerializer(content.getClass());
                    serializer.serialize(content, gen, cTxt);
                }
                gen.writeEndArray();
            }
        }
    }
}
