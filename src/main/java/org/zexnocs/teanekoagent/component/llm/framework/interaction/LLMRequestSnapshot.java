package org.zexnocs.teanekoagent.component.llm.framework.interaction;

import org.zexnocs.teanekoagent.component.llm.framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent.component.llm.framework.message.LLMProtocolSnapshots;
import org.zexnocs.teanekoagent.component.llm.framework.message.interfaces.ILLMMessage;
import org.zexnocs.teanekoagent.component.llm.framework.tool.interfaces.ILLMFunctionParameter;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 实际模型调用边界的临时输入快照，不保留凭据、执行器或任意模型扩展参数。
 * <br>2026/11/06：包含工具定义及临时纠正提示，结束后随交互结果释放，不写业务数据库。
 * <br>2026/11/06：将长度、数量或枚举约束纳入协议声明，并在本地落实校验。
 *
 * @param messages 本次实际提交的完整消息
 * @param declaration 模型、工具及输出结构的纯 JSON 声明
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.5-l
 * @version 4.8.6-l
 */
public record LLMRequestSnapshot(List<ILLMMessage> messages, String declaration) {
    private static final JsonMapper JSON = new JsonMapper();

    /**
     * 固定输入，避免后续工具续轮改变此前观察。
     * @param messages 原输入消息
     * @param declaration 不含执行器的声明
     */
    public LLMRequestSnapshot {
        messages = LLMProtocolSnapshots.messages(messages);
    }

    /**
     * 只选择协议声明字段，禁止序列化 options.metadata 中可能存在的凭据。
     * @param prompt 即将提交给模型端口的输入
     * @return 本次不可变观察
     */
    public static LLMRequestSnapshot from(ILLMPrompt prompt) {
        var fields = new LinkedHashMap<String, Object>();
        var options = prompt.getOptions();
        if (options != null) {
            options.findProvider().ifPresent(value -> fields.put("provider", value));
            options.findModel().ifPresent(value -> fields.put("model", value));
            options.findToolChoice().ifPresent(value -> fields.put("toolChoice", value));
            options.findResponseFormat().ifPresent(value -> fields.put("responseFormat", value.name()));
            options.findResponseSchema().ifPresent(value -> fields.put("responseSchema", value.schema()));
            fields.put("tools", options.findTools().orElse(List.of()).stream().map(tool -> Map.of(
                    "name", tool.getName(), "description", tool.getDescription(),
                    "parameters", schema(tool.getParameters()), "strict", tool.isStrict())).toList());
        }
        return new LLMRequestSnapshot(prompt.getMessages(), JSON.writeValueAsString(fields));
    }

    /**
     * 从已缓存的类型描述投影 Schema，不反射扫描工具或保留其实现。
     * @param parameter 冻结的参数声明
     * @return 纯 JSON 对象
     */
    private static Map<String, Object> schema(ILLMFunctionParameter parameter) {
        // 2026/11/06：观察快照同步保留实际协议的容量约束。
        var value = new LinkedHashMap<String, Object>(parameter.getLimits().schema());
        value.put("type", parameter.getType());
        if (parameter.getDescription() != null) {
            value.put("description", parameter.getDescription());
        }
        if ("object".equals(parameter.getType())) {
            var properties = new LinkedHashMap<String, Object>();
            parameter.getProperties().forEach((name, child) -> properties.put(name, schema(child)));
            value.put("properties", properties);
            value.put("required", parameter.getRequired());
            value.put("additionalProperties", parameter.isAdditionalProperties());
        } else if ("array".equals(parameter.getType()) && parameter.getItems() != null) {
            value.put("items", schema(parameter.getItems()));
        }
        if (!parameter.getEnumValues().isEmpty()) {
            value.put("enum", parameter.getEnumValues());
        }
        return value;
    }
}
