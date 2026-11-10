package org.zexnocs.teanekoagent.llm.framework.structured;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMContent;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMSystemMessage;

import java.util.List;
import java.util.Objects;

/**
 * 宿主生成的不可变结构指令；以类型识别，禁止按用户正文猜测并删除 Schema。
 * <br>2026/11/06：原生 Schema 模式只保留行为约束，其他模式保留完整字段说明。
 *
 * @param guidance 不依赖协议能力的 JSON 行为约束
 * @param schema 固定的完整 Schema
 * @param nativeSchema 是否已由协议携带 Schema
 * <br>2026/11/07：迁入独立 LLM 子模块，保留公开协议与原配置语义。
 * <br>2026/11/10：原生提示使用协议无关描述，避免将 Responses 的结构字段误称为 response_format。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.6-l
 * @version 4.9.1-l
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
                // 2026/11/10：具体协议字段由适配器决定，不在通用指令里误指向另一个 API。
                + (nativeSchema ? "字段结构遵循本次请求携带的 JSON Schema。" : "JSON Schema：" + schema)).build();
    }
}
