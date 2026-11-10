package org.zexnocs.teanekoagent.llm.instance.antigravity;

import org.zexnocs.teanekoagent.llm.framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.LLMProviderAccess;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.LLMResponseFormat;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMFunctionParameterJsonMapper;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 将统一参数映射为原生生成请求，结构合同和输出额度不能被扩展参数覆盖。
 * <br>2026/11/10：Schema 通过 responseJsonSchema 发送；模型安全追加至可信集合路径。
 * <br>2026/11/10：流式路径由显式选项决定，查询参数只来自本地固定协议。
 *
 * @author zExNocs
 * @date 2026/11/10
 * @since 4.9.1-l
 * @version 4.9.1-l
 */
public final class AntigravityRequestMapper {
    /** 纯协议转换器不保存运行状态。 */
    private AntigravityRequestMapper() { }

    /**
     * @param prompt 完整输入
     * @param options 已解析参数
     * @param access 同代次可信连接
     * @param mapper JSON 转换器
     * @return 不修改原消息和参数的原生请求
     * @throws IllegalArgumentException 当模型、路径或参数不满足原生协议时
     */
    public static AntigravityRequestData toRequest(ILLMPrompt prompt, LLMModelOptions options,
            LLMProviderAccess access, ObjectMapper mapper) {
        var messages = AntigravityMessages.encode(prompt.getMessages(), mapper);
        var generation = generation(options);
        var declarations = new ArrayList<Map<String, Object>>();
        for (var tool : options.findTools().orElse(List.of())) {
            declarations.add(Map.of("name", tool.getName(), "description", tool.getDescription(),
                    "parametersJsonSchema", LLMFunctionParameterJsonMapper.toJsonSchema(tool.getParameters())));
        }
        var tools = declarations.isEmpty() ? List.<Map<String, Object>>of()
                : List.<Map<String, Object>>of(Map.of("functionDeclarations", List.copyOf(declarations)));
        // 2026/11/10：先校验可信集合和型号，再选择原生生成动作。
        var path = modelPath(access.apiPathOr(AntigravityModel.DEFAULT_API_PATH), options.getModel());
        if (options.findStream().orElse(true)) {
            path = path.substring(0, path.length() - "generateContent".length()) + "streamGenerateContent?alt=sse";
        }
        return new AntigravityRequestData(access.baseUrlOr(AntigravityModel.DEFAULT_BASE_URL), path, access.apiKey(),
                messages.contents(), messages.systemInstruction(), generation, tools, toolConfig(options));
    }

    /**
     * @param collection 可信配置中的模型集合路径
     * @param model 模型标识，允许标准 models/ 前缀
     * @return 不含查询、跳转或外部地址的 generateContent 路径
     * @throws IllegalArgumentException 当输入会改变端点或已包含模型动作时
     */
    public static String modelPath(String collection, String model) {
        String name = model.startsWith("models/") ? model.substring(7) : model;
        if (!name.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}") || name.contains("..")
                || !collection.matches("/(?:[A-Za-z0-9_-]+/)*models/?")) {
            throw new IllegalArgumentException("原生模型请求要求合法型号及以 /models 结尾的集合路径，不填写生成动作。");
        }
        return collection.replaceFirst("/$", "") + "/" + name + ":generateContent";
    }

    /**
     * @param options 已解析生成参数
     * @return 完整生成约束，不补隐含 token 上限
     * @throws IllegalArgumentException 当扩展覆盖受控参数或思考配置相互冲突时
     */
    private static Map<String, Object> generation(LLMModelOptions options) {
        var result = new LinkedHashMap<String, Object>();
        options.findMaxTokens().ifPresent(value -> result.put("maxOutputTokens", value));
        options.findTemperature().ifPresent(value -> result.put("temperature", value));
        options.findTopP().ifPresent(value -> result.put("topP", value));
        options.findPresencePenalty().ifPresent(value -> result.put("presencePenalty", value));
        options.findFrequencyPenalty().ifPresent(value -> result.put("frequencyPenalty", value));
        options.findStopWords().filter(words -> !words.isEmpty()).ifPresent(value -> result.put("stopSequences", value));
        if (options.findResponseFormat().orElse(LLMResponseFormat.TEXT) == LLMResponseFormat.JSON) {
            result.put("responseMimeType", "application/json");
            options.findResponseSchema().ifPresent(schema -> result.put("responseJsonSchema", schema.schema()));
        }
        var extensions = options.findMetadata().orElse(Map.of());
        for (String key : extensions.keySet()) {
            if (key.startsWith("body.") || key.startsWith("antigravity.")
                    && !Set.of("antigravity.thinkingLevel", "antigravity.thinkingBudget").contains(key)) {
                throw new IllegalArgumentException("原生模型扩展字段未声明，不能覆盖消息、工具、Schema 或生成额度。");
            }
        }
        var level = extensions.get("antigravity.thinkingLevel");
        var budget = extensions.get("antigravity.thinkingBudget");
        if (level != null && budget != null || (level != null || budget != null) && options.findThinking().isPresent()) {
            throw new IllegalArgumentException("思考开关、thinkingLevel 和 thinkingBudget 只能设置其中一项。");
        }
        if (level != null) {
            if (!(level instanceof String text)
                    || !Set.of("minimal", "low", "medium", "high").contains(text.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("thinkingLevel 仅允许 minimal、low、medium 或 high。");
            }
            result.put("thinkingConfig", Map.of("thinkingLevel", text.toUpperCase(Locale.ROOT)));
        } else if (budget != null) {
            if (!(budget instanceof Integer value) || value < -1) {
                throw new IllegalArgumentException("thinkingBudget 要求不小于 -1 的整数，具体上限由模型决定。");
            }
            result.put("thinkingConfig", Map.of("thinkingBudget", value));
        } else {
            options.findThinking().ifPresent(enabled -> result.put("thinkingConfig",
                    Map.of("thinkingBudget", enabled ? -1 : 0)));
        }
        return Map.copyOf(result);
    }

    /**
     * @param options 已冻结的工具声明和选择
     * @return 原生函数调用策略，无工具时不生成无效配置
     * @throws IllegalArgumentException 当指定工具不在本次目录中时
     */
    private static Map<String, Object> toolConfig(LLMModelOptions options) {
        var tools = options.findTools().orElse(List.of());
        var choice = options.findToolChoice().orElse("auto");
        if (tools.isEmpty()) {
            if (!Set.of("auto", "none").contains(choice)) {
                throw new IllegalArgumentException("指定工具策略需要本次存在可用工具。");
            }
            return Map.of();
        }
        var config = switch (choice) {
            case "auto" -> Map.<String, Object>of("mode", "AUTO");
            case "none" -> Map.<String, Object>of("mode", "NONE");
            case "required" -> Map.<String, Object>of("mode", "ANY");
            default -> {
                if (tools.stream().noneMatch(tool -> choice.equals(tool.getName()))) {
                    throw new IllegalArgumentException("原生工具选择未在本次工具目录中声明。");
                }
                yield Map.of("mode", "ANY", "allowedFunctionNames", List.of(choice));
            }
        };
        return Map.of("functionCallingConfig", config);
    }
}
