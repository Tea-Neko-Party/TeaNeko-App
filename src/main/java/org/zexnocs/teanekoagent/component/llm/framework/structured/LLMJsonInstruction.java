package org.zexnocs.teanekoagent.component.llm.framework.structured;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.zexnocs.teanekoagent.component.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.component.llm.framework.message.interfaces.ILLMContent;
import org.zexnocs.teanekoagent.component.llm.framework.message.interfaces.ILLMSystemMessage;

import java.util.List;
import java.util.Objects;

/**
 * 宿主生成的不可变结构指令；以类型识别，禁止按用户正文猜测并删除 Schema。
 * <br>2026/11/06：原生 Schema 模式只保留行为约束，其他模式保留完整字段说明。
 *
 * @param guidance 不依赖协议能力的 JSON 行为约束
 * @param schema 固定的完整 Schema
 * @param nativeSchema 是否已由协议携带 Schema
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.6-l
 * @version 4.8.6-l
 */
public record LLMJsonInstruction(@JsonIgnore String guidance, @JsonIgnore String schema,
                                 @JsonIgnore boolean nativeSchema) implements ILLMSystemMessage {
    /**
     * 固定不含运行句柄的声明。
     * @param guidance 行为约束
     * @param schema 完整结构
     * @param nativeSchema 协议是否承载结构
     */
    public LLMJsonInstruction {
        Objects.requireNonNull(guidance);
        Objects.requireNonNull(schema);
    }

    /** @return 每次生成独立内容容器，调用者不能修改原始合同 */
    @Override
    public List<ILLMContent> getContents() {
        return new LLMContentListBuilder().addText(guidance
                + (nativeSchema ? "字段结构遵循协议 response_format 中的 JSON Schema。" : "JSON Schema：" + schema)).build();
    }
}
